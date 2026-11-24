package com.dengerin.app.data.lyrics

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Respons dari endpoint LRCLIB (`/api/get` dan `/api/search`). */
data class LrclibTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val instrumental: Boolean,
)

/**
 * Klien LRCLIB memakai OkHttp + `org.json` (sudah ada di Android), tanpa dependency baru.
 * `/api/get` paling akurat karena menyertakan durasi; `/api/search` jadi fallback saat
 * judul dari YouTube mengandung annotation sehingga `get` mengembalikan 404.
 */
class LrclibService(
    private val client: OkHttpClient = DEFAULT_CLIENT,
) {

    suspend fun find(title: String, artist: String, durationMs: Long): LrclibTrack? =
        withContext(Dispatchers.IO) {
            val duration = (durationMs / 1000L).toInt()
            val byGet = runCatching { getTrack(title, artist, duration) }
                .onFailure { Log.d(TAG, "LRCLIB /get gagal: ${it.message}") }
                .getOrNull()
            if (byGet != null) return@withContext byGet

            val bySearch = runCatching { search(title, artist, duration) }
                .onFailure { Log.d(TAG, "LRCLIB /search gagal: ${it.message}") }
                .getOrNull()
                .orEmpty()
            if (bySearch.isNotEmpty()) return@withContext pickBest(bySearch, title, artist, duration)

            // Terakhir: cari berdasarkan judul saja (artis sering tidak persis sama).
            runCatching { search(title, "", duration) }
                .onFailure { Log.d(TAG, "LRCLIB /search (judul) gagal: ${it.message}") }
                .getOrNull()
                .orEmpty()
                .let { results -> if (results.isEmpty()) null else pickBest(results, title, artist, duration) }
        }

    private fun getTrack(title: String, artist: String, duration: Int): LrclibTrack? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("lrclib.net")
            .addPathSegment("api")
            .addPathSegment("get")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", duration.toString())
            .build()
        val response = client.newCall(request(url)).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code != 404) Log.d(TAG, "LRCLIB /get HTTP ${resp.code}")
                return@use null
            }
            resp.body?.string()?.let { body ->
                JSONObject(body).takeIf { it.length() > 0 }?.let(::parseTrack)
            }
        }
    }

    private fun search(title: String, artist: String, duration: Int): List<LrclibTrack> {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("lrclib.net")
            .addPathSegment("api")
            .addPathSegment("search")
            .addQueryParameter("track_name", title)
        if (artist.isNotBlank()) builder.addQueryParameter("artist_name", artist)

        val response = client.newCall(request(builder.build())).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) {
                Log.d(TAG, "LRCLIB /search HTTP ${resp.code}")
                return@use emptyList()
            }
            val body = resp.body?.string().orEmpty()
            val array = runCatching { JSONArray(body) }.getOrElse { return@use emptyList() }
            (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index) }.getOrNull()?.let(::parseTrack)
            }
        }
    }

    private fun request(url: HttpUrl): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .build()

    private fun parseTrack(json: JSONObject): LrclibTrack = LrclibTrack(
        id = json.optLong("id"),
        title = json.optString("trackName", ""),
        artist = json.optString("artistName", ""),
        album = json.optString("albumName", ""),
        durationSeconds = json.optInt("duration", 0),
        syncedLyrics = json.optString("syncedLyrics", "").ifBlank { null },
        plainLyrics = json.optString("plainLyrics", "").ifBlank { null },
        instrumental = json.optBoolean("instrumental", false),
    )

    private fun pickBest(
        tracks: List<LrclibTrack>,
        title: String,
        artist: String,
        duration: Int,
    ): LrclibTrack {
        fun LrclibTrack.score(): Int {
            var value = 0
            if (normalize(this.title) == normalize(title)) value += 6
            if (normalize(this.artist) == normalize(artist)) value += 6
            if (this.syncedLyrics != null) value += 4
            val delta = kotlin.math.abs(this.durationSeconds - duration)
            if (duration > 0 && delta <= 2) value += 3
            if (delta > 15) value -= 4
            return value
        }
        return tracks.maxBy { it.score() }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""\[[^\]]*\]"""), "")
        .replace(Regex("""[^a-z0-9]"""), "")
        .trim()

    private companion object {
        const val TAG = "DengerinLyrics"
        const val USER_AGENT = "Dengerin/1.0 (Android live lyrics; dengerin.app)"

        val DEFAULT_CLIENT: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        }
    }
}
