package com.dengerin.app.presentation.components

import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.spotify.SpotifyDomResult
import com.dengerin.app.data.spotify.SpotifyDomSession
import com.dengerin.app.data.spotify.SpotifySessionDom
import com.dengerin.app.data.spotify.cleanAndroidUserAgent
import com.dengerin.app.data.spotify.evaluateSessionDom
import com.dengerin.app.data.spotify.grantOnMainThread
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog masuk Spotify layar penuh. WebView selalu diberi ukuran MATCH_PARENT eksplisit
 * supaya tidak terukur 0 dp (penyebab halaman hitam kosong). Saat status terputus, cookie
 * WebView lama dibersihkan sekali sebelum memuat form supaya tidak auto-login ke akun lama.
 *
 * Alur dua tahap: form login memakai UA Android asli (tanpa `; wv`) agar reCAPTCHA/Continue
 * with Google lolos. Begitu `sp_dc` tertangkap, WebView beralih ke UA Chrome desktop dan
 * memuat `open.spotify.com/preferences` untuk mengambil accessToken ber-scope penuh dari
 * `<script id="session" type="application/json">`. DataStore tidak disentuh di sini; semua
 * penulisan terjadi lewat callback suspend sebelum dialog ditutup.
 */
@Suppress("DEPRECATION")
@Composable
fun SpotifyWebLoginSheet(
    onDismiss: () -> Unit,
    onSessionCaptured: suspend (
        cookie: String,
        accessToken: String?,
        expiresAtMs: Long,
        clientId: String,
    ) -> Unit,
    /** true hanya saat DataStore tidak punya sp_dc: cookie lama harus dibuang dulu. */
    clearCookiesOnOpen: Boolean = true,
    /** Dipanggil sebelum penulisan sesi baru supaya flag logout manual dicabut lebih dulu. */
    onBeforeCommit: () -> Unit = {},
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("") }
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var capturedCookie by remember { mutableStateOf("") }
    var fallbackSession by remember { mutableStateOf<SpotifyDomSession?>(null) }
    val committed = remember { AtomicBoolean(false) }
    val polling = remember { AtomicBoolean(false) }
    val preferencesLoaded = remember { AtomicBoolean(false) }
    val cookiesCleared = remember { AtomicBoolean(false) }

    /**
     * Dialog HARUS tertutup walau penulisan sesi gagal; kalau tidak, pengguna terjebak
     * di halaman web Spotify tanpa jalan kembali ke Dengerin.
     */
    fun commitAndDismiss(cookie: String, token: String, expiry: Long, clientId: String) {
        scope.launch {
            try {
                statusText = "Sesi siap. Membuka koleksi Spotify…"
                runCatching { onBeforeCommit() }
                runCatching { onSessionCaptured(cookie, token, expiry, clientId) }
            } finally {
                onDismiss()
            }
        }
    }

    /**
     * `sp_dc` hasil login bisa tersimpan host-only di accounts.spotify.com. Tanpa injeksi
     * ini, request ke open.spotify.com/preferences tidak membawa cookie dan sesi dibaca
     * sebagai anonymous selamanya.
     */
    fun injectSpDcCookie(cookie: String) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(
                "https://.spotify.com",
                "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure",
            )
            flush()
        }
    }

    fun readSpDc(): String {
        val manager = CookieManager.getInstance()
        return listOf(OPEN_SPOTIFY_URL, ACCOUNTS_URL)
            .asSequence()
            .mapNotNull { domain ->
                SP_DC_REGEX.find(manager.getCookie(domain).orEmpty())?.groupValues?.get(1)
            }
            .firstOrNull()
            ?.replace(COOKIE_CLEAN_REGEX, "")
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    fun resetPolling() {
        pollJob?.cancel()
        polling.set(false)
        preferencesLoaded.set(false)
        fallbackSession = null
        capturedCookie = ""
        loadError = null
        statusText = ""
    }

    /** Kembali ke form login dengan UA Android dan cookie state awal. */
    fun resetToLogin(view: WebView) {
        resetPolling()
        progress = 0
        view.settings.userAgentString = cleanAndroidUserAgent(context)
        view.loadUrl(SpotifySessionDom.LOGIN_URL)
    }

    /** Commit sekali saja, lalu tutup dialog meski penulisan sesi gagal. */
    fun commitSession(view: WebView, session: SpotifyDomSession, cookie: String) {
        if (!committed.compareAndSet(false, true)) return
        polling.set(false)
        view.stopLoading()
        val now = System.currentTimeMillis()
        val expiry = if (session.expiresAtMs <= now) now + FALLBACK_TTL_MS else session.expiresAtMs
        commitAndDismiss(cookie, session.accessToken, expiry, session.clientId)
    }

    /**
     * Tunggu `sp_dc`, lalu baca token dari halaman tempat login selesai. Kalau valid
     * (`isAnonymous === false`), langsung commit — tidak usah menunggu `/preferences`.
     * Halaman `/preferences` hanya dipakai kalau token embed belum tersedia, dengan
     * polling cepat dan break lebih awal saat sesi jelas anonymous.
     */
    fun startPolling(view: WebView) {
        if (committed.get()) return
        val onPreferences = preferencesLoaded.get()
        if (!onPreferences && !view.url.orEmpty().startsWith(OPEN_SPOTIFY_URL)) return
        if (!polling.compareAndSet(false, true)) return
        pollJob?.cancel()
        pollJob = scope.launch {
            if (!onPreferences) {
                statusText = "Menunggu login Spotify selesai…"
                var cookie = ""
                repeat(POLL_ATTEMPTS) { attempt ->
                    cookie = readSpDc()
                    if (cookie.isNotBlank() || attempt == POLL_ATTEMPTS - 1) return@repeat
                    delay(POLL_INTERVAL_MS)
                }
                if (cookie.isBlank()) {
                    polling.set(false)
                    loadError = "Cookie sesi Spotify belum tertangkap. Coba masuk ulang ya."
                    return@launch
                }
                capturedCookie = cookie

                val immediate = view.evaluateSessionDom()
                if (immediate is SpotifyDomResult.Ready) {
                    commitSession(view, immediate.session, cookie)
                    return@launch
                }
                fallbackSession = null

                injectSpDcCookie(cookie)
                statusText = "Mengambil sesi akun Spotify…"
                preferencesLoaded.set(true)
                view.settings.userAgentString = SpotifySessionDom.CHROME_DESKTOP_USER_AGENT
                view.loadUrl(SpotifySessionDom.PREFERENCES_URL)
                return@launch
            }

            var ready: SpotifyDomResult.Ready? = null
            var anonymousStreak = 0
            var round = 0
            while (round < STAGE2_ROUNDS && ready == null && anonymousStreak < ANON_BREAK_LIMIT) {
                statusText = "Mengambil sesi akun Spotify…"
                var attempt = 0
                while (attempt < FAST_POLL_ATTEMPTS && ready == null &&
                    anonymousStreak < ANON_BREAK_LIMIT
                ) {
                    when (val result = view.evaluateSessionDom()) {
                        is SpotifyDomResult.Ready -> {
                            ready = result
                            anonymousStreak = 0
                        }
                        SpotifyDomResult.Anonymous -> anonymousStreak++
                        SpotifyDomResult.Pending -> anonymousStreak = 0
                    }
                    attempt++
                    if (ready == null && anonymousStreak < ANON_BREAK_LIMIT &&
                        attempt < FAST_POLL_ATTEMPTS
                    ) {
                        delay(FAST_POLL_INTERVAL_MS)
                    }
                }
                if (ready == null && anonymousStreak < ANON_BREAK_LIMIT &&
                    round < STAGE2_ROUNDS - 1
                ) {
                    view.reload()
                    delay(RELOAD_DELAY_MS)
                }
                round++
            }
            polling.set(false)

            val session = ready?.session ?: fallbackSession
            val cookie = capturedCookie.ifBlank { readSpDc() }
            if (session == null || cookie.isBlank()) {
                loadError =
                    "Token akun Spotify belum siap. Tekan tombol muat ulang di kanan atas ya."
                statusText = ""
                return@launch
            }
            commitSession(view, session, cookie)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pollJob?.cancel()
            webView?.let { view ->
                view.stopLoading()
                view.clearHistory()
                view.clearCache(true)
                view.destroy()
            }
            // Jangan hapus cookie sesi di sini: sp_dc/accessToken di DataStore tetap
            // dipertahankan; penghapusan hanya lewat tombol "Keluar dari Spotify".
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .background(colors.background),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Tutup halaman masuk Spotify",
                            tint = colors.textPrimary,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_dengerin_mark),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Masuk dengan Akun Spotify",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Selesaikan masuk di halaman Spotify.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { webView?.let(::resetToLogin) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Muat ulang halaman login",
                            tint = colors.accent,
                        )
                    }
                }

                if (progress < 100 && loadError == null) {
                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = colors.accent,
                        trackColor = colors.surfaceElevated,
                    )
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            // Ukuran eksplisit mencegah WebView terukur 0 px di dalam Compose.
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                // UA Android asli tanpa "; wv": platform cocok untuk reCAPTCHA
                                // dan Continue with Google tetap diizinkan.
                                userAgentString = cleanAndroidUserAgent(context)
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                            }

                            // Saat status terputus, cookie lama HARUS dibuang sekali di awal
                            // sebelum URL dimuat; kalau tidak, form auto-login ke akun lama.
                            // Cookie CSRF/reCAPTCHA akan dibuat ulang oleh halaman login itu sendiri.
                            if (clearCookiesOnOpen && cookiesCleared.compareAndSet(false, true)) {
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.removeAllCookies(null)
                                cookieManager.flush()
                            }
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            cookieManager.flush()

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest,
                                ): Boolean = keepInWebView(request.url.scheme)

                                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?,
                                ): Boolean = keepInWebView(url?.substringBefore(":"))

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    progress = 0
                                    loadError = null
                                    view?.let(::startPolling)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    progress = 100
                                    view?.let(::startPolling)
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean,
                                ) {
                                    view?.let(::startPolling)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame != false) {
                                        loadError = "Gagal memuat Spotify: ${error?.description ?: "koneksi terputus"}."
                                        progress = 100
                                    }
                                }

                                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?,
                                ) {
                                    loadError = "Gagal memuat Spotify: ${description ?: "koneksi terputus"}."
                                    progress = 100
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    val status = errorResponse?.statusCode ?: 0
                                    if (request?.isForMainFrame == true && status >= 400) {
                                        loadError = "Halaman Spotify tidak tersedia (HTTP $status)."
                                        progress = 100
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                // Widevine/DRM harus disetujui agar tidak muncul
                                // "Playback dinonaktifkan" pada web player Spotify.
                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    request?.grantOnMainThread()
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress.coerceIn(0, 100)
                                    if (newProgress >= 70) {
                                        view?.let(::startPolling)
                                    }
                                }

                                // window.open() dari Google/Facebook/Apple tetap memakai WebView
                                // yang sama: cookie, UA, dan capture sp_dc tidak terputus.
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?,
                                ): Boolean {
                                    val message = resultMsg ?: return false
                                    val transport = message.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = view ?: return false
                                    message.sendToTarget()
                                    return true
                                }
                            }

                            loadUrl(SpotifySessionDom.LOGIN_URL)
                        }.also { webView = it }
                    },
                )

                statusText.takeIf { loadError == null && it.isNotBlank() }?.let { message ->
                    Text(
                        message,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                loadError?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { webView?.let(::resetToLogin) }) {
                            Text("Coba Lagi", color = colors.accent)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Login dengan Google? Pastikan memilih 'Coba cara lain / Sandi' jika diminta Passkey perangkat.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** http(s) tetap di WebView; intent:// dan spotify:// diabaikan supaya tidak blank/exit. */
private fun keepInWebView(scheme: String?): Boolean = when {
    scheme.isNullOrBlank() -> true
    scheme.equals("http", ignoreCase = true) -> false
    scheme.equals("https", ignoreCase = true) -> false
    else -> true
}

private val SP_DC_REGEX = Regex("""(?:^|;\s*)sp_dc=([^;]+)""")
private val COOKIE_CLEAN_REGEX = Regex("""[\s"']""")

private const val POLL_INTERVAL_MS = 600L
private const val POLL_ATTEMPTS = 15
private const val FAST_POLL_INTERVAL_MS = 250L
private const val FAST_POLL_ATTEMPTS = 8
private const val ANON_BREAK_LIMIT = 3
private const val STAGE2_ROUNDS = 2
private const val RELOAD_DELAY_MS = 600L
private const val FALLBACK_TTL_MS = 50 * 60_000L
private const val OPEN_SPOTIFY_URL = "https://open.spotify.com"
private const val ACCOUNTS_URL = "https://accounts.spotify.com"
