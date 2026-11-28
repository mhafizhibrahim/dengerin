package com.dengerin.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.LocalCachedTrackIds
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.player.QueueItem
import com.dengerin.app.player.QueueSourceType

private data class UpcomingEntry(val index: Int, val item: QueueItem)

/**
 * Antrean ala Spotify: lagu aktif, USER_QUEUE, sisa konteks, lalu AUTOPLAY_QUEUE.
 * Semua operasi memakai queueId (hapus/putar) atau index global (ubah urutan).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val items = remember(state.queueItems) { state.queueItems.toList() }
    val currentIndex = remember(items, state.currentQueueId, state.song) {
        val byId = state.currentQueueId?.let { id -> items.indexOfFirst { it.queueId == id } } ?: -1
        if (byId >= 0) byId else items.indexOfFirst { sameSong(it.track, state.song) }
    }
    val current = items.getOrNull(currentIndex)
    val upcoming = remember(items, currentIndex) {
        items.mapIndexedNotNull { index, item ->
            if (index > currentIndex) UpcomingEntry(index, item) else null
        }
    }
    val userQueue = upcoming.filter { it.item.sourceType == QueueSourceType.USER_QUEUE }
    val contextQueue = upcoming.filter { it.item.sourceType == QueueSourceType.CONTEXT_QUEUE }
    val autoplayQueue = upcoming.filter { it.item.sourceType == QueueSourceType.AUTOPLAY_QUEUE }
    val contextTitle = state.contextTitle.ifBlank { "antrean" }
    val sectionTitle = "Berikutnya dari $contextTitle"

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
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.32f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Antrean",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item(key = "header-current") { SectionHeader("Sedang Diputar") }
                if (current != null) {
                    item(key = "current-${current.queueId}") {
                        CurrentRow(item = current, isPlaying = state.isPlaying)
                    }
                } else {
                    item(key = "current-empty") {
                        EmptyText("Belum ada lagu yang diputar.")
                    }
                }

                if (userQueue.isNotEmpty()) {
                    item(key = "header-user") {
                        SectionHeader(
                            title = "Berikutnya di Antrean",
                            trailing = {
                                TextButton(onClick = onClear) {
                                    Text("Hapus Antrean", color = colors.accent)
                                }
                            },
                        )
                    }
                    items(userQueue, key = { it.item.queueId }) { entry ->
                        UpcomingRow(
                            entry = entry,
                            zone = userQueue,
                            onPlay = onPlay,
                            onRemove = onRemove,
                            onMove = onMove,
                        )
                    }
                }

                item(key = "header-context") {
                    SectionHeader(
                        title = sectionTitle,
                        trailing = {
                            AutoplaySwitch(
                                enabled = state.isAutoplay,
                                onToggleAutoplay = onToggleAutoplay,
                            )
                        },
                    )
                }
                if (contextQueue.isEmpty() && autoplayQueue.isEmpty()) {
                    item(key = "context-empty") {
                        EmptyText("Tidak ada lagu berikutnya dari konteks ini.")
                    }
                }
                items(contextQueue, key = { it.item.queueId }) { entry ->
                    UpcomingRow(
                        entry = entry,
                        zone = contextQueue,
                        onPlay = onPlay,
                        onRemove = onRemove,
                        onMove = onMove,
                    )
                }
                if (autoplayQueue.isNotEmpty()) {
                    item(key = "header-autoplay") {
                        SectionHeader("Putar Otomatis · Lagu Serupa")
                    }
                    items(autoplayQueue, key = { it.item.queueId }) { entry ->
                        UpcomingRow(
                            entry = entry,
                            zone = autoplayQueue,
                            onPlay = onPlay,
                            onRemove = onRemove,
                            onMove = onMove,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SheetToggle(
                    active = state.isShuffle,
                    icon = Icons.Rounded.Shuffle,
                    description = if (state.isShuffle) "Matikan acak" else "Aktifkan acak",
                    onClick = onToggleShuffle,
                )
                Spacer(Modifier.width(20.dp))
                SheetToggle(
                    active = state.repeatMode != PlayerUiState.REPEAT_OFF,
                    icon = if (state.repeatMode == PlayerUiState.REPEAT_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    },
                    description = when (state.repeatMode) {
                        PlayerUiState.REPEAT_ALL -> "Ulang semua"
                        PlayerUiState.REPEAT_ONE -> "Ulang satu lagu"
                        else -> "Aktifkan ulang"
                    },
                    onClick = onCycleRepeat,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Autoplay ${if (state.isAutoplay) "aktif" else "mati"}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(LocalBottomClearance.current))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun EmptyText(text: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = text,
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun AutoplaySwitch(
    enabled: Boolean,
    onToggleAutoplay: (Boolean) -> Unit,
) {
    val colors = LocalDengerinColors.current
    Switch(
        checked = enabled,
        onCheckedChange = onToggleAutoplay,
        modifier = Modifier.height(32.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.border,
        ),
    )
}

@Composable
private fun CurrentRow(item: QueueItem, isPlaying: Boolean) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = songListImageRequest(context, item.track),
            contentDescription = if (item.track.id in cachedIds) {
                "Sampul lagu ${item.track.title}, tersedia offline"
            } else {
                "Sampul lagu ${item.track.title}"
            },
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    SpotifyEqualizer(
                        playing = true,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = item.track.title,
                    color = colors.accent,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee() else Modifier,
                )
            }
            Text(
                text = item.track.artist,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpcomingRow(
    entry: UpcomingEntry,
    zone: List<UpcomingEntry>,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    val track = entry.item.track
    val position = zone.indexOfFirst { it.item.queueId == entry.item.queueId }
    var dragOffset by remember(entry.item.queueId) { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = tween(120),
        label = "queueDragOffset",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = animatedOffset },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPlay(entry.item.queueId) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = songListImageRequest(context, track),
                    contentDescription = if (track.id in cachedIds) {
                        "Sampul lagu ${track.title}, tersedia offline"
                    } else {
                        "Sampul lagu ${track.title}"
                    },
                    placeholder = darkSlatePlaceholder(),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(entry.item.queueId) {
                        detectDragGestures(
                            onDragStart = { dragOffset = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset = (dragOffset + dragAmount.y)
                                    .coerceIn(-rowHeightPx, rowHeightPx)
                                when {
                                    dragOffset >= rowHeightPx && position < zone.lastIndex -> {
                                        onMove(entry.index, zone[position + 1].index)
                                        dragOffset = 0f
                                    }
                                    dragOffset <= -rowHeightPx && position > 0 -> {
                                        onMove(entry.index, zone[position - 1].index)
                                        dragOffset = 0f
                                    }
                                }
                            },
                            onDragEnd = { dragOffset = 0f },
                            onDragCancel = { dragOffset = 0f },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Geser urutan ${track.title}",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            androidx.compose.material3.IconButton(
                onClick = { onRemove(entry.item.queueId) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Hapus ${track.title} dari antrean",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetToggle(
    active: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(48.dp, 44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) colors.accent else colors.textSecondary,
                modifier = Modifier.size(20.dp),
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

private fun sameSong(a: Song?, b: Song?): Boolean {
    if (a == null || b == null) return false
    return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
}
