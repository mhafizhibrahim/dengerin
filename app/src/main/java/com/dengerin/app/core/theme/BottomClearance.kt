package com.dengerin.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ruang bawah otomatis untuk semua layar scrollable. Nilainya sudah termasuk
 * `WindowInsets.navigationBars`; lihat tabel di DESIGN_RULES.md.
 */
val LocalBottomClearance = compositionLocalOf { 96.dp }

@Composable
fun BottomClearanceProvider(
    withBottomNav: Boolean,
    miniPlayerVisible: Boolean,
    content: @Composable () -> Unit,
) {
    val clearance: Dp = when {
        withBottomNav && miniPlayerVisible -> 160.dp
        withBottomNav -> 96.dp
        miniPlayerVisible -> 104.dp
        else -> 36.dp
    }
    CompositionLocalProvider(LocalBottomClearance provides clearance, content = content)
}
