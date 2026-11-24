package com.dengerin.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.SearchViewModel
import com.dengerin.app.presentation.components.SongRow

private data class MusicCategory(val title: String, val query: String, val variant: Int)

private val CATEGORIES = listOf(
    MusicCategory("Pop Indonesia", "Pop Indonesia", 0),
    MusicCategory("Indie & Senja", "Indie Senja", 1),
    MusicCategory("R&B & Chill", "R&B Chill", 2),
    MusicCategory("K-Pop Hits", "K-Pop Hits", 3),
    MusicCategory("Dangdut & Koplo", "Dangdut Koplo", 4),
    MusicCategory("Rock & Alternatif", "Rock Alternatif", 5),
    MusicCategory("Lo-Fi & Fokus", "Lo-Fi Fokus", 6),
    MusicCategory("Hits Global", "Hits Global", 7),
)

private val HASHTAGS = listOf(
    "#pop viral indonesia" to "pop viral indonesia",
    "#r&b indonesia" to "r&b indonesia",
    "#galau malem" to "galau malem",
    "#semangat pagi" to "semangat pagi",
    "#akustik santai" to "akustik santai",
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playerState: PlayerUiState,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    val pickQuery: (String) -> Unit = { keyword -> viewModel.prefillAndSearch(keyword) }

    Column(
        modifier = modifier
            .fillMaxSize()
            // DengerinApp sengaja tidak mengirim innerPadding atas untuk tab ini,
            // jadi status bar ditangani eksplisit di sini (edge-to-edge aman).
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Cari",
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Temukan lagu favoritmu, langsung putar tanpa iklan.",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        SearchBar(
            query = ui.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = { viewModel.search() },
        )
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (ui.query.isBlank()) {
                ExploreContent(
                    recentQueries = ui.recentQueries,
                    onPickQuery = pickQuery,
                )
            } else when {
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
                            ) { Text("Coba Lagi") }
                        }
                    }
                }

                ui.songs.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(items = ui.songs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                isActive = song.id == playerState.song?.id,
                                onClick = { onSongClick(song) },
                                onAddToPlaylist = onAddToPlaylist,
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
                                text = "Tekan Search untuk mulai nyari lagu.",
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
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(colors.surfaceElevated)
            .border(1.dp, if (focused) colors.accent else colors.border, shape),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            shape = shape,
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            placeholder = {
                Text(
                    text = "Apa yang ingin kamu dengarkan?",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Cari",
                    tint = colors.textSecondary,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus teks",
                            tint = colors.textSecondary,
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
                focusedLeadingIconColor = colors.textSecondary,
                unfocusedLeadingIconColor = colors.textSecondary,
                focusedTrailingIconColor = colors.textSecondary,
                unfocusedTrailingIconColor = colors.textSecondary,
            ),
        )
    }
}

@Composable
private fun ExploreContent(
    recentQueries: List<String>,
    onPickQuery: (String) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val gradients = exploreGradients()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recentQueries.isNotEmpty()) {
            item {
                SectionLabel("Pencarian Terakhir")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentQueries.take(5).forEach { keyword ->
                        Text(
                            text = keyword,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, colors.border, RoundedCornerShape(50))
                                .background(colors.surfaceElevated)
                                .clickable { onPickQuery(keyword) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionLabel("Jelajahi Semua")
            CATEGORIES.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { category ->
                        CategoryCard(
                            category = category,
                            brush = gradients[category.variant % gradients.size],
                            onClick = { onPickQuery(category.query) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        item {
            SectionLabel("Temukan sesuatu yang lain")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HASHTAGS.forEachIndexed { index, (label, query) ->
                    HashtagCard(
                        label = label,
                        brush = gradients[(index + 2) % gradients.size],
                        onClick = { onPickQuery(query) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = text,
        color = colors.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CategoryCard(
    category: MusicCategory,
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(shape)
            .background(brush)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = category.title,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = colors.textPrimary.copy(alpha = 0.72f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .rotate(22f),
        )
    }
}

@Composable
private fun HashtagCard(
    label: String,
    brush: Brush,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(172.dp)
            .clip(shape)
            .background(brush)
            .border(1.dp, colors.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = colors.textPrimary.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .rotate(22f),
        )
        Text(
            text = label,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** Gradien diracik dari token Dengerin, bukan warna baru: tetap Dark Slate + aksen. */
@Composable
private fun exploreGradients(): List<Brush> {
    val colors = LocalDengerinColors.current
    return listOf(
        listOf(colors.surfaceElevated, colors.accent.copy(alpha = 0.42f)),
        listOf(colors.surfaceElevated, colors.textSecondary.copy(alpha = 0.42f)),
        listOf(colors.surface, colors.accent.copy(alpha = 0.30f)),
        listOf(colors.surfaceElevated, colors.border.copy(alpha = 0.90f)),
        listOf(colors.surface, colors.accentSoftContent.copy(alpha = 0.36f)),
        listOf(colors.surfaceElevated, colors.textPrimary.copy(alpha = 0.16f)),
        listOf(colors.surface, colors.accent.copy(alpha = 0.22f)),
        listOf(colors.surfaceElevated, colors.accentSoftContainer, colors.border),
    ).map { Brush.linearGradient(it) }
}
