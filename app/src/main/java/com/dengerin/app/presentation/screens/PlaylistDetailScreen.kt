package com.dengerin.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.PlaylistDetailViewModel
import com.dengerin.app.presentation.components.SongRow
import com.dengerin.app.presentation.components.rememberCoverGradient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    playerState: PlayerUiState,
    miniPlayer: @Composable () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    val tracks by viewModel.tracks.collectAsState()
    val info by viewModel.info.collectAsState()
    val gradient by rememberCoverGradient(info?.coverUrl.orEmpty())
    val title = info?.name ?: "Playlist"
    val coverUrl = info?.coverUrl.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
        bottomBar = {
            // Detail playlist tidak punya navigation bar, tapi tetap pakai mini player melayang.
            Column {
                miniPlayer()
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(padding),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface),
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
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${tracks.size} lagu",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val playable = viewModel.resolvedTracks()
                                    playable.firstOrNull()?.let { first -> onSongClick(first, playable) }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.background,
                            ),
                        ) {
                            Text("Putar Semua", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val playable = viewModel.resolvedTracks().shuffled()
                                    playable.firstOrNull()?.let { first -> onSongClick(first, playable) }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Acak")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, colors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (tracks.isEmpty()) {
                                Text(
                                    text = "Playlist ini masih kosong.",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                tracks.forEachIndexed { index, song ->
                                    SongRow(
                                        song = song,
                                        isActive = song.id == playerState.song?.id ||
                                            (song.spotifyId.isNotBlank() && song.spotifyId == playerState.song?.spotifyId),
                                        onClick = { onSongClick(song, tracks) },
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                    if (index != tracks.lastIndex) {
                                        Spacer(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(colors.border),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}
