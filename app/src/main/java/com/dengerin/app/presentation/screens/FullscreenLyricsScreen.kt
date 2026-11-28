package com.dengerin.app.presentation.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.dengerin.app.core.nowPlayingCoverUrl
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.lyrics.LrcParser
import com.dengerin.app.data.lyrics.NO_LYRICS_MESSAGE
import com.dengerin.app.presentation.LyricsUiState
import com.dengerin.app.presentation.LyricsViewModel
import com.dengerin.app.presentation.TimelineProviders
import com.dengerin.app.presentation.components.ArtistNameText
import com.dengerin.app.presentation.components.rememberAlbumColor
import com.dengerin.app.presentation.rememberCurrentSecond
import com.dengerin.app.presentation.rememberTimelineProviders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val LYRIC_SCROLL_COALESCE_MS = 90L

/**
 * Live Lyrics layar penuh. Posisi fokus baris aktif berada di ~34% atas viewport,
 * warna teks putih di atas gradien album yang diredupkan, dan timeline tidak pernah
 * menyentuh daftar lirik kecuali indeks baris aktif benar-benar berpindah.
 */
@Composable
fun FullscreenLyricsScreen(
    viewModel: LyricsViewModel,
    timeline: StateFlow<Pair<Long, Long>>,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onSearchLyrics: () -> Unit,
    onOpenArtist: ((String?, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val coverUrl = state.song?.let { nowPlayingCoverUrl(context, it) }.orEmpty()
    val albumColor by rememberAlbumColor(coverUrl)
    val animatedAlbumColor by animateColorAsState(
        targetValue = albumColor,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "lyricsAlbumColor",
    )
    val timelineProviders = rememberTimelineProviders(timeline)
    val darkBase = colors.miniPlayerDark
    val fadeColor = remember(animatedAlbumColor, darkBase) {
        lerp(animatedAlbumColor, darkBase, 0.45f)
    }
    val backdrop = remember(animatedAlbumColor, darkBase, fadeColor) {
        Brush.verticalGradient(
            listOf(
                lerp(animatedAlbumColor, darkBase, 0.10f),
                fadeColor,
                darkBase,
            ),
        )
    }

    // BackHandler dipasang DengerinApp di lapisan paling atas overlay ini.
    SetupEdgeToEdgeBars(albumColor)

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
                onOpenArtist = onOpenArtist,
            )

            LyricsViewport(
                state = state,
                timelineProviders = timelineProviders,
                fadeColor = fadeColor,
                onLineClick = viewModel::seekToLine,
                onSearchLyrics = onSearchLyrics,
                modifier = Modifier.weight(1f),
            )

            LyricsBottomControls(
                timelineProviders = timelineProviders,
                songId = state.song?.id,
                isPlaying = isPlaying,
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
        val previousLightStatus = insets.isAppearanceLightStatusBars
        val previousLightNav = insets.isAppearanceLightNavigationBars
        insets.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onDispose {
            window.statusBarColor = previousStatus
            window.navigationBarColor = previousNav
            insets.apply {
                isAppearanceLightStatusBars = previousLightStatus
                isAppearanceLightNavigationBars = previousLightNav
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
    onOpenArtist: ((String?, String) -> Unit)?,
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
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = state.song?.title.orEmpty(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ArtistNameText(
                artist = state.song?.artist.orEmpty(),
                artistId = state.song?.artistId.orEmpty(),
                artistIds = state.song?.artistIds.orEmpty(),
                onOpenArtist = onOpenArtist,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        SyncPill(
            text = "−0.5s",
            description = "Geser sinkronisasi lirik mundur 0,5 detik",
            onClick = { onAdjust(-500L) },
        )
        Spacer(Modifier.width(6.dp))
        SyncPill(
            text = "+0.5s",
            description = "Geser sinkronisasi lirik maju 0,5 detik",
            onClick = { onAdjust(500L) },
        )
        if (state.syncOffsetMs != 0L) {
            Spacer(Modifier.width(6.dp))
            SyncPill(
                text = syncLabel(state.syncOffsetMs),
                description = "Atur ulang sinkronisasi lirik",
                compact = true,
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun SyncPill(
    text: String,
    description: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(if (compact) 28.dp else 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = if (compact) 8.dp else 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun syncLabel(offsetMs: Long): String {
    val magnitude = abs(offsetMs)
    val sign = if (offsetMs > 0) "+" else if (offsetMs < 0) "−" else ""
    val seconds = magnitude / 1_000L
    val tenths = (magnitude % 1_000L) / 100L
    return if (tenths == 0L) "$sign${seconds}s" else "$sign$seconds,$tenths" + "s"
}

@Composable
private fun LyricsViewport(
    state: LyricsUiState,
    timelineProviders: TimelineProviders,
    fadeColor: Color,
    onLineClick: (Int) -> Unit,
    onSearchLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val anchorPx = viewportPx * 0.34f
        val targetOffsetPx = -(viewportPx * 0.34f).toInt()
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var programmaticScroll by remember { mutableStateOf(false) }
        var suppressUntil by remember { mutableLongStateOf(0L) }
        var showResumePill by remember { mutableStateOf(false) }
        var lastScrolledIndex by remember { mutableIntStateOf(-1) }
        var lastScrollAt by remember { mutableLongStateOf(0L) }

        // Timeline berubah tiap ~160 ms, tapi derivedStateOf hanya memberi sinyal
        // ketika indeks baris aktif benar-benar berganti (binary search O(log N)).
        val activeLineIndex by remember(
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
        val latestActiveIndex = rememberUpdatedState(activeLineIndex)

        val lyricTextStyle = remember {
            TextStyle(
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            )
        }

        suspend fun scrollToActive(index: Int) {
            if (index !in state.lines.indices) return
            if (suppressUntil > System.currentTimeMillis()) return

            val distance = if (lastScrolledIndex >= 0) {
                abs(index - lastScrolledIndex)
            } else {
                Int.MAX_VALUE
            }
            programmaticScroll = true
            try {
                when {
                    distance > 15 -> try {
                        listState.scrollToItem(index, scrollOffset = targetOffsetPx)
                    } catch (_: IllegalArgumentException) {
                        listState.scrollToItem(index)
                    }

                    distance > 5 -> try {
                        listState.animateScrollToItem(index, scrollOffset = targetOffsetPx)
                    } catch (_: IllegalArgumentException) {
                        listState.animateScrollToItem(index)
                    }

                    else -> {
                        val target = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }
                        if (target == null) {
                            try {
                                listState.animateScrollToItem(index, scrollOffset = targetOffsetPx)
                            } catch (_: IllegalArgumentException) {
                                listState.animateScrollToItem(index)
                            }
                        } else {
                            val delta = target.offset - anchorPx
                            if (abs(delta) > 1f) {
                                listState.animateScrollBy(
                                    delta,
                                    tween(durationMillis = 400, easing = FastOutSlowInEasing),
                                )
                            }
                        }
                    }
                }
                lastScrolledIndex = index
            } finally {
                programmaticScroll = false
                lastScrollAt = System.currentTimeMillis()
            }
        }

        // Baris lirik rapat bisa berganti lebih cepat dari animasi scroll. Tunda
        // sedikit lalu baca indeks terbaru agar animasi lama tidak saling menimpa.
        LaunchedEffect(activeLineIndex, state.lines.size, viewportPx.toInt()) {
            val elapsed = System.currentTimeMillis() - lastScrollAt
            if (elapsed < LYRIC_SCROLL_COALESCE_MS) {
                delay(LYRIC_SCROLL_COALESCE_MS - elapsed)
            }
            scrollToActive(latestActiveIndex.value)
        }

        // Geser manual: jeda auto-scroll 2,5 detik setelah jari dilepas.
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress to programmaticScroll }
                .collect { (inProgress, isProgrammatic) ->
                    if (inProgress && !isProgrammatic) {
                        suppressUntil = System.currentTimeMillis() + 2_500L
                        showResumePill = true
                        lastScrolledIndex = latestActiveIndex.value
                        lastScrollAt = System.currentTimeMillis()
                    }
                }
        }

        LaunchedEffect(suppressUntil) {
            if (suppressUntil == 0L) return@LaunchedEffect
            val wait = suppressUntil - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            showResumePill = false
            scrollToActive(latestActiveIndex.value)
            suppressUntil = 0L
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = maxOf(maxHeight * 0.55f, LocalBottomClearance.current),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                state.isLoading && !state.hasLyrics -> item(
                    key = "lyrics-loading",
                    contentType = "lyric_status",
                ) {
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
                    itemsIndexed(
                        items = state.lines,
                        key = { index, line -> "${index}_${line.timestampMs}" },
                        contentType = { _, _ -> "lyric_line" },
                    ) { index, line ->
                        LyricLine(
                            text = line.text,
                            relative = index - activeLineIndex,
                            isInstrumental = isInstrumentalLine(line.text),
                            textStyle = lyricTextStyle,
                            onClick = { onLineClick(index) },
                        )
                    }
                }

                state.plainLines.isNotEmpty() -> {
                    itemsIndexed(
                        items = state.plainLines,
                        key = { index, _ -> "plain_$index" },
                        contentType = { _, _ -> "plain_line" },
                    ) { _, line ->
                        PlainLyricLine(text = line, style = lyricTextStyle)
                    }
                }
            }

            if (!state.isLoading && !state.hasLyrics) {
                item(key = "lyrics-empty", contentType = "lyric_status") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 48.dp),
                    ) {
                        Text(
                            text = state.error ?: NO_LYRICS_MESSAGE,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onSearchLyrics,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
                            ),
                        ) {
                            Text(
                                text = "Cari Lirik Manual",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Overlay gradient ringan: tidak memakai Offscreen/DstIn dan tidak menahan klik.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(24.dp)
                .background(Brush.verticalGradient(listOf(fadeColor, Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeColor))),
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
                    .background(colors.accent)
                    .clickable {
                        showResumePill = false
                        suppressUntil = 0L
                        scope.launch { scrollToActive(latestActiveIndex.value) }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(
                    text = "Kembali ke lirik",
                    color = colors.onAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun isInstrumentalLine(text: String): Boolean {
    val value = text.trim()
    return value == "♪" ||
        value.equals("instrumental", ignoreCase = true) ||
        value.equals("[instrumental]", ignoreCase = true)
}

@Composable
private fun LyricLine(
    text: String,
    relative: Int,
    isInstrumental: Boolean,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    val targetAlpha = when {
        relative == 0 -> 1.0f
        relative < 0 -> 0.38f
        else -> 0.66f
    }
    val targetScale = if (relative == 0) 1.035f else 1.0f

    // State sengaja tidak dibaca dengan `by` di composition: `.value` hanya dibaca
    // di dalam graphicsLayer supaya animasi tetap di render phase.
    val animatedAlpha = animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "lyricLineAlpha",
    )
    val animatedScale = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "lyricLineScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha = animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = tween(90),
        label = "lyricLinePress",
    )
    val annotatedText = remember(text) { AnnotatedString(text) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .graphicsLayer {
                alpha = animatedAlpha.value * pressAlpha.value
                scaleX = animatedScale.value
                scaleY = animatedScale.value
                // Rata kiri: teks membesar ke kanan, tidak terpotong di tepi layar.
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (isInstrumental) {
            InstrumentalDots()
        } else {
            Text(text = annotatedText, style = textStyle)
        }
    }
}

/** Tiga titik dengan tinggi tetap; pulsing hanya mengubah alpha layer dot, bukan alpha baris. */
@Composable
private fun InstrumentalDots() {
    val transition = rememberInfiniteTransition(label = "instrumentalDots")
    Row(
        modifier = Modifier
            .height(30.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(3) { index ->
            val pulse = transition.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 180),
                ),
                label = "instrumentalDot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .graphicsLayer { alpha = pulse.value },
            )
        }
    }
}

@Composable
private fun PlainLyricLine(text: String, style: TextStyle) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.68f),
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    )
}

@Composable
private fun LyricsBottomControls(
    timelineProviders: TimelineProviders,
    songId: String?,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val durationMs by remember(timelineProviders.duration) {
        derivedStateOf { timelineProviders.duration().coerceAtLeast(1L) }
    }
    val currentSecond by timelineProviders.rememberCurrentSecond()

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    /** Seek baru saja dikirim: tahan thumb sampai player mengonfirmasi posisi. */
    var pendingSeekMs by remember { mutableLongStateOf(-1L) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val seeking = isDragging || pendingSeekMs >= 0L
    val displayProgressProvider: () -> Float = {
        if (isDragging || pendingSeekMs >= 0L) {
            dragProgress
        } else {
            val total = timelineProviders.duration().coerceAtLeast(1L).toFloat()
            (timelineProviders.position().toFloat() / total).coerceIn(0f, 1f)
        }
    }
    // Waktu jam 1 Hz saat playback; real-time mengikuti jari saat drag/seek tertunda.
    val displaySecond = if (seeking) {
        (dragProgress * durationMs).toLong() / 1_000L
    } else {
        currentSecond
    }

    val latestPendingSeek = rememberUpdatedState(pendingSeekMs)
    val latestDragging = rememberUpdatedState(isDragging)

    LaunchedEffect(songId) {
        isDragging = false
        dragProgress = 0f
        pendingSeekMs = -1L
    }
    LaunchedEffect(timelineProviders.position) {
        snapshotFlow(timelineProviders.position).collect { positionMs ->
            val pending = latestPendingSeek.value
            if (pending >= 0L && !latestDragging.value && abs(positionMs - pending) <= 600L) {
                pendingSeekMs = -1L
            }
        }
    }
    LaunchedEffect(pendingSeekMs) {
        if (pendingSeekMs >= 0L) {
            delay(1_200L)
            pendingSeekMs = -1L
        }
    }

    val updateDragProgress: (Float) -> Unit = { x ->
        dragProgress = (x / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val commitSeek: () -> Unit = {
        val target = (dragProgress * timelineProviders.duration().coerceAtLeast(1L)).toLong()
        pendingSeekMs = target
        onSeek(target)
        isDragging = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .drawBehind {
                    val progress = displayProgressProvider().coerceIn(0f, 1f)
                    val trackHeight = 3.dp.toPx()
                    val thumbDiameter = 10.dp.toPx()
                    val trackTop = (size.height - trackHeight) / 2f
                    val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.24f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHeight),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width * progress, trackHeight),
                        cornerRadius = radius,
                    )
                    val thumbLeft = (size.width - thumbDiameter).coerceAtLeast(0f) * progress
                    drawCircle(
                        color = Color.White,
                        radius = thumbDiameter / 2f,
                        center = Offset(thumbLeft + thumbDiameter / 2f, size.height / 2f),
                    )
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = "Geser posisi lagu"
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        isDragging = true
                        updateDragProgress(offset.x)
                        commitSeek()
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateDragProgress(offset.x)
                        },
                        onHorizontalDrag = { change, _ ->
                            updateDragProgress(change.position.x)
                            change.consume()
                        },
                        onDragEnd = commitSeek,
                        onDragCancel = { isDragging = false },
                    )
                }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displaySecond * 1_000L),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onTogglePlayPause)
                .align(Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Jeda" else "Putar",
                tint = colors.miniPlayerDark,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
