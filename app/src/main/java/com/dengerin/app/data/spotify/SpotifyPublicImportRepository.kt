package com.dengerin.app.data.spotify

import android.util.Log
import com.dengerin.app.core.hdCoverUrl
import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.domain.model.SpotifyTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ImportedPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val tracks: List<SpotifyTrack>,
)

private data class ImageCandidate(
    val url: String,
    val width: Int,
    val height: Int,
)

/**
 * Impor playlist publik hanya dari link, tanpa login Spotify.
 *
 * Dua jalur:
 * 1. [fetchFull] — token anonim dari halaman embed + Partner API yang di-page
 *    100 lagu per halaman. Dipakai untuk playlist besar (widget embed cuma
 *    mengirim 100 lagu pertama).
 * 2. [fetchEmbed] — fallback bila token/Partner API tidak tersedia, tetap
 *    memberi hasil walaupun terpotong ~100 lagu.
 */
class SpotifyPublicImportRepository {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun import(
        link: String,
        onProgress: (String) -> Unit = {},
    ): Result<ImportedPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistId(link)
            try {
                onProgress("Mengambil daftar lagu lengkap dari Spotify…")
                fetchFull(playlistId, onProgress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Token anonim bukan API resmi dan bisa berubah; fallback ke embed
                // supaya impor tetap berhasil walaupun terpotong.
                Log.w(TAG, "Impor penuh gagal, pakai embed: ${error.message}")
                onProgress("Daftar penuh tidak tersedia, memakai versi ringkas…")
                fetchEmbed(playlistId)
            }
        }
    }

    private fun extractPlaylistId(link: String): String {
        val trimmed = link.trim()
        if (trimmed.startsWith("spotify:playlist:", ignoreCase = true)) {
            return trimmed.substring("spotify:playlist:".length)
                .substringBefore("?")
                .substringBefore("/")
        }
        PLAYLIST_URL_REGEX.find(trimmed)?.let { return it.groupValues[1] }
        if (trimmed.contains("spotify.link/", ignoreCase = true)) {
            val request = Request.Builder().url(trimmed).get().build()
            client.newCall(request).execute().use { response ->
                PLAYLIST_URL_REGEX.find(response.request.url.toString())
                    ?.let { return it.groupValues[1] }
                val body = response.body?.string().orEmpty()
                PLAYLIST_URL_REGEX.find(body)?.let { return it.groupValues[1] }
            }
        }
        if (trimmed.matches(Regex("""[A-Za-z0-9]{22}"""))) return trimmed
        throw IllegalArgumentException("Link playlist tidak dikenali. Tempel link open.spotify.com/playlist/... ya.")
    }

    /**
     * Ambil seluruh lagu lewat Partner API memakai token anonim yang disematkan
     * Spotify di halaman embed. Web API publik sering 429 untuk token anonim,
     * sehingga jalur ini lebih stabil sekaligus membawa metadata lengkap.
     */
    private suspend fun fetchFull(
        playlistId: String,
        onProgress: (String) -> Unit,
    ): ImportedPlaylist {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val embedHtml = client.newCall(
            Request.Builder()
                .url(embedUrl)
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
                .get()
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Gagal memuat playlist (HTTP ${response.code}).")
            }
            response.body?.string().orEmpty()
        }
        val token = TOKEN_REGEX.find(embedHtml)?.groupValues?.get(1)
            ?.takeIf { it.length > 20 }
            ?: throw IllegalStateException("Token anonim Spotify tidak ditemukan.")

        suspend fun partnerQuery(offset: Int): JSONObject {
            val payload = JSONObject()
                .put(
                    "variables",
                    JSONObject()
                        .put("uri", "spotify:playlist:$playlistId")
                        .put("offset", offset)
                        .put("limit", PAGE_SIZE)
                        .put("enableWatchFeedEntrypoint", false),
                )
                .put("operationName", "fetchPlaylist")
                .put(
                    "extensions",
                    JSONObject().put(
                        "persistedQuery",
                        JSONObject()
                            .put("version", 1)
                            .put("sha256Hash", PLAYLIST_QUERY_HASH),
                    ),
                )
            val request = Request.Builder()
                .url("https://api-partner.spotify.com/pathfinder/v1/query")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("App-Platform", "WebPlayer")
                .header("Spotify-App-Version", WEB_PLAYER_VERSION)
                .header("Origin", "https://open.spotify.com")
                .header("Referer", "https://open.spotify.com/")
                .header("User-Agent", WEB_USER_AGENT)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            for (attempt in 0 until PAGE_RETRIES) {
                try {
                    return client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Gagal memuat daftar lagu (HTTP ${response.code}).")
                        }
                        JSONObject(response.body?.string().orEmpty())
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    if (attempt == PAGE_RETRIES - 1) throw error
                    delay(PAGE_RETRY_DELAY_MS * (attempt + 1))
                }
            }
            throw IOException("Gagal memuat daftar lagu dari Spotify.")
        }

        val first = partnerQuery(0)
        val playlist = first.optJSONObject("data")?.optJSONObject("playlistV2")
            ?: throw IllegalStateException("Data playlist tidak ditemukan.")
        val name = playlist.optString("name").ifBlank { "Playlist Spotify" }
        val cover = findBestCoverUrlFromPartner(playlist)
        val total = playlist.optJSONObject("content")?.optInt("totalCount", 0) ?: 0
        if (total <= 0) throw IllegalStateException("Playlist ini kosong.")

        val ceiling = minOf(total, MAX_TRACKS)
        val tracks = mutableListOf<SpotifyTrack>()
        var page = first
        var offset = 0
        while (offset < ceiling) {
            val items = page.optJSONObject("data")
                ?.optJSONObject("playlistV2")
                ?.optJSONObject("content")
                ?.optJSONArray("items") ?: JSONArray()
            if (items.length() == 0) break
            for (index in 0 until items.length()) {
                parsePartnerTrack(items.optJSONObject(index))?.let { tracks += it }
            }
            offset += PAGE_SIZE
            onProgress("Mengambil lagu ${tracks.size}/$ceiling…")
            if (items.length() < PAGE_SIZE || offset >= ceiling) break
            delay(PAGE_DELAY_MS)
            page = partnerQuery(offset)
        }
        if (tracks.isEmpty()) throw IllegalStateException("Playlist ini kosong atau tidak bisa diimpor.")

        return ImportedPlaylist(
            id = playlistId,
            name = name,
            coverUrl = cover,
            tracks = tracks,
        )
    }

    /** Fallback lama: embed widget hanya berisi ~100 lagu pertama. */
    private fun fetchEmbed(playlistId: String): ImportedPlaylist {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/playlist/$playlistId")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .get()
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gagal memuat playlist (HTTP ${response.code}).")
            response.body?.string().orEmpty()
        }
        val raw = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IllegalStateException("Data playlist tidak ditemukan di halaman Spotify.")
        val root = JSONObject(raw)
        val container = findTrackListContainer(root)
            ?: throw IllegalStateException("Lagu playlist tidak ditemukan.")

        val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")
        val name = container.optString("title")
            .ifBlank { pageProps?.optString("title").orEmpty() }
            .ifBlank { "Playlist Spotify" }
        val cover = findBestCoverUrl(root, container)

        val array = container.optJSONArray("trackList") ?: JSONArray()
        val tracks = mutableListOf<SpotifyTrack>()
        for (index in 0 until array.length()) {
            val track = parseEmbedTrack(array.optJSONObject(index) ?: continue)
            if (track != null) tracks += track
        }
        if (tracks.isEmpty()) throw IllegalStateException("Playlist ini kosong atau tidak bisa diimpor.")

        return ImportedPlaylist(
            id = playlistId,
            name = name,
            coverUrl = cover,
            // Duplikat sengaja dipertahankan: Phase 5 memberi entryId unik per entri.
            tracks = tracks,
        )
    }

    /** Satu entri `fetchPlaylist`: `itemV2.data` berisi metadata lagu lengkap. */
    private fun parsePartnerTrack(item: JSONObject?): SpotifyTrack? {
        val data = item?.optJSONObject("itemV2")?.optJSONObject("data") ?: return null
        val type = data.optString("__typename")
        if (type == "NotFound" || type == "UnavailableMedia") return null
        val uri = data.optString("uri")
        val id = uri.substringAfterLast(":").ifBlank { return null }
        val title = data.optString("name").ifBlank { return null }
        val artistsArray = data.optJSONObject("artists")?.optJSONArray("items")
        val artistNames = (0 until (artistsArray?.length() ?: 0)).mapNotNull { index ->
            artistsArray?.optJSONObject(index)?.optJSONObject("profile")?.optString("name")
                ?.takeIf { it.isNotBlank() }
        }
        val artist = artistNames.joinToString(", ").ifBlank { "Artis tidak diketahui" }
        val durationMs = data.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L) ?: 0L

        val candidates = mutableListOf<ImageCandidate>()
        val sources = data.optJSONObject("albumOfTrack")?.optJSONObject("coverArt")?.optJSONArray("sources")
        if (sources != null) {
            for (index in 0 until sources.length()) {
                val source = sources.optJSONObject(index) ?: continue
                val url = source.optString("url")
                if (url.startsWith("http", ignoreCase = true)) {
                    candidates += ImageCandidate(url, source.optInt("width", 0), source.optInt("height", 0))
                }
            }
        }
        val sorted = candidates.sortedWith(
            compareByDescending<ImageCandidate> { it.width }.thenByDescending { it.height },
        )
        val cover = sorted.firstOrNull()?.url.orEmpty()
        val thumb = sorted.getOrNull(1)?.url ?: cover

        return SpotifyTrack(
            id = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            thumbnailUrl = listThumbnailUrl(thumb).orEmpty(),
            coverUrl = hdCoverUrl(cover).orEmpty(),
            uri = uri,
        )
    }

    private fun findBestCoverUrlFromPartner(playlist: JSONObject): String {
        val images = playlist.optJSONObject("images")?.optJSONArray("items") ?: return ""
        val candidates = mutableListOf<ImageCandidate>()
        for (index in 0 until images.length()) {
            val sources = images.optJSONObject(index)?.optJSONArray("sources") ?: continue
            for (sourceIndex in 0 until sources.length()) {
                val source = sources.optJSONObject(sourceIndex) ?: continue
                val url = source.optString("url")
                if (url.startsWith("http", ignoreCase = true)) {
                    candidates += ImageCandidate(url, source.optInt("width", 0), source.optInt("height", 0))
                }
            }
        }
        return candidates
            .distinctBy { it.url }
            .maxWithOrNull(
                compareBy<ImageCandidate> { it.width * it.height }.thenBy { it.url.length },
            )
            ?.url
            .orEmpty()
    }

    private fun parseEmbedTrack(json: JSONObject): SpotifyTrack? {
        val uri = json.optString("uri").ifBlank { json.optString("uid") }
        val id = uri.substringAfterLast(":").ifBlank { return null }
        val title = json.optString("title").ifBlank { return null }
        val subtitle = json.optString("subtitle")
        val artist = subtitle.split("•").firstOrNull().orEmpty().trim().ifBlank { "Artis tidak diketahui" }
        // Spotify API mengirim images[] terurut terbesar → terkecil. Pakai yang
        // medium (~300x300) untuk list dan yang pertama (~640x640) untuk cover.
        val (smallRaw, hdRaw) = parseTrackImages(json)
        return SpotifyTrack(
            id = id,
            title = title,
            artist = artist,
            durationMs = parseDuration(json.optString("duration")),
            // Tanpa gambar trek, biarkan kosong: sampul playlist tidak dipinjam.
            // SongResolver yang akan mengisi thumbnail YouTube asli.
            thumbnailUrl = listThumbnailUrl(smallRaw).orEmpty(),
            coverUrl = hdCoverUrl(hdRaw).orEmpty(),
            uri = uri,
        )
    }

    /** Pasangkan (thumbnail medium ~300px, cover HD ~640px) dari images[] Spotify. */
    private fun parseTrackImages(json: JSONObject): Pair<String, String> {
        val array = json.optJSONArray("images")
            ?: json.optJSONObject("album")?.optJSONArray("images")
            ?: json.optJSONObject("artwork")?.optJSONArray("images")
        val candidates = mutableListOf<ImageCandidate>()
        if (array != null) {
            for (index in 0 until array.length()) {
                imageCandidate(array.opt(index))?.let(candidates::add)
            }
        }
        val sorted = candidates
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<ImageCandidate> { it.width }
                    .thenByDescending { it.height },
            )
        val cover = sorted.firstOrNull()?.url.orEmpty()
        val thumb = sorted.getOrNull(1)?.url ?: cover
        if (cover.isBlank()) {
            // Embed lama hanya punya satu URL; tidak ada info ukuran untuk diturunkan.
            val single = json.optString("imageUrl").ifBlank { json.optString("image") }
            return single to single
        }
        return thumb to cover
    }

    private fun imageCandidate(value: Any?): ImageCandidate? = when (value) {
        is String -> value.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let { ImageCandidate(it, 0, 0) }
        is JSONObject -> {
            val url = listOf("url", "src", *COVER_KEYS.toTypedArray())
                .asSequence()
                .map { value.optString(it) }
                .firstOrNull { it.startsWith("http", ignoreCase = true) }
            val dimensions = value.optJSONObject("dimensions")
            val width = value.optInt("width", dimensions?.optInt("width", 0) ?: 0)
            val height = value.optInt("height", dimensions?.optInt("height", 0) ?: 0)
            url?.let { ImageCandidate(it, width, height) }
        }
        else -> null
    }

    private fun parseDuration(value: String): Long {
        if (value.isBlank()) return 0L
        value.toLongOrNull()?.let { return it }
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun findTrackListContainer(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.optJSONArray("trackList") != null) return node
                node.keys().forEach { key ->
                    val found = findTrackListContainer(node.opt(key))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    val found = findTrackListContainer(node.opt(index))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    /**
     * Spotify menyimpan sampul di beberapa bentuk. Path entitas didahulukan agar
     * gambar trek tidak salah terpilih sebagai sampul playlist.
     */
    private fun findBestCoverUrl(root: JSONObject, container: JSONObject): String {
        val candidates = mutableListOf<ImageCandidate>()
        val entity = root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")

        collectImageCandidates(entity?.optJSONObject("visualIdentity")?.optJSONObject("image"), candidates)
        collectImageCandidates(entity?.optJSONObject("coverArt"), candidates)
        collectImageCandidates(container.optJSONObject("visualIdentity")?.optJSONObject("image"), candidates)
        collectImageCandidates(container.optJSONObject("coverArt"), candidates)

        if (candidates.isEmpty()) {
            collectImageCandidates(root, candidates)
            collectImageCandidates(container, candidates)
        }

        return candidates
            .distinctBy { it.url }
            .maxWithOrNull(
                compareBy<ImageCandidate> { it.width * it.height }
                    .thenBy { it.url.length },
            )
            ?.url
            .orEmpty()
    }

    private fun collectImageCandidates(
        node: Any?,
        out: MutableList<ImageCandidate>,
        depth: Int = 0,
    ) {
        if (node == null || depth > MAX_IMAGE_DEPTH) return
        when (node) {
            is JSONObject -> {
                val url = listOf("url", "src", *COVER_KEYS.toTypedArray())
                    .asSequence()
                    .map { node.optString(it) }
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                if (!url.isNullOrBlank()) {
                    val dimensions = node.optJSONObject("dimensions")
                    val width = node.optInt("width", dimensions?.optInt("width", 0) ?: 0)
                    val height = node.optInt("height", dimensions?.optInt("height", 0) ?: 0)
                    out += ImageCandidate(url, width, height)
                }
                node.keys().forEach { key ->
                    collectImageCandidates(node.opt(key), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectImageCandidates(node.opt(index), out, depth + 1)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DengerinSpotifyImport"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val WEB_PLAYER_VERSION = "1.2.81.104.g225ec0e6"
        const val PLAYLIST_QUERY_HASH = "bb67e0af06e8d6f52b531f97468ee4acd44cd0f82b988e15c2ea47b1148efc77"
        const val MAX_IMAGE_DEPTH = 8
        const val PAGE_SIZE = 100
        const val MAX_TRACKS = 10_000
        const val PAGE_DELAY_MS = 120L
        const val PAGE_RETRIES = 3
        const val PAGE_RETRY_DELAY_MS = 400L
        val TOKEN_REGEX = Regex(""""accessToken"\s*:\s*"([^"]+)"""")
        val PLAYLIST_URL_REGEX =
            Regex("""(?:open|play)\.spotify\.com/(?:embed/)?playlist/([A-Za-z0-9]+)""")
        val NEXT_DATA_REGEX =
            Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val COVER_KEYS = setOf("coverUrl", "imageUrl", "cover", "artworkUrl", "image")
    }
}
