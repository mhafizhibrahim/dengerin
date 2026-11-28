package com.dengerin.app.presentation.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.dengerin.app.core.network.NetworkState
import com.dengerin.app.core.nowPlayingCover
import com.dengerin.app.core.nowPlayingImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.PlaybackSettings
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.LyricsViewModel
import com.dengerin.app.presentation.TimelineProviders
import com.dengerin.app.presentation.components.ArtistNameText
import com.dengerin.app.presentation.components.LyricsCard
import com.dengerin.app.presentation.components.QueueSheet
import com.dengerin.app.presentation.components.SongMenuSheet
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.SpotifyLikeIcon
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.rememberAlbumColor
import com.dengerin.app.presentation.components.spotifyClickable
import com.dengerin.app.presentation.rememberCurrentSecond
import com.dengerin.app.presentation.rememberTimelineProviders
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pemutar penuh ala Spotify. Sheet masuk dari bawah, bisa ditarik turun untuk ditutup,
 * dan seluruh warna berasal dari token Fase 5 + warna dominan sampul yang diredupkan.
 *
 * Parameter publik lama tidak berubah; callback baru bersifat opsional supaya
 * pemanggil existing (DengerinApp) tetap kompil.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    container: AppContainer,
    state: PlayerUiState,
    controller: PlayerController,
    lyricsViewModel: LyricsViewModel,
    onOpenLyrics: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleLike: ((Song) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((artistId: String?, artistName: String) -> Unit)? = null,
    /** Dipanggil saat sampul di-swipe. Default: putar lagu tujuan dengan antrean aktif. */
    onSongSelected: ((Song, List<Song>) -> Unit)? = null,
    /** Default: membuka chooser Android; gagal ditampilkan sebagai Toast, tidak crash. */
    onShare: ((Song) -> Unit)? = null,
    /** Operasi antrean memakai queueId agar duplikat lagu tidak tertukar. */
    onRemoveUpcomingItem: ((String) -> Unit)? = null,
    onMoveUpcomingItem: ((Int, Int) -> Unit)? = null,
    /** Umpan balik snackbar pusat untuk aksi menu lagu. */
    onAddToQueue: ((Song) -> Unit)? = null,
    /** "Cari lagu" menutup sheet dan membuka tab Pencarian. */
    onSearch: () -> Unit = {},
    onPlayNext: ((Song) -> Unit)? = null,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val settings by container.playbackPreferences.settings.collectAsState()
    val network by container.networkMonitor.state.collectAsState()
    val song = state.song
    val coverUrl = song?.let { nowPlayingCover(settings, network, it).url }.orEmpty()
    val albumColor by rememberAlbumColor(coverUrl)
    val animatedAlbumColor by animateColorAsState(
        targetValue = albumColor,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "nowPlayingAlbumColor",
    )
    val timelineProviders = rememberTimelineProviders(controller.timeline)
    val gradient = remember(animatedAlbumColor, colors) {
        Brush.verticalGradient(
            listOf(
                lerp(animatedAlbumColor, colors.background, 0.10f),
                lerp(animatedAlbumColor, colors.background, 0.58f),
                colors.background,
            ),
        )
    }
    // Preview hanya berubah saat lirik/song berubah — bukan tiap tick posisi.
    val lyricsPreview by lyricsViewModel.preview.collectAsState()
    val queue = song?.let { state.queue.ifEmpty { listOf(it) } }.orEmpty()
    val selectSong = onSongSelected ?: { target, list -> controller.playSong(target, list) }
    val shareSong = onShare ?: { target -> shareSongExternally(context, target) }
    val audioInfo = listOf(
        if (state.isPlayingFromCache) "Dari cache" else "Dari jaringan",
        "Opus Audio",
        formatDuration(if (state.durationMs > 0) state.durationMs else song?.durationMs ?: 0L),
    ).joinToString(" • ")

    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Drag-to-dismiss + enter slide-in. Hanya sheet ini yang bergeser, bukan mini player.
    var dragY by remember { mutableFloatStateOf(0f) }
    var isDraggingSheet by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
    val dismissThreshold = screenHeightPx * 0.25f
    val settleY by animateFloatAsState(
        targetValue = if (isDraggingSheet) dragY else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "nowPlayingSheetSettle",
    )
    val enterY by animateFloatAsState(
        targetValue = if (entered) 0f else screenHeightPx,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "nowPlayingSheetEnter",
    )
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(song?.id) {
        showMenu = false
        showQueue = false
        dragY = 0f
    }

    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, verticalAmount ->
                isDraggingSheet = true
                dragY = (dragY + verticalAmount).coerceAtLeast(0f)
                change.consume()
            },
            onDragEnd = {
                isDraggingSheet = false
                if (dragY > dismissThreshold) onClose() else dragY = 0f
            },
            onDragCancel = {
                isDraggingSheet = false
                dragY = 0f
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .graphicsLayer {
                translationY = if (isDraggingSheet) dragY else settleY + enterY
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().background(gradient)) {
            NowPlayingTopBar(
                source = song?.let { playbackSource(it, state.isPlayingFromCache) }.orEmpty(),
                isPlaying = state.isPlaying,
                onClose = onClose,
                onMenu = { showMenu = true },
                dragModifier = dragModifier,
            )

            if (song == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Belum ada lagu yang diputar.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Cari lagu",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.spotifyClickable(onClick = onSearch),
                    )
                }
                return@Box
            }

            val currentSong = song
            val stableSelect by rememberUpdatedState(selectSong)
            val onSongSelectedStable = remember { { target: Song, list: List<Song> ->
                stableSelect(target, list)
            } }
            val onOpenLyricsStable by rememberUpdatedState(onOpenLyrics)
            val openLyricsStable = remember { { onOpenLyricsStable() } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .padding(bottom = LocalBottomClearance.current),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NowPlayingCoverPager(
                    queue = CoverQueue(queue),
                    currentSong = currentSong,
                    settings = settings,
                    network = network,
                    onSongSelected = onSongSelectedStable,
                )

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            color = colors.onDarkSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Spacer(Modifier.height(4.dp))
                        ArtistNameText(
                            artist = currentSong.artist,
                            artistId = currentSong.artistId,
                            artistIds = currentSong.artistIds,
                            onOpenArtist = onOpenArtist,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (onToggleLike != null) onToggleLike(currentSong) else controller.toggleLike()
                        },
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = if (state.isLiked) {
                                "Hapus dari Lagu Disukai"
                            } else {
                                "Tambahkan ke Lagu Disukai"
                            }
                        },
                    ) {
                        SpotifyLikeIcon(
                            liked = state.isLiked,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                // SATU-SATUNYA tempat yang mengcollect timeline tiap ~160 ms.
                NowPlayingSeekBar(
                    timelineProviders = timelineProviders,
                    songId = currentSong.id,
                    onSeekTo = controller::seekTo,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToggleControl(
                        active = state.isShuffle,
                        icon = Icons.Filled.Shuffle,
                        description = if (state.isShuffle) "Matikan acak" else "Aktifkan acak",
                        onClick = controller::toggleShuffle,
                    )
                    IconButton(onClick = controller::skipPrevious) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Lagu sebelumnya",
                            tint = colors.onDarkSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = colors.accent,
                            strokeWidth = 4.dp,
                        )
                    } else {
                        PlayPauseButton(
                            isPlaying = state.isPlaying,
                            onClick = controller::togglePlayPause,
                        )
                    }
                    IconButton(onClick = controller::skipNext) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Lagu berikutnya",
                            tint = colors.onDarkSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    ToggleControl(
                        active = state.repeatMode != PlayerUiState.REPEAT_OFF,
                        icon = if (state.repeatMode == PlayerUiState.REPEAT_ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        description = when (state.repeatMode) {
                            PlayerUiState.REPEAT_ALL -> "Ulang semua"
                            PlayerUiState.REPEAT_ONE -> "Ulang satu lagu"
                            else -> "Aktifkan ulang"
                        },
                        onClick = controller::cycleRepeat,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.isPlayingFromCache) colors.accent else colors.textSecondary),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.isPlayingFromCache) "Cache" else "Opus Audio",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Bagikan",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.spotifyClickable { shareSong(currentSong) },
                    )
                    Spacer(Modifier.width(18.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.spotifyClickable { showQueue = true },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Buka antrean",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Antrean",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                if (lyricsPreview.hasLyrics) {
                    Spacer(Modifier.height(20.dp))
                    LyricsCard(
                        stateFlow = lyricsViewModel.uiState,
                        timelineProviders = timelineProviders,
                        hasLyrics = true,
                        albumColor = animatedAlbumColor,
                        onOpen = openLyricsStable,
                    )
                } else if (!lyricsPreview.isLoading) {
                    // Tanpa kartu lirik: tetap ada jalan tenang ke layar lirik/fallback.
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Lihat lirik",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.spotifyClickable(onClick = openLyricsStable),
                    )
                }

                if (state.error != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.error.orEmpty(),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showMenu) {
            song?.let { currentSong ->
                SongMenuSheet(
                    song = currentSong,
                    isLiked = state.isLiked,
                    onDismiss = { showMenu = false },
                    onToggleLike = {
                        if (onToggleLike != null) onToggleLike(currentSong) else controller.toggleLike()
                    },
                    onAddToPlaylist = {
                        showMenu = false
                        onAddToPlaylist(currentSong)
                    },
                    onAddToQueue = {
                        if (onAddToQueue != null) onAddToQueue(currentSong)
                        else controller.addToQueue(currentSong, state.contextTitle)
                        showMenu = false
                    },
                    onPlayNext = {
                        if (onPlayNext != null) onPlayNext(currentSong)
                        else controller.playNext(currentSong, state.contextTitle)
                        showMenu = false
                    },
                    onShare = {
                        showMenu = false
                        shareSong(currentSong)
                    },
                    onAudioInfo = {},
                    audioInfo = audioInfo,
                    onOpenArtist = onOpenArtist,
                )
            }
        }

        if (showQueue) {
            QueueSheet(
                state = state,
                onDismiss = { showQueue = false },
                onPlay = { queueId -> controller.jumpToQueueItem(queueId) },
                onRemove = { queueId ->
                    if (onRemoveUpcomingItem != null) onRemoveUpcomingItem(queueId)
                    else controller.removeUpcomingItem(queueId)
                },
                onMove = { from, to ->
                    if (onMoveUpcomingItem != null) onMoveUpcomingItem(from, to)
                    else controller.moveUpcomingItem(from, to)
                },
                onClear = controller::clearUserQueue,
                onToggleAutoplay = controller::setAutoplay,
                onToggleShuffle = controller::toggleShuffle,
                onCycleRepeat = controller::cycleRepeat,
            )
        }
    }
}

