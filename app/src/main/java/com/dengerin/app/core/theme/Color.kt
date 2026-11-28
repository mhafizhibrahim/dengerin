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
    val onAccent: Color,
    val shortcut: Color,
    /** Stop gradien overlay bawah: Transparent → Early → Mid → End. */
    val navGradientEarly: Color,
    val navGradientMid: Color,
    val navGradientEnd: Color,
    /** Posisi stop tengah: 0.58f di dark, 0.62f di light. */
    val navGradientMidStop: Float,
    val navActive: Color,
    val navInactive: Color,
    /** Latar mini player: warna dominan sampul di-light-kan ke hitam ini. */
    val miniPlayerDark: Color,
    val onDarkSurface: Color,
    val error: Color,
    val cosmetics: ProfileCosmeticColors,
)

/** Gradien kosmetik profil. Daftar warnanya selalu berasal dari [ProfileCosmeticColors]. */
enum class CosmeticGradient {
    SLATE,
    EMERALD,
    CYBER,
    AMETHYST,
    SOLAR,
    AURORA,
    SUNSET,
    NIGHT,
    CREATOR,
}

/** Token kosmetik profil mengikuti kontrak Fase 3 di DESIGN_RULES.md. */
data class ProfileCosmeticColors(
    val emerald: Color,
    val cyberBlue: Color,
    val cyberCyan: Color,
    val amethyst: Color,
    val magenta: Color,
    val gold: Color,
    val amber: Color,
    val cosmicTeal: Color,
    val twilightOrange: Color,
    val twilightPurple: Color,
    val nightBlue: Color,
    val nightIndigo: Color,
    val slate: Color,
    val creatorBlack: Color,
    val creatorGold: Color,
    val creatorNeon: Color,
) {
    fun gradient(name: CosmeticGradient): List<Color> = when (name) {
        CosmeticGradient.SLATE -> listOf(slate, slate, slate)
        CosmeticGradient.EMERALD -> listOf(emerald, emerald.copy(alpha = 0.45f), emerald)
        CosmeticGradient.CYBER -> listOf(cyberBlue, cyberCyan, cyberBlue)
        CosmeticGradient.AMETHYST -> listOf(amethyst, magenta, amethyst)
        CosmeticGradient.SOLAR -> listOf(gold, amber, gold)
        CosmeticGradient.AURORA -> listOf(cosmicTeal, amethyst, magenta, cosmicTeal)
        CosmeticGradient.SUNSET -> listOf(twilightOrange, twilightPurple, twilightOrange)
        CosmeticGradient.NIGHT -> listOf(nightIndigo, nightBlue, nightIndigo)
        CosmeticGradient.CREATOR -> listOf(creatorBlack, creatorGold, creatorBlack, creatorNeon)
    }
}

private val ProfileCosmetics = ProfileCosmeticColors(
    emerald = Color(0xFF1ED760),
    cyberBlue = Color(0xFF0088FF),
    cyberCyan = Color(0xFF00F0FF),
    amethyst = Color(0xFF9D4EDD),
    magenta = Color(0xFFFF2A85),
    gold = Color(0xFFFFD700),
    amber = Color(0xFFFF7B00),
    cosmicTeal = Color(0xFF00F0FF),
    twilightOrange = Color(0xFFFF9F45),
    twilightPurple = Color(0xFF9D4EDD),
    nightBlue = Color(0xFF0B3D91),
    nightIndigo = Color(0xFF1B2A6B),
    slate = Color(0xFF27313D),
    creatorBlack = Color(0xFF05080B),
    creatorGold = Color(0xFF8A6A12),
    creatorNeon = Color(0xFF0B7F3B),
)

val DarkDengerinColors = DengerinColors(
    background = Color(0xFF121212),
    surface = Color(0xFF181818),
    surfaceElevated = Color(0xFF282828),
    border = Color.White.copy(alpha = 0.08f),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB3B3B3),
    accent = Color(0xFF1ED760),
    accentSoftContainer = Color(0x1A1ED760),
    accentSoftContent = Color(0xFF69E899),
    onAccent = Color.Black,
    shortcut = Color(0xFF2A2A2A),
    navGradientEarly = Color.Transparent,
    navGradientMid = Color(0xEB000000),
    navGradientEnd = Color(0xFF000000),
    navGradientMidStop = 0.55f,
    navActive = Color(0xFFFFFFFF),
    navInactive = Color(0xFFB3B3B3),
    miniPlayerDark = Color(0xFF181818),
    onDarkSurface = Color.White,
    error = Color(0xFFFF6B6B),
    cosmetics = ProfileCosmetics,
)

val LightDengerinColors = DengerinColors(
    background = Color(0xFFF4F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDF1F5),
    border = Color.Black.copy(alpha = 0.08f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    accent = Color(0xFF1DB954),
    accentSoftContainer = Color(0xFFD8F7E4),
    accentSoftContent = Color(0xFF15803D),
    onAccent = Color.Black,
    shortcut = Color(0xFFFFFFFF),
    navGradientEarly = Color.Transparent,
    navGradientMid = Color(0xFFF4F6F8),
    navGradientEnd = Color(0xFFF4F6F8),
    navGradientMidStop = 0.55f,
    navActive = Color(0xFF0F172A),
    navInactive = Color(0xFF64748B),
    miniPlayerDark = Color(0xFF181818),
    onDarkSurface = Color.White,
    error = Color(0xFFD32F2F),
    cosmetics = ProfileCosmetics,
)

val LocalDengerinColors = compositionLocalOf { DarkDengerinColors }
