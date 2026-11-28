package com.dengerin.app.presentation.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Kompatibilitas nama lama. Perilakunya sekarang mengikuti kontrak Fase 5:
 * `spotifyPressScale()` → 0.97f / tween(90), tanpa ripple dan tanpa menahan scroll.
 */
@Composable
fun Modifier.iosPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = spotifyPressScale(interactionSource, pressedScale)

@Composable
fun Modifier.iosClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.97f,
): Modifier = spotifyClickable(pressedScale = pressedScale, onClick = onClick)
