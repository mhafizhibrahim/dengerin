package com.dengerin.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.lyrics.LrcParser
import com.dengerin.app.presentation.LyricsUiState
import com.dengerin.app.presentation.TimelineProviders
import kotlinx.coroutines.flow.StateFlow

/**
 * Kartu pratinjau lirik ala Spotify. Hanya [LyricsPreviewLines] yang membaca
 * timeline; latar kartu dan Now Playing tidak ikut recompose tiap tick posisi.
 */
@Composable
fun LyricsCard(
    stateFlow: StateFlow<LyricsUiState>,
    timelineProviders: TimelineProviders,
    hasLyrics: Boolean,
    albumColor: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasLyrics) return
    val colors = LocalDengerinColors.current
    val darkBase = colors.miniPlayerDark
    val background = Brush.verticalGradient(
        listOf(
            mix(albumColor, darkBase, 0.48f),
            mix(albumColor, darkBase, 0.76f),
            darkBase,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .spotifyClickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lirik",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Perluas",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.spotifyClickable(onClick = onOpen),
                )
            }

            Spacer(Modifier.height(14.dp))
            LyricsPreviewLines(stateFlow = stateFlow, timelineProviders = timelineProviders)
        }
    }
}

/** Bagian kartu yang benar-benar perlu mengikuti posisi lagu. */
@Composable
private fun LyricsPreviewLines(
    stateFlow: StateFlow<LyricsUiState>,
    timelineProviders: TimelineProviders,
) {
    val state by stateFlow.collectAsState()
    val activeIndex by remember(
        state.lines,
        state.syncOffsetMs,
        state.isSynced,
        timelineProviders.position,
    ) {
        derivedStateOf {
            if (state.isSynced && state.lines.isNotEmpty()) {
                LrcParser.activeIndex(
                    state.lines,
                    timelineProviders.position() + state.syncOffsetMs + LrcParser.ACTIVE_LEAD_MS,
                )
            } else {
                -1
            }
        }
    }
    val previewTextStyle = remember {
        TextStyle(
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
        )
    }

    state.sourceLabel?.takeIf { it.isNotBlank() }?.let { label ->
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.48f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
    }
    when {
        state.isSynced && state.lines.isNotEmpty() -> {
            val start = (activeIndex - 1).coerceAtLeast(0)
            val end = (start + 3).coerceAtMost(state.lines.lastIndex)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (index in start..end) {
                    PreviewLine(
                        text = state.lines[index].text,
                        relative = index - activeIndex,
                        textStyle = previewTextStyle,
                    )
                }
            }
        }

        state.plainLines.isNotEmpty() -> Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.plainLines.take(4).forEach { text ->
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.68f),
                    style = previewTextStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(text: String, relative: Int, textStyle: TextStyle) {
    val targetAlpha = when {
        relative == 0 -> 1.0f
        relative < 0 -> 0.38f
        else -> 0.66f
    }
    val targetScale = if (relative == 0) 1.035f else 1.0f
    val animatedAlpha = animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "previewLineAlpha",
    )
    val animatedScale = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "previewLineScale",
    )
    val annotatedText = remember(text) { AnnotatedString(text) }

    Text(
        text = annotatedText,
        style = textStyle,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha.value
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    )
}

private fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = 1f,
)
