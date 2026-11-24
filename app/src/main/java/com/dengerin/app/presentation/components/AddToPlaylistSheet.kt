package com.dengerin.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.data.model.Song
import com.dengerin.app.presentation.AddToPlaylistViewModel

/** Bottom sheet "Tambah ke playlist" — satu tempat untuk semua lagu di aplikasi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song?,
    viewModel: AddToPlaylistViewModel?,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (song == null || viewModel == null) return
    val colors = LocalDengerinColors.current
    val state by viewModel.uiState.collectAsState()
    var showCreateInput by remember(song.id) { mutableStateOf(false) }
    var playlistName by remember(song.id) { mutableStateOf("") }
    val sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = sheetShape,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // Border atas 1dp mengikuti token; ModalBottomSheet sudah memotong sudut 20dp.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.border),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.border.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = upgradeThumbnailUrl(song.thumbnailUrl)
                        ?.takeIf { it.isNotBlank() }
                        ?: R.drawable.ic_music_placeholder,
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tambah ke playlist",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = song.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showCreateInput = !showCreateInput },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentSoftContainer,
                    contentColor = colors.accent,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("+ Buat Playlist Baru", fontWeight = FontWeight.SemiBold)
            }

            if (showCreateInput) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = {
                        Text("Nama playlist", color = colors.textSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accent,
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showCreateInput = false }) {
                        Text("Batal", color = colors.textSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            viewModel.createPlaylistAndAdd(playlistName) { message ->
                                playlistName = ""
                                showCreateInput = false
                                onFeedback(message)
                            }
                        },
                        enabled = playlistName.isNotBlank() && !state.isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.background,
                        ),
                    ) { Text("Buat & Tambah") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isBusy) { viewModel.toggleLiked(onFeedback) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (state.isLiked) colors.accent else colors.textSecondary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lagu Disukai",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Simpan ke koleksi suka kamu",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.isLiked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Sudah disukai",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Playlist Lokal",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                if (state.playlists.isEmpty()) {
                    item {
                        Text(
                            text = "Belum ada playlist. Buat yang pertama lewat tombol di atas ya.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
                items(items = state.playlists, key = { it.playlist.id }) { option ->
                    Card(
                        onClick = { viewModel.togglePlaylist(option, onFeedback) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated),
                        border = BorderStroke(1.dp, colors.border),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.playlist.name,
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${option.songCount} lagu",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (option.containsSong) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Sudah ada di playlist",
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Tambah ke playlist",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
