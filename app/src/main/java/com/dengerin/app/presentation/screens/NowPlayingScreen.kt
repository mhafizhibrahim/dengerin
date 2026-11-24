package com.dengerin.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.LyricsViewModel
import com.dengerin.app.presentation.components.LyricsCard
import com.dengerin.app.presentation.components.rememberAlbumColor
import com.dengerin.app.presentation.components.rememberCoverGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    controller: PlayerController,
    lyricsViewModel: LyricsViewModel,
    onOpenLyrics: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val song = state.song
    val gradient by rememberCoverGradient(song?.thumbnailUrl.orEmpty())
    val albumColor by rememberAlbumColor(song?.thumbnailUrl.orEmpty())
    val lyricsState by lyricsViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Sedang Diputar",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Tutup layar pemutar",
                            tint = colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        if (song == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Belum ada lagu yang diputar.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text("Cari lagu", color = colors.accent) }
            }
            return@Scaffold
        }

        val duration = if (state.durationMs > 0) state.durationMs else song.durationMs
        var isDragging by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableFloatStateOf(0f) }
        val displayPosition = if (isDragging) sliderPosition.toLong() else state.positionMs

        LaunchedEffect(state.positionMs) {
            if (!isDragging) sliderPosition = state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
        }
        LaunchedEffect(song.id) {
            isDragging = false
            sliderPosition = 0f
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = upgradeThumbnailUrl(song.thumbnailUrl)
                    ?.takeIf { it.isNotBlank() }
                    ?: R.drawable.ic_music_placeholder,
                contentDescription = "Sampul album ${song.title}",
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = song.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = song.artist,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))
            Slider(
                value = sliderPosition.coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { value ->
                    isDragging = true
                    sliderPosition = value
                },
                onValueChangeFinished = {
                    controller.seekTo(sliderPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(displayPosition), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(duration), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = controller::toggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (state.isShuffle) "Matikan acak" else "Aktifkan acak",
                        tint = if (state.isShuffle) colors.accent else colors.textSecondary,
                    )
                }
                IconButton(onClick = controller::toggleLike) {
                    Icon(
                        imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (state.isLiked) "Batal suka" else "Tandai suka",
                        tint = if (state.isLiked) colors.accent else colors.textSecondary,
                    )
                }
                IconButton(onClick = controller::cycleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == PlayerUiState.REPEAT_ONE) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = when (state.repeatMode) {
                            PlayerUiState.REPEAT_ALL -> "Ulang semua"
                            PlayerUiState.REPEAT_ONE -> "Ulang satu lagu"
                            else -> "Aktifkan ulang"
                        },
                        tint = if (state.repeatMode != PlayerUiState.REPEAT_OFF) {
                            colors.accent
                        } else {
                            colors.textSecondary
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = controller::skipPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Lagu sebelumnya",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(28.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(72.dp),
                        color = colors.accent,
                        strokeWidth = 5.dp,
                    )
                } else {
                    BoxPlayPause(
                        isPlaying = state.isPlaying,
                        onClick = controller::togglePlayPause,
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.width(28.dp))
                IconButton(onClick = controller::skipNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Lagu berikutnya",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            LyricsCard(
                state = lyricsState,
                albumColor = albumColor,
                onOpen = onOpenLyrics,
            )

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
}

@Composable
private fun BoxPlayPause(
    isPlaying: Boolean,
    onClick: () -> Unit,
    color: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Jeda" else "Putar",
            tint = Color.Black,
            modifier = Modifier.size(36.dp),
        )
    }
}