/** Pembungkus stabil supaya pager tidak ikut recompose saat posisi lagu berjalan. */
@Immutable
private data class CoverQueue(val songs: List<Song>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingCoverPager(
    queue: CoverQueue,
    currentSong: Song,
    settings: PlaybackSettings,
    network: NetworkState,
    onSongSelected: (Song, List<Song>) -> Unit,
) {
    val context = LocalContext.current
    val songs = queue.songs
    val currentPageIndex = songs.indexOfFirst { sameSong(it, currentSong) }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex,
        pageCount = { songs.size },
    )
    val latestSong by rememberUpdatedState(currentSong)
    val latestSelect by rememberUpdatedState(onSongSelected)
    LaunchedEffect(currentPageIndex) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }
    LaunchedEffect(pagerState, queue) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val target = songs.getOrNull(page) ?: return@collect
                if (!sameSong(target, latestSong)) latestSelect(target, songs)
            }
    }

    HorizontalPager(
        state = pagerState,
        pageSpacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
    ) { page ->
        // Antrean bisa berubah/ menyusut saat pager masih settle.
        val pageSong = songs.getOrNull(page) ?: return@HorizontalPager
        AsyncImage(
            model = nowPlayingImageRequest(context, pageSong, settings, network),
            contentDescription = "Sampul album ${pageSong.title}",
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * Seekbar + waktu. Hanya membaca timeline lewat provider; progres digambar di fase draw
 * supaya ujung track tetap membulat dan bounds sentuh tidak berubah tiap tick.
 */
@Composable
private fun NowPlayingSeekBar(
    timelineProviders: TimelineProviders,
    songId: String,
    onSeekTo: (Long) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val durationMs by remember(timelineProviders.duration) {
        derivedStateOf { timelineProviders.duration().coerceAtLeast(1L) }
    }
    val currentSecond by timelineProviders.rememberCurrentSecond()

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    /** >= 0 berarti seek baru saja dikirim; pertahankan thumb sampai player update. */
    var pendingSeekMs by remember { mutableLongStateOf(-1L) }
    var showRemaining by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val trackHeight = animateDpAsState(
        targetValue = if (isDragging) 5.dp else 3.dp,
        animationSpec = tween(120),
        label = "seekbarTrackHeight",
    )
    val thumbSize = animateDpAsState(
        targetValue = if (isDragging) 15.dp else 12.dp,
        animationSpec = tween(120),
        label = "seekbarThumbSize",
    )

    val seeking = isDragging || pendingSeekMs >= 0L
    val displayProgressProvider: () -> Float = {
        if (isDragging || pendingSeekMs >= 0L) {
            dragProgress
        } else {
            val total = timelineProviders.duration().coerceAtLeast(1L).toFloat()
            (timelineProviders.position().toFloat() / total).coerceIn(0f, 1f)
        }
    }
    // Jam 1 Hz saat playback; real-time mengikuti jari saat drag/seek tertunda.
    val displaySecond = if (seeking) {
        (dragProgress * durationMs).toLong() / 1_000L
    } else {
        currentSecond
    }
    val remainingSecond = (durationMs / 1_000L - displaySecond).coerceAtLeast(0L)

    val latestPendingSeek = rememberUpdatedState(pendingSeekMs)
    val latestDragging = rememberUpdatedState(isDragging)

    LaunchedEffect(songId) {
        showRemaining = false
        pendingSeekMs = -1L
        dragProgress = 0f
    }
    LaunchedEffect(timelineProviders.position) {
        snapshotFlow(timelineProviders.position).collect { positionMs ->
            val pending = latestPendingSeek.value
            if (pending >= 0L && !latestDragging.value &&
                kotlin.math.abs(positionMs - pending) <= 600L
            ) {
                pendingSeekMs = -1L
            }
        }
    }
    LaunchedEffect(pendingSeekMs) {
        if (pendingSeekMs >= 0L) {
            // Jika seek gagal/ditunda, jangan biarkan thumb "ngunci" selamanya.
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
        onSeekTo(target)
        isDragging = false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .drawBehind {
                    val progress = displayProgressProvider().coerceIn(0f, 1f)
                    val trackPx = trackHeight.value.toPx()
                    val thumbPx = thumbSize.value.toPx()
                    val trackTop = (size.height - trackPx) / 2f
                    val radius = CornerRadius(trackPx / 2f, trackPx / 2f)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.24f),
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackPx),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width * progress, trackPx),
                        cornerRadius = radius,
                    )
                    val thumbLeft = (size.width - thumbPx).coerceAtLeast(0f) * progress
                    drawCircle(
                        color = Color.White,
                        radius = thumbPx / 2f,
                        center = Offset(thumbLeft + thumbPx / 2f, size.height / 2f),
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
            modifier = Modifier
                .fillMaxWidth()
                .spotifyClickable { showRemaining = !showRemaining },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (showRemaining) {
                    "-${formatDuration(remainingSecond * 1_000L)}"
                } else {
                    formatDuration(displaySecond * 1_000L)
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = if (showRemaining) {
                    formatDuration(displaySecond * 1_000L)
                } else {
                    "-${formatDuration(remainingSecond * 1_000L)}"
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    source: String,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onMenu: () -> Unit,
    dragModifier: Modifier,
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .then(dragModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Tutup layar pemutar",
                tint = colors.onDarkSurface,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "MEMUTAR DARI...",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    SpotifyEqualizer(
                        playing = true,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = source,
                    color = colors.onDarkSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onMenu) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Buka menu lagu",
                tint = colors.onDarkSurface,
            )
        }
    }
}

@Composable
private fun ToggleControl(
    active: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(56.dp)
            .spotifyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) colors.accent else colors.onDarkSurface.copy(alpha = 0.82f),
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) colors.accent else Color.Transparent),
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(colors.onDarkSurface)
            .spotifyClickable(onClick = onClick),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Jeda" else "Putar",
            tint = colors.onAccent,
            modifier = Modifier.size(30.dp),
        )
    }
}

private fun playbackSource(song: Song, fromCache: Boolean): String = when {
    fromCache -> "Cache"
    song.source.equals("SPOTIFY", ignoreCase = true) -> "Spotify"
    song.source.equals("LOCAL", ignoreCase = true) -> "Lokal"
    song.source.equals("YOUTUBE", ignoreCase = true) -> "YouTube"
    song.source.isBlank() -> "Dengerin"
    else -> song.source.lowercase().replaceFirstChar { it.uppercaseChar() }
}

private fun sameSong(a: Song?, b: Song?): Boolean {
    if (a == null || b == null) return false
    return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
}

private fun shareSongExternally(context: Context, song: Song) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, song.title)
        putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan lagu")) }
        .onFailure { Toast.makeText(context, "Tidak bisa membuka menu bagikan.", Toast.LENGTH_SHORT).show() }
}
