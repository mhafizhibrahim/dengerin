package com.dengerin.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song

@Composable
fun SongRow(
    song: Song,
    isActive: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    // Decode dibatasi 144 px (3x ukuran tampilan 52 dp) agar fling 100+ lagu
    // tidak membanjiri memori saat Coil mengcache thumbnail YouTube.
    val thumbnailUrl = upgradeThumbnailUrl(song.thumbnailUrl)?.takeIf { it.isNotBlank() }
    val coverModel = if (thumbnailUrl != null) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(THUMBNAIL_DECODE_SIZE)
            .crossfade(false)
            .build()
    } else {
        R.drawable.ic_music_placeholder
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
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
        if (onAddToPlaylist != null) {
            Spacer(Modifier.width(2.dp))
            IconButton(
                onClick = { onAddToPlaylist(song) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Tambah ${song.title} ke playlist",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        if (isActive) {
            Spacer(Modifier.width(6.dp))
            Spacer(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

private const val THUMBNAIL_DECODE_SIZE = 144
