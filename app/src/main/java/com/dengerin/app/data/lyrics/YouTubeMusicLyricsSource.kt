package com.dengerin.app.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Lirik polos dari YouTube Music + label sumber yang ditampilkan YTM (mis. "Sumber: LyricFind"). */
data class YouTubeMusicLyrics(
    val lyrics: String,
    val sourceLabel: String?,
)

/**
 * Klien Internal `youtubei` YouTube Music (client WEB_REMIX) untuk mengambil lirik
 * berdasarkan `videoId` lagu yang sedang diputar.
 *
 * Request sengaja diberi timeout 5 detik dan dibungkus `runCatching`: gagal/offline
 * tidak boleh memblokir pemutaran audio. Hasil disimpan repository di cache Room,
 * jadi lagu yang diputar ulang tidak mengunduh JSON lagi (0 MB).
 */
class YouTubeMusicLyricsSource(
    private val client: OkHttpClient,
) {

    suspend fun load(videoId: String): YouTubeMusicLyrics? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        val fastClient = client.newBuilder()
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        val browseId = runCatching { fetchLyricsBrowseId(fastClient, videoId) }
            .onFailure { Log.d(TAG, "youtubei/next gagal: ${it.message}") }
            .getOrNull()
            ?: return@withContext null

        runCatching { fetchLyrics(fastClient, browseId) }
            .onFailure { Log.d(TAG, "youtubei/browse gagal: ${it.message}") }
            .getOrNull()
    }

    /** Langkah 1: cari tab Lirik (`MPLYt...`) dari response `next`. */
    private fun fetchLyricsBrowseId(client: OkHttpClient, videoId: String): String? {
        val root = post(client, NEXT_URL, JSONObject().put("videoId", videoId)) ?: return null
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs")
            ?: return null

        for (index in 0 until tabs.length()) {
            val browseId = tabs.optJSONObject(index)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                .orEmpty()
            if (browseId.startsWith(LYRICS_BROWSE_PREFIX)) return browseId
        }
        // Tidak ada tab lirik = lagu memang tidak punya lirik di YouTube Music.
        return null
    }

    /** Langkah 2: baca shelf deskripsi lirik dari response `browse`. */
    private fun fetchLyrics(client: OkHttpClient, browseId: String): YouTubeMusicLyrics? {
        val root = post(client, BROWSE_URL, JSONObject().put("browseId", browseId)) ?: return null
        val contents = root.optJSONObject("contents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return null

        val shelf = (0 until contents.length())
            .mapNotNull { contents.optJSONObject(it)?.optJSONObject("musicDescriptionShelfRenderer") }
            .firstOrNull()
            ?: return null

        val lyrics = joinRuns(shelf.optJSONArray("description")).trim()
        if (lyrics.isEmpty()) return null

        val footer = joinRuns(shelf.optJSONArray("footer")).trim()
        val sourceLabel = when {
            footer.isEmpty() -> null
            footer.startsWith(SOURCE_PREFIX, ignoreCase = true) -> footer
            else -> "$SOURCE_PREFIX $footer"
        }
        return YouTubeMusicLyrics(lyrics = lyrics, sourceLabel = sourceLabel)
    }

    private fun post(client: OkHttpClient, url: String, payload: JSONObject): JSONObject? {
        val body = JSONObject()
            .put("context", context())
            .apply {
                val keys = payload.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, payload.get(key))
                }
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        // `youtubei` YTM membutuhkan API key publik WEB_REMIX; URL dasar tetap sesuai
        // endpoint yang diminta (?prettyPrint=false), key hanya ditambahkan sebagai query.
        val request = Request.Builder()
            .url("$url&key=$WEB_REMIX_API_KEY")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d(TAG, "youtubei HTTP ${response.code}")
                return null
            }
            val text = response.body?.string().orEmpty()
            return runCatching { JSONObject(text) }.getOrNull()
        }
    }

    private fun context(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "id")
            .put("gl", "ID"),
    )

    private fun joinRuns(runs: JSONArray?): String {
        if (runs == null) return ""
        return buildString {
            for (index in 0 until runs.length()) {
                append(runs.optJSONObject(index)?.optString("text").orEmpty())
            }
        }
    }

    private companion object {
        const val TAG = "DengerinYtmLyrics"
        const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
        const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        const val CLIENT_VERSION = "1.20241127.01.00"
        const val WEB_REMIX_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        const val LYRICS_BROWSE_PREFIX = "MPLYt"
        const val SOURCE_PREFIX = "Sumber:"
        const val TIMEOUT_MS = 5_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
