package com.dengerin.app.core.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Token warna mengikuti DESIGN_RULES.md — jangan hardcode warna di composable. */
data class DengerinColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentSoftContainer: Color,
    val accentSoftContent: Color,
    val error: Color,
)

val DarkDengerinColors = DengerinColors(
    background = Color(0xFF0C1014),
    surface = Color(0xFF151B22),
    surfaceElevated = Color(0xFF1E2630),
    border = Color(0xFF27313D),
    textPrimary = Color(0xFFF5F7FA),
    textSecondary = Color(0xFF8B98A5),
    accent = Color(0xFF1ED760),
    accentSoftContainer = Color(0x1A1ED760),
    accentSoftContent = Color(0xFF69E899),
    error = Color(0xFFFF6B6B),
)

val LightDengerinColors = DengerinColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FA),
    surfaceElevated = Color(0xFFEDF1F5),
    border = Color(0xFFDCE3EA),
    textPrimary = Color(0xFF0F1419),
    textSecondary = Color(0xFF5B6B7A),
    accent = Color(0xFF1ED760),
    accentSoftContainer = Color(0xFFD8F7E4),
    accentSoftContent = Color(0xFF108A43),
    error = Color(0xFFD32F2F),
)

val LocalDengerinColors = compositionLocalOf { DarkDengerinColors }
