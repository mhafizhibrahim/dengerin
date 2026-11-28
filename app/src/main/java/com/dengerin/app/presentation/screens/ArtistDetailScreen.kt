package com.dengerin.app.presentation.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.core.cardImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyArtistOverview
import com.dengerin.app.data.spotify.SpotifyArtistOverviewTrack
import com.dengerin.app.data.spotify.SpotifyArtistRelatedPlaylist
import com.dengerin.app.data.spotify.SpotifyArtistRelease
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.components.SongMenuSheet
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.spotifyClickable
import kotlinx.coroutines.launch

@Composable
fun ArtistDetailScreen(
    artistId: String,
    artistName: String,
    repository: SpotifyAccountImportRepository,
    playerState: PlayerUiState,
    onClose: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onOpenArtist: ((String?, String) -> Unit)? = null,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<SpotifyArtistOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var following by remember(artistId, artistName) { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<Song?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            repository.fetchArtistOverview(artistId.ifBlank { null }, artistName)
                .onSuccess { overview = it; loading = false }
                .onFailure { error = it.message ?: "Gagal memuat artis"; loading = false }
        }
    }
    LaunchedEffect(artistId, artistName) { load() }

    val songs = remember(overview) { overview?.topTracks.orEmpty().map { it.toSong() } }
    val canPlay = songs.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                val header = overview?.headerImageUrl.orEmpty().ifBlank { overview?.avatarUrl.orEmpty() }
                if (header.isNotBlank()) {
                    AsyncImage(model = header, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(colors.surfaceElevated))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), colors.background.copy(alpha = 0.95f)))))
                IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(overview?.name?.ifBlank { artistName } ?: artistName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!overview?.monthlyListeners.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(overview!!.monthlyListeners, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) } }
        } else if (error != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(error ?: "Gagal memuat", color = colors.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Coba lagi",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.spotifyClickable { load() },
                    )
                }
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surfaceElevated).spotifyClickable { following = !following }.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(if (following) "Mengikuti" else "Ikuti", color = colors.textPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.background).padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onPlaySong(songs.random(), songs.shuffled()) },
                        enabled = canPlay,
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Acak",
                            tint = colors.textSecondary.copy(alpha = if (canPlay) 1f else 0.38f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .alpha(if (canPlay) 1f else 0.38f)
                            .spotifyClickable(enabled = canPlay) { onPlaySong(songs.first(), songs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Putar", tint = colors.onAccent, modifier = Modifier.size(30.dp))
                    }
                }
            }
            if (!canPlay) {
                item {
                    Text(
                        text = "Belum ada lagu yang tersedia untuk artis ini",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
            if (songs.isNotEmpty()) {
                item { Text("Populer", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                itemsIndexed(overview?.topTracks.orEmpty().take(5), key = { _, t -> "top_${t.id.ifBlank { t.title }}" }, contentType = { _, _ -> "track_row" }) { index, track ->
                    val song = track.toSong()
                    val isActive = song.id == playerState.song?.id || (song.spotifyId.isNotBlank() && song.spotifyId == playerState.song?.spotifyId)
                    Row(modifier = Modifier.fillMaxWidth().spotifyClickable { onPlaySong(song, songs) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = colors.textSecondary, modifier = Modifier.width(24.dp))
                        AsyncImage(model = track.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = if (isActive) colors.accent else colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(track.playcount.takeIf { it.isNotBlank() }, formatDuration(track.durationMs)).filterNotNull().joinToString(" • "), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        if (isActive && playerState.isPlaying) SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
                        IconButton(onClick = { menuSong = song }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opsi untuk ${song.title}", tint = colors.textSecondary) }
                    }
                }
            }
            if (overview?.releases.orEmpty().isNotEmpty()) {
                item { Text("Rilis Populer", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(overview!!.releases, key = { _, r -> "rel_${r.id}" }, contentType = { _, _ -> "release_card" }) { _, release ->
                            ReleaseCard(release)
                        }
                    }
                }
            }
            if (overview?.featuringPlaylists.orEmpty().isNotEmpty()) {
                item { Text("Menampilkan ${overview?.name ?: artistName}", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(overview!!.featuringPlaylists, key = { _, p -> "feat_${p.id}" }, contentType = { _, _ -> "playlist_card" }) { _, pl ->
                            FeaturingPlaylistCard(pl, onClick = { onOpenPlaylist(pl.id) })
                        }
                    }
                }
            }
        }
    }
    menuSong?.let { selected ->
        SongMenuSheet(
            song = selected,
            isLiked = false,
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
            onOpenArtist = onOpenArtist,
        )
    }
    }
}

private fun SpotifyArtistOverviewTrack.toSong() = Song(
    id = id, title = title, artist = artist, durationMs = durationMs, url = uri.takeIf { it.startsWith("http") }.orEmpty(),
    thumbnailUrl = coverUrl, coverUrl = coverUrl, spotifyId = id, artistId = artistId, artistIds = artistIds, source = "SPOTIFY"
)

@Composable
private fun ReleaseCard(release: SpotifyArtistRelease) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.width(140.dp)) {
        AsyncImage(model = release.coverUrl, contentDescription = release.name, contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(8.dp))
        Text(release.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(release.year, release.type).filter { it.isNotBlank() }.joinToString(" · "), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun FeaturingPlaylistCard(playlist: SpotifyArtistRelatedPlaylist, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.width(140.dp).spotifyClickable(onClick = onClick)) {
        AsyncImage(model = playlist.coverUrl, contentDescription = playlist.name, contentScale = ContentScale.Crop, modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Oleh ${playlist.ownerName.ifBlank { "Spotify" }}", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}
