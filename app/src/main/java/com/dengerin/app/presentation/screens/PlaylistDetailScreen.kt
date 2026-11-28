package com.dengerin.app.presentation.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.LocalLikedTrackIds
import com.dengerin.app.core.cardImageRequest
import com.dengerin.app.core.isLikedIn
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatTotalDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.PlaylistDetailViewModel
import com.dengerin.app.presentation.components.SongMenuSheet
import com.dengerin.app.presentation.components.SongRow
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.rememberPlaylistCoverGradient
import kotlinx.coroutines.launch

private enum class SortOrder {
    DEFAULT,
    TITLE,
    ARTIST,
    DURATION,
}

/** Baris UI immutable: kunci LazyColumn memakai entryId (atau trackId untuk virtual). */
@Immutable
private data class PlaylistTrackRow(
    val entryId: String,
    val trackId: String,
    val song: Song,
)

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    playerState: PlayerUiState,
    controller: PlayerController,
    onSongClick: (Song, List<Song>, String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onFindSongs: () -> Unit,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val likedIds = LocalLikedTrackIds.current
    val tracks by viewModel.tracks.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val info by viewModel.info.collectAsState()
    val isVirtual = viewModel.playlistId() == LibraryRepository.PLAYLIST_LIKED ||
        viewModel.playlistId() == LibraryRepository.PLAYLIST_HISTORY
    // Palette di-extract sekali per (playlistId, coverUrl), tidak ikut recompose list.
    val gradient by rememberPlaylistCoverGradient(
        playlistId = viewModel.playlistId(),
        imageUrl = info?.coverUrl.orEmpty(),
    )
    val title = info?.name ?: "Playlist"
    val description = info?.description.orEmpty()
    val coverUrl = info?.coverUrl.orEmpty()

    var playlistQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    var sortExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var renameDescription by remember { mutableStateOf("") }
    var menuRow by remember { mutableStateOf<PlaylistTrackRow?>(null) }

    // tracks dari Repository mengikuti urutan entries; indeksnya dipakai untuk
    // memetakan lagu ke entryId asli tanpa menebak dari judul/artis.
    val rowsSource = remember(tracks, entries) {
        tracks.mapIndexed { index, song ->
            PlaylistTrackRow(
                entryId = entries.getOrNull(index)?.entryId.orEmpty(),
                trackId = song.spotifyId.ifBlank { song.id },
                song = song,
            )
        }
    }
    val visibleRows = remember(rowsSource, playlistQuery, sortOrder) {
        val keyword = playlistQuery.trim()
        val filtered = rowsSource.filter { row ->
            keyword.isBlank() ||
                row.song.title.contains(keyword, ignoreCase = true) ||
                row.song.artist.contains(keyword, ignoreCase = true)
        }
        when (sortOrder) {
            SortOrder.DEFAULT -> filtered
            SortOrder.TITLE -> filtered.sortedBy { it.song.title.trim().lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.song.artist.trim().lowercase() }
            SortOrder.DURATION -> filtered.sortedByDescending { it.song.durationMs }
        }
    }
    // Snapshot immutable untuk LazyColumn saat Room/Firestore mengubah daftar.
    val safeRows = remember(visibleRows) { visibleRows.toList() }

    val currentInPlaylist = playerState.song?.let { current ->
        tracks.any { sameTrack(it, current) }
    } ?: false
    val playlistPlaying = currentInPlaylist && playerState.isPlaying
    val totalDuration = tracks.sumOf { it.durationMs }

    fun playPlaylist(shuffleStart: Boolean) {
        scope.launch {
            val base = when (sortOrder) {
                SortOrder.DEFAULT -> viewModel.resolvedTracks()
                else -> applySortOrder(viewModel.resolvedTracks(), sortOrder)
            }
            if (base.isEmpty()) return@launch
            if (!shuffleStart && currentInPlaylist && !playerState.isPlaying && !playerState.isLoading) {
                controller.togglePlayPause()
            } else {
                controller.playQueue(base, shuffleStart = shuffleStart, contextTitle = title)
            }
        }
    }

    fun sharePlaylist() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Dengerin playlist: $title")
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan playlist")) }
    }

    Box(modifier = modifier.fillMaxSize().background(gradient)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 88.dp,
                bottom = LocalBottomClearance.current,
            ),
        ) {
            item {
                PlaylistCollage(
                    coverUrl = coverUrl,
                    songs = tracks.take(4).map { it to songListImageRequest(context, it) },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${tracks.size} lagu · ${formatTotalDuration(totalDuration)}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Kontrol utama: tomput bulat hijau #1ED760.
                    IconButton(
                        onClick = { playPlaylist(shuffleStart = false) },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                    ) {
                        Icon(
                            imageVector = if (playlistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playlistPlaying) "Jeda playlist" else "Putar playlist",
                            tint = colors.onAccent,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { playPlaylist(shuffleStart = true) },
                        shape = RoundedCornerShape(50),
                        enabled = tracks.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Acak", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = playlistQuery,
                    onValueChange = { playlistQuery = it },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(4.dp),
                    singleLine = true,
                    placeholder = { Text("Cari di playlist ini…", color = colors.textSecondary) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceElevated,
                        unfocusedContainerColor = colors.surfaceElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.accent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )
                Spacer(Modifier.height(10.dp))
            }

            if (safeRows.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (tracks.isEmpty()) {
                                "Belum ada lagu di playlist ini."
                            } else {
                                "Lagu tidak ditemukan."
                            },
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = safeRows,
                    key = { _, row -> "${row.entryId.ifBlank { row.trackId }}|${row.song.id}|${row.song.spotifyId}" },
                    contentType = { _, _ -> "track_row" },
                ) { _, row ->
                    SongRow(
                        song = row.song,
                        isActive = sameTrack(playerState.song, row.song),
                        isPlaying = playerState.isPlaying && sameTrack(playerState.song, row.song),
                        onClick = {
                            scope.launch {
                                val queue = applySortOrder(viewModel.resolvedTracks(), sortOrder)
                                val resolved = queue.firstOrNull { sameTrack(it, row.song) } ?: row.song
                                onSongClick(resolved, queue, title)
                            }
                        },
                        onToggleLike = onToggleLike,
                        onMore = { menuRow = row },
                    )
                }
            }
        }

        // Tombol atas mengambang di atas gradasi sampul.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.68f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = colors.textPrimary)
            }

            Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.68f)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Urutkan playlist", tint = colors.textPrimary)
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                        containerColor = colors.surfaceElevated,
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(sortLabel(order), color = colors.textPrimary) },
                                trailingIcon = {
                                    if (order == sortOrder) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent)
                                    }
                                },
                                onClick = {
                                    sortOrder = order
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    IconButton(
                        onClick = { moreExpanded = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.68f)),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Opsi playlist",
                            tint = colors.textPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        containerColor = colors.surfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tambah lagu", color = colors.textPrimary) },
                            onClick = {
                                moreExpanded = false
                                onFindSongs()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bagikan playlist", color = colors.textPrimary) },
                            onClick = {
                                moreExpanded = false
                                sharePlaylist()
                            },
                        )
                        if (!isVirtual) {
                            DropdownMenuItem(
                                text = { Text("Ubah nama & deskripsi", color = colors.textPrimary) },
                                onClick = {
                                    moreExpanded = false
                                    renameName = title
                                    renameDescription = description
                                    showRename = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus playlist", color = colors.error) },
                                onClick = {
                                    moreExpanded = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    menuRow?.let { row ->
        SongMenuSheet(
            song = row.song,
            isLiked = row.song.isLikedIn(likedIds),
            onDismiss = { menuRow = null },
            onToggleLike = { onToggleLike(row.song); menuRow = null },
            onAddToPlaylist = { onAddToPlaylist(row.song); menuRow = null },
            onAddToQueue = { onAddToQueue(row.song); menuRow = null },
            onPlayNext = { onPlayNext(row.song); menuRow = null },
            onShare = { sharePlaylist(); menuRow = null },
            onAudioInfo = {},
            onRemoveFromPlaylist = if (row.entryId.isNotBlank()) {
                {
                    viewModel.removeEntry(row.entryId)
                    menuRow = null
                }
            } else {
                null
            },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Ubah playlist", color = colors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama playlist") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameDescription,
                        onValueChange = { renameDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Deskripsi (opsional)") },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameName.isNotBlank(),
                    onClick = {
                        viewModel.rename(renameName.trim(), renameDescription.trim())
                        showRename = false
                    },
                ) { Text("Simpan", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Hapus playlist?", color = colors.textPrimary) },
            text = {
                Text(
                    text = "\"$title\" dan semua lagu di dalamnya akan dihapus dari koleksi kamu.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete()
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error,
                        contentColor = colors.onAccent,
                    ),
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }
}

/** Header Spotify: satu sampul utuh atau collage 2/3/4 sampul. */
@Composable
private fun PlaylistCollage(
    coverUrl: String,
    songs: List<Pair<Song, Any>>,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(210.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surfaceElevated),
    ) {
        if (songs.size <= 1) {
            AsyncImage(
                model = cardImageRequest(context, coverUrl.ifBlank { songs.firstOrNull()?.first?.displayThumbnailUrl.orEmpty() }),
                contentDescription = "Sampul playlist",
                placeholder = darkSlatePlaceholder(),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }
        when (songs.size) {
            2 -> Row(modifier = Modifier.fillMaxSize()) {
                songs.forEach { (_, model) -> CollageCell(model, Modifier.weight(1f).fillMaxSize()) }
            }
            3 -> Row(modifier = Modifier.fillMaxSize()) {
                CollageCell(songs[0].second, Modifier.weight(1f).fillMaxSize())
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    CollageCell(songs[1].second, Modifier.weight(1f).fillMaxWidth())
                    CollageCell(songs[2].second, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageCell(songs[0].second, Modifier.weight(1f).fillMaxSize())
                    CollageCell(songs[1].second, Modifier.weight(1f).fillMaxSize())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CollageCell(songs[2].second, Modifier.weight(1f).fillMaxSize())
                    CollageCell(songs[3].second, Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun CollageCell(model: Any, modifier: Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        placeholder = darkSlatePlaceholder(),
        error = painterResource(R.drawable.ic_music_placeholder),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.DEFAULT -> "Default"
    SortOrder.TITLE -> "Judul A-Z"
    SortOrder.ARTIST -> "Artis A-Z"
    SortOrder.DURATION -> "Durasi"
}

private fun applySortOrder(songs: List<Song>, order: SortOrder): List<Song> = when (order) {
    SortOrder.DEFAULT -> songs
    SortOrder.TITLE -> songs.sortedBy { it.title.trim().lowercase() }
    SortOrder.ARTIST -> songs.sortedBy { it.artist.trim().lowercase() }
    SortOrder.DURATION -> songs.sortedByDescending { it.durationMs }
}

private fun sameTrack(a: Song?, b: Song?): Boolean {
    if (a == null || b == null) return false
    if (a.id == b.id) return true
    val aStable = a.spotifyId.ifBlank { a.id }
    val bStable = b.spotifyId.ifBlank { b.id }
    return aStable.isNotBlank() && aStable == bStable
}
