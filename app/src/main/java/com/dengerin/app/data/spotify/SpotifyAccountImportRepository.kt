package com.dengerin.app.data.spotify

import com.dengerin.app.core.hdCoverUrl
import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.core.youtubeListThumbnailUrl
import com.dengerin.app.core.youtubeThumbnailUrl
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.domain.model.SpotifyTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class SpotifyCollectionKind { LIKED, PLAYLIST, ALBUM }

data class SpotifyProfile(
    val displayName: String,
    val avatarUrl: String,
    val id: String = "",
)

data class SpotifyCollection(
    val id: String,
    val name: String,
    val coverUrl: String,
    val kind: SpotifyCollectionKind,
    val trackCount: Int = 0,
    val isPrivate: Boolean = false,
    val uri: String = "",
)

data class SpotifyCatalogArtist(
    val id: String,
    val uri: String,
    val name: String,
    val avatarUrl: String = "",
    /** Dibaca dari payload, tetapi tidak pernah dipakai sebagai syarat tunggal. */
    val verified: Boolean = false,
    /** Followers/monthlyListeners bila tersedia di payload. */
    val audienceSize: Long = 0L,
    /** Subscriber channel NewPipe bila tersedia. */
    val subscriberCount: Long = 0L,
    val isFeaturedTopResult: Boolean = false,
    val isOfficialMusicChannel: Boolean = false,
    val isPopularArtist: Boolean = false,
)

data class SpotifyCatalogPlaylist(
    val id: String,
    val uri: String,
    val name: String,
    val description: String = "",
    val ownerName: String = "",
    val coverUrl: String = "",
    val totalTracks: Int = 0,
)

data class SpotifyCatalogTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    /** Semua ID artis pada lagu, bukan hanya artis pertama. */
    val artistIds: List<String> = emptyList(),
    val thumbnailUrl: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
)

sealed class SpotifyCatalogTopResult {
    data class Artist(val item: SpotifyCatalogArtist) : SpotifyCatalogTopResult()
    data class Playlist(val item: SpotifyCatalogPlaylist) : SpotifyCatalogTopResult()
    data class Track(val item: SpotifyCatalogTrack) : SpotifyCatalogTopResult()
}

data class SpotifyCatalogSearchResult(
    val topResult: SpotifyCatalogTopResult? = null,
    /** Urutan asli slot atas `topResultsV2`, dipakai guardrail sebelum fallback ke lagu. */
    val orderedTopResults: List<SpotifyCatalogTopResult> = emptyList(),
    val artists: List<SpotifyCatalogArtist> = emptyList(),
    val playlists: List<SpotifyCatalogPlaylist> = emptyList(),
    val tracks: List<SpotifyCatalogTrack> = emptyList(),
)

data class SpotifyArtistOverviewTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artistId: String = "",
    val artistIds: List<String> = emptyList(),
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val playcount: String = "",
)

data class SpotifyArtistRelease(
    val id: String,
    val uri: String,
    val name: String,
    val coverUrl: String = "",
    val year: String = "",
    val type: String = "",
)

data class SpotifyArtistRelatedPlaylist(
    val id: String,
    val uri: String,
    val name: String,
    val coverUrl: String = "",
    val ownerName: String = "",
)

data class SpotifyArtistOverview(
    val id: String = "",
    val name: String = "",
    val avatarUrl: String = "",
    val headerImageUrl: String = "",
    val monthlyListeners: String = "",
    val biography: String = "",
    val topTracks: List<SpotifyArtistOverviewTrack> = emptyList(),
    val releases: List<SpotifyArtistRelease> = emptyList(),
    val featuringPlaylists: List<SpotifyArtistRelatedPlaylist> = emptyList(),
)

data class SpotifyPublicPlaylist(
    val id: String,
    val uri: String,
    val name: String,
    val description: String = "",
    val ownerName: String = "",
    val coverUrl: String = "",
    val totalTracks: Int = 0,
    val tracks: List<SpotifyCatalogTrack> = emptyList(),
)

private data class PathfinderResult(
    val code: Int,
    val body: String,
    val retryAfterMs: Long = 0L,
)

/**
 * Impor koleksi akun Spotify meniru arsitektur Spotube: `client-token` dari
 * `clienttoken.spotify.com` + Bearer token sesi Web Player → `POST pathfinder/v1/query`.
 * Hash persisted query diekstrak otomatis dari bundle JS Web Player, sehingga tidak
 * terkena `PersistedQueryNotFound` saat Spotify memperbarui versi web-nya.
 *
 * Tidak ada `/v1/me/playlists` atau `/v1/me/tracks` (menolak token Web Player dengan
 * HTTP 403), tidak ada OAuth Developer App, dan tidak ada YouTube/NewPipe saat impor.
 * Setiap error HTTP diteruskan apa adanya supaya sheet impor bisa menampilkannya.
 */
class SpotifyAccountImportRepository(
    private val session: SpotifySessionManager,
    private val clientTokenManager: SpotifyClientTokenManager,
    private val hashResolver: SpotifyPathfinderHashResolver,
    private val musicExtractorRepository: MusicExtractorRepository? = null,
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Satu pembatas global untuk semua Pathfinder: pagination dan koleksi berbagi kuota ini. */
    private val pathfinderSemaphore = Semaphore(PATHFINDER_CONCURRENCY)

    /**
     * Profil diambil dari `/v1/me`. Token Web Player kadang ditolak 403 di endpoint publik
     * ini: segarkan token sekali lewat `/preferences`, lalu ulangi. Kalau tetap gagal,
     * pakai profil yang sudah tersimpan di DataStore — profile tidak boleh menghalangi
     * pengambilan koleksi.
     */
    suspend fun fetchProfile(): Result<SpotifyProfile> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                val json = getJsonWithRetry("$API_BASE/v1/me")
                SpotifyProfile(
                    displayName = json.optString("display_name").ifBlank { "Akun Spotify" },
                    avatarUrl = firstImageUrl(json.optJSONArray("images")),
                    id = json.optString("id").orEmpty(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val cached = session.profile.firstOrNull()
                SpotifyProfile(
                    displayName = cached?.displayName?.takeIf { it.isNotBlank() }
                        ?: "Akun Spotify",
                    avatarUrl = cached?.avatarUrl.orEmpty(),
                    id = cached?.id.orEmpty(),
                )
            }
        }
    }

    /** Ulangi sekali setelah refresh token bila Web API menjawab 401/403. */
    private suspend fun getJsonWithRetry(url: String, canRetry: Boolean = true): JSONObject =
        try {
            getJson(url)
        } catch (error: IOException) {
            val authError = error.message?.let { message ->
                message.contains("HTTP 401") || message.contains("HTTP 403")
            } == true
            if (!canRetry || !authError) throw error
            session.refreshSessionToken().getOrThrow()
            getJsonWithRetry(url, canRetry = false)
        }

    /**
     * Playlist milik sendiri & yang diikuti lewat `libraryV3` dengan paginasi `offset`.
     * Lagu Disukai tetap disertakan sebagai entri sintetis; isinya diambil terpisah oleh
     * [fetchTracks] memakai `fetchLibraryTracks`.
     */
    suspend fun fetchCollections(): Result<List<SpotifyCollection>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = mutableListOf(
                SpotifyCollection(
                    id = COLLECTION_LIKED,
                    name = "Lagu Disukai",
                    coverUrl = "",
                    kind = SpotifyCollectionKind.LIKED,
                ),
            )

            // Jumlah liked dipakai hanya untuk label UI. Kegagalan di sini tidak boleh
            // menghalangi daftar playlist, jadi ditelan dengan aman.
            val likedCount = runCatching {
                val json = query(
                    "fetchLibraryTracks",
                    JSONObject()
                        .put("uri", "spotify:collection:tracks")
                        .put("offset", 0)
                        .put("limit", 1),
                    KEY_LIKED,
                )
                json.optJSONObject("data")
                    ?.optJSONObject("me")
                    ?.optJSONObject("library")
                    ?.optJSONObject("tracks")
                    ?.optInt("totalCount", 0) ?: 0
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                0
            }
            if (likedCount > 0) result[0] = result[0].copy(trackCount = likedCount)

            // Variabel mengikuti bentuk yang dipakai Web Player/Spogo: features kosong
            // dan withCuration = false. Playlist dibaca dari items[i].item.data.
            val variables = JSONObject()
                .put("filters", JSONArray().put("Playlists"))
                .put("order", JSONObject.NULL)
                .put("textFilter", "")
                .put("features", JSONArray())
                .put("limit", PAGE_SIZE)
                .put("offset", 0)
                .put("flatten", false)
                .put("expandedFolders", JSONArray())
                .put("folderUri", JSONObject.NULL)
                .put("includeFoldersWhenFlattening", true)
                .put("withCuration", false)

            var offset = 0
            var total = Int.MAX_VALUE
            while (offset < total && result.size < MAX_COLLECTIONS) {
                variables.put("offset", offset)
                val json = query("libraryV3", variables, KEY_LIBRARY)
                val library = json.optJSONObject("data")
                    ?.optJSONObject("me")
                    ?.optJSONObject("libraryV3")
                    ?: break
                total = library.optInt("totalCount", 0).takeIf { it > 0 } ?: break
                val items = library.optJSONArray("items") ?: break
                for (index in 0 until items.length()) {
                    // Album belum punya jalur fetchTracks di Pathfinder: jangan tampilkan
                    // entri yang nanti gagal saat diimpor. Fokus Liked + Playlist.
                    val parsed = parseCollection(items.optJSONObject(index))
                    if (parsed == null || parsed.kind == SpotifyCollectionKind.ALBUM) continue
                    if (result.any { it.id == parsed.id }) continue
                    result += parsed
                }
                offset += PAGE_SIZE
                if (items.length() < PAGE_SIZE) break
                delay(PAGE_DELAY_MS)
            }
            result.sortWith(compareBy<SpotifyCollection> { it.kind.ordinal }.thenBy { it.name })
            result
        }
    }

    private fun parseCollection(wrapper: JSONObject?): SpotifyCollection? {
        if (wrapper == null) return null
        // libraryV3 membungkus tiap entri di `item.data` (atau `itemV2.data` pada
        // response lain). Jangan baca `uri` langsung dari wrapper.
        val data = wrapper.optJSONObject("item")?.optJSONObject("data")
            ?: wrapper.optJSONObject("itemV2")?.optJSONObject("data")
            ?: wrapper.optJSONObject("data")
            ?: wrapper.optJSONObject("item")
            ?: wrapper
        val uri = data.optString("uri")
            .ifBlank { wrapper.optString("_uri") }
            .ifBlank { data.optString("_uri") }
        val typeName = data.optString("__typename").ifBlank { wrapper.optString("__typename") }
        val kind = when {
            uri.startsWith("spotify:playlist:") -> SpotifyCollectionKind.PLAYLIST
            uri.startsWith("spotify:album:") -> SpotifyCollectionKind.ALBUM
            // Liked sudah diwakili entri sintetis: jangan dobel.
            else -> return null
        }
        val id = uri.substringAfterLast(":").takeIf { it.isNotBlank() } ?: return null
        val ownerName = data.optJSONObject("ownerV2")?.optJSONObject("profile")?.optString("name")
            ?.takeIf { it.isNotBlank() }
            ?: data.optJSONObject("ownerV2")?.optString("name").orEmpty()
        val name = data.optString("name").ifBlank { ownerName }.ifBlank { "Koleksi Spotify" }
        val private = data.optBoolean("isPrivate", false) ||
            typeName.equals("PrivatePlaylist", ignoreCase = true) ||
            data.optString("visibility").equals("PRIVATE", ignoreCase = true)
        val count = data.optJSONObject("content")?.optInt("totalCount", 0)
            ?: data.optJSONObject("tracks")?.optInt("total", 0)
            ?: wrapper.optJSONObject("content")?.optInt("totalCount", 0)
            ?: 0
        return SpotifyCollection(
            id = id,
            name = name,
            coverUrl = findBestImageUrl(data),
            kind = kind,
            trackCount = count,
            isPrivate = private,
            uri = uri,
        )
    }

    /**
     * Isi koleksi. Halaman pertama diambil lebih dulu untuk membaca `totalCount`, lalu
     * halaman sisanya diambil paralel. Semua request tetap lewat semaphore global di
     * [post], jadi konkurensi Pathfinder tidak pernah lebih dari PATHFINDER_CONCURRENCY.
     */
    suspend fun fetchTracks(
        collection: SpotifyCollection,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        if (collection.kind == SpotifyCollectionKind.ALBUM) {
            return@withContext Result.failure(
                IOException("Album tidak tersedia lewat jalur impor Spotify Pathfinder."),
            )
        }
        runCatching {
            val liked = collection.kind == SpotifyCollectionKind.LIKED
            val pageSize = if (liked) LIKED_PAGE_SIZE else TRACK_PAGE_SIZE

            val first = fetchTrackPage(collection, liked, 0, pageSize)
            val tracks = first.tracks.toMutableList()
            val total = if (first.total > 0) first.total else first.rawCount
            onProgress(tracks.size, total)

            coroutineScope {
                val pages = mutableListOf<Deferred<TrackPage>>()
                var offset = pageSize
                while (offset < total && tracks.size + pages.size * pageSize < MAX_TRACKS) {
                    val pageOffset = offset
                    pages += async { fetchTrackPage(collection, liked, pageOffset, pageSize) }
                    offset += pageSize
                }
                // await() dipanggil berurutan sesuai offset, jadi urutan lagu tetap asli
                // walaupun halaman selesai tidak berurutan.
                for (page in pages) {
                    if (tracks.size >= MAX_TRACKS) break
                    val result = page.await()
                    tracks += result.tracks
                    onProgress(tracks.size.coerceAtMost(MAX_TRACKS), total)
                }
            }
            tracks.take(MAX_TRACKS)
        }
    }

    private data class TrackPage(
        val tracks: List<SpotifyTrack>,
        val rawCount: Int,
        val total: Int,
    )

    private suspend fun fetchTrackPage(
        collection: SpotifyCollection,
        liked: Boolean,
        offset: Int,
        pageSize: Int,
    ): TrackPage {
        val variables = if (liked) {
            JSONObject()
                .put("uri", "spotify:collection:tracks")
                .put("offset", offset)
                .put("limit", pageSize)
        } else {
            JSONObject()
                .put("uri", "spotify:playlist:${collection.id}")
                .put("offset", offset)
                .put("limit", pageSize)
                .put("enableWatchFeedEntrypoint", false)
        }
        val operation = if (liked) "fetchLibraryTracks" else "fetchPlaylist"
        val hashKey = if (liked) KEY_LIKED else KEY_PLAYLIST
        val json = query(operation, variables, hashKey)
        val parsed = mutableListOf<SpotifyTrack>()

        val rawItems: JSONArray
        val count: Int
        if (liked) {
            val library = json.optJSONObject("data")
                ?.optJSONObject("me")
                ?.optJSONObject("library")
            val trackNode = library?.optJSONObject("tracks")
            rawItems = trackNode?.optJSONArray("items") ?: JSONArray()
            count = trackNode?.optInt("totalCount", 0)
                ?: library?.optInt("totalCount", 0) ?: 0
            for (index in 0 until rawItems.length()) {
                val wrapper = rawItems.optJSONObject(index) ?: continue
                val trackWrapper = wrapper.optJSONObject("track") ?: wrapper
                val data = trackWrapper.optJSONObject("data")
                if (data == null) {
                    collectTracks(wrapper, 0, parsed)
                    continue
                }
                // fetchLibraryTracks menyimpan URI di `track._uri`, bukan di `data.uri`.
                val uri = data.optString("uri").ifBlank {
                    trackWrapper.optString("_uri").ifBlank { wrapper.optString("_uri") }
                }
                if (uri.isNotBlank()) data.put("uri", uri)
                parseTrack(data, uri)?.let { parsed += it }
            }
        } else {
            val content = json.optJSONObject("data")
                ?.optJSONObject("playlistV2")
                ?.optJSONObject("content")
            rawItems = content?.optJSONArray("items") ?: JSONArray()
            count = content?.optInt("totalCount", 0) ?: 0
            for (index in 0 until rawItems.length()) {
                val wrapper = rawItems.optJSONObject(index) ?: continue
                val data = wrapper.optJSONObject("itemV2")?.optJSONObject("data")
                    ?: wrapper.optJSONObject("item")?.optJSONObject("data")
                    ?: wrapper.optJSONObject("data")
                    ?: wrapper
                val uri = data.optString("uri").ifBlank { wrapper.optString("_uri") }
                if (uri.startsWith("spotify:track:")) {
                    parseTrack(data, uri)?.let { parsed += it }
                } else {
                    collectTracks(wrapper, 0, parsed)
                }
            }
        }
        return TrackPage(parsed, rawItems.length(), count)
    }

    private fun collectTracks(node: Any?, depth: Int, out: MutableList<SpotifyTrack>) {
        if (depth > MAX_JSON_DEPTH || node == null) return
        when (node) {
            is JSONObject -> {
                val uri = node.optString("uri").orEmpty()
                if (uri.startsWith("spotify:track:")) {
                    parseTrack(node, uri)?.let { out += it }
                    return
                }
                for (key in node.keys()) collectTracks(node.opt(key), depth + 1, out)
            }
            is JSONArray -> {
                for (index in 0 until node.length()) collectTracks(node.opt(index), depth + 1, out)
            }
        }
    }

    private fun parseTrack(item: JSONObject, uri: String): SpotifyTrack? {
        val id = uri.substringAfterLast(":").ifBlank { return null }
        val title = item.optString("name").ifBlank { return null }
        val artists = item.optJSONObject("artists")?.optJSONArray("items")
            ?: item.optJSONArray("artists")
        val names = (0 until (artists?.length() ?: 0)).mapNotNull { index ->
            val artist = artists?.optJSONObject(index) ?: return@mapNotNull null
            artist.optJSONObject("profile")?.optString("name")
                ?.takeIf { it.isNotBlank() }
                ?: artist.optString("name").takeIf { it.isNotBlank() }
        }
        val artist = names.joinToString(", ")
            .ifBlank { item.optString("artist") }
            .ifBlank { "Artis tidak diketahui" }
        val durationMs = listOf(
            item.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0L) ?: 0L,
            item.optLong("durationMs", 0L),
            item.optLong("duration_ms", 0L),
        ).firstOrNull { it > 0L } ?: 0L
        val cover = findBestImageUrl(item)
        return SpotifyTrack(
            id = id,
            title = title,
            artist = artist,
            durationMs = durationMs,
            thumbnailUrl = listThumbnailUrl(cover).orEmpty(),
            coverUrl = hdCoverUrl(cover).orEmpty(),
            uri = uri,
        )
    }

    private fun firstImageUrl(images: JSONArray?): String {
        if (images == null) return ""
        var best = ""
        var bestArea = -1
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index) ?: continue
            val url = image.optString("url")
            if (url.isBlank()) continue
            val area = image.optInt("width", 0) * image.optInt("height", 0)
            if (area >= bestArea) {
                best = url
                bestArea = area
            }
        }
        return best
    }

    private fun findBestImageUrl(node: Any?): String {
        var best = ""
        var bestArea = 0
        fun visit(value: Any?, depth: Int) {
            if (depth > MAX_JSON_DEPTH || value == null) return
            when (value) {
                is JSONObject -> {
                    val url = value.optString("url").ifBlank { value.optString("src") }
                    if (url.startsWith("http")) {
                        val area = value.optInt("width", 1) * value.optInt("height", 1)
                        if (area >= bestArea) {
                            best = url
                            bestArea = area
                        }
                    }
                    for (key in value.keys()) visit(value.opt(key), depth + 1)
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) visit(value.opt(index), depth + 1)
                }
                is String -> {
                    if (value.startsWith("http") && best.isEmpty()) best = value
                }
            }
        }
        visit(node, 0)
        return best
    }

    /**
     * Satu jalur POST Pathfinder. 401/403 → segarkan accessToken + clientToken sekali lalu
     * ulangi; PersistedQueryNotFound → buang cache hash dan ekstrak ulang dari Web Player.
     */
    private suspend fun query(
        operationName: String,
        variables: JSONObject,
        hashKey: String,
        allowPublicToken: Boolean = false,
    ): JSONObject {
        var retriedAuth = false
        var retriedHash = false
        var hashes = hashResolver.getHashes().getOrElse { error ->
            throw IOException(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Hash GraphQL Spotify belum tersedia.",
            )
        }
        while (true) {
            val hash = hashes.forKey(hashKey).takeIf { it.isNotBlank() }
                ?: throw IOException("Hash GraphQL Spotify ($hashKey) belum tersedia.")
            val tokenPair = if (allowPublicToken) {
                session.getPublicOrSessionAccessToken().getOrElse { error ->
                    if (error is CancellationException) throw error
                    throw IOException(error.message?.takeIf { it.isNotBlank() } ?: "Token Spotify belum tersedia.")
                }
            } else {
                val token = session.getSessionAccessToken().getOrElse { error ->
                    if (error is CancellationException) throw error
                    throw IOException(error.message?.takeIf { it.isNotBlank() } ?: "Sesi Spotify belum bisa disegarkan.")
                }
                val cid = session.clientId.firstOrNull()?.takeIf { it.isNotBlank() } ?: ""
                SpotifySessionManager.PublicSpotifyToken(token, cid)
            }
            val clientToken = clientTokenManager.getClientToken().getOrNull()
            val result = post(operationName, variables, hash, tokenPair.accessToken, clientToken)

            // Token dari halaman embed bisa saja scope-nya belum cukup untuk Pathfinder.
            // 401 maupun 403 memicu refresh token sesi + client token sekali, tanpa
            // menghapus sp_dc, lalu query diulang.
            if ((result.code == 401 || result.code == 403) && !retriedAuth) {
                retriedAuth = true
                runCatching { session.refreshSessionToken() }
                runCatching { clientTokenManager.refreshClientToken() }
                continue
            }
            if (result.code !in 200..299) throw IOException(httpError(result.code, result.body))

            val json = runCatching { JSONObject(result.body) }.getOrElse {
                throw IOException("Respons Pathfinder bukan JSON yang valid (HTTP ${result.code}).")
            }
            val message = json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                .orEmpty()
            if (message.contains("PersistedQueryNotFound", ignoreCase = true) && !retriedHash) {
                retriedHash = true
                hashes = hashResolver.getHashes(forceRefresh = true).getOrThrow()
                continue
            }
            if (message.isNotBlank()) {
                throw IOException("Spotify menolak query $operationName: $message")
            }
            return json
        }
    }

    private suspend fun post(
        operationName: String,
        variables: JSONObject,
        hash: String,
        token: String,
        clientToken: String?,
    ): PathfinderResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("variables", variables)
            .put("operationName", operationName)
            .put(
                "extensions",
                JSONObject().put(
                    "persistedQuery",
                    JSONObject().put("version", 1).put("sha256Hash", hash),
                ),
            )
        val requestBuilder = Request.Builder()
            .url(PATHFINDER_URL)
            .header("Authorization", "Bearer $token")
            .header("app-platform", "WebPlayer")
            .header("spotify-app-version", WEB_PLAYER_VERSION)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .header("User-Agent", SpotifySessionDom.CHROME_DESKTOP_USER_AGENT)
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
        if (!clientToken.isNullOrBlank()) requestBuilder.header("client-token", clientToken)
        val request = requestBuilder
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        // Semua request — termasuk halaman paralel dan beberapa koleksi — lewat satu
        // pembatas ini. Jadi konkurensi total tidak pernah melebihi 3.
        pathfinderSemaphore.withPermit {
            var rateLimitRetries = 0
            var result: PathfinderResult? = null
            while (result == null) {
                val response = client.newCall(request).execute()
                try {
                    val retryAfterMs = parseRetryAfter(response.header("Retry-After"))
                    val body = response.body?.string().orEmpty()
                    val code = response.code
                    if (code == 429 && rateLimitRetries < MAX_RATE_LIMIT_RETRIES) {
                        rateLimitRetries++
                        delay(retryAfterMs.coerceIn(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS))
                        continue
                    }
                    result = PathfinderResult(code, body, retryAfterMs)
                } finally {
                    response.close()
                }
            }
            result
        }
    }

    /** `Retry-After` bisa berupa detik atau HTTP-date; fallback 1500 ms. */
    private fun parseRetryAfter(raw: String?): Long {
        if (raw.isNullOrBlank()) return DEFAULT_RETRY_AFTER_MS
        raw.trim().toLongOrNull()?.let { seconds ->
            if (seconds >= 0L) return seconds * 1000L
        }
        for (pattern in RETRY_AFTER_DATE_PATTERNS) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(raw.trim())
            }.getOrNull()
            if (parsed != null) {
                return (parsed.time - System.currentTimeMillis()).coerceAtLeast(0L)
            }
        }
        return DEFAULT_RETRY_AFTER_MS
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val token = session.getSessionAccessToken().getOrElse { error ->
            if (error is CancellationException) throw error
            throw IOException(
                error.message?.takeIf { it.isNotBlank() } ?: "Sesi Spotify belum bisa disegarkan.",
            )
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", SpotifySessionDom.CHROME_DESKTOP_USER_AGENT)
            .build()
        val response = client.newCall(request).execute()
        try {
            val body = response.body?.string().orEmpty()
            if (response.code !in 200..299) throw IOException(httpError(response.code, body))
            JSONObject(body)
        } finally {
            response.close()
        }
    }

    private fun httpError(code: Int, body: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?: JSONObject(body).optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return "Spotify menolak permintaan (HTTP $code)" + message?.let { ": $it" }.orEmpty() + "."
    }

    suspend fun searchSpotifyCatalog(query: String): Result<SpotifyCatalogSearchResult> = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isBlank()) return@withContext Result.success(SpotifyCatalogSearchResult())
        runCatching {
            searchSpotifyCatalogPathfinder(keyword).getOrElse { error ->
                if (error is CancellationException) throw error
                searchSpotifyCatalogFallback(keyword).getOrThrow()
            }
        }
    }

    private suspend fun searchSpotifyCatalogPathfinder(keyword: String): Result<SpotifyCatalogSearchResult> = runCatching {
        val variables = JSONObject()
            .put("searchTerm", keyword)
            .put("offset", 0)
            .put("limit", 10)
            .put("numberOfTopResults", 5)
            .put("includeAudiobooks", false)
            .put("includeArtistHasConcertsField", false)
            .put("includePreReleases", false)
            .put("includeLocalConcertsField", false)
        val json = query("searchDesktop", variables, KEY_SEARCH, allowPublicToken = true)
        parseCatalogSearch(json, keyword)
    }

    private fun findNodeByKey(root: Any?, key: String, depth: Int = 0): JSONObject? {
        if (root == null || depth > MAX_JSON_DEPTH) return null
        when (root) {
            is JSONObject -> {
                root.optJSONObject(key)?.let { return it }
                root.keys().forEach { childKey ->
                    findNodeByKey(root.opt(childKey), key, depth + 1)?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until root.length()) {
                    findNodeByKey(root.opt(i), key, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun unwrapEntity(node: JSONObject, depth: Int = 0): JSONObject {
        if (depth >= 4) return node
        val wrapped = node.optJSONObject("itemV2")?.optJSONObject("data")
            ?: node.optJSONObject("item")?.optJSONObject("data")
            ?: node.optJSONObject("featuredItem")?.optJSONObject("data")
            ?: node.optJSONObject("data")
        return wrapped?.let { unwrapEntity(it, depth + 1) } ?: node
    }

    private fun topResultKey(item: SpotifyCatalogTopResult): String = when (item) {
        is SpotifyCatalogTopResult.Artist -> "artist:${item.item.id}"
        is SpotifyCatalogTopResult.Playlist -> "playlist:${item.item.id}"
        is SpotifyCatalogTopResult.Track -> "track:${item.item.id}"
    }

    private fun putTopResult(
        target: LinkedHashMap<String, SpotifyCatalogTopResult>,
        item: SpotifyCatalogTopResult,
    ) {
        target.putIfAbsent(topResultKey(item), item)
    }

    private fun parseEntityAsTopResult(node: JSONObject): SpotifyCatalogTopResult? {
        val candidate = unwrapEntity(node)
        val uri = candidate.optString("uri")
        return when {
            uri.startsWith("spotify:artist:") -> parseCatalogArtist(candidate)?.let { SpotifyCatalogTopResult.Artist(it) }
            uri.startsWith("spotify:playlist:") -> parseCatalogPlaylist(candidate)?.let { SpotifyCatalogTopResult.Playlist(it) }
            uri.startsWith("spotify:track:") -> parseCatalogTrack(candidate)?.let { SpotifyCatalogTopResult.Track(it) }
            else -> null
        }
    }

    /** `topResultsV2.featured` dipakai lebih dulu, lalu `itemsV2` resmi backend. */
    private fun parseTopSlots(root: JSONObject, target: LinkedHashMap<String, SpotifyCatalogTopResult>) {
        parseEntityAsTopResult(root)?.let { putTopResult(target, it) }
        root.optJSONObject("featured")?.let { parseTopSlots(it, target) }

        val arrays = mutableListOf<JSONArray>()
        root.optJSONArray("featured")?.let(arrays::add)
        root.optJSONArray("itemsV2")?.let(arrays::add)
        root.optJSONArray("items")?.let(arrays::add)
        arrays.forEach { array ->
            for (i in 0 until array.length()) {
                val wrapper = array.optJSONObject(i) ?: continue
                parseEntityAsTopResult(wrapper)?.let { putTopResult(target, it) }
            }
        }
    }

    private fun collectTracksInOrder(
        root: JSONObject,
        tracks: LinkedHashMap<String, SpotifyCatalogTrack>,
        depth: Int = 0,
    ) {
        if (depth > MAX_JSON_DEPTH) return
        val arrays = mutableListOf<JSONArray>()
        root.optJSONArray("itemsV2")?.let(arrays::add)
        root.optJSONArray("items")?.let(arrays::add)
        arrays.forEach { array ->
            for (i in 0 until array.length()) {
                val wrapper = array.optJSONObject(i) ?: continue
                val candidate = unwrapEntity(wrapper)
                if (candidate.optString("uri").startsWith("spotify:track:")) {
                    parseCatalogTrack(candidate)?.let { tracks.putIfAbsent(it.id, it) }
                }
            }
        }
        root.keys().forEach { key ->
            val child = root.opt(key)
            if (child is JSONObject) collectTracksInOrder(child, tracks, depth + 1)
        }
    }

    private fun readCountFrom(value: Any?): Long = when (value) {
        is Number -> value.toLong().takeIf { it > 0L } ?: 0L
        is String -> value.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        is JSONObject -> readCountFrom(value.opt("total") ?: value.opt("count") ?: value.opt("value"))
        else -> 0L
    }

    private fun parseCatalogSearch(root: JSONObject, query: String): SpotifyCatalogSearchResult {
        val data = root.optJSONObject("data") ?: root
        val searchV2 = data.optJSONObject("searchV2") ?: data.optJSONObject("search") ?: data
        val artists = linkedMapOf<String, SpotifyCatalogArtist>()
        val playlists = linkedMapOf<String, SpotifyCatalogPlaylist>()
        val tracks = linkedMapOf<String, SpotifyCatalogTrack>()

        val topRoot = findNodeByKey(searchV2, "topResultsV2")
            ?: findNodeByKey(searchV2, "topResults")
            ?: findNodeByKey(searchV2, "topResult")
            ?: findNodeByKey(data, "topResultsV2")
            ?: findNodeByKey(data, "topResults")
            ?: findNodeByKey(data, "topResult")
        val orderedTop = LinkedHashMap<String, SpotifyCatalogTopResult>()
        if (topRoot != null) parseTopSlots(topRoot, orderedTop)

        findNodeByKey(searchV2, "tracksV2")
            ?.let { collectTracksInOrder(it, tracks) }
            ?: findNodeByKey(data, "tracksV2")?.let { collectTracksInOrder(it, tracks) }

        var legacyTop: SpotifyCatalogTopResult? = null
        fun visit(node: Any?, depth: Int) {
            if (node == null || depth > MAX_JSON_DEPTH) return
            when (node) {
                is JSONObject -> {
                    val typename = node.optString("__typename")
                    val uri = node.optString("uri")
                    when {
                        uri.startsWith("spotify:artist:") && typename.contains("Artist", true) -> {
                            parseCatalogArtist(node)?.let { artists.putIfAbsent(it.id, it) }
                        }
                        uri.startsWith("spotify:playlist:") && typename.contains("Playlist", true) -> {
                            parseCatalogPlaylist(node)?.let { playlists.putIfAbsent(it.id, it) }
                        }
                        uri.startsWith("spotify:track:") && typename.contains("Track", true) -> {
                            parseCatalogTrack(node)?.let { tracks.putIfAbsent(it.id, it) }
                        }
                    }
                    if (legacyTop == null) legacyTop = parseTopResult(node)
                    node.keys().forEach { visit(node.opt(it), depth + 1) }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(searchV2, 0)
        if (tracks.isEmpty() || artists.isEmpty() || playlists.isEmpty()) visit(data, 0)
        if (orderedTop.isEmpty()) legacyTop?.let { putTopResult(orderedTop, it) }

        val featuredArtists = orderedTop.values
            .filterIsInstance<SpotifyCatalogTopResult.Artist>()
            .associateBy { it.item.id }
        val upgradedArtists = linkedMapOf<String, SpotifyCatalogArtist>()
        artists.values.forEach { artist ->
            val featured = featuredArtists[artist.id]
            upgradedArtists[artist.id] = if (featured == null) {
                artist
            } else {
                artist.copy(
                    avatarUrl = artist.avatarUrl.ifBlank { featured.item.avatarUrl },
                    isFeaturedTopResult = true,
                )
            }
        }
        featuredArtists.forEach { (id, featured) ->
            if (!upgradedArtists.containsKey(id)) {
                upgradedArtists[id] = featured.item.copy(isFeaturedTopResult = true)
            }
        }

        val fallbackTop = orderedTop.values.firstOrNull()
            ?: upgradedArtists.values.firstOrNull()?.let { SpotifyCatalogTopResult.Artist(it) }
            ?: playlists.values.firstOrNull()?.let { SpotifyCatalogTopResult.Playlist(it) }
            ?: tracks.values.firstOrNull()?.let { SpotifyCatalogTopResult.Track(it) }
        val raw = SpotifyCatalogSearchResult(
            topResult = fallbackTop,
            orderedTopResults = orderedTop.values.toList(),
            artists = upgradedArtists.values.take(12).toList(),
            playlists = playlists.values.take(12).toList(),
            tracks = tracks.values.take(20).toList(),
        )
        return applySearchGuardrails(raw, query)
    }

    private fun parseTopResult(node: JSONObject): SpotifyCatalogTopResult? {
        val top = node.optJSONObject("topResults") ?: node.optJSONObject("topResult") ?: return null
        val items = top.optJSONArray("items")
        val candidate = top.optJSONObject("item") ?: top.optJSONObject("data") ?: items?.optJSONObject(0) ?: top
        return parseEntityAsTopResult(candidate)
    }

    private fun parseCatalogArtist(node: JSONObject): SpotifyCatalogArtist? {
        val uri = node.optString("uri").takeIf { it.startsWith("spotify:artist:") } ?: return null
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null
        val profile = node.optJSONObject("profile") ?: node
        val name = profile.optString("name")
            .ifBlank { node.optString("name") }
            .ifBlank { null } ?: return null
        val avatar = findBestImageUrl(node.optJSONObject("visuals") ?: node.optJSONObject("avatar") ?: node)
        val stats = node.optJSONObject("stats") ?: profile.optJSONObject("stats")
        val verified = node.optBoolean("verified") || profile.optBoolean("verified") ||
            node.optBoolean("isVerified") || profile.optBoolean("isVerified")
        val followers = readCountFrom(node.opt("followers")).takeIf { it > 0L }
            ?: readCountFrom(profile.opt("followers"))
        val monthlyListeners = readCountFrom(stats?.opt("monthlyListeners")).takeIf { it > 0L }
            ?: readCountFrom(stats?.opt("monthly_listeners"))
        return SpotifyCatalogArtist(
            id = id,
            uri = uri,
            name = name,
            avatarUrl = hdCoverUrl(avatar).orEmpty(),
            verified = verified,
            audienceSize = maxOf(followers, monthlyListeners),
        )
    }

    private fun parseCatalogPlaylist(node: JSONObject): SpotifyCatalogPlaylist? {
        val uri = node.optString("uri").takeIf { it.startsWith("spotify:playlist:") } ?: return null
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null
        val name = node.optString("name").ifBlank { return null }
        val owner = node.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")
            ?.ifBlank { node.optJSONObject("ownerV2")?.optString("name") }
            ?.ifBlank { node.optJSONObject("owner")?.optString("name") }
            .orEmpty()
        val desc = node.optString("description").orEmpty()
        val cover = findBestImageUrl(node.optJSONObject("images") ?: node)
        val count = node.optJSONObject("content")?.optInt("totalCount", 0) ?: 0
        return SpotifyCatalogPlaylist(id, uri, name, desc, owner, hdCoverUrl(cover).orEmpty(), count)
    }

    private fun parseCatalogTrack(node: JSONObject): SpotifyCatalogTrack? {
        val uri = node.optString("uri").takeIf { it.startsWith("spotify:track:") } ?: return null
        val id = uri.substringAfterLast(':').takeIf { it.isNotBlank() } ?: return null
        val title = node.optString("name").ifBlank { return null }
        val artistItems = node.optJSONObject("artists")?.optJSONArray("items") ?: node.optJSONArray("artists")
        val artistIds = (0 until (artistItems?.length() ?: 0)).mapNotNull { i ->
            val artistNode = artistItems?.optJSONObject(i) ?: return@mapNotNull null
            val uri = artistNode.optString("uri")
                .ifBlank { artistNode.optJSONObject("data")?.optString("uri").orEmpty() }
            uri.takeIf { it.startsWith("spotify:artist:") }?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
        }.distinct()
        val artistId = artistIds.firstOrNull().orEmpty()
        val artistNames = (0 until (artistItems?.length() ?: 0)).mapNotNull { i ->
            val a = artistItems?.optJSONObject(i) ?: return@mapNotNull null
            a.optJSONObject("profile")?.optString("name")?.ifBlank { a.optString("name") }?.takeIf { it.isNotBlank() }
        }
        val cover = findBestImageUrl(node.optJSONObject("albumOfTrack") ?: node)
        val durationMs = node.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)
            ?.takeIf { it > 0L } ?: node.optLong("duration_ms", 0L)
        return SpotifyCatalogTrack(
            id = id, uri = uri, title = title,
            artist = artistNames.joinToString(", ").ifBlank { "Artis tidak diketahui" },
            artistId = artistId,
            artistIds = artistIds,
            thumbnailUrl = listThumbnailUrl(cover).orEmpty(),
            coverUrl = hdCoverUrl(cover).orEmpty(),
            durationMs = durationMs,
        )
    }

    private suspend fun searchSpotifyCatalogFallback(keyword: String): Result<SpotifyCatalogSearchResult> = runCatching {
        withContext(Dispatchers.IO) {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val tracks = runCatching {
                musicExtractorRepository?.searchSongs(keyword)?.getOrDefault(emptyList())?.take(10)?.map {
                    SpotifyCatalogTrack(
                        id = it.id,
                        uri = it.url.ifBlank { "https://www.youtube.com/watch?v=${it.id}" },
                        title = it.title,
                        artist = it.artist,
                        artistId = it.artistId,
                        artistIds = it.artistIds,
                        thumbnailUrl = it.thumbnailUrl,
                        coverUrl = it.coverUrl,
                        durationMs = it.durationMs,
                    )
                } ?: SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(keyword)).relatedItems
                    .filterIsInstance<StreamInfoItem>().take(10).map {
                        val vid = it.url.substringAfter("v=", it.url.substringAfterLast('/'))
                        SpotifyCatalogTrack(
                            id = vid,
                            uri = it.url,
                            title = it.name,
                            artist = it.uploaderName ?: "Artis tidak diketahui",
                            artistId = it.uploaderUrl?.substringAfterLast('/').orEmpty(),
                            artistIds = listOfNotNull(it.uploaderUrl?.substringAfterLast('/')?.takeIf { id -> id.isNotBlank() }),
                            thumbnailUrl = listThumbnailUrl(it.thumbnails.maxByOrNull { t -> t.height }?.url ?: youtubeListThumbnailUrl(it.url)).orEmpty(),
                            coverUrl = hdCoverUrl(it.thumbnails.maxByOrNull { t -> t.height }?.url ?: youtubeThumbnailUrl(it.url)).orEmpty(),
                            durationMs = (it.duration * 1000L).coerceAtLeast(0L),
                        )
                    }
            }.getOrDefault(emptyList())

            val playlists = runCatching {
                val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(keyword, listOf("playlists"), ""))
                info.relatedItems.filterIsInstance<PlaylistInfoItem>().take(8).map { p ->
                    val id = p.url.substringAfterLast('/').substringAfterLast('=')
                    SpotifyCatalogPlaylist(
                        id = id,
                        uri = p.url,
                        name = p.name,
                        ownerName = p.uploaderName ?: "",
                        coverUrl = hdCoverUrl(p.thumbnails.maxByOrNull { it.height }?.url).orEmpty(),
                        totalTracks = p.streamCount.toInt().coerceAtLeast(0),
                    )
                }
            }.getOrDefault(emptyList())

            val artists = runCatching {
                val info = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(keyword, listOf("channels"), ""))
                info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>().take(8).map { c ->
                    val id = c.url.substringAfterLast('/')
                    val name = c.name.orEmpty()
                    val subscribers = c.subscriberCount.coerceAtLeast(0L)
                    SpotifyCatalogArtist(
                        id = id,
                        uri = c.url,
                        name = name,
                        avatarUrl = hdCoverUrl(c.thumbnails.maxByOrNull { it.height }?.url).orEmpty(),
                        subscriberCount = subscribers,
                        isOfficialMusicChannel = isOfficialMusicChannelName(name) ||
                            subscribers >= NEW_PIPE_OFFICIAL_SUBSCRIBER_THRESHOLD,
                    )
                }
            }.getOrDefault(emptyList())

            val orderedTop = buildList<SpotifyCatalogTopResult> {
                val artistTop = artists.firstOrNull()
                if (artistTop != null && isFullNameMatch(keyword, artistTop.name)) {
                    add(SpotifyCatalogTopResult.Artist(artistTop))
                }
                tracks.firstOrNull()?.let { add(SpotifyCatalogTopResult.Track(it)) }
                if (artistTop != null && !isFullNameMatch(keyword, artistTop.name)) {
                    add(SpotifyCatalogTopResult.Artist(artistTop))
                }
                playlists.firstOrNull()?.let { add(SpotifyCatalogTopResult.Playlist(it)) }
            }
            val raw = SpotifyCatalogSearchResult(
                topResult = orderedTop.firstOrNull(),
                orderedTopResults = orderedTop,
                artists = artists,
                playlists = playlists,
                tracks = tracks,
            )
            applySearchGuardrails(raw, keyword)
        }
    }

    suspend fun fetchArtistOverview(artistId: String?, artistName: String): Result<SpotifyArtistOverview> = withContext(Dispatchers.IO) {
        runCatching {
            val providedId = artistId?.trim().orEmpty()
            val resolvedId = if (isValidSpotifyArtistId(providedId)) {
                normalizeSpotifyArtistId(providedId).orEmpty()
            } else {
                // ID NewPipe/channel bukan ID Spotify: jangan dikirim ke queryArtistOverview.
                resolveArtistIdByName(artistName).getOrNull().orEmpty()
            }
            if (resolvedId.isBlank()) {
                return@runCatching fetchArtistOverviewFallback(artistName, "").getOrThrow()
            }
            fetchArtistOverviewPathfinder(resolvedId, artistName).getOrElse { error ->
                if (error is CancellationException) throw error
                fetchArtistOverviewFallback(artistName, resolvedId).getOrThrow()
            }
        }
    }

    private suspend fun resolveArtistIdByName(name: String): Result<String> = runCatching {
        val catalog = searchSpotifyCatalogPathfinder(name).getOrThrow()
        val candidates = buildList<SpotifyCatalogArtist> {
            addAll(catalog.artists)
            val top = catalog.topResult
            if (top is SpotifyCatalogTopResult.Artist) add(top.item)
        }
        val chosen = candidates.firstOrNull { artist ->
            isFullNameMatch(name, artist.name) ||
                (artist.isPopularArtist && isArtistRelevantToQuery(name, artist.name))
        } ?: candidates.firstOrNull { artist -> isArtistRelevantToQuery(name, artist.name) }
        ?: throw IOException("Artis tidak ditemukan")
        normalizeSpotifyArtistId(chosen.id) ?: throw IOException("Artis tidak ditemukan")
    }

    private suspend fun fetchArtistOverviewPathfinder(artistId: String, fallbackName: String): Result<SpotifyArtistOverview> = runCatching {
        val uri = "spotify:artist:$artistId"
        val variables = JSONObject().put("uri", uri).put("locale", "id").put("includePrerelease", false)
        val json = query("queryArtistOverview", variables, KEY_ARTIST_OVERVIEW, allowPublicToken = true)
        parseArtistOverview(json, artistId, fallbackName)
    }

    private fun parseArtistOverview(root: JSONObject, artistId: String, fallbackName: String): SpotifyArtistOverview {
        val data = root.optJSONObject("data") ?: root
        val artist = findArtistObject(data) ?: data
        val profile = artist.optJSONObject("profile") ?: artist
        val name = profile.optString("name").ifBlank { artist.optString("name") }.ifBlank { fallbackName }
        val avatar = findBestImageUrl(artist.optJSONObject("visuals")?.optJSONObject("avatarImage") ?: profile)
        val header = findBestImageUrl(artist.optJSONObject("visuals")?.optJSONObject("headerImage") ?: artist)
        val stats = artist.optJSONObject("stats")
        val monthly = stats?.optLong("monthlyListeners", 0L)?.takeIf { it > 0L }
            ?: stats?.optLong("monthly_listeners", 0L) ?: 0L
        val bio = artist.optJSONObject("profile")?.optJSONObject("biography")?.optString("text")
            ?.ifBlank { artist.optJSONObject("biography")?.optString("text") }.orEmpty()
        val topTracks = parseArtistTopTracks(artist, artistId, name)
        val releases = parseArtistReleases(artist)
        val featuring = parseFeaturingPlaylists(artist, name)
        return SpotifyArtistOverview(
            id = artistId, name = name, avatarUrl = hdCoverUrl(avatar).orEmpty(),
            headerImageUrl = hdCoverUrl(header).orEmpty().ifBlank { hdCoverUrl(avatar).orEmpty() },
            monthlyListeners = if (monthly > 0) formatMonthlyListeners(monthly) else "",
            biography = bio, topTracks = topTracks, releases = releases, featuringPlaylists = featuring,
        )
    }

    private fun findArtistObject(data: JSONObject): JSONObject? {
        var found: JSONObject? = null
        fun visit(node: Any?, depth: Int) {
            if (found != null || node == null || depth > MAX_JSON_DEPTH) return
            when (node) {
                is JSONObject -> {
                    val uri = node.optString("uri")
                    if (uri.startsWith("spotify:artist:") && (node.has("profile") || node.has("discography") || node.has("visuals"))) {
                        found = node; return
                    }
                    node.keys().forEach { visit(node.opt(it), depth + 1) }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(data, 0)
        return found
    }

    private fun parseArtistTopTracks(artist: JSONObject, artistId: String, artistName: String): List<SpotifyArtistOverviewTrack> {
        val discography = artist.optJSONObject("discography")
        // Jangan menyapu seluruh objek artis: related content bisa memuat lagu artis lain.
        val container = discography?.optJSONObject("topTracks")
            ?: artist.optJSONObject("topTracks")
            ?: discography
            ?: return emptyList()
        val out = linkedMapOf<String, SpotifyArtistOverviewTrack>()
        fun visit(node: Any?, depth: Int) {
            if (node == null || depth > MAX_JSON_DEPTH) return
            when (node) {
                is JSONObject -> {
                    val uri = node.optString("uri")
                    if (uri.startsWith("spotify:track:")) {
                        val track = parseCatalogTrack(node)
                        if (track != null) {
                            val candidate = TrackOwnershipCandidate(
                                title = track.title,
                                artist = track.artist,
                                artistIds = track.artistIds,
                            )
                            if (!isTrackOwnedByArtist(candidate, artistId, artistName)) {
                                node.keys().forEach { visit(node.opt(it), depth + 1) }
                                return@visit
                            }
                            val playcount = node.optString("playcount").ifBlank { node.optJSONObject("playcount")?.optString("total").orEmpty() }
                            out.putIfAbsent(track.id, SpotifyArtistOverviewTrack(
                                id = track.id, uri = track.uri, title = track.title, artist = track.artist,
                                artistId = track.artistId.ifBlank { artistId }, artistIds = track.artistIds,
                                coverUrl = track.coverUrl, durationMs = track.durationMs, playcount = playcount,
                            ))
                        }
                    }
                    node.keys().forEach { visit(node.opt(it), depth + 1) }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(container, 0)
        return out.values.take(10).toList()
    }

    private fun parseArtistReleases(artist: JSONObject): List<SpotifyArtistRelease> {
        val out = linkedMapOf<String, SpotifyArtistRelease>()
        fun visit(node: Any?, depth: Int) {
            if (node == null || depth > MAX_JSON_DEPTH) return
            when (node) {
                is JSONObject -> {
                    val uri = node.optString("uri")
                    if (uri.startsWith("spotify:album:")) {
                        val id = uri.substringAfterLast(':')
                        val name = node.optString("name").ifBlank { return }
                        val date = node.optJSONObject("date")?.optInt("year", 0)?.takeIf { it > 0 }?.toString()
                            ?: node.optString("release_date").take(4).takeIf { it.any(Char::isDigit) }.orEmpty()
                        val type = node.optString("type").ifBlank { node.optString("__typename") }
                            .replace("Album", "Album").replace("Single", "Single").replace("Ep", "EP")
                        out.putIfAbsent(id, SpotifyArtistRelease(id, uri, name, hdCoverUrl(findBestImageUrl(node)).orEmpty(), date, type))
                    }
                    node.keys().forEach { visit(node.opt(it), depth + 1) }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(artist.optJSONObject("discography") ?: artist, 0)
        return out.values.take(10).toList()
    }

    private fun parseFeaturingPlaylists(artist: JSONObject, artistName: String): List<SpotifyArtistRelatedPlaylist> {
        val out = linkedMapOf<String, SpotifyArtistRelatedPlaylist>()
        fun visit(node: Any?, depth: Int) {
            if (node == null || depth > MAX_JSON_DEPTH) return
            when (node) {
                is JSONObject -> {
                    val uri = node.optString("uri")
                    if (uri.startsWith("spotify:playlist:")) {
                        val name = node.optString("name")
                        if (name.contains(artistName, true) || name.startsWith("This Is", true) || name.startsWith("Menampilkan", true)) {
                            val id = uri.substringAfterLast(':')
                            out.putIfAbsent(id, SpotifyArtistRelatedPlaylist(id, uri, name, hdCoverUrl(findBestImageUrl(node)).orEmpty(), node.optJSONObject("ownerV2")?.optString("name").orEmpty()))
                        }
                    }
                    node.keys().forEach { visit(node.opt(it), depth + 1) }
                }
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), depth + 1)
            }
        }
        visit(artist.optJSONObject("relatedContent") ?: artist.optJSONObject("profile") ?: artist, 0)
        return out.values.take(10).toList()
    }

    private fun formatMonthlyListeners(count: Long): String {
        val value = when {
            count >= 1_000_000_000L -> String.format(Locale("id", "ID"), "%.1f M", count / 1_000_000_000.0)
            count >= 1_000_000L -> String.format(Locale("id", "ID"), "%.1f jt", count / 1_000_000.0)
            count >= 1_000L -> String.format(Locale("id", "ID"), "%.1f rb", count / 1_000.0)
            else -> count.toString()
        }.replace(".0", "")
        return "$value pendengar bulanan"
    }

    private suspend fun fetchArtistOverviewFallback(
        artistName: String,
        targetArtistId: String = "",
    ): Result<SpotifyArtistOverview> = runCatching {
        withContext(Dispatchers.IO) {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val channel = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(artistName, listOf("channels"), ""))
                .relatedItems.filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>().firstOrNull()
            val targetName = stripYouTubeArtistSuffix(artistName)
            val tracks = musicExtractorRepository?.searchSongs("$artistName lagu populer")?.getOrDefault(emptyList()).orEmpty()
                .filter { song ->
                    isTrackOwnedByArtist(
                        track = TrackOwnershipCandidate(
                            title = song.title,
                            artist = song.artist,
                            artistIds = song.artistIds,
                            isNewPipe = true,
                        ),
                        targetArtistId = targetArtistId,
                        targetArtistName = targetName,
                    )
                }
                .take(10)
                .map {
                    SpotifyArtistOverviewTrack(
                        id = it.id, uri = it.url, title = it.title, artist = it.artist,
                        artistIds = it.artistIds, coverUrl = it.coverUrl, durationMs = it.durationMs,
                    )
                }
            SpotifyArtistOverview(
                id = channel?.url?.substringAfterLast('/').orEmpty(),
                name = stripYouTubeArtistSuffix(channel?.name ?: artistName),
                avatarUrl = hdCoverUrl(channel?.thumbnails?.maxByOrNull { it.height }?.url).orEmpty(),
                headerImageUrl = hdCoverUrl(channel?.thumbnails?.maxByOrNull { it.height }?.url).orEmpty(),
                topTracks = tracks,
            )
        }
    }

    suspend fun fetchPublicPlaylist(playlistId: String): Result<SpotifyPublicPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val id = playlistId.substringAfterLast(':').substringAfterLast('/')
            fetchPublicPlaylistPathfinder(id).getOrElse { error ->
                if (error is CancellationException) throw error
                fetchPublicPlaylistFallback(id).getOrThrow()
            }
        }
    }

    private suspend fun fetchPublicPlaylistPathfinder(id: String): Result<SpotifyPublicPlaylist> = runCatching {
        val uri = "spotify:playlist:$id"
        val variables = JSONObject().put("uri", uri).put("offset", 0).put("limit", 100)
        val json = query("fetchPlaylist", variables, KEY_PLAYLIST, allowPublicToken = true)
        val playlist = json.optJSONObject("data")?.optJSONObject("playlistV2") ?: JSONObject()
        val content = playlist.optJSONObject("content") ?: JSONObject()
        val items = content.optJSONArray("items") ?: JSONArray()
        val tracks = mutableListOf<SpotifyCatalogTrack>()
        for (i in 0 until items.length()) {
            val wrapper = items.optJSONObject(i) ?: continue
            val data = wrapper.optJSONObject("itemV2")?.optJSONObject("data") ?: wrapper.optJSONObject("item")?.optJSONObject("data") ?: continue
            val trackUri = data.optString("uri")
            if (trackUri.startsWith("spotify:track:")) parseCatalogTrack(data)?.let { tracks += it }
        }
        val owner = playlist.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name").orEmpty()
        SpotifyPublicPlaylist(
            id = id, uri = uri,
            name = playlist.optString("name").ifBlank { "Playlist" },
            description = playlist.optString("description").orEmpty(),
            ownerName = owner,
            coverUrl = hdCoverUrl(findBestImageUrl(playlist.optJSONObject("images") ?: playlist)).orEmpty(),
            totalTracks = content.optInt("totalCount", tracks.size),
            tracks = tracks,
        )
    }

    private suspend fun fetchPublicPlaylistFallback(id: String): Result<SpotifyPublicPlaylist> = runCatching {
        withContext(Dispatchers.IO) {
            val url = if (id.startsWith("http")) id else "https://www.youtube.com/playlist?list=$id"
            val info = PlaylistInfo.getInfo(NewPipe.getService(ServiceList.YouTube.serviceId), url)
            val tracks = info.relatedItems.filterIsInstance<StreamInfoItem>().take(100).map {
                val vid = it.url.substringAfter("v=", it.url.substringAfterLast('/'))
                SpotifyCatalogTrack(
                    id = vid, uri = it.url, title = it.name,
                    artist = it.uploaderName ?: "Artis tidak diketahui",
                    artistId = it.uploaderUrl?.substringAfterLast('/').orEmpty(),
                    artistIds = listOfNotNull(it.uploaderUrl?.substringAfterLast('/')?.takeIf { id -> id.isNotBlank() }),
                    thumbnailUrl = listThumbnailUrl(it.thumbnails.maxByOrNull { t -> t.height }?.url ?: youtubeListThumbnailUrl(it.url)).orEmpty(),
                    coverUrl = hdCoverUrl(it.thumbnails.maxByOrNull { t -> t.height }?.url ?: youtubeThumbnailUrl(it.url)).orEmpty(),
                    durationMs = (it.duration * 1000L).coerceAtLeast(0L),
                )
            }
            SpotifyPublicPlaylist(
                id = id, uri = url, name = info.name, description = info.description?.content.orEmpty(),
                ownerName = info.uploaderName.orEmpty(),
                coverUrl = hdCoverUrl(info.thumbnails.maxByOrNull { it.height }?.url).orEmpty(),
                totalTracks = tracks.size, tracks = tracks,
            )
        }
    }

    companion object {
        const val COLLECTION_LIKED = "__spotify_liked__"
        const val API_BASE = "https://api.spotify.com"
        const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
        const val WEB_PLAYER_VERSION = "1.2.52.442.g0158086c"
        const val PAGE_SIZE = 50
        const val LIKED_PAGE_SIZE = 100
        const val TRACK_PAGE_SIZE = 300
        const val MAX_COLLECTIONS = 2_000
        const val MAX_TRACKS = 10_000
        const val MAX_JSON_DEPTH = 14
        const val PAGE_DELAY_MS = 0L
        const val PATHFINDER_CONCURRENCY = 3
        private const val MAX_RATE_LIMIT_RETRIES = 2
        private const val DEFAULT_RETRY_AFTER_MS = 1_500L
        private const val MIN_RETRY_DELAY_MS = 200L
        private const val MAX_RETRY_DELAY_MS = 5_000L
        private val RETRY_AFTER_DATE_PATTERNS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",
            "EEE MMM d HH:mm:ss yyyy",
        )
        private const val KEY_LIBRARY = "libraryV3"
        private const val KEY_LIKED = "fetchLibraryTracks"
        private const val KEY_PLAYLIST = "fetchPlaylist"
        private const val KEY_SEARCH = "searchDesktop"
        private const val KEY_ARTIST_OVERVIEW = "queryArtistOverview"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
