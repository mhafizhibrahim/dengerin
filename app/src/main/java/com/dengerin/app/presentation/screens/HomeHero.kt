package com.dengerin.app.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.youtubeVideoId
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import kotlin.math.abs

private val MOODS = listOf("Semua", "Santai", "Semangat", "Fokus", "Perjalanan", "Galau")

/** Sampul YouTube resolusi tertinggi; fallback ke thumbnail asli lagu. */
private fun heroCoverUrl(song: Song): String {
    youtubeVideoId(song.url)?.let { return "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
    return upgradeThumbnailUrl(song.thumbnailUrl).orEmpty()
}

/** Hero unggulan dengan efek coverflow: kartu tengah besar, sisi mengecil halus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCoverflowCarousel(
    songs: List<Song>,
    playerState: PlayerUiState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val colors = LocalDengerinColors.current
    val pagerState = rememberPagerState(pageCount = { songs.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 72.dp),
            pageSpacing = (-24).dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val song = songs[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            val distance = abs(pageOffset)
            val scale = 1f - (distance * 0.18f)
            val alpha = 1f - (distance * 0.25f)
            val isCurrentSong = playerState.song?.id == song.id

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                    .zIndex(if (distance < 0.5f) 1f else 0f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                    .clickable {
                        if (isCurrentSong) onTogglePlayPause() else onPlaySong(song, songs)
                    },
            ) {
                AsyncImage(
                    model = heroCoverUrl(song).takeIf { it.isNotBlank() }
                        ?: R.drawable.ic_music_placeholder,
                    contentDescription = "Sampul lagu ${song.title}",
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    colors.background.copy(alpha = 0f),
                                    colors.background.copy(alpha = 0.45f),
                                    colors.background.copy(alpha = 0.94f),
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            color = colors.textPrimary.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.92f))
                            .border(1.dp, colors.border, CircleShape)
                            .clickable {
                                if (isCurrentSong) onTogglePlayPause() else onPlaySong(song, songs)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isCurrentSong && playerState.isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (isCurrentSong && playerState.isPlaying) {
                                "Jeda ${song.title}"
                            } else {
                                "Putar ${song.title}"
                            },
                            tint = colors.background,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(songs.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.accent else colors.border),
                )
            }
        }
    }
}

/** Filter mood: aktif memakai pill aksen, non-aktif permukaan + border tipis. */
@Composable
fun MoodChipRow(
    selectedMood: String,
    onMoodSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(MOODS) { mood ->
            val selected = mood == selectedMood
            Text(
                text = mood,
                color = if (selected) colors.background else colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(50))
                    .background(if (selected) colors.accent else colors.surface)
                    .clickable { onMoodSelected(mood) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
