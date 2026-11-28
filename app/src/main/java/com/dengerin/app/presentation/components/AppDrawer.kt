package com.dengerin.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.profile.UserProfile

/** Side drawer yang dibuka dari avatar di Beranda / Cari / Koleksi Kamu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    profile: UserProfile?,
    email: String,
    onViewProfile: () -> Unit,
    onAddAccount: () -> Unit,
    onRecentlyPlayed: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val name = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: email.takeIf { it.isNotBlank() }
        ?: "Pendengar Dengerin"

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = colors.background,
        drawerContentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = LocalBottomClearance.current),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatarButton(profile = profile, onClick = onViewProfile, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onViewProfile),
                ) {
                    Text(
                        text = name,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lihat profil",
                        color = colors.accent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.height(12.dp))

            DrawerItem(Icons.Filled.ManageAccounts, "Tambah / Ganti Akun", onAddAccount)
            DrawerItem(Icons.Filled.History, "Baru diputar", onRecentlyPlayed)
            DrawerItem(Icons.Filled.BarChart, "Statistik Mendengarkan", onStatistics)
            DrawerItem(Icons.Filled.Settings, "Pengaturan", onSettings)
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
