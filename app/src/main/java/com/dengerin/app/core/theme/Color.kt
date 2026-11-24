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
    cosmetics = ProfileCosmetics,
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
    cosmetics = ProfileCosmetics,
)

val LocalDengerinColors = compositionLocalOf { DarkDengerinColors }
