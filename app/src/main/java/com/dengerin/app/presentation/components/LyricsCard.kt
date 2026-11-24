package com.dengerin.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.lyrics.LyricLine
import com.dengerin.app.presentation.LyricsUiState

/** Kartu pratinjau lirik ala Spotify, ditempatkan di bawah kontrol Now Playing. */
@Composable
fun LyricsCard(
    state: LyricsUiState,
    albumColor: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(albumColor, mix(albumColor, colors.background, 0.42f)),
                ),
            )
            .border(1.dp, colors.border.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lirik",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(onClick = onOpen),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Buka lirik layar penuh",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                }

                state.isSynced && state.lines.isNotEmpty() -> {
                    val start = (state.activeLineIndex - 1).coerceAtLeast(0)
                    val end = (start + 4).coerceAtMost(state.lines.lastIndex)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (index in start..end) {
                            PreviewLine(
                                line = state.lines[index],
                                relative = index - state.activeLineIndex,
                            )
                        }
                    }
                }

                state.plainLines.isNotEmpty() -> Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    state.plainLines.take(5).forEach { text ->
                        Text(
                            text = text,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }

                else -> Text(
                    text = state.error ?: "Lirik belum tersedia untuk lagu ini.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(line: LyricLine, relative: Int) {
    val targetColor = when {
        relative == 0 -> Color.White
        relative < 0 -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val color by animateColorAsState(targetValue = targetColor, label = "previewLineColor")
    val scale by animateFloatAsState(
        targetValue = if (relative == 0) 1.02f else 1f,
        label = "previewLineScale",
    )
    Text(
        text = line.text,
        color = color,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = if (relative == 0) FontWeight.Bold else FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale),
    )
}

private fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = 1f,
)
