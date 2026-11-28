package com.dengerin.app.data.spotify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dengerin.app.core.FirestoreIds
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal val Context.spotifySessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dengerin_spotify_session",
)

private val SP_DC_KEY = stringPreferencesKey("sp_dc_encrypted")
private val ACCESS_TOKEN_KEY = stringPreferencesKey("spotify_access_token_encrypted")
private val TOKEN_EXPIRY_KEY = longPreferencesKey("spotify_token_expiry_ms")
private val CLIENT_ID_KEY = stringPreferencesKey("clientId")
private val DISPLAY_NAME_KEY = stringPreferencesKey("spotifyDisplayName")
private val AVATAR_URL_KEY = stringPreferencesKey("spotifyAvatarUrl")
private val USER_ID_KEY = stringPreferencesKey("spotifyUserId")
private val CONNECTED_AT_KEY = longPreferencesKey("spotifyConnectedAt")
private val LOGGED_OUT_AT_KEY = longPreferencesKey("spotifyLoggedOutAt")

// Kunci versi lama/mentah ikut dibuang supaya logout benar-benar bersih setelah upgrade.
private val LEGACY_SP_DC_KEY = stringPreferencesKey("sp_dc")
private val LEGACY_ACCESS_TOKEN_KEY = stringPreferencesKey("accessToken")
private val LEGACY_TOKEN_EXPIRY_KEY = longPreferencesKey("accessTokenExpirationTimestampMs")

/**
 * Sesi akun Spotify tanpa Client ID dan tanpa parameter TOTP: `sp_dc` disimpan terenkripsi
 * dengan AndroidKeyStore, lalu access token diambil dari JSON sesi yang disematkan Spotify
 * di halaman `https://open.spotify.com/`. Token di-cache di memori dan disimpan terenkripsi
 * di DataStore sampai menjelang expiry.
 *
 * Penghapusan sesi HANYA terjadi lewat [logoutSpotify], yang dipanggil dari aksi manual
 * pengguna "Keluar dari Spotify". Error jaringan/HTTP/401/403/429 dan sesi anonymous tidak
 * pernah menghapus `sp_dc`, token, atau profil yang tersimpan.
 *
 * Token sesi dipakai sebagai Bearer untuk Web API resmi `api.spotify.com/v1/me/...`.
 * Firestore dipakai untuk menyimpan identitas akun dan menandai waktu logout manual agar
 * perangkat lain tidak memulihkan sesi lama.
 */
