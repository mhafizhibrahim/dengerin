package com.dengerin.app.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.core.LocalCachedTrackIds
import com.dengerin.app.core.LocalLikedTrackIds
import com.dengerin.app.core.isLikedIn
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    isActive: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    /** Tombol `+`/centang langsung toggle Lagu Disukai bila disediakan pemanggil. */
    onToggleLike: ((Song) -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    /** Lottie equalizer & marquee hanya muncul saat lagu ini benar-benar diputar. */
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    val likedIds = LocalLikedTrackIds.current
    val isLiked = song.isLikedIn(likedIds)
    val isCached = song.id in cachedIds
    // Decode 144x144, crossfade mati: aman saat fast fling ratusan baris.
    val coverModel = remember(song.id, song.displayThumbnailUrl) {
        songListImageRequest(context, song)
    }
    // clickable polos: tanpa Animatable/InteractionSource per row saat scroll cepat.
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = coverModel,
                contentDescription = if (isCached) {
                    "Sampul ${song.title}, tersedia offline"
                } else {
                    null
                },
                placeholder = darkSlatePlaceholder(),
                error = darkSlatePlaceholder(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isActive) colors.accent else colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee() else Modifier,
                )
                Text(
                    text = buildString {
                        append(song.artist)
                        if (song.durationMs > 0) append("  ·  ").append(formatDuration(song.durationMs))
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onToggleLike != null || onAddToPlaylist != null) {
                Spacer(Modifier.width(2.dp))
                IconButton(
                    onClick = {
                        if (onToggleLike != null) onToggleLike(song) else onAddToPlaylist?.invoke(song)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    if (onToggleLike != null) {
                        SpotifyLikeIcon(liked = isLiked, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Tambah ${song.title} ke playlist",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Opsi untuk ${song.title}",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (isActive) {
                Spacer(Modifier.width(6.dp))
                if (isPlaying) {
                    SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
                } else {
                    Spacer(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    )
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}