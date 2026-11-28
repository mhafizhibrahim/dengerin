package com.dengerin.app.data.spotify

import android.content.Context
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Penyegaran token tanpa UI. Setelah login selesai, halaman yang dipakai selalu
 * `open.spotify.com/preferences` dengan UA Chrome desktop: di halaman ini skrip sesi
 * memuat accessToken ber-scope penuh (`isAnonymous == false`) dan sudah tidak ada
 * reCAPTCHA. WebView dipakai karena Spotify menolak GET lewat OkHttp (TLS fingerprint
 * bukan Chromium + redirect silent `sp_dc`/`sp_key` tidak berjalan).
 */
class HeadlessSpotifyTokenRefresher(context: Context) {

    private val appContext = context.applicationContext

    suspend fun refresh(spDc: String): Result<SpotifyDomSession> = withContext(Dispatchers.Main) {
        try {
            val cleanSpDc = normalizeSpDc(spDc)
            if (cleanSpDc.isBlank()) {
                throw IllegalArgumentException("Cookie sesi Spotify tidak ditemukan.")
            }
            injectCookie(cleanSpDc)

            val session = withTimeout(TIMEOUT_MS) {
                val webView = WebView(appContext)
                try {
                    configure(webView)
                    webView.loadUrl(SpotifySessionDom.PREFERENCES_URL)

                    repeat(POLL_ATTEMPTS) { attempt ->
                        when (val result = webView.evaluateSessionDom()) {
                            is SpotifyDomResult.Ready -> {
                                val session = result.session
                                val expiry = if (session.expiresAtMs <= System.currentTimeMillis()) {
                                    System.currentTimeMillis() + FALLBACK_TTL_MS
                                } else {
                                    session.expiresAtMs
                                }
                                return@withTimeout session.copy(expiresAtMs = expiry)
                            }
                            SpotifyDomResult.Anonymous -> Unit
                            SpotifyDomResult.Pending -> Unit
                        }
                        if (attempt < POLL_ATTEMPTS - 1) delay(POLL_INTERVAL_MS)
                    }
                    throw SpotifySessionExpiredException()
                } finally {
                    runCatching { webView.stopLoading() }
                    webView.destroy()
                }
            }
            Result.success(session)
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

    private fun injectCookie(spDc: String) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(
                "https://.spotify.com",
                "sp_dc=$spDc; Domain=.spotify.com; Path=/; Secure",
            )
            flush()
        }
    }

    private fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = SpotifySessionDom.CHROME_DESKTOP_USER_AGENT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grantOnMainThread()
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean = keepInWebView(request.url.scheme)

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                keepInWebView(url?.substringBefore(":"))
        }
    }

    private fun keepInWebView(scheme: String?): Boolean = when {
        scheme.isNullOrBlank() -> true
        scheme.equals("http", ignoreCase = true) -> false
        scheme.equals("https", ignoreCase = true) -> false
        else -> true
    }

    private fun normalizeSpDc(raw: String): String {
        val trimmed = raw.trim()
        val value = SP_DC_REGEX.find(trimmed)?.groupValues?.get(1) ?: trimmed
        return value.replace(CLEAN_REGEX, "")
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 700L
        const val POLL_ATTEMPTS = 15
        const val FALLBACK_TTL_MS = 50 * 60_000L
        val SP_DC_REGEX = Regex("""(?:^|;\s*)sp_dc=([^;]+)""")
        val CLEAN_REGEX = Regex("""[\s"']""")
    }
}