class SpotifySessionManager(
    context: Context,
    private val tokenRefresher: HeadlessSpotifyTokenRefresher,
    private val authState: StateFlow<FirebaseUser?>,
) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.spotifySessionDataStore
    private val tokenMutex = Mutex()
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpiresAtMs = 0L

    @Volatile
    private var lastForcedRefreshAtMs = 0L

    private val publicTokenMutex = Mutex()

    @Volatile
    private var cachedPublicToken: String? = null

    @Volatile
    private var cachedPublicTokenExpiryMs = 0L

    @Volatile
    private var cachedPublicClientId: String = FALLBACK_CLIENT_ID

    private val publicHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Flag memori logout manual. Ditulis lebih dulu oleh [logoutSpotify] supaya listener
     * cloud/refresher tidak sempat memulihkan atau menyegarkan token saat proses berjalan.
     */
    private val _isExplicitlyLoggedOut = MutableStateFlow(false)
    val isExplicitlyLoggedOut: Boolean get() = _isExplicitlyLoggedOut.value
    val explicitLogoutState: StateFlow<Boolean> = _isExplicitlyLoggedOut.asStateFlow()

    /** Dipanggil sebelum menulis sesi baru (login manual maupun restore yang diizinkan). */
    fun markConnected() {
        _isExplicitlyLoggedOut.value = false
    }

    private val storedPreferences: Flow<Preferences> = dataStore.data
        .catch { emit(emptyPreferences()) }

    /** Cookie mentah tidak pernah keluar dari sini; UI cukup tahu ada sesi atau tidak. */
    val cookie: Flow<String?> = storedPreferences
        .map { prefs -> prefs[SP_DC_KEY]?.let { decrypt(it) } ?: prefs[LEGACY_SP_DC_KEY] }

    /** Client ID Web Player hasil ekstraksi `<script id="session">`; dipakai client-token. */
    val clientId: Flow<String> = storedPreferences
        .map { prefs -> prefs[CLIENT_ID_KEY]?.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_ID }

    /** Waktu sesi lokal terakhir disambungkan; dipakai pembanding `spotifyLoggedOutAt`. */
    val sessionConnectedAt: Flow<Long> = storedPreferences
        .map { prefs -> prefs[CONNECTED_AT_KEY] ?: 0L }

    /**
     * Penanda logout manual lokal. Tetap bertahan setelah proses mati saat penulisan
     * Firestore gagal (offline), sehingga restore cloud tidak menyambung akun lama kembali.
     */
    val loggedOutAt: Flow<Long> = storedPreferences
        .map { prefs -> prefs[LOGGED_OUT_AT_KEY] ?: 0L }

    /** Status koneksi langsung mengikuti flag logout, tanpa menunggu DataStore selesai ditulis. */
    val isConnected: Flow<Boolean> = combine(storedPreferences, explicitLogoutState) { prefs, loggedOut ->
        !loggedOut && (
            !prefs[SP_DC_KEY].isNullOrBlank() ||
                !prefs[LEGACY_SP_DC_KEY].isNullOrBlank() ||
                !prefs[ACCESS_TOKEN_KEY].isNullOrBlank() ||
                !prefs[LEGACY_ACCESS_TOKEN_KEY].isNullOrBlank()
            )
    }.distinctUntilChanged()

    /** Profil tersimpan ikut bertahan saat fetch gagal atau aplikasi dibuka ulang. */
    val profile: Flow<SpotifyProfile?> = storedPreferences.map { prefs ->
        val name = prefs[DISPLAY_NAME_KEY]
        if (name.isNullOrBlank()) null
        else SpotifyProfile(
            displayName = name,
            avatarUrl = prefs[AVATAR_URL_KEY].orEmpty(),
            id = prefs[USER_ID_KEY].orEmpty(),
        )
    }

    suspend fun saveCookie(rawCookie: String, connectedAtMs: Long = System.currentTimeMillis()) {
        if (isExplicitlyLoggedOut) return
        val value = normalizeSpDc(rawCookie)
        require(value.isNotBlank()) { "Cookie sesi Spotify tidak ditemukan." }
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_SP_DC_KEY)
            prefs.remove(LOGGED_OUT_AT_KEY)
            prefs[SP_DC_KEY] = encrypt(value)
            prefs[CONNECTED_AT_KEY] = connectedAtMs
        }
        invalidateToken()
    }

    /** Token awal dari WebView: disimpan terenkripsi di DataStore sebelum dialog ditutup. */
    suspend fun saveSessionToken(token: String, expiresAtMs: Long, clientId: String = "") {
        if (isExplicitlyLoggedOut) return
        val value = token.trim()
        require(value.isNotBlank()) { "Token sesi Spotify kosong." }
        val resolvedExpiry = resolveExpiry(expiresAtMs)
        storeToken(value, resolvedExpiry, clientId)
        tokenMutex.withLock {
            cachedToken = value
            tokenExpiresAtMs = resolvedExpiry
        }
    }

    /** Profil akun disimpan supaya tidak hilang bila fetch gagal/Flutter-like cold start. */
    suspend fun saveProfile(displayName: String, avatarUrl: String, userId: String = "") {
        if (isExplicitlyLoggedOut) return
        dataStore.edit { prefs ->
            prefs[DISPLAY_NAME_KEY] = displayName.trim().ifBlank { "Akun Spotify" }
            prefs[AVATAR_URL_KEY] = avatarUrl.trim()
            if (userId.isNotBlank()) prefs[USER_ID_KEY] = userId
        }
    }

    /**
     * Identitas akun (`GET /v1/me`) juga disimpan ke `users/{uid}` saat Google login aktif.
     * `spotifyLoggedOutAt` dihapus supaya penanda logout lama tidak memblokir sesi baru.
     */
    suspend fun saveProfileToCloud(profile: SpotifyProfile) {
        if (isExplicitlyLoggedOut) return
        val uid = authState.value?.uid?.let { runCatching { FirestoreIds.user(it) }.getOrNull() }
            ?: return
        runCatching {
            firestore.collection(COLLECTION_USERS).document(uid)
                .set(
                    mapOf(
                        "spotifyDisplayName" to profile.displayName,
                        "spotifyAvatarUrl" to profile.avatarUrl,
                        "spotifyUserId" to profile.id,
                        "spotifyConnectedAt" to System.currentTimeMillis(),
                        "spotifyLoggedOutAt" to FieldValue.delete(),
                    ),
                    SetOptions.merge(),
                ).await()
        }
    }

    /**
     * SATU-SATUNYA jalur penghapusan sesi: tombol manual "Keluar / Putuskan Akun Spotify".
     * Urutan ini wajib: flag → DataStore → WebView → Firestore. Tidak ada dialog login,
     * tidak ada refresh token, dan tidak ada pemulihan cloud setelah ini.
     */
    @Suppress("DEPRECATION")
    suspend fun logoutSpotify() {
        // 1. Tutup semua jalur auto-restore/refresh sebelum DataStore disentuh.
        _isExplicitlyLoggedOut.value = true
        invalidateToken()

        // 2. Hapus seluruh data Spotify di Preferences DataStore dan simpan penanda logout.
        removeStoredSession(markLogout = true)

        // 3. Bersihkan penyimpanan WebView di main thread (cookie + DOM storage).
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }

        // 4. Tandai logout di users/{uid} agar perangkat lain tidak memulihkan sesi lama.
        val uid = authState.value?.uid?.let { runCatching { FirestoreIds.user(it) }.getOrNull() }
        if (uid != null) {
            runCatching {
                firestore.collection(COLLECTION_USERS).document(uid)
                    .update(
                        mapOf(
                            "spotifySpDc" to FieldValue.delete(),
                            "spotifyDisplayName" to FieldValue.delete(),
                            "spotifyAvatarUrl" to FieldValue.delete(),
                            "spotifyLoggedOutAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
            }
        }
    }

    /** Partner API menolak token (401/403): buang cache memori, jangan sentuh DataStore. */
    fun invalidateCachedToken() {
        cachedToken = null
        tokenExpiresAtMs = 0L
    }

    private suspend fun removeStoredSession(markLogout: Boolean = false) {
        dataStore.edit { prefs ->
            if (markLogout) prefs[LOGGED_OUT_AT_KEY] = System.currentTimeMillis()
            prefs.remove(SP_DC_KEY)
            prefs.remove(LEGACY_SP_DC_KEY)
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(LEGACY_ACCESS_TOKEN_KEY)
            prefs.remove(TOKEN_EXPIRY_KEY)
            prefs.remove(LEGACY_TOKEN_EXPIRY_KEY)
            prefs.remove(CLIENT_ID_KEY)
            prefs.remove(DISPLAY_NAME_KEY)
            prefs.remove(AVATAR_URL_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(CONNECTED_AT_KEY)
        }
    }

    private suspend fun storeToken(value: String, expiry: Long, clientId: String = "") {
        dataStore.edit { prefs ->
            prefs.remove(LEGACY_ACCESS_TOKEN_KEY)
            prefs[ACCESS_TOKEN_KEY] = encrypt(value)
            prefs[TOKEN_EXPIRY_KEY] = expiry
            prefs.remove(LEGACY_TOKEN_EXPIRY_KEY)
            prefs[CLIENT_ID_KEY] = clientId.ifBlank { FALLBACK_CLIENT_ID }
        }
    }

    private fun resolveExpiry(expiresAtMs: Long): Long {
        val now = System.currentTimeMillis()
        return if (expiresAtMs > now + TOKEN_REFRESH_MARGIN_MS) {
            expiresAtMs
        } else {
            now + FALLBACK_TOKEN_TTL_MS
        }
    }

    data class PublicSpotifyToken(
        val accessToken: String,
        val clientId: String,
    )

    /**
     * Token untuk endpoint publik (search/artist/playlist) tanpa memaksa login.
     * Prioritas: sesi user aktif, lalu token tamu dari embed playlist publik.
     * Token tamu hanya di-cache di memori, tidak disimpan ke DataStore sesi terenkripsi.
     */
    suspend fun getPublicOrSessionAccessToken(): Result<PublicSpotifyToken> = withContext(Dispatchers.IO) {
        val activeSession = !isExplicitlyLoggedOut && hasStoredSession()
        if (activeSession) {
            getSessionAccessToken().onSuccess { token ->
                val client = clientId.firstOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_ID
                return@withContext Result.success(PublicSpotifyToken(token, client))
            }
        }
        runCatching { getPublicAccessTokenLocked() }
    }

    private suspend fun hasStoredSession(): Boolean {
        val prefs = runCatching { dataStore.data.firstOrNull() }.getOrNull() ?: return false
        return !prefs[SP_DC_KEY].isNullOrBlank() ||
            !prefs[LEGACY_SP_DC_KEY].isNullOrBlank() ||
            !prefs[ACCESS_TOKEN_KEY].isNullOrBlank() ||
            !prefs[LEGACY_ACCESS_TOKEN_KEY].isNullOrBlank()
    }

    private suspend fun getPublicAccessTokenLocked(): PublicSpotifyToken = publicTokenMutex.withLock {
        val now = System.currentTimeMillis()
        cachedPublicToken?.takeIf { now < cachedPublicTokenExpiryMs - TOKEN_REFRESH_MARGIN_MS }
            ?.let { return@withLock PublicSpotifyToken(it, cachedPublicClientId) }
        fetchPublicEmbedToken().also {
            cachedPublicToken = it.accessToken
            cachedPublicClientId = it.clientId.ifBlank { FALLBACK_CLIENT_ID }
            cachedPublicTokenExpiryMs = resolveExpiry(publicTokenExpiryMs(it.accessToken))
        }
    }

    private fun publicTokenExpiryMs(token: String): Long {
        // Embed token biasanya JWT; baca exp jika ada, fallback ke TTL default.
        val parts = token.split('.')
        if (parts.size >= 2) {
            val payload = runCatching {
                val bytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                JSONObject(String(bytes, Charsets.UTF_8))
            }.getOrNull()
            val expSec = payload?.optLong("exp", 0L) ?: 0L
            if (expSec > 0L) return expSec * 1000L
        }
        return System.currentTimeMillis() + FALLBACK_TOKEN_TTL_MS
    }

    private suspend fun fetchPublicEmbedToken(): PublicSpotifyToken = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(PUBLIC_EMBED_URL)
            .header("User-Agent", SpotifySessionDom.CHROME_DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
            .build()
        publicHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code !in 200..299) {
                throw IOException("Gagal mengambil token publik Spotify (HTTP ${response.code}).")
            }
            parsePublicEmbedToken(body)
        }
    }

    private fun parsePublicEmbedToken(html: String): PublicSpotifyToken {
        val scriptRegex = Regex("""<script[^>]+id=["'](?<id>__NEXT_DATA__|session)["'][^>]*>(?<json>.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
        val jsonText = scriptRegex.find(html)?.groups?.get("json")?.value
            ?: throw IOException("Token publik Spotify tidak ditemukan di halaman embed.")
        val root = JSONObject(jsonText)
        val accessToken = findStringByKeys(root, setOf("accessToken", "access_token")).orEmpty()
        if (accessToken.isBlank()) {
            throw IOException("Token akses publik Spotify kosong.")
        }
        val client = findStringByKeys(root, setOf("clientId", "client_id"))
            ?.takeIf { it.isNotBlank() } ?: FALLBACK_CLIENT_ID
        return PublicSpotifyToken(accessToken = accessToken, clientId = client)
    }

    private fun findStringByKeys(node: Any?, keys: Set<String>, depth: Int = 0): String? {
        if (node == null || depth > 12) return null
        when (node) {
            is JSONObject -> {
                for (key in node.keys()) {
                    val value = node.opt(key)
                    if (key in keys && value is String && value.isNotBlank()) return value
                    findStringByKeys(value, keys, depth + 1)?.let { return it }
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    findStringByKeys(node.opt(i), keys, depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    /** Token sesi web untuk header `Authorization: Bearer ...` pada Web API resmi. */
    suspend fun getSessionAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        if (isExplicitlyLoggedOut) {
            return@withContext Result.failure(
                IllegalStateException("Sesi Spotify sudah diputus. Hubungkan akun kembali."),
            )
        }
        try {
            val token = tokenMutex.withLock {
                if (isExplicitlyLoggedOut) {
                    throw IllegalStateException("Sesi Spotify sudah diputus. Hubungkan akun kembali.")
                }
                val now = System.currentTimeMillis()
                cachedToken?.takeIf { now < tokenExpiresAtMs - TOKEN_REFRESH_MARGIN_MS }
                    ?.let { return@withLock it }

                // Token WebView yang tersimpan langsung dipakai; tanpa request refresh tambahan.
                val persisted = runCatching { dataStore.data.firstOrNull() }.getOrNull()
                val persistedToken = persisted?.get(ACCESS_TOKEN_KEY)?.let { decrypt(it) }
                val persistedExpiry = persisted?.get(TOKEN_EXPIRY_KEY) ?: 0L
                if (!persistedToken.isNullOrBlank() &&
                    now < persistedExpiry - TOKEN_REFRESH_MARGIN_MS
                ) {
                    cachedToken = persistedToken
                    tokenExpiresAtMs = persistedExpiry
                    return@withLock persistedToken
                }

                refreshTokenLocked()
            }
            Result.success(token)
        } catch (error: TimeoutCancellationException) {
            Result.failure(
                IOException("Gagal menyegarkan sesi Spotify. Periksa koneksi lalu coba lagi."),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /**
     * Paksa segar token lewat `open.spotify.com/preferences` tanpa menyentuh `sp_dc`.
     * Dipanggil repository sekali kali saat Web API menjawab HTTP 401.
     */
    suspend fun refreshSessionToken(): Result<String> = withContext(Dispatchers.IO) {
        if (isExplicitlyLoggedOut) {
            return@withContext Result.failure(
                IllegalStateException("Sesi Spotify sudah diputus. Hubungkan akun kembali."),
            )
        }
        try {
            Result.success(tokenMutex.withLock {
                if (isExplicitlyLoggedOut) {
                    throw IllegalStateException("Sesi Spotify sudah diputus. Hubungkan akun kembali.")
                }
                val now = System.currentTimeMillis()
                if (now - lastForcedRefreshAtMs < REFRESH_COOLDOWN_MS) {
                    cachedToken?.takeIf { now < tokenExpiresAtMs - TOKEN_REFRESH_MARGIN_MS }
                        ?.let { return@withLock it }
                }
                val refreshed = refreshTokenLocked()
                lastForcedRefreshAtMs = System.currentTimeMillis()
                refreshed
            })
        } catch (error: TimeoutCancellationException) {
            Result.failure(
                IOException("Gagal menyegarkan sesi Spotify. Periksa koneksi lalu coba lagi."),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Harus dipanggil di dalam [tokenMutex]; WebView headless memakai cookie asli. */
    private suspend fun refreshTokenLocked(): String {
        val spDc = cookie.firstOrNull().orEmpty()
        if (spDc.isBlank()) {
            throw IllegalStateException(
                "Sesi Spotify belum tersambung. Masuk dengan akun Spotify ya.",
            )
        }
        val refreshed = tokenRefresher.refresh(spDc).getOrThrow()
        val expiry = resolveExpiry(refreshed.expiresAtMs)
        storeToken(refreshed.accessToken, expiry, refreshed.clientId)
        cachedToken = refreshed.accessToken
        tokenExpiresAtMs = expiry
        return refreshed.accessToken
    }

    private fun invalidateToken() {
        cachedToken = null
        tokenExpiresAtMs = 0L
    }

    private fun sessionKey(): SecretKey {
        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
        val payload = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + payload
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= GCM_IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            sessionKey(),
            GCMParameterSpec(GCM_TAG_BITS, combined.copyOfRange(0, GCM_IV_BYTES)),
        )
        String(
            cipher.doFinal(combined.copyOfRange(GCM_IV_BYTES, combined.size)),
            Charsets.UTF_8,
        ).takeIf { it.isNotBlank() }
    }.onFailure {
        // Jangan pernah mencatat nilai cookie ke log, dan jangan hapus sesi hanya karena
        // ciphertext gagal dibaca (misalnya kunci perangkat berubah).
    }.getOrNull()

    private fun normalizeSpDc(raw: String): String {
        val trimmed = raw.trim()
        val value = SP_DC_REGEX.find(trimmed)?.groupValues?.get(1) ?: trimmed
        // Header cookie harus persis `sp_dc=<nilai>`: buang kutip, spasi, dan newline.
        return value.replace(WHITESPACE_OR_QUOTE_REGEX, "")
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "dengerin_spotify_session_key"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val TOKEN_REFRESH_MARGIN_MS = 60_000L
        const val FALLBACK_TOKEN_TTL_MS = 50 * 60_000L
        const val REFRESH_COOLDOWN_MS = 1_500L
        const val COLLECTION_USERS = "users"
        const val FALLBACK_CLIENT_ID = "d8a5ed958d274c2e8ee717e6a4b0971d"
        const val PUBLIC_EMBED_URL = "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val SP_DC_REGEX = Regex("""(?:^|;\s*)sp_dc=([^;]+)""")
        val WHITESPACE_OR_QUOTE_REGEX = Regex("""[\s"']""")
    }
}
