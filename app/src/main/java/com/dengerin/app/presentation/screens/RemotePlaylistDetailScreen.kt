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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.model.songKey
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyCatalogTrack
import com.dengerin.app.data.spotify.SpotifyPublicPlaylist
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.rememberPlaylistCoverGradient
import com.dengerin.app.presentation.components.spotifyClickable
import kotlinx.coroutines.launch

@Composable
fun RemotePlaylistDetailScreen(
    playlistId: String,
    repository: SpotifyAccountImportRepository,
    libraryRepository: LibraryRepository,
    playerState: PlayerUiState,
    onClose: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    var playlist by remember { mutableStateOf<SpotifyPublicPlaylist?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val playlistDbId = remember(playlistId) { "spotify_$playlistId" }
    val saved by libraryRepository.isSaved(playlistDbId).collectAsState(initial = false)
    var saving by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true; error = null
            repository.fetchPublicPlaylist(playlistId)
                .onSuccess { playlist = it; loading = false }
                .onFailure { error = it.message ?: "Gagal memuat playlist"; loading = false }
        }
    }
    LaunchedEffect(playlistId) { load() }

    val songs = remember(playlist) { playlist?.tracks.orEmpty().map { it.toSong() } }
    val coverUrl = playlist?.coverUrl.orEmpty()
    val gradient = rememberPlaylistCoverGradient(playlistDbId, coverUrl)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().background(gradient.value).padding(bottom = 16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.2f))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(190.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.height(16.dp))
                    Text(playlist?.name.orEmpty().ifBlank { "Playlist" }, color = colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!playlist?.description.isNullOrBlank()) Text(playlist!!.description, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Dibuat oleh ${playlist?.ownerName?.ifBlank { "Spotify" } ?: "Spotify"} • ${playlist?.totalTracks ?: songs.size} lagu", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val p = playlist ?: return@IconButton
                    if (saved || saving) return@IconButton
                    saving = true
                    scope.launch {
                        libraryRepository.saveRemoteSpotifyPlaylist(p.id, p.name, p.coverUrl, p.tracks)
                        saving = false
                    }
                }) {
                    Icon(if (saved) Icons.Filled.Check else Icons.Filled.Add, contentDescription = if (saved) "Tersimpan" else "Simpan", tint = if (saved) colors.accent else colors.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (songs.isNotEmpty()) onPlaySong(songs.random(), songs.shuffled()) }) { Icon(Icons.Filled.Shuffle, contentDescription = "Acak", tint = colors.textSecondary) }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(colors.accent).spotifyClickable { if (songs.isNotEmpty()) onPlaySong(songs.first(), songs) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Putar", tint = colors.onAccent, modifier = Modifier.size(30.dp))
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
            itemsIndexed(songs, key = { _, s -> songKey(s) }, contentType = { _, _ -> "track_row" }) { _, song ->
                val isActive = song.id == playerState.song?.id || (song.spotifyId.isNotBlank() && song.spotifyId == playerState.song?.spotifyId)
                Row(modifier = Modifier.fillMaxWidth().spotifyClickable { onPlaySong(song, songs) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = song.thumbnailUrl.ifBlank { song.coverUrl }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, color = if (isActive) colors.accent else colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isActive && playerState.isPlaying) { SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(formatDuration(song.durationMs), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun SpotifyCatalogTrack.toSong() = Song(
    id = id.ifBlank { uri }, title = title, artist = artist, durationMs = durationMs,
    url = uri.takeIf { it.startsWith("http") }.orEmpty(),
    thumbnailUrl = thumbnailUrl, coverUrl = coverUrl, spotifyId = id, artistId = artistId, artistIds = artistIds, source = "SPOTIFY",
)
