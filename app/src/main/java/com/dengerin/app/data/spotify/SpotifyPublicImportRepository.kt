package com.dengerin.app.data.spotify

import com.dengerin.app.domain.model.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

/** Impor playlist publik hanya dari link, tanpa login Spotify. */
class SpotifyPublicImportRepository {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun import(link: String): Result<ImportedPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractPlaylistId(link)
            fetchEmbed(playlistId)
        }
    }

    private fun extractPlaylistId(link: String): String {
        val trimmed = link.trim()
        if (trimmed.startsWith("spotify:playlist:", ignoreCase = true)) {
            return trimmed.substringAfter(":").substringBefore("?").substringBefore("/")
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
            val track = parseEmbedTrack(array.optJSONObject(index) ?: continue, cover)
            if (track != null) tracks += track
        }
        if (tracks.isEmpty()) throw IllegalStateException("Playlist ini kosong atau tidak bisa diimpor.")

        return ImportedPlaylist(
            id = playlistId,
            name = name,
            coverUrl = cover,
            tracks = tracks.distinctBy { it.id },
        )
    }

    private fun parseEmbedTrack(json: JSONObject, playlistCover: String): SpotifyTrack? {
        val uri = json.optString("uri").ifBlank { json.optString("uid") }
        val id = uri.substringAfterLast(":").ifBlank { return null }
        val title = json.optString("title").ifBlank { return null }
        val subtitle = json.optString("subtitle")
        val artist = subtitle.split("•").firstOrNull().orEmpty().trim().ifBlank { "Artis tidak diketahui" }
        val trackImage = json.optString("imageUrl").ifBlank { json.optString("image") }
        return SpotifyTrack(
            id = id,
            title = title,
            artist = artist,
            durationMs = parseDuration(json.optString("duration")),
            // Sampul playlist dipakai sementara sampai SongResolver cocokkan ke YouTube.
            thumbnailUrl = trackImage.ifBlank { playlistCover },
            uri = uri,
        )
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
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val MAX_IMAGE_DEPTH = 8
        val PLAYLIST_URL_REGEX =
            Regex("""(?:open|play)\.spotify\.com/(?:embed/)?playlist/([A-Za-z0-9]+)""")
        val NEXT_DATA_REGEX =
            Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val COVER_KEYS = setOf("coverUrl", "imageUrl", "cover", "artworkUrl", "image")
    }
}
