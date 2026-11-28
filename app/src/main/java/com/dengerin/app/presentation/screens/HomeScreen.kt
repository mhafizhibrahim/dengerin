package com.dengerin.app.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.dengerin.app.core.LocalCachedTrackIds
import com.dengerin.app.core.cardImageRequest
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.model.songKey
import com.dengerin.app.data.preferences.isDataSaverActive
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.HomeViewModel
import com.dengerin.app.presentation.components.ProfileAvatarButton
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.spotifyClickable

private enum class HomeFilter { SEMUA, MUSIK, PLAYLIST, CACHE }

@Composable
fun HomeScreen(
    container: AppContainer,
    viewModel: HomeViewModel,
    playerState: PlayerUiState,
    onOpenPlaylist: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    profile: UserProfile? = null,
    onOpenDrawer: () -> Unit = {},
    /** Drawer "Baru diputar" menaikkan nilai ini untuk membuka filter Cache. */
    historyRequest: Int = 0,
    /** Tombol "Coba lagi" pada error katalog. */
    onRetry: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    var filter by remember { mutableStateOf(HomeFilter.SEMUA) }
    LaunchedEffect(historyRequest) {
        if (historyRequest > 0) filter = HomeFilter.CACHE
    }
    val settings by container.playbackPreferences.settings.collectAsState()
    val network by container.networkMonitor.state.collectAsState()
    // Data Saver + seluler memakai mqdefault; Wi-Fi boleh hqdefault.
    val cardTargetPx = if (settings.isDataSaverActive(network) && network.isCellular) 240 else 360

    val activeSongId = playerState.song?.id
    val isPlaying = playerState.isPlaying

    val shortcuts = buildList {
        add(
            Shortcut(
                id = LibraryRepository.PLAYLIST_LIKED,
                title = "Lagu Disukai",
                subtitle = "Playlist · lokal",
                coverUrl = "",
                isLiked = true,
                onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_LIKED) },
            ),
        )
        add(
            Shortcut(
                id = LibraryRepository.PLAYLIST_HISTORY,
                title = "Riwayat Putar",
                subtitle = "Riwayat putar",
                coverUrl = "",
                onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_HISTORY) },
            ),
        )
        state.playlists.take(2).forEach { playlist ->
            add(
                Shortcut(
                    id = playlist.id,
                    title = playlist.name,
                    subtitle = "Playlist · ${playlist.source.lowercase()}",
                    coverUrl = playlist.coverUrl,
                    onClick = { onOpenPlaylist(playlist.id) },
                ),
            )
        }
        state.history.take(2).forEach { song ->
            add(
                Shortcut(
                    id = song.id,
                    title = song.title,
                    subtitle = song.artist,
                    coverUrl = song.coverUrl.ifBlank { song.thumbnailUrl },
                    active = activeSongId == song.id,
                    playing = isPlaying,
                    onClick = { onSongClick(song, state.history) },
                ),
            )
        }
        // Minimal 4 kartu: isi dari katalog bila playlist/riwayat belum ada.
        (state.frequent + state.hits + state.chill)
            .distinctBy { it.spotifyId.ifBlank { it.id } }
            .forEach { song ->
                if (size < 4) {
                    add(
                        Shortcut(
                            id = song.id,
                            title = song.title,
                            subtitle = song.artist,
                            coverUrl = song.coverUrl.ifBlank { song.thumbnailUrl },
                            active = activeSongId == song.id,
                            playing = isPlaying,
                            onClick = { onSongClick(song, listOf(song)) },
                        ),
                    )
                }
            }
    }.take(6)

    val recentAll = state.history.distinctBy { it.spotifyId.ifBlank { it.id } }
    val recent = recentAll.take(12)
    val frequent = state.frequent
        .distinctBy { it.spotifyId.ifBlank { it.id } }
        .take(12)
    val mixSource = state.chill.distinctBy { it.spotifyId.ifBlank { it.id } }.take(12)
    val frequentSource = frequent.ifEmpty { state.hits }
    val hitsSource = state.hits.ifEmpty { state.chill }
    val cacheSource = recentAll.ifEmpty { state.hits }
    val safeCacheTracks = remember(cacheSource) { cacheSource.toList() }

    val playlistCards = buildList {
        state.playlists.forEach { playlist ->
            add(
                CardItem(
                    id = playlist.id,
                    title = playlist.name,
                    subtitle = "Playlist · ${playlist.source.lowercase()}",
                    coverUrl = playlist.coverUrl,
                    onClick = { onOpenPlaylist(playlist.id) },
                ),
            )
        }
        if (isEmpty()) {
            add(
                CardItem(
                    id = LibraryRepository.PLAYLIST_LIKED,
                    title = "Lagu Disukai",
                    subtitle = "Playlist · lokal",
                    coverUrl = "",
                    onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_LIKED) },
                ),
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Konten sengaja mengalir sampai ke bawah: mini player + nav melayang di atasnya.
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = LocalBottomClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "home_top_bar", contentType = "header") {
            HomeTopBar(
                profile = profile,
                filter = filter,
                onOpenDrawer = onOpenDrawer,
                onFilterChange = { filter = it },
            )
        }

        if (filter == HomeFilter.SEMUA || filter == HomeFilter.PLAYLIST || filter == HomeFilter.CACHE) {
            if (shortcuts.isNotEmpty()) {
                item(key = "home_quick_grid", contentType = "shortcut_grid") {
                    SectionHeader("Akses Cepat")
                    QuickGrid(
                        shortcuts = shortcuts,
                        targetPx = cardTargetPx,
                    )
                }
            }
        }

        if (filter == HomeFilter.SEMUA || filter == HomeFilter.MUSIK) {
            item(key = "home_header_frequent", contentType = "header") {
                SectionHeader("Sering Kamu Putar")
            }
            item(key = "home_rail_frequent", contentType = "track_rail") {
                HorizontalCards(
                    items = frequentSource.map { it.toCard(frequentSource, onSongClick) },
                    activeSongId = activeSongId,
                    isPlaying = isPlaying,
                    targetPx = cardTargetPx,
                )
            }

            if (mixSource.isNotEmpty()) {
                item(key = "home_header_mix", contentType = "header") {
                    SectionHeader("Mix Untukmu")
                }
                item(key = "home_rail_mix", contentType = "track_rail") {
                    HorizontalCards(
                        items = mixSource.map { it.toCard(mixSource, onSongClick) },
                        activeSongId = activeSongId,
                        isPlaying = isPlaying,
                        targetPx = cardTargetPx,
                    )
                }
            }

            item(key = "home_header_recent", contentType = "header") {
                SectionHeader(
                    title = "Baru diputar",
                    actionLabel = "Tampilkan semua",
                    onAction = { filter = HomeFilter.CACHE },
                )
            }
            item(key = "home_rail_recent", contentType = "track_rail") {
                HorizontalCards(
                    items = recent.map { it.toCard(recent, onSongClick) },
                    activeSongId = activeSongId,
                    isPlaying = isPlaying,
                    targetPx = cardTargetPx,
                )
            }

            item(key = "home_header_hits", contentType = "header") {
                SectionHeader("Hits Indonesia Hari Ini")
            }
            item(key = "home_rail_hits", contentType = "track_rail") {
                HorizontalCards(
                    items = hitsSource.map { it.toCard(hitsSource, onSongClick) },
                    activeSongId = activeSongId,
                    isPlaying = isPlaying,
                    targetPx = cardTargetPx,
                )
            }
        }

        if (filter == HomeFilter.PLAYLIST) {
            item(key = "home_header_playlists", contentType = "header") {
                SectionHeader("Playlist Kamu")
            }
            item(key = "home_rail_playlists", contentType = "playlist_rail") {
                HorizontalCards(
                    items = playlistCards,
                    activeSongId = activeSongId,
                    isPlaying = isPlaying,
                    targetPx = cardTargetPx,
                )
            }
        }

        if (filter == HomeFilter.CACHE) {
            item(key = "home_header_cache_all", contentType = "header") {
                SectionHeader("Baru diputar · semua")
            }
            itemsIndexed(
                items = safeCacheTracks,
                key = { _, song -> songKey(song) },
                contentType = { _, _ -> "track_row" },
            ) { _, song ->
                CompactSongRow(
                    song = song,
                    isActive = song.id == activeSongId,
                    isPlaying = isPlaying,
                    onClick = { onSongClick(song, safeCacheTracks) },
                )
            }
        }

        if (state.isLoadingCatalog) {
            item(key = "home_loading", contentType = "status") {
                Text(
                    "Menyiapkan katalog musik…",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.catalogError != null) {
            item(key = "home_error", contentType = "status") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = state.catalogError.orEmpty(),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Coba lagi",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.spotifyClickable(onClick = onRetry),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    profile: UserProfile?,
    filter: HomeFilter,
    onOpenDrawer: () -> Unit,
    onFilterChange: (HomeFilter) -> Unit,
) {
    val colors = LocalDengerinColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatarButton(profile = profile, onClick = onOpenDrawer, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeFilter.entries.forEach { option ->
                val selected = filter == option
                val label = when (option) {
                    HomeFilter.SEMUA -> "Semua"
                    HomeFilter.MUSIK -> "Musik"
                    HomeFilter.PLAYLIST -> "Playlist"
                    HomeFilter.CACHE -> "Cache"
                }
                Text(
                    text = label,
                    color = if (selected) colors.onAccent else colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colors.accent else colors.surface)
                        .spotifyClickable { onFilterChange(option) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.spotifyClickable(onClick = onAction),
            )
        }
    }
}

@Composable
private fun QuickGrid(shortcuts: List<Shortcut>, targetPx: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shortcuts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        targetPx = targetPx,
                        onClick = shortcut.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HorizontalCards(
    items: List<CardItem>,
    activeSongId: String?,
    isPlaying: Boolean,
    targetPx: Int,
) {
    if (items.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, card -> "${card.id}|${card.title}" },
            contentType = { _, _ -> "catalog_card" },
        ) { _, card ->
            CatalogCard(
                card = card,
                isActive = card.id == activeSongId,
                isPlaying = isPlaying,
                targetPx = targetPx,
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    shortcut: Shortcut,
    onClick: () -> Unit,
    targetPx: Int = 360,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.shortcut)
            .spotifyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shortcut.isLiked) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.cosmetics.amethyst, colors.onDarkSurface),
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
            } else {
                AsyncImage(
                    model = cardImageRequest(context, upgradeThumbnailUrl(shortcut.coverUrl, targetPx)),
                    contentDescription = null,
                    placeholder = darkSlatePlaceholder(),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.title,
                    color = colors.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortcut.subtitle,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (shortcut.active && shortcut.playing) {
                Box(Modifier.padding(end = 10.dp)) {
                    SpotifyEqualizer(playing = true, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(card: CardItem, isActive: Boolean, isPlaying: Boolean, targetPx: Int = 360) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(148.dp)
            .spotifyClickable(onClick = card.onClick),
    ) {
        AsyncImage(
            model = cardImageRequest(context, upgradeThumbnailUrl(card.coverUrl, targetPx)),
            contentDescription = null,
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.title,
                color = if (isActive) colors.accent else colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isActive && isPlaying) {
                SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
            }
        }
        Text(
            text = card.subtitle,
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    val isCached = song.id in cachedIds
    val isCurrentlyPlaying = isActive && isPlaying
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = songListImageRequest(context, song),
            contentDescription = if (isCached) {
                "Sampul ${song.title}, tersedia offline"
            } else {
                null
            },
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isActive) colors.accent else colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isCurrentlyPlaying) Modifier.basicMarquee() else Modifier,
            )
            Text(
                text = song.artist,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrentlyPlaying) {
            SpotifyEqualizer(playing = true, modifier = Modifier.size(16.dp))
        }
    }
}

private fun Song.toCard(queue: List<Song>, onClick: (Song, List<Song>) -> Unit) = CardItem(
    id = id,
    title = title,
    subtitle = buildString {
        append(artist)
        if (durationMs > 0) append("  ·  ").append(formatDuration(durationMs))
    },
    coverUrl = coverUrl.ifBlank { thumbnailUrl },
    onClick = { onClick(this@toCard, queue) },
)

private data class Shortcut(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val isLiked: Boolean = false,
    val active: Boolean = false,
    val playing: Boolean = false,
    val onClick: () -> Unit,
)

private data class CardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val onClick: () -> Unit,
)