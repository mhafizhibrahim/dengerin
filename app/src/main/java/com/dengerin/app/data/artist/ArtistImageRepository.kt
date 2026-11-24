package com.dengerin.app.data.artist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Foto profil asli artis dari Deezer Public API (gratis, tanpa API key).
 * Hasil disimpan di cache memori supaya artis yang sama tidak di-fetch ulang.
 */
class ArtistImageRepository {

    private val cache = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** @return URL foto wajah artis, atau kosong jika tidak tersedia. */
    suspend fun getImageUrl(artistName: String): String = withContext(Dispatchers.IO) {
        val key = artistName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return@withContext ""
        cache[key]?.let { return@withContext it }

        val query = URLEncoder.encode(artistName.trim(), "UTF-8")
        val request = Request.Builder()
            .url("https://api.deezer.com/search/artist?q=$query&limit=1")
            .header("User-Agent", "Dengerin/1.1 (Android)")
            .build()

        val imageUrl = runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Deezer HTTP ${response.code} untuk artis: $artistName")
                    return@use ""
                }
                val root = JSONObject(response.body?.string().orEmpty())
                val artist = root.optJSONArray("data")?.optJSONObject(0) ?: return@use ""
                listOf("picture_xl", "picture_big", "picture_medium")
                    .map { artist.optString(it) }
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                    .orEmpty()
            }
        }.onFailure { error ->
            Log.w(TAG, "Gagal memuat foto artis: $artistName", error)
        }.getOrDefault("")

        // Hanya cache hasil valid; kegagalan jaringan boleh dicoba lagi nanti.
        if (imageUrl.isNotBlank()) cache[key] = imageUrl
        imageUrl
    }

    /** Cache dipakai ViewModel supaya perubahan chip/tab tetap instan. */
    fun cachedImageUrl(artistName: String): String =
        cache[artistName.trim().lowercase(Locale.ROOT)].orEmpty()

    private companion object {
        const val TAG = "DengerinArtistImage"
    }
}
