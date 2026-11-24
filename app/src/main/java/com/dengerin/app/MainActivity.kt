package com.dengerin.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.dengerin.app.core.theme.AppThemeMode
import com.dengerin.app.core.theme.DengerinTheme
import com.dengerin.app.data.update.UpdateChecker
import com.dengerin.app.presentation.DengerinApp
import com.dengerin.app.presentation.update.UpdateDialog
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    companion object {
        // Cek update otomatis cukup sekali per proses, bukan per recreate activity.
        private var autoUpdateChecked = false
    }

    private lateinit var requestNotificationPermission: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* tidak wajib */ }
        maybeAskNotificationPermission()

        val container = (application as DengerinApplication).container
        setContent {
            val themeMode by container.themePreferences.themeMode.collectAsState(
                initial = AppThemeMode.SYSTEM,
            )
            DengerinTheme(themeMode = themeMode) {
                // Cek update otomatis: sekali per proses, di background, tanpa
                // memblokir UI. Gagal (offline/404/rate-limit) senyap.
                val currentVersion = remember { UpdateChecker.currentVersion(this@MainActivity) }
                var pendingUpdate by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
                LaunchedEffect(Unit) {
                    if (autoUpdateChecked) return@LaunchedEffect
                    autoUpdateChecked = true
                    delay(1000)
                    val release = UpdateChecker.fetchLatestRelease()
                    if (release != null && UpdateChecker.isNewerVersion(release.version, currentVersion)) {
                        pendingUpdate = release
                    }
                }

                DengerinApp(container)
                pendingUpdate?.let { release ->
                    UpdateDialog(
                        release = release,
                        currentVersion = currentVersion,
                        onDismiss = { pendingUpdate = null },
                    )
                }
                if (pendingUpdate != null) {
                    // Dialog update sengaja tidak bisa ditutup dengan back. Serap back di sini
                    // supaya NavHost tidak mem-pop halaman di belakang dialog.
                    BackHandler(enabled = true) {}
                }
            }
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
}
