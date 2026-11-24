package com.dengerin.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dengerin.app.core.theme.AppThemeMode
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.preferences.ThemePreferences
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(
    themePreferences: ThemePreferences,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    val currentMode by themePreferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Koleksi",
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Ruang buat lagu favorit kamu, menyusul di Fase 2.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Belum ada koleksi",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Nanti kamu bisa simpan lagu dan bikin playlist di sini.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "Tampilan",
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Coba ganti tema, langsung berubah kok.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeOption(
                    label = "Sistem",
                    selected = currentMode == AppThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.SYSTEM) }
                    },
                )
                ThemeOption(
                    label = "Gelap",
                    selected = currentMode == AppThemeMode.DARK,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.DARK) }
                    },
                )
                ThemeOption(
                    label = "Terang",
                    selected = currentMode == AppThemeMode.LIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.LIGHT) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) colors.accent else colors.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .background(if (selected) colors.accentSoftContainer else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
