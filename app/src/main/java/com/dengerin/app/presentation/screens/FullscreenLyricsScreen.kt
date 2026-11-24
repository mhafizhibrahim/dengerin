package com.dengerin.app.presentation.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.presentation.LyricsUiState
import com.dengerin.app.presentation.LyricsViewModel
import com.dengerin.app.presentation.components.LyricsFallbackColor
import com.dengerin.app.presentation.components.rememberAlbumColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live Lyrics layar penuh. Latar mengikuti warna dominan sampul (sudah di-tuning supaya
 * teks putih tetap kontras), daftar lirik mengikuti posisi lagu, dan kontrol sinkronisasi
 * ±0.5 s tersedia untuk mengatasi selisih intro audio YouTube.
 */
@Composable
fun FullscreenLyricsScreen(
    viewModel: LyricsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    val albumColor by rememberAlbumColor(state.song?.thumbnailUrl.orEmpty())
    val backdrop = remember(albumColor, colors) {
        Brush.verticalGradient(
            listOf(albumColor, mix(albumColor, colors.background, 0.22f), colors.background),
        )
    }

    SetupEdgeToEdgeBars(albumColor)
    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backdrop),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LyricsTopBar(
                state = state,
                onClose = onClose,
                onAdjust = viewModel::adjustSyncOffset,
                onReset = viewModel::resetSyncOffset,
            )

            LyricsViewport(
                state = state,
                albumColor = albumColor,
                onLineClick = viewModel::seekToLine,
                modifier = Modifier.weight(1f),
            )

            LyricsBottomControls(
                state = state,
                onSeek = viewModel::seekTo,
                onTogglePlayPause = viewModel::togglePlayPause,
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION") // statusBarColor/navigationBarColor masih dipakai untuk API 26+ di sini.
private fun SetupEdgeToEdgeBars(albumColor: Color) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        val previousStatus = window.statusBarColor
        val previousNav = window.navigationBarColor
        val previousLightStatus = insets?.isAppearanceLightStatusBars
        val previousLightNav = insets?.isAppearanceLightNavigationBars
        insets?.let {
            it.isAppearanceLightStatusBars = false
            it.isAppearanceLightNavigationBars = false
        }
        onDispose {
            window.statusBarColor = previousStatus
            window.navigationBarColor = previousNav
            insets?.let { controller ->
                if (previousLightStatus != null) controller.isAppearanceLightStatusBars = previousLightStatus
                if (previousLightNav != null) controller.isAppearanceLightNavigationBars = previousLightNav
            }
        }
    }
    LaunchedEffect(albumColor) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        window.statusBarColor = albumColor.toArgb()
        window.navigationBarColor = albumColor.toArgb()
    }
}

@Composable
private fun LyricsTopBar(
    state: LyricsUiState,
    onClose: () -> Unit,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Tutup lirik layar penuh",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(
                text = state.song?.title.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.song?.artist.orEmpty(),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SyncPill(text = "−0.5s", onClick = { onAdjust(-500L) })
        Spacer(Modifier.width(6.dp))
        SyncPill(
            text = if (state.syncOffsetMs == 0L) "+0.5s" else syncLabel(state.syncOffsetMs),
            onClick = if (state.syncOffsetMs == 0L) {
                { onAdjust(500L) }
            } else {
                onReset
            },
        )
    }
}

