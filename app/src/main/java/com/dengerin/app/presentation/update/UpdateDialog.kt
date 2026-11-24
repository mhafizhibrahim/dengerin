package com.dengerin.app.presentation.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.update.ApkDownloader
import com.dengerin.app.data.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * Dialog "Pembaruan Tersedia" dengan unduh langsung + progres.
 * Semua warna diambil dari token DESIGN_RULES.md lewat LocalDengerinColors.
 */
@Composable
fun UpdateDialog(
    release: UpdateChecker.ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = "Pembaruan tersedia",
                modifier = Modifier.size(40.dp),
                tint = colors.accent,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Pembaruan Tersedia",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "v$currentVersion → v${release.version}",
                color = colors.accentSoftContent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.accentSoftContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            BoxChangelog(release.changelog)

            if (downloading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = colors.accent,
                    trackColor = colors.border,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Mengunduh APK… ${(progress * 100).toInt()}%",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (!ApkDownloader.isApkUrl(release.downloadUrl)) {
                        openUpdateUrl(context, release.downloadUrl)
                        onDismiss()
                        return@Button
                    }
                    downloading = true
                    error = null
                    progress = 0f
                    scope.launch {
                        try {
                            val file = ApkDownloader.download(
                                context = context,
                                url = release.downloadUrl,
                                version = release.version,
                                onProgress = { progress = it },
                            )
                            ApkDownloader.install(context, file)
                            onDismiss()
                        } catch (e: Exception) {
                            error = "Gagal mengunduh APK. Cek koneksi internet, ya."
                        } finally {
                            downloading = false
                        }
                    }
                },
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.background,
                    disabledContainerColor = colors.surfaceElevated,
                    disabledContentColor = colors.textSecondary,
                ),
            ) {
                Text(
                    text = if (downloading) "Mengunduh…" else "Perbarui",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = !downloading,
            ) {
                Text("Nanti", color = colors.textSecondary, fontSize = 13.sp)
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
            .padding(12.dp),
    ) {
        Text(
            text = changelog,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Buka halaman rilis di browser kalau asset bukan APK langsung; gagal senyap. */
fun openUpdateUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // Tidak ada aplikasi yang bisa membuka URL — abaikan.
    }
}
