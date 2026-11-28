package com.dengerin.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dengerin.app.core.theme.LocalDengerinColors

/**
 * Sheet minimalis dari header Koleksi dan tab "Buat": cukup nama + deskripsi opsional.
 * Impor Spotify tetap tersedia sebagai aksi sekunder tanpa mengganti halaman.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    onDismiss: () -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onImportSpotify: () -> Unit,
    onConnectSpotify: () -> Unit,
    onOpenSpotifyImport: () -> Unit,
    spotifyConnected: Boolean = false,
) {
    val colors = LocalDengerinColors.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // Dark #121212 mengikuti token background; light tetap memakai token surface.
        containerColor = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.32f)),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Buat Playlist Baru",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nama playlist", color = colors.textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                ),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Deskripsi (opsional)", color = colors.textSecondary) },
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onCreatePlaylist(name.trim(), description.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text("Buat", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .clickable {
                        if (spotifyConnected) onOpenSpotifyImport() else onConnectSpotify()
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (spotifyConnected) "Koleksi Spotify Terhubung" else "Hubungkan Akun Spotify",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Impor playlist pribadi & Lagu Disukai tanpa Client ID",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onImportSpotify,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text("Impor dari Spotify", color = colors.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
