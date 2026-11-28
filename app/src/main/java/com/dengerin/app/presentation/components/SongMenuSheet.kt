package com.dengerin.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song

/**
 * Menu `⋮` dari Now Playing. Sheet ini murni UI: semua aksi dikirim lewat callback
 * eksplisit dan tidak pernah mengakses repository langsung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    song: Song,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onAudioInfo: () -> Unit,
    modifier: Modifier = Modifier,
    audioInfo: String = "",
    /** Hanya tampil bila sheet dipanggil dari dalam playlist. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onOpenArtist: ((String?, String) -> Unit)? = null,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    var showAudioInfo by remember(song.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.32f)),
            )
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = songListImageRequest(context, song),
                    contentDescription = "Sampul lagu ${song.title}",
                    placeholder = darkSlatePlaceholder(),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ArtistNameText(
                        artist = song.artist,
                        artistId = song.artistId,
                        artistIds = song.artistIds,
                        onOpenArtist = onOpenArtist?.let { callback ->
                            { id, name ->
                                callback(id, name)
                                onDismiss()
                            }
                        },
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(6.dp))

            MenuRow(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isLiked) "Hapus dari Lagu Disukai" else "Tambahkan ke Lagu Disukai",
                tint = if (isLiked) colors.accent else colors.textSecondary,
                onClick = onToggleLike,
            )
            MenuRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Tambahkan ke playlist lain",
                onClick = onAddToPlaylist,
            )
            MenuRow(
                icon = Icons.Rounded.AddToQueue,
                label = "Tambahkan ke antrean",
                onClick = onAddToQueue,
            )
            MenuRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                label = "Putar berikutnya",
                onClick = onPlayNext,
            )
            MenuRow(
                icon = Icons.Filled.Share,
                label = "Bagikan",
                onClick = onShare,
            )
            if (onRemoveFromPlaylist != null) {
                MenuRow(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Hapus dari playlist ini",
                    tint = colors.error,
                    onClick = onRemoveFromPlaylist,
                )
            }
            if (audioInfo.isNotBlank()) {
                MenuRow(
                    icon = Icons.Filled.Info,
                    label = "Info Audio/Cache",
                    onClick = {
                        onAudioInfo()
                        showAudioInfo = !showAudioInfo
                    },
                )

                AnimatedVisibility(visible = showAudioInfo) {
                    Text(
                        text = audioInfo,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 36.dp, bottom = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val colors = LocalDengerinColors.current
    val iconTint = tint ?: colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .spotifyClickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
