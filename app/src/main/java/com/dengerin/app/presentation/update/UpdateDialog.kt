package com.dengerin.app.presentation.update

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dengerin.app.BuildConfig
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.update.GitHubUpdateManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private sealed interface ForceUpdateState {
    object Idle : ForceUpdateState
    data class Downloading(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : ForceUpdateState

    object Installing : ForceUpdateState
    object InstallOpened : ForceUpdateState
    data class Failed(val message: String) : ForceUpdateState
}

/**
 * Dialog pembaruan wajib: tidak bisa ditutup dengan back, klik di luar, maupun tombol.
 * Dipakai baik saat aplikasi dibuka maupun dari Pengaturan → Tentang Dengerin.
 */
@Composable
fun ForceUpdateDialog(
    release: GitHubUpdateManager.ReleaseInfo,
    currentVersion: String = BuildConfig.VERSION_NAME,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ForceUpdateState>(ForceUpdateState.Idle) }
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val file = pendingInstall
        pendingInstall = null
        if (file != null && GitHubUpdateManager.canInstallApk(context)) {
            GitHubUpdateManager.installApk(context, file)
            state = ForceUpdateState.InstallOpened
        } else if (file != null) {
            state = ForceUpdateState.Failed("Izin instal belum diberikan. Coba lagi, ya.")
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // DialogProperties sudah mematikan back, BackHandler ini mengunci navigasi di bawahnya.
        BackHandler(enabled = true) {}

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = LocalBottomClearance.current),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_dengerin_mark),
                contentDescription = "Logo Dengerin",
                modifier = Modifier.size(32.dp),
                tint = colors.accent,
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = "Pembaruan tersedia",
                modifier = Modifier.size(36.dp),
                tint = colors.accent,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pembaruan Tersedia",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Versi terbaru sudah tersedia. Yuk, perbarui sekarang.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v$currentVersion  →  v${release.version}",
                color = colors.accentSoftContent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentSoftContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            val sizeText = release.apkSizeBytes?.let { formatSize(it) } ?: "Ukuran tidak diketahui"
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ukuran APK: $sizeText",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))
            BoxChangelog(release.changelog)

            when (val current = state) {
                is ForceUpdateState.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (current.totalBytes > 0) {
                                current.percent / 100f
                            } else {
                                0f
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = colors.accent,
                        trackColor = colors.border,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            append("Mengunduh...")
                            if (current.totalBytes > 0) append(" ${current.percent}%")
                            append(" (")
                            append(formatSize(current.downloadedBytes))
                            if (current.totalBytes > 0) {
                                append(" / ${formatSize(current.totalBytes)}")
                            }
                            append(')')
                        },
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                is ForceUpdateState.Installing -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Membuka installer Android...",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                ForceUpdateState.InstallOpened -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Silakan konfirmasi pemasangan di layar Android.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                is ForceUpdateState.Failed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = current.message,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }

                ForceUpdateState.Idle -> Unit
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (
                        state is ForceUpdateState.Downloading ||
                        state is ForceUpdateState.Installing ||
                        state is ForceUpdateState.InstallOpened
                    ) {
                        return@Button
                    }
                    val apkUrl = release.apkUrl
                    if (apkUrl.isNullOrBlank()) {
                        state = ForceUpdateState.Failed("Asset APK tidak ada di rilis ini. Gunakan Unduh via GitHub.")
                        return@Button
                    }
                    state = ForceUpdateState.Downloading(0, 0L, release.apkSizeBytes ?: -1L)
                    scope.launch {
                        GitHubUpdateManager.downloadApkWithProgress(apkUrl).collect { download ->
                            when (download) {
                                is GitHubUpdateManager.DownloadState.Downloading -> {
                                    state = ForceUpdateState.Downloading(
                                        download.percent,
                                        download.downloadedBytes,
                                        download.totalBytes,
                                    )
                                }

                                is GitHubUpdateManager.DownloadState.ReadyToInstall -> {
                                    state = ForceUpdateState.Installing
                                    if (GitHubUpdateManager.canInstallApk(context)) {
                                        GitHubUpdateManager.installApk(context, download.file)
                                        state = ForceUpdateState.InstallOpened
                                    } else {
                                        pendingInstall = download.file
                                        installPermissionLauncher.launch(
                                            GitHubUpdateManager.installPermissionIntent(context),
                                        )
                                    }
                                }

                                is GitHubUpdateManager.DownloadState.Failed -> {
                                    state = ForceUpdateState.Failed(download.message)
                                }
                            }
                        }
                    }
                },
                enabled = state !is ForceUpdateState.Downloading &&
                    state !is ForceUpdateState.Installing &&
                    state !is ForceUpdateState.InstallOpened,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.surfaceElevated,
                    disabledContentColor = colors.textSecondary,
                ),
            ) {
                Text(
                    text = when (state) {
                        is ForceUpdateState.Failed -> "Coba Lagi"
                        is ForceUpdateState.Downloading -> "Mengunduh..."
                        is ForceUpdateState.Installing -> "Memasang..."
                        ForceUpdateState.InstallOpened -> "Menunggu Instalasi"
                        ForceUpdateState.Idle -> "Install Langsung"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    GitHubUpdateManager.openReleasePage(context, release.releaseUrl)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary,
                    disabledContentColor = colors.textSecondary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.textPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Unduh via GitHub",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun BoxChangelog(changelog: String) {
    val colors = LocalDengerinColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .padding(bottom = LocalBottomClearance.current),
    ) {
        Text(
            text = changelog,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Format MB dengan koma desimal ala Indonesia, mis. "12,4 MB". */
private fun formatSize(bytes: Long): String {
    val megabytes = bytes / (1024f * 1024f)
    return if (megabytes >= 1f) {
        String.format(Locale("id", "ID"), "%.1f MB", megabytes)
    } else {
        String.format(Locale("id", "ID"), "%.0f KB", bytes / 1024f)
    }
}
