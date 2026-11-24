package com.dengerin.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.HomeViewModel

private enum class HomeFilter { SEMUA, MUSIK, PLAYLIST, RIWAYAT }

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    playerState: PlayerUiState,
    onOpenSearch: () -> Unit,
    onImportLink: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    var filter by remember { mutableStateOf(HomeFilter.SEMUA) }

    val shortcuts = buildList {
        add(
            Shortcut(
                id = LibraryRepository.PLAYLIST_LIKED,
                title = "Lagu Disukai",
                coverUrl = "",
                isLiked = true,
                onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_LIKED) },
            ),
        )
        add(
            Shortcut(
                id = LibraryRepository.PLAYLIST_HISTORY,
                title = "Riwayat Putar",
                coverUrl = "",
                onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_HISTORY) },
            ),
        )
        state.playlists.take(2).forEach { playlist ->
            add(
                Shortcut(
                    id = playlist.id,
                    title = playlist.name,
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
                    coverUrl = song.thumbnailUrl,
                    onClick = { onSongClick(song, state.history) },
                ),
            )
        }
    }.take(6)

    val recent = state.history
        .distinctBy { it.spotifyId.ifBlank { it.id } }
        .take(12)
    val frequent = state.frequent
        .distinctBy { it.spotifyId.ifBlank { it.id } }
        .take(12)

    // Rak kosong tetap diisi katalog lokal.
    val recentSource = if (recent.isNotEmpty() || filter == HomeFilter.RIWAYAT) recent else state.hits
    val frequentSource = if (frequent.isNotEmpty()) frequent else state.chill
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
            add(
                CardItem(
                    id = LibraryRepository.PLAYLIST_HISTORY,
                    title = "Riwayat Putar",
                    subtitle = "Playlist · lokal",
                    coverUrl = "",
                    onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_HISTORY) },
                ),
            )
        }
    }

    val carousels = buildList {
        if (filter == HomeFilter.SEMUA || filter == HomeFilter.RIWAYAT) {
            add(
                Carousel(
                    title = "Baru Saja Diputar",
                    items = recentSource.map { it.toCard(recentSource, onSongClick) },
                ),
            )
        }
        if (filter == HomeFilter.SEMUA || filter == HomeFilter.PLAYLIST) {
            add(Carousel(title = "Playlist Kamu", items = playlistCards))
        }
        if (filter == HomeFilter.SEMUA || filter == HomeFilter.MUSIK) {
            add(
                Carousel(
                    "Sering Kamu Putar",
                    frequentSource.map { it.toCard(frequentSource, onSongClick) },
                ),
            )
            add(Carousel("Hits Indonesia Hari Ini", state.hits.map { it.toCard(state.hits, onSongClick) }))
            add(Carousel("Rekomendasi Santai", state.chill.map { it.toCard(state.chill, onSongClick) }))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = greeting(),
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Dengerin lagu, tanpa drama iklan.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeFilter.entries.forEach { option ->
                    val selected = filter == option
                    Text(
                        text = when (option) {
                            HomeFilter.SEMUA -> "Semua"
                            HomeFilter.MUSIK -> "Musik"
                            HomeFilter.PLAYLIST -> "Playlist"
                            HomeFilter.RIWAYAT -> "Riwayat"
                        },
                        color = if (selected) colors.accent else colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, if (selected) colors.accent else colors.border, RoundedCornerShape(50))
                            .background(if (selected) colors.accentSoftContainer else colors.surfaceElevated)
                            .clickable { filter = option }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        item {
            Card(
                onClick = onOpenSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Mau denger apa hari ini?",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Cari  ›", color = colors.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = "Impor Playlist Spotify",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tempel link playlist publik—tanpa perlu masuk.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onImportLink,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.background,
                        ),
                    ) {
                        Text("Impor Link", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (shortcuts.isNotEmpty()) {
            item {
                SectionTitle("Akses Cepat")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shortcuts.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { shortcut ->
                                ShortcutCard(
                                    shortcut = shortcut,
                                    onClick = shortcut.onClick,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (state.isLoadingCatalog) {
            item {
                Text(
                    "Menyiapkan katalog musik…",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        carousels.forEach { carousel ->
            if (carousel.items.isNotEmpty()) {
                item {
                    SectionTitle(carousel.title)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        carousel.items.forEach { card ->
                            CatalogCard(card = card, isActive = card.id == playerState.song?.id)
                        }
                    }
                }
            }
        }

        if (state.catalogError != null) {
            item {
                Text(
                    text = state.catalogError.orEmpty(),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = text,
        color = colors.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ShortcutCard(
    shortcut: Shortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    Card(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shortcut.isLiked) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.accentSoftContainer, colors.accent),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = colors.background,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = upgradeThumbnailUrl(shortcut.coverUrl)
                        ?.takeIf { it.isNotBlank() }
                        ?: R.drawable.ic_music_placeholder,
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = shortcut.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatalogCard(card: CardItem, isActive: Boolean) {
    val colors = LocalDengerinColors.current
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, if (isActive) colors.accent else colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = card.onClick)
            .padding(8.dp),
    ) {
        AsyncImage(
            model = upgradeThumbnailUrl(card.coverUrl)
                ?.takeIf { it.isNotBlank() }
                ?: R.drawable.ic_music_placeholder,
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_music_placeholder),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            color = if (isActive) colors.accent else colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.subtitle,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Song.toCard(queue: List<Song>, onClick: (Song, List<Song>) -> Unit) = CardItem(
    id = id,
    title = title,
    subtitle = buildString {
        append(artist)
        if (durationMs > 0) append("  ·  ").append(formatDuration(durationMs))
    },
    coverUrl = upgradeThumbnailUrl(thumbnailUrl).orEmpty(),
    onClick = { onClick(this@toCard, queue) },
)

private data class Shortcut(
    val id: String,
    val title: String,
    val coverUrl: String,
    val isLiked: Boolean = false,
    val onClick: () -> Unit,
)

private data class CardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val onClick: () -> Unit,
)

private data class Carousel(val title: String, val items: List<CardItem>)

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..10 -> "Selamat Pagi"
        in 11..14 -> "Selamat Siang"
        in 15..17 -> "Selamat Sore"
        else -> "Selamat Malam"
    }
}
