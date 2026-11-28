package com.dengerin.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dengerin.app.core.ShortcutActions
import com.dengerin.app.core.theme.AppThemeMode
import com.dengerin.app.core.theme.DengerinTheme
import com.dengerin.app.presentation.DengerinApp

class MainActivity : ComponentActivity() {

    private lateinit var requestNotificationPermission: ActivityResultLauncher<String>
    private var permissionAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash wajib dipasang sebelum super.onCreate() agar tema splash aktif
        // sejak frame pertama. Edge-to-edge menyusul setelah activity berdiri.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenNowPlayingIntent(intent)
        handleShortcutIntent(intent)

        requestNotificationPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* tidak wajib */ }

        val container = (application as DengerinApplication).container
        setContent {
            val themeMode by container.themePreferences.themeMode.collectAsState(
                initial = AppThemeMode.SYSTEM,
            )
            DengerinTheme(themeMode = themeMode) {
                DengerinApp(container)
            }
        }
        // Tandai konten utama sudah selesai digambar setelah frame pertama, supaya
        // metrik TTID/TTFD di Perfetto/Macrobenchmark akurat.
        window.decorView.post { reportFullyDrawn() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenNowPlayingIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleOpenNowPlayingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) != true) return
        (application as DengerinApplication).container.requestOpenNowPlaying()
        // Dihapus supaya rotasi/kembali ke activity tidak membuka sheet lagi.
        intent.removeExtra(EXTRA_OPEN_NOW_PLAYING)
    }

    /** Pintasan launcher diteruskan sebagai event agar tindakan tidak bergantung pada Compose. */
    private fun handleShortcutIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ShortcutActions.SEARCH &&
            action != ShortcutActions.LIKED &&
            action != ShortcutActions.SHUFFLE
        ) return
        (application as DengerinApplication).container.requestShortcut(action)
        // Kembalikan ke MAIN supaya rotasi tidak mengulang aksi pintasan.
        intent.action = Intent.ACTION_MAIN
    }

    override fun onResume() {
        super.onResume()
        // Jangan tampilkan dialog izin saat startup; minta setelah activity benar-benar aktif.
        if (!permissionAsked) {
            permissionAsked = true
            maybeAskNotificationPermission()
        }
    }

    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.dengerin.app.EXTRA_OPEN_NOW_PLAYING"
    }
}
