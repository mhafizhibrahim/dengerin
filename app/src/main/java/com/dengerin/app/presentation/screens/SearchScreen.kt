package com.dengerin.app.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.SearchViewModel
import com.dengerin.app.presentation.components.SongRow

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playerState: PlayerUiState,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cari Lagu",
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Cari lagu favoritmu, langsung putar tanpa iklan.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))

        TextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            placeholder = {
                Text("Cari judul atau artis…", color = colors.textSecondary)
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Cari", tint = colors.textSecondary)
            },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus teks",
                            tint = colors.textSecondary,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceElevated,
                unfocusedContainerColor = colors.surfaceElevated,
                disabledContainerColor = colors.surfaceElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedLeadingIconColor = colors.textSecondary,
                unfocusedLeadingIconColor = colors.textSecondary,
                focusedTrailingIconColor = colors.textSecondary,
                unfocusedTrailingIconColor = colors.textSecondary,
            ),
        )

        Spacer(Modifier.height(16.dp))

        when {
            ui.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent)
                        Spacer(Modifier.height(12.dp))
                        Text("Lagi nyari lagu…", color = colors.textSecondary)
                    }
                }
            }

            ui.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = ui.error.orEmpty(),
                            color = colors.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::retry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }

            ui.songs.isNotEmpty() -> {
                LazyColumn(
                    // innerPadding dari Scaffold utama (mini player + navigation bar).
                    modifier = modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                ) {
                    items(items = ui.songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isActive = song.id == playerState.song?.id,
                            onClick = { onSongClick(song) },
                        )
                    }
                }
            }

            ui.hasSearched -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Belum ada lagu ketemu.\nCoba kata kunci lain ya.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = colors.border,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Ketik lagu yang mau kamu dengerin,\n lalu tekan Search.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
