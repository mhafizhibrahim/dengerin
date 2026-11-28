package com.dengerin.app.data.spotify

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.PermissionRequest
import android.webkit.WebSettings
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

data class SpotifyDomSession(
    val accessToken: String,
    val expiresAtMs: Long,
    val clientId: String = "",
)

internal sealed interface SpotifyDomResult {
    data class Ready(val session: SpotifyDomSession) : SpotifyDomResult
    data object Anonymous : SpotifyDomResult
    data object Pending : SpotifyDomResult
}

internal class SpotifySessionExpiredException :
    IOException("Sesi Spotify kedaluwarsa, silakan masuk ulang")

/** Kontrak DOM sesi yang dipakai bersama oleh WebView login dan refresher headless. */
internal object SpotifySessionDom {

    /** Halaman embed ringan: tidak memuat pemutar DRM, tetap menyisipkan sesi akun. */
    const val EMBED_URL = "https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M"

    /** Redirect login langsung ke embed agar tidak kena layar "Playback dinonaktifkan". */
    const val LOGIN_URL =
        "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2Fembed%2Fplaylist%2F37i9dQZF1DXcBWIGoYBM5M"

    /**
     * Halaman sesi ber-scope penuh. Dipakai setelah login (UA Chrome desktop) dan oleh
     * refresher headless; halaman ini sudah tidak punya reCAPTCHA/form login.
     */
    const val PREFERENCES_URL = "https://open.spotify.com/preferences"

    /** UA Chrome desktop untuk halaman `/preferences` (bukan untuk form login). */
    const val CHROME_DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * hasil: JSON string jika `isAnonymous === false`, `'ANON'` jika sesi masih anonymous,
     * atau `''` saat elemen belum tersedia / DOM belum selesai dimuat.
     */
    const val READ_SESSION_JS = """(function() {
  try {
    const pack = (s) => JSON.stringify({
      accessToken: s.accessToken,
      expiresAt: s.accessTokenExpirationTimestampMs || 0,
      clientId: s.clientId || ''
    });
    const sessionEl = document.querySelector('script#session[type="application/json"]')
      || document.getElementById('session');
    if (sessionEl && sessionEl.textContent) {
      const raw = JSON.parse(sessionEl.textContent);
      if (raw && raw.accessToken) return raw.isAnonymous === false ? pack(raw) : 'ANON';
    }
    const nextEl = document.getElementById('__NEXT_DATA__');
    if (nextEl && nextEl.textContent) {
      const parsed = JSON.parse(nextEl.textContent);
      const page = parsed && parsed.props && parsed.props.pageProps;
      if (page) {
        const session = (page.state && page.state.settings && page.state.settings.session)
          || page.session;
        if (session && session.accessToken) {
          return session.isAnonymous === false ? pack(session) : 'ANON';
        }
      }
    }
    const html = document.documentElement.innerHTML;
    const m = html.match(/"accessToken"\s*:\s*"([^"]+)"/);
    const e = html.match(/"accessTokenExpirationTimestampMs"\s*:\s*(\d+)/);
    const a = html.match(/"isAnonymous"\s*:\s*(true|false)/);
    if (m && (!a || a[1] === 'false')) {
      return JSON.stringify({
        accessToken: m[1],
        expiresAt: e ? Number(e[1]) : 0,
        clientId: ''
      });
    }
    return '';
  } catch (err) { return ''; }
})();"""

    fun parse(raw: String?): SpotifyDomResult {
        val text = unwrapJsString(raw) ?: return SpotifyDomResult.Pending
        if (text.isBlank()) return SpotifyDomResult.Pending
        if (text == "ANON") return SpotifyDomResult.Anonymous

        val json = runCatching { JSONObject(text) }.getOrNull() ?: return SpotifyDomResult.Pending
        val token = json.optString("accessToken").takeIf { it.isNotBlank() }
            ?: return SpotifyDomResult.Pending
        val expiry = json.optLong("expiresAt", 0L)
            .takeIf { it != 0L }
            ?: json.optLong("accessTokenExpirationTimestampMs", 0L)
        return SpotifyDomResult.Ready(
            SpotifyDomSession(
                accessToken = token,
                expiresAtMs = expiry,
                clientId = json.optString("clientId").orEmpty(),
            ),
        )
    }

    /** `evaluateJavascript` mengembalikan string JSON-quoted; unescape sebelum parse. */
    private fun unwrapJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { JSONObject("{\"value\":$raw}").optString("value") }.getOrNull()
    }
}

/**
 * UA Android asli perangkat, hanya penanda WebView `; wv` yang dibuang.
 * UA desktop/Windows memicu reCAPTCHA Enterprise karena `navigator.platform` tidak cocok.
 */
internal fun cleanAndroidUserAgent(context: Context): String {
    val defaultUa = WebSettings.getDefaultUserAgent(context)
    return defaultUa.replace("; wv", "").replace("wv)", ")")
}

/**
 * Setujui permission WebView (termasuk `RESOURCE_PROTECTED_MEDIA_ID` / Widevine DRM)
 * di main thread supaya Spotify tidak menampilkan "Playback dinonaktifkan".
 */
internal fun PermissionRequest.grantOnMainThread() {
    Handler(Looper.getMainLooper()).post {
        runCatching { grant(resources) }
    }
}

internal suspend fun WebView.evaluateSessionDom(): SpotifyDomResult =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(SpotifySessionDom.READ_SESSION_JS) { raw ->
            if (continuation.isActive) {
                continuation.resume(SpotifySessionDom.parse(raw))
            }
        }
    }
