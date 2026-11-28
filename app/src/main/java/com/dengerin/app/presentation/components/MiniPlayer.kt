package com.dengerin.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.rememberTimelineProviders
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Mini player Fase 5: terkunci di atas Bottom Nav. Tidak ada drag, dock, atau morphing.
 * Swipe kiri/kanan hanya berlaku pada area teks untuk lagu sebelumnya/berikutnya.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyMiniPlayer(
    state: PlayerUiState,
    controller: PlayerController,
    onOpen: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 42.dp.toPx() }
    val coverModel = remember(song.id, song.displayThumbnailUrl) {
        songListImageRequest(context, song)
    }

    val dominantTint by rememberMiniPlayerTint(song.displayThumbnailUrl)
    val tint by animateColorAsState(
        targetValue = dominantTint,
        animationSpec = tween(350),
        label = "mini-player-tint",
    )
    val textOffset = remember { Animatable(0f) }

    LaunchedEffect(song.id) { textOffset.snapTo(0f) }

    Box(
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pointerInput(song.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val offset = textOffset.value
                                scope.launch {
                                    textOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    )
                                }
                                when {
                                    offset > swipeThreshold -> onPrevious()
                                    offset < -swipeThreshold -> onNext()
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    textOffset.snapTo(
                                        (textOffset.value + dragAmount).coerceIn(-72f, 72f),
                                    )
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(modifier = Modifier.graphicsLayer { translationX = textOffset.value }) {
                    Text(
                        text = song.title,
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = song.artist,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }

            IconButton(onClick = { onToggleLike(song) }) {
                SpotifyLikeIcon(
                    liked = state.isLiked,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = controller::togglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Progress putih 2.dp menempel di tepi paling bawah kartu. Digambar di fase
        // draw (bukan fillMaxWidth) supaya ticker tidak me-relayout kartu.
        MiniPlayerProgress(timeline = controller.timeline)
    }
}

@Composable
private fun BoxScope.MiniPlayerProgress(timeline: StateFlow<Pair<Long, Long>>) {
    val providers = rememberTimelineProviders(timeline)
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                val total = providers.duration().coerceAtLeast(1L)
                val fraction = (providers.position().toFloat() / total).coerceIn(0f, 1f)
                drawRect(color = Color.White.copy(alpha = 0.24f), size = size)
                if (fraction > 0f) {
                    drawRect(color = Color.White, size = Size(size.width * fraction, size.height))
                }
            },
    )
}
