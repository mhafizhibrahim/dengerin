package com.dengerin.app.core.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun DengerinTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colors = if (isDark) DarkDengerinColors else LightDengerinColors

    // Ganti tema instan: hanya flip flag system bar, tanpa animasi / relayout window.
    val context = LocalContext.current
    DisposableEffect(isDark) {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)?.apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        onDispose { }
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.Black,
            secondary = colors.accent,
            onSecondary = Color.Black,
            primaryContainer = colors.accentSoftContainer,
            onPrimaryContainer = colors.accentSoftContent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.error,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.Black,
            secondary = colors.accent,
            onSecondary = Color.Black,
            primaryContainer = colors.accentSoftContainer,
            onPrimaryContainer = colors.accentSoftContent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            error = colors.error,
            onError = Color.White,
        )
    }

    CompositionLocalProvider(LocalDengerinColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