@Composable
private fun SyncPill(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun syncLabel(offsetMs: Long): String =
    if (offsetMs > 0) "+${offsetMs / 1000}s" else "${offsetMs / 1000}s"

@Composable
private fun LyricsViewport(
    state: LyricsUiState,
    albumColor: Color,
    onLineClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportPx = with(LocalDensity.current) { maxHeight.toPx() }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var programmaticScroll by remember { mutableStateOf(false) }
        var suppressUntil by remember { mutableLongStateOf(0L) }
        var showResumePill by remember { mutableStateOf(false) }

        suspend fun scrollToActive() {
            val index = state.activeLineIndex
            if (index < 0 || index >= state.lines.size) return
            val anchorPx = (viewportPx * 0.32f).toInt().coerceAtLeast(0)
            programmaticScroll = true
            try {
                try {
                    listState.animateScrollToItem(index, scrollOffset = -anchorPx)
                } catch (_: IllegalArgumentException) {
                    // Compose lama menolak offset negatif; tetap sejajar di atas sebagai fallback.
                    listState.animateScrollToItem(index)
                }
            } finally {
                programmaticScroll = false
            }
        }

        LaunchedEffect(state.activeLineIndex, state.lines.size, viewportPx.toInt()) {
            if (suppressUntil == 0L || System.currentTimeMillis() >= suppressUntil) {
                scrollToActive()
                showResumePill = false
                suppressUntil = 0L
            }
        }

        // Scroll manual: jeda auto-scroll 3.5 detik dan tampilkan tombol kembali.
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress to programmaticScroll }
                .collect { (inProgress, isProgrammatic) ->
                    if (inProgress && !isProgrammatic) {
                        suppressUntil = System.currentTimeMillis() + 3_500L
                        showResumePill = true
                    }
                }
        }

        LaunchedEffect(suppressUntil) {
            val wait = suppressUntil - System.currentTimeMillis()
            if (suppressUntil == 0L) return@LaunchedEffect
            if (wait > 0) delay(wait)
            showResumePill = false
            scrollToActive()
            suppressUntil = 0L
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 56.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.isLoading && !state.hasLyrics -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    }
                }

                state.isSynced && state.lines.isNotEmpty() -> {
                    itemsIndexed(state.lines, key = { index, line -> "${line.timestampMs}-$index" }) { index, line ->
                        LyricText(
                            text = line.text,
                            relative = index - state.activeLineIndex,
                            onClick = { onLineClick(index) },
                        )
                    }
                }

                state.plainLines.isNotEmpty() -> {
                    itemsIndexed(state.plainLines) { _, line ->
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 26.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                        )
                    }
                }

                else -> item {
                    Text(
                        text = state.error ?: "Lirik belum tersedia untuk lagu ini.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                    )
                }
            }
        }

        // Edge fade: teks memudar halus, tidak terpotong kasar.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(Brush.verticalGradient(listOf(albumColor, Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, mix(albumColor, colors.background, 0.6f)),
                    ),
                ),
        )

        AnimatedVisibility(
            visible = showResumePill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable {
                        showResumePill = false
                        suppressUntil = 0L
                        scope.launch { scrollToActive() }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "Kembali ke lirik",
                    color = LyricsFallbackColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LyricText(
    text: String,
    relative: Int,
    onClick: () -> Unit,
) {
    val targetColor = when {
        relative == 0 -> Color.White
        relative < 0 -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val color by animateColorAsState(targetValue = targetColor, label = "lyricLineColor")
    val scale by animateFloatAsState(
        targetValue = if (relative == 0) 1.03f else 1f,
        label = "lyricLineScale",
    )
    Text(
        text = text,
        color = color,
        fontSize = 26.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun LyricsBottomControls(
    state: LyricsUiState,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val displayPosition = if (isDragging) sliderPosition.toLong() else state.positionMs

    LaunchedEffect(state.positionMs) {
        if (!isDragging) {
            sliderPosition = state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
        }
    }
    LaunchedEffect(state.song?.id) {
        isDragging = false
        sliderPosition = 0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Slider(
            value = sliderPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { value ->
                isDragging = true
                sliderPosition = value
            },
            onValueChangeFinished = {
                onSeek(sliderPosition.toLong())
                isDragging = false
            },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.34f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displayPosition),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formatDuration(state.durationMs),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onTogglePlayPause)
                .align(Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                tint = LyricsFallbackColor,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = 1f,
)
