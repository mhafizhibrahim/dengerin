package com.dengerin.app.presentation.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.util.formatTotalDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.PlaylistDetailViewModel
import com.dengerin.app.presentation.components.SongRow
import com.dengerin.app.presentation.components.rememberPlaylistCoverGradient
import kotlinx.coroutines.launch

private enum class SortOrder {
    DEFAULT,
    TITLE,
    ARTIST,
    DURATION,
}

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    playerState: PlayerUiState,
    controller: PlayerController,
    miniPlayer: @Composable () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks by viewModel.tracks.collectAsState()
    val info by viewModel.info.collectAsState()
    val gradient by rememberPlaylistCoverGradient(info?.coverUrl.orEmpty())
    val title = info?.name ?: "Playlist"
    val coverUrl = info?.coverUrl.orEmpty()

    var playlistQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    var sortExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }

    val visibleTracks = remember(tracks, playlistQuery, sortOrder) {
        val keyword = playlistQuery.trim()
        val filtered = if (keyword.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.artist.contains(keyword, ignoreCase = true)
            }
        }
        applySortOrder(filtered, sortOrder)
    }

    val currentInPlaylist = playerState.song?.let { current ->
        tracks.any { sameTrack(it, current) }
    } ?: false
    val playlistPlaying = currentInPlaylist && playerState.isPlaying
    val totalDuration = tracks.sumOf { it.durationMs }

    fun addTarget(): Song? = playerState.song
        ?.takeIf { current -> tracks.any { sameTrack(it, current) } }
        ?: tracks.firstOrNull()

    fun playPlaylist() {
        scope.launch {
            val base = applySortOrder(viewModel.resolvedTracks(), sortOrder)
            if (base.isEmpty()) return@launch
            // Lagu dari playlist ini sedang dijeda: lanjutkan, jangan putar ulang.
            if (currentInPlaylist && !playerState.isPlaying && !playerState.isLoading) {
                controller.togglePlayPause()
            } else {
                controller.playQueue(base, shuffleStart = true)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        bottomBar = {
            // Detail playlist tidak punya navigation bar, tapi mini player tetap melayang.
            Column { miniPlayer() }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(gradient)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 88.dp,
                    bottom = 24.dp,
                ),
            ) {
                item {
                    Card(
                        modifier = Modifier.size(210.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        AsyncImage(
                            model = upgradeThumbnailUrl(coverUrl)
                                ?.takeIf { it.isNotBlank() }
                                ?: R.drawable.ic_music_placeholder,
                            contentDescription = "Sampul playlist $title",
                            placeholder = painterResource(R.drawable.ic_music_placeholder),
                            error = painterResource(R.drawable.ic_music_placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${tracks.size} lagu  •  ${formatTotalDuration(totalDuration)}",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Sisi kiri: aksi pendukung.
                        IconButton(
                            onClick = { addTarget()?.let(onAddToPlaylist) },
                            enabled = tracks.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.PlaylistAdd,
                                contentDescription = "Tambah lagu ke playlist",
                                tint = if (tracks.isNotEmpty()) colors.textPrimary else colors.border,
                            )
                        }
                        IconButton(onClick = ::sharePlaylist) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Bagikan playlist",
                                tint = colors.textPrimary,
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Sisi kanan: Shuffle dengan indikator, lalu Play/Pause bundar.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = controller::toggleShuffle,
                                enabled = tracks.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    contentDescription = if (playerState.isShuffle) {
                                        "Matikan acak"
                                    } else {
                                        "Aktifkan acak"
                                    },
                                    tint = if (playerState.isShuffle) colors.accent else colors.textSecondary,
                                )
                            }
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (playerState.isShuffle) colors.accent else Color.Transparent,
                                    ),
                            )
                        }
                        Spacer(Modifier.width(18.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                                .then(
                                    if (tracks.isNotEmpty()) Modifier.clickable(onClick = { playPlaylist() })
                                    else Modifier,
                                ),
                        ) {
                            Icon(
                                imageVector = if (playlistPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playlistPlaying) "Jeda playlist" else "Putar playlist",
                                tint = colors.background,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
                        ) {
                            TextField(
                                value = playlistQuery,
                                onValueChange = { playlistQuery = it },
                                modifier = Modifier.fillMaxSize(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                placeholder = {
                                    Text(
                                        "Temukan di playlist",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.textPrimary,
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = colors.accent,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                ),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            OutlinedButton(
                                onClick = { sortExpanded = true },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.border),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textPrimary,
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sortir", style = MaterialTheme.typography.bodyMedium)
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                                containerColor = colors.surfaceElevated,
                            ) {
                                SortOrder.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = sortLabel(option),
                                                color = if (option == sortOrder) {
                                                    colors.accent
                                                } else {
                                                    colors.textPrimary
                                                },
                                            )
                                        },
                                        leadingIcon = {
                                            if (option == sortOrder) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = colors.accent,
                                                )
                                            }
                                        },
                                        onClick = {
                                            sortOrder = option
                                            sortExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (visibleTracks.isEmpty()) {
                    item {
                        Text(
                            text = if (tracks.isEmpty()) {
                                "Playlist ini masih kosong. Tambahkan lagu lewat tombol di atas ya."
                            } else {
                                "Tidak ada lagu yang cocok dengan \"$playlistQuery\"."
                            },
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                } else {
                    itemsIndexed(items = visibleTracks, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isActive = sameTrack(song, playerState.song),
                            onClick = { onSongClick(song, visibleTracks) },
                            onAddToPlaylist = onAddToPlaylist,
                        )
                        if (index != visibleTracks.lastIndex) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.border),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }

            // Top bar transparan di atas gradien, aman dari status bar.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.68f)),
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = colors.textPrimary,
                    )
                }
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
                                addTarget()?.let(onAddToPlaylist)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bagikan playlist", color = colors.textPrimary) },
                            onClick = {
                                moreExpanded = false
                                sharePlaylist()
                            },
                        )
                    }
                }
            }
        }
    }
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
    return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
}
