package com.dengerin.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.LocalLikedTrackIds
import com.dengerin.app.core.isLikedIn
import com.dengerin.app.core.listImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.model.songKey
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.presentation.LibraryViewModel
import com.dengerin.app.presentation.components.ProfileAvatarButton
import com.dengerin.app.presentation.components.SongMenuSheet
import com.dengerin.app.presentation.components.SongRow
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.spotifyClickable

private val LIBRARY_TABS = listOf("Playlist", "Lagu Disukai", "Artis", "Cache")

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    profile: UserProfile? = null,
    onOpenDrawer: () -> Unit = {},
    /** 2 = buka dialog impor Spotify; playlist baru memakai CreateSheet di root. */
    actionRequest: Int = 0,
    /** Pintasan "Lagu Disukai" membuka tab ini langsung, bukan Playlist. */
    initialTab: String = "Playlist",
    onActionRequestConsumed: () -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onToggleLike: (Song) -> Unit = {},
    onConnectSpotify: () -> Unit = {},
    onOpenSpotifyImport: () -> Unit = {},
    spotifyConnected: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.action.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var linkInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Playlist") }
    var sortNewest by remember { mutableStateOf(true) }
    var gridView by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(initialTab) { selectedTab = initialTab }

    LaunchedEffect(actionRequest) {
        if (actionRequest == 2) showImportDialog = true
        if (actionRequest != 0) onActionRequestConsumed()
    }

    // Dialog tetap terbuka sampai sampul 10 lagu pertama siap, lalu ditutup
    // otomatis bersamaan dengan perpindahan ke detail playlist.
    LaunchedEffect(action.importedPlaylistId, action.error) {
        if (action.importedPlaylistId != null || action.error != null) showImportDialog = false
    }

    val visiblePlaylists = remember(state.playlists, searchQuery, sortNewest) {
        val keyword = searchQuery.trim()
        val filtered = state.playlists.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                it.description.contains(keyword, ignoreCase = true)
        }
        // Pin selalu menang, lalu arah urutan terakhir diubah pengguna.
        if (sortNewest) {
            filtered.sortedWith(compareByDescending<LocalPlaylistEntity> { it.isPinned }
                .thenByDescending { it.updatedAt })
        } else {
            filtered.sortedWith(compareByDescending<LocalPlaylistEntity> { it.isPinned }
                .thenBy { it.updatedAt })
        }
    }
    val artistNames = remember(state.liked, state.history) {
        (state.liked + state.history).map { it.artist.trim() }.distinct().filter { it.isNotBlank() }
    }
    // Salinan immutable untuk LazyColumn: update Room/Firestore saat fast scroll aman.
    val safePlaylists = remember(visiblePlaylists) { visiblePlaylists.toList() }
    val safeLiked = remember(state.liked) { state.liked.toList() }
    val safeHistory = remember(state.history) { state.history.toList() }
    val safeArtistNames = remember(artistNames) { artistNames.toList() }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Konten mengalir di bawah mini player + nav yang melayang.
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = LocalBottomClearance.current,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatarButton(profile = profile, onClick = onOpenDrawer, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Koleksi Kamu",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Cari playlist",
                            tint = colors.textPrimary,
                        )
                    }
                    IconButton(onClick = onCreatePlaylist) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Buat playlist lokal",
                            tint = colors.textPrimary,
                        )
                    }
                }
            }

            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Cari playlist…", color = colors.textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LIBRARY_TABS.forEach { tab ->
                        val selected = tab == selectedTab
                        Text(
                            text = tab,
                            color = if (selected) colors.onAccent else colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) colors.accent else colors.surface)
                                .spotifyClickable { selectedTab = tab }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (sortNewest) "↕ Terbaru" else "↕ Terlama",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.spotifyClickable { sortNewest = !sortNewest },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(false, true).forEach { tile ->
                            val label = if (tile) "Grid" else "List"
                            Text(
                                text = label,
                                color = if (gridView == tile) colors.accent else colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (gridView == tile) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.spotifyClickable { gridView = tile },
                            )
                        }
                    }
                }
            }

            // "Lagu Disukai" selalu pinned paling atas, apa pun filter aktifnya.
            item {
                LikedCollectionRow(
                    count = state.liked.size,
                    onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_LIKED) },
                )
            }

            when (selectedTab) {
                "Playlist" -> {
                    item(key = "spotify_connection") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .clickable {
                                    if (spotifyConnected) onOpenSpotifyImport() else onConnectSpotify()
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (spotifyConnected) "Koleksi Spotify Terhubung"
                                    else "Hubungkan Akun Spotify",
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (spotifyConnected) "Ketuk untuk mengimpor seluruh koleksi"
                                    else "Impor playlist pribadi & Lagu Disukai gratis tanpa Client ID",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .padding(14.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Impor Playlist Spotify",
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tempel link playlist publik—tanpa perlu masuk.",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { showImportDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = colors.onAccent,
                                    ),
                                ) { Text("Impor Link Playlist") }
                                if (action.isBusy) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        action.progress ?: "Sedang mengambil data…",
                                        color = colors.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                action.message?.let { message ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        message,
                                        color = colors.accentSoftContent,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                action.error?.let { error ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        error,
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        CollectionRow(
                            icon = Icons.Filled.History,
                            title = "Riwayat Putar",
                            subtitle = "${state.history.size} lagu",
                            onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_HISTORY) },
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Playlist",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = onCreatePlaylist) {
                                Icon(Icons.Filled.Add, contentDescription = "Playlist baru", tint = colors.accent)
                            }
                        }
                    }

                    if (gridView) {
                        itemsIndexed(
                            items = safePlaylists.chunked(2),
                            key = { _, row -> "playlist_grid_${row.first().id}|${row.last().id}" },
                            contentType = { _, _ -> "playlist_grid_row" },
                        ) { _, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { playlist ->
                                    PlaylistTile(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.id) },
                                        onTogglePin = {
                                            viewModel.togglePin(playlist.id, !playlist.isPinned)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = safePlaylists,
                            key = { _, playlist -> "playlist_${playlist.id}" },
                            contentType = { _, _ -> "playlist_row" },
                        ) { _, playlist ->
                            PlaylistRow(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                                onTogglePin = { viewModel.togglePin(playlist.id, !playlist.isPinned) },
                            )
                        }
                    }
                }

                "Lagu Disukai" -> {
                    if (safeLiked.isEmpty()) {
                        item {
                            Text(
                                "Belum ada lagu yang kamu sukai.",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = safeLiked,
                            key = { _, song -> songKey(song) },
                            contentType = { _, _ -> "track_row" },
                        ) { _, song ->
                            SongRow(
                                song = song,
                                isActive = false,
                                onClick = { onSongClick(song, safeLiked) },
                                onToggleLike = onToggleLike,
                                onMore = { menuSong = song },
                            )
                        }
                    }
                }

                "Artis" -> {
                    if (safeArtistNames.isEmpty()) {
                        item {
                            Text(
                                "Belum ada artis dari koleksi kamu.",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = safeArtistNames,
                            key = { _, name -> "artist_$name" },
                            contentType = { _, _ -> "artist_row" },
                        ) { _, name ->
                            ArtistRow(name = name)
                        }
                    }
                }

                "Cache" -> {
                    if (safeHistory.isEmpty()) {
                        item {
                            Text(
                                "Belum ada lagu dari riwayat putar.",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = safeHistory,
                            key = { _, song -> songKey(song) },
                            contentType = { _, _ -> "track_row" },
                        ) { _, song ->
                            SongRow(
                                song = song,
                                isActive = false,
                                onClick = { onSongClick(song, safeHistory) },
                                onToggleLike = onToggleLike,
                                onMore = { menuSong = song },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!action.isBusy) showImportDialog = false },
            title = { Text("Impor Link Playlist", color = colors.textPrimary) },
            text = {
                Column {
                    Text(
                        "Tempel link open.spotify.com/playlist/..., spotify:playlist:..., atau spotify.link/....",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                    if (action.isBusy) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = action.progress ?: "Menyiapkan playlist…",
                            color = colors.accentSoftContent,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !action.isBusy,
                    onClick = {
                        clipboard.getText()?.let { linkInput = it.text }
                    },
                ) { Text("Tempel", color = colors.accent) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !action.isBusy,
                        onClick = { showImportDialog = false },
                    ) {
                        Text("Batal", color = colors.textSecondary)
                    }
                    TextButton(
                        enabled = linkInput.isNotBlank() && !action.isBusy,
                        onClick = {
                            viewModel.importLink(linkInput.trim())
                            linkInput = ""
                        },
                    ) { Text("Impor", color = colors.accent) }
                }
            },
            containerColor = colors.surface,
        )
    }

    menuSong?.let { selected ->
        SongMenuSheet(
            song = selected,
            isLiked = selected.isLikedIn(LocalLikedTrackIds.current),
            onDismiss = { menuSong = null },
            onToggleLike = {
                onToggleLike(selected)
                menuSong = null
            },
            onAddToPlaylist = {
                onAddToPlaylist(selected)
                menuSong = null
            },
            onAddToQueue = {
                onAddToQueue(selected)
                menuSong = null
            },
            onPlayNext = {
                onPlayNext(selected)
                menuSong = null
            },
            onShare = { menuSong = null },
            onAudioInfo = {},
        )
    }
}

@Composable
private fun LikedCollectionRow(count: Int, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.cosmetics.amethyst, colors.cosmetics.cyberBlue),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Lagu Disukai",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("$count lagu · Dipasang di atas", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "Dipasangkan di atas",
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun CollectionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun ArtistRow(name: String) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
        )
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun PlaylistRow(
    playlist: LocalPlaylistEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = listImageRequest(context, playlist.coverUrl),
                contentDescription = null,
                placeholder = darkSlatePlaceholder(),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (playlist.source) {
                        "SPOTIFY_SYNC" -> "Playlist · Spotify"
                        "SPOTIFY_IMPORT" -> "Playlist · Impor"
                        else -> "Playlist · Lokal"
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Badge(playlist.source)
            IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = if (playlist.isPinned) {
                        "Lepas pin ${playlist.name}"
                    } else {
                        "Pasang pin ${playlist.name}"
                    },
                    tint = if (playlist.isPinned) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun PlaylistTile(
    playlist: LocalPlaylistEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface)
            .spotifyClickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = listImageRequest(context, playlist.coverUrl),
                contentDescription = null,
                placeholder = darkSlatePlaceholder(),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(4.dp)),
            )
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp),
            ) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = if (playlist.isPinned) {
                        "Lepas pin ${playlist.name}"
                    } else {
                        "Pasang pin ${playlist.name}"
                    },
                    tint = if (playlist.isPinned) colors.accent else colors.onDarkSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = playlist.name,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        )
        Text(
            text = when (playlist.source) {
                "SPOTIFY_SYNC" -> "Playlist · Spotify"
                "SPOTIFY_IMPORT" -> "Playlist · Impor"
                else -> "Playlist · Lokal"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Badge(source: String) {
    val colors = LocalDengerinColors.current
    val label = when (source) {
        "SPOTIFY_SYNC" -> "Spotify"
        "SPOTIFY_IMPORT" -> "Impor"
        else -> "Lokal"
    }
    Text(
        text = label,
        color = colors.accentSoftContent,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.accentSoftContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}