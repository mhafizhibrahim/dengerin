package com.dengerin.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    controller: PlayerController,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val colors = LocalDengerinColors.current
    val duration = if (state.durationMs > 0) state.durationMs else song.durationMs
    val progress = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = upgradeThumbnailUrl(song.thumbnailUrl)
                        ?.takeIf { it.isNotBlank() }
                        ?: R.drawable.ic_music_placeholder,
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(4.dp))
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(24.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = controller::togglePlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Jeda" else "Putar",
                            tint = colors.accent,
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = colors.accent,
                trackColor = colors.border,
            )
        }
    }
}
