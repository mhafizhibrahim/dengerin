package com.dengerin.app.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.cardImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.spotifyClickable

private val MOODS = listOf("Semua", "Santai", "Semangat", "Fokus", "Perjalanan", "Galau")

/** Carousel datar ala Spotify: sampul 148.dp, teks di bawah, tanpa kartu/border. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCoverflowCarousel(
    songs: List<Song>,
    targetPx: Int = 360,
    playerState: PlayerUiState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { songs.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val song = songs.getOrNull(page) ?: return@HorizontalPager
            val isCurrentSong = playerState.song?.id == song.id
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .spotifyClickable {
                        if (isCurrentSong) onTogglePlayPause() else onPlaySong(song, songs)
                    },
            ) {
                AsyncImage(
                    model = cardImageRequest(
                        context,
                        upgradeThumbnailUrl(
                            song.coverUrl.ifBlank { song.thumbnailUrl },
                            targetPx,
                        ),
                    ),
                    contentDescription = "Sampul lagu ${song.title}",
                    placeholder = darkSlatePlaceholder(),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(148.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        color = if (isCurrentSong) colors.accent else colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrentSong && playerState.isPlaying) {
                        SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = song.artist,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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

/** Filter mood: pill tanpa border, aktif hijau dengan teks hitam. */
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
                color = if (selected) colors.onAccent else colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else colors.surface)
                    .spotifyClickable { onMoodSelected(mood) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}