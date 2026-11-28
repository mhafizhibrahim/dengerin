package com.dengerin.app.data.spotify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * `client-token` Web Player ala Spotube. Pathfinder menolak request tanpa header ini,
 * jadi token diminta dari `clienttoken.spotify.com` memakai `clientId` hasil ekstraksi
 * sesi Web Player dan `deviceId` UUID persisten di Preferences DataStore.
 *
 * Token disimpan beserta waktu kedaluwarsanya dan diminta ulang otomatis menjelang
 * habis, atau dipaksa segar oleh repository saat Pathfinder membalas HTTP 401.
 */
class SpotifyClientTokenManager(
    context: Context,
    private val session: SpotifySessionManager,
) {
    private val dataStore: DataStore<Preferences> =
        context.applicationContext.spotifySessionDataStore
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val storedPreferences = dataStore.data.catch { emit(emptyPreferences()) }

    /** Satu refresh bersama: banyak halaman paralel tidak boleh meminta token bersamaan. */
    private val requestMutex = Mutex()

    @Volatile
    private var lastForcedRefreshAtMs = 0L

    /** Token client yang masih berlaku; otomatis meminta baru bila sudah kedaluwarsa. */
    suspend fun getClientToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            readValidCachedToken() ?: requestMutex.withLock {
                // Cek ulang di dalam lock: pemanggil lain bisa saja baru saja menyegarkan.
                readValidCachedToken() ?: requestNewTokenLocked()
            }
        }
    }

    private suspend fun readValidCachedToken(): String? {
        val prefs = storedPreferences.firstOrNull() ?: emptyPreferences()
        val cached = prefs[CLIENT_TOKEN_KEY]
        val expiresAt = prefs[CLIENT_TOKEN_EXPIRY_KEY] ?: 0L
        return cached?.takeIf {
            it.isNotBlank() && System.currentTimeMillis() < expiresAt - REFRESH_MARGIN_MS
        }
    }

    /** Paksa token baru tanpa menghapus cookie `sp_dc` (dipakai saat HTTP 401). */
    suspend fun refreshClientToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requestMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastForcedRefreshAtMs < REFRESH_COOLDOWN_MS) {
                    readValidCachedToken()?.let { return@withLock it }
                }
                val refreshed = requestNewTokenLocked()
                lastForcedRefreshAtMs = System.currentTimeMillis()
                refreshed
            }
        }
    }

    private suspend fun requestNewTokenLocked(): String {
        val prefs = storedPreferences.firstOrNull() ?: emptyPreferences()
        val deviceId = prefs[DEVICE_ID_KEY]?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { generated ->
                dataStore.edit { it[DEVICE_ID_KEY] = generated }
            }

        val candidates = linkedSetOf<String>()
        session.clientId.firstOrNull()?.takeIf { it.isNotBlank() }?.let { candidates += it }
        candidates += FALLBACK_CLIENT_ID

        var lastError: IOException? = null
        var index = 0
        var liveIdTried = false
        while (index < candidates.size) {
            val clientId = candidates.elementAt(index)
            try {
                return requestWithClientId(clientId, deviceId)
            } catch (error: IOException) {
                lastError = error
                if (!liveIdTried && error.message?.contains("HTTP 400") == true) {
                    liveIdTried = true
                    runCatching { session.refreshSessionToken() }
                    val liveId = session.clientId.firstOrNull().orEmpty()
                    if (liveId.isNotBlank() && !candidates.contains(liveId)) candidates += liveId
                }
            }
            index++
        }
        throw lastError ?: IOException("Client token Spotify belum tersedia.")
    }

    private suspend fun requestWithClientId(clientId: String, deviceId: String): String {
        val payload = JSONObject()
            .put(
                "client_data",
                JSONObject()
                    .put("client_version", WEB_PLAYER_VERSION)
                    .put("client_id", clientId)
                    .put(
                        "js_sdk_data",
                        JSONObject()
                            .put("device_brand", "unknown")
                            .put("device_model", "unknown")
                            .put("os", "windows")
                            .put("os_version", "NT 10.0")
                            .put("device_id", deviceId)
                            .put("device_type", "computer"),
                    ),
            )

        val request = Request.Builder()
            .url(CLIENT_TOKEN_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Origin", "https://open.spotify.com")
            .header("Referer", "https://open.spotify.com/")
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .header("User-Agent", SpotifySessionDom.CHROME_DESKTOP_USER_AGENT)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        try {
            val body = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                val reason = runCatching {
                    JSONObject(body).optString("error").ifBlank { body }
                }.getOrDefault(body)
                throw IOException(
                    "Gagal mengambil client token Spotify (HTTP ${response.code}): " +
                        reason.take(240),
                )
            }
            val granted = runCatching { JSONObject(body).optJSONObject("granted_token") }
                .getOrNull()
                ?: throw IOException("Client token Spotify tidak ditemukan pada respons.")
            val token = granted.optString("token").takeIf { it.isNotBlank() }
                ?: throw IOException("Client token Spotify kosong.")
            val expiresAt = System.currentTimeMillis() + lifetimeMs(granted)
            dataStore.edit { preferences ->
                preferences[CLIENT_TOKEN_KEY] = token
                preferences[CLIENT_TOKEN_EXPIRY_KEY] = expiresAt
            }
            return token
        } finally {
            response.close()
        }
    }

    /** Spotify mengirim `expires_after` dalam milidetik; beberapa build memakai detik. */
    private fun lifetimeMs(granted: JSONObject): Long {
        for (key in arrayOf("expires_after", "expiresAfter", "expires_in", "expiresIn")) {
            val raw = granted.optLong(key, 0L)
            if (raw > 0L) return if (raw < 1_000_000L) raw * 1000L else raw
        }
        return DEFAULT_TTL_MS
    }

    private companion object {
        const val CLIENT_TOKEN_URL = "https://clienttoken.spotify.com/v1/clienttoken"
        const val WEB_PLAYER_VERSION = "1.2.52.442.g0158086c"
        const val FALLBACK_CLIENT_ID = "d8a5ed958d274c2e8ee717e6a4b0971d"
        const val REFRESH_MARGIN_MS = 60_000L
        const val REFRESH_COOLDOWN_MS = 1_500L
        const val DEFAULT_TTL_MS = 6 * 24 * 60 * 60_000L
        val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()
        val CLIENT_TOKEN_KEY = stringPreferencesKey("spotifyClientToken")
        val CLIENT_TOKEN_EXPIRY_KEY = longPreferencesKey("spotifyClientTokenExpiryMs")
        val DEVICE_ID_KEY = stringPreferencesKey("spotifyDeviceId")
    }
}
