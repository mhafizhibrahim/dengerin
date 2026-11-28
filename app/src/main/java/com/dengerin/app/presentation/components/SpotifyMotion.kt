package com.dengerin.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Tekanan ringan ala Spotify: 0.97f dengan tween 90 ms, tanpa menahan scroll. */
@Composable
fun Modifier.spotifyPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(90),
        label = "spotifyPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** `clickable` + [spotifyPressScale] dengan satu `InteractionSource`. */
@Composable
fun Modifier.spotifyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    ).spotifyPressScale(interactionSource, pressedScale)
}
