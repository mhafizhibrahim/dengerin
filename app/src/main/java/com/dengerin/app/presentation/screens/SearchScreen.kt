package com.dengerin.app.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.LocalCachedTrackIds
import com.dengerin.app.core.LocalLikedTrackIds
import com.dengerin.app.core.genreCoverImageRequest
import com.dengerin.app.core.isLikedIn
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.DarkDengerinColors
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatDuration
import com.dengerin.app.data.genre.GenreCatalog
import com.dengerin.app.data.genre.GenreCategory
import com.dengerin.app.data.genre.backgroundColor
import com.dengerin.app.data.lyrics.LyricSearchHit
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.model.songKey
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.spotify.SpotifyCatalogArtist
import com.dengerin.app.data.spotify.SpotifyCatalogPlaylist
import com.dengerin.app.data.spotify.SpotifyCatalogTopResult
import com.dengerin.app.data.spotify.SpotifyCatalogTrack
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.SearchFilter
import com.dengerin.app.presentation.SearchUiState
import com.dengerin.app.presentation.SearchViewModel
import com.dengerin.app.presentation.components.ArtistNameText
import com.dengerin.app.presentation.components.ProfileAvatarButton
import com.dengerin.app.presentation.components.SpotifyEqualizer
import com.dengerin.app.presentation.components.SpotifyLikeIcon
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.components.spotifyClickable

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
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onPlayLyricMatch: (LyricSearchHit) -> Unit,
    focusRequester: FocusRequester,
    focusSignal: Int = 0,
    profile: UserProfile? = null,
    onOpenDrawer: () -> Unit = {},
    onOpenArtist: (artistId: String, artistName: String) -> Unit = { _, _ -> },
    onOpenRemotePlaylist: (playlistId: String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    val focusManager = LocalFocusManager.current
    val activeGenre = ui.activeGenre

    // Jangan pernah requestFocus() dari sini: saat detail genre aktif, SearchBar
    // tidak di-compose sehingga FocusRequester tidak punya node terpasang dan
    // requestFocus() melempar IllegalStateException.
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) viewModel.activateSearch()
    }
    LaunchedEffect(ui.query, ui.activeGenreId) {
        runCatching { if (listState.canScrollBackward) listState.animateScrollToItem(0) }
    }
    BackHandler(enabled = activeGenre != null) {
        viewModel.closeGenre()
    }
    BackHandler(enabled = activeGenre == null && ui.isSearchActive) {
        focusManager.clearFocus()
        viewModel.closeSearch()
    }

    if (activeGenre != null) {
        GenreDetailScreen(
            genre = activeGenre,
            coverUrl = ui.genreCovers[activeGenre.id],
            isFetching = activeGenre.id in ui.genreFetching,
            songs = ui.genreSongs,
            isLoading = ui.genreLoading,
            error = ui.genreError,
            playerState = playerState,
            listState = listState,
            onBack = viewModel::closeGenre,
            onRetry = viewModel::retry,
            onSongClick = onSongClick,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onToggleLike = onToggleLike,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        if (ui.isSearchActive) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.closeSearch()
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali ke Jelajahi Semua",
                        tint = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                SearchBar(
                    query = ui.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.submitSearch()
                    },
                    onActivate = viewModel::activateSearch,
                    focusRequester = focusRequester,
                    focusSignal = focusSignal,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarButton(profile = profile, onClick = onOpenDrawer, size = 32.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Cari",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(14.dp))
            SearchBar(
                query = ui.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                },
                onActivate = viewModel::activateSearch,
                focusRequester = focusRequester,
                focusSignal = focusSignal,
            )
        }
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                ui.isSearchActive && ui.query.isBlank() -> RecentSearchContent(
                    recentQueries = ui.recentQueries,
                    onPick = { keyword ->
                        focusManager.clearFocus()
                        viewModel.prefillAndSearch(keyword)
                    },
                    onRemove = viewModel::removeRecent,
                    listState = listState,
                )

                ui.query.isBlank() -> ExploreContent(
                    recentQueries = ui.recentQueries,
                    genreCovers = ui.genreCovers,
                    genreFetching = ui.genreFetching,
                    onPickQuery = viewModel::prefillAndSearch,
                    onGenreClick = viewModel::openGenre,
                    listState = listState,
                )

                else -> LiveResultsContent(
                    ui = ui,
                    playerState = playerState,
                    listState = listState,
                    onGenreClick = viewModel::openGenre,
                    onFilterSelect = viewModel::selectFilter,
                    onOpenArtist = onOpenArtist,
                    onOpenRemotePlaylist = onOpenRemotePlaylist,
                    onUseSuggestion = { text, keepKeyboard ->
                        viewModel.useSuggestion(text, saveHistory = !keepKeyboard)
                        if (!keepKeyboard) focusManager.clearFocus()
                    },
                    onPlayLyricMatch = onPlayLyricMatch,
                    onSongClick = onSongClick,
                    onAddToPlaylist = onAddToPlaylist,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onToggleLike = onToggleLike,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onActivate: () -> Unit,
    focusRequester: FocusRequester,
    focusSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    // Dark memakai surfaceElevated (#282828), Light memakai surface (putih), tanpa border.
    val isDark = colors.background == DarkDengerinColors.background
    val fieldColor = if (isDark) colors.surfaceElevated else colors.surface

    LaunchedEffect(focused) {
        if (focused) onActivate()
    }
    // Hanya minta fokus ketika TextField benar-benar terpasang di komposisi ini.
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(fieldColor),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
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
                    Icons.Outlined.Search,
                    contentDescription = "Cari",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus teks",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
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
    genreCovers: Map<String, String>,
    genreFetching: Set<String>,
    onPickQuery: (String) -> Unit,
    onGenreClick: (GenreCategory) -> Unit,
    listState: LazyListState,
) {
    val colors = LocalDengerinColors.current
    val gradients = exploreGradients()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                .background(colors.surfaceElevated)
                                .spotifyClickable { onPickQuery(keyword) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionLabel("Jelajahi Semua")
        }
        itemsIndexed(GenreCatalog.all.chunked(2)) { _, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { category ->
                    CategoryCard(
                        category = category,
                        coverUrl = genreCovers[category.id],
                        isFetching = category.id in genreFetching,
                        onClick = { onGenreClick(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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

/** Query kosong di Active Search: riwayat DataStore dengan tombol hapus per item. */
@Composable
private fun RecentSearchContent(
    recentQueries: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    listState: LazyListState,
) {
    val colors = LocalDengerinColors.current
    val safeRecent = remember(recentQueries) { recentQueries.toList() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { SectionLabel("Pencarian terkini") }
        if (safeRecent.isEmpty()) {
            item {
                Text(
                    text = "Belum ada pencarian terkini. Coba ketik lagu atau lirik favoritmu.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        itemsIndexed(
            items = safeRecent,
            key = { _, keyword -> "recent_$keyword" },
            contentType = { _, _ -> "recent_query_row" },
        ) { _, keyword ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(keyword) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = keyword,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(keyword) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Hapus $keyword dari riwayat",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
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
    category: GenreCategory,
    coverUrl: String?,
    isFetching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(4.dp)
    val coverShape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .height(100.dp)
            .clip(shape)
            .clipToBounds()
            .background(category.backgroundColor())
            .spotifyClickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = category.title,
            color = colors.onDarkSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Sampul miring ala Spotify: sengaja keluar kanan-bawah lalu dipotong kartu.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(68.dp)
                .offset(x = 12.dp, y = 8.dp)
                .graphicsLayer { rotationZ = 25f }
                .clip(coverShape)
                .background(colors.onDarkSurface.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                coverUrl != null -> AsyncImage(
                    model = remember(coverUrl) { genreCoverImageRequest(context, coverUrl) },
                    contentDescription = "Sampul ${category.title}",
                    placeholder = darkSlatePlaceholder(),
                    error = painterResource(R.drawable.ic_music_placeholder),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(coverShape),
                )

                isFetching -> CircularProgressIndicator(
                    color = colors.onDarkSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )

                else -> Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = colors.onDarkSurface.copy(alpha = 0.62f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun HashtagCard(
    label: String,
    brush: Brush,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(172.dp)
            .clip(shape)
            .background(brush)
            .spotifyClickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = colors.textPrimary.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .graphicsLayer { rotationZ = 22f },
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

/** A–C: saran teks → kartu lirik → pintasan genre + lagu langsung. */
@Composable
private fun LiveResultsContent(
    ui: SearchUiState,
    playerState: PlayerUiState,
    listState: LazyListState,
    onGenreClick: (GenreCategory) -> Unit,
    onFilterSelect: (SearchFilter) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onOpenRemotePlaylist: (String) -> Unit,
    onUseSuggestion: (String, Boolean) -> Unit,
    onPlayLyricMatch: (LyricSearchHit) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val safeSuggestions = remember(ui.suggestions) { ui.suggestions.take(5).toList() }
    val safeLyricMatches = remember(ui.lyricMatches) { ui.lyricMatches.toList() }
    val safeSongs = remember(ui.songs) { ui.songs.toList() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchFilter.entries.forEach { filter ->
                    val selected = ui.selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) colors.accent else colors.surfaceElevated)
                            .spotifyClickable { onFilterSelect(filter) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = filter.label,
                            color = if (selected) colors.onAccent else colors.textPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Bagian A — saran autocomplete (maksimal 5).
        itemsIndexed(
            items = safeSuggestions,
            key = { _, suggestion -> "suggestion_$suggestion" },
            contentType = { _, _ -> "suggestion_row" },
        ) { _, suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                // false = ketuk teks (simpan riwayat + tutup keyboard); true = panah ↖.
                onTextClick = { onUseSuggestion(suggestion, false) },
                onArrowClick = { onUseSuggestion(suggestion, true) },
            )
        }

        // Bagian B — lagu yang liriknya cocok (maksimal 2).
        if (safeLyricMatches.isNotEmpty()) {
            item { SectionLabel("Cocok dengan lirik") }
            itemsIndexed(
                items = safeLyricMatches,
                key = { _, hit -> "lyric_${hit.resolverQuery}" },
                contentType = { _, _ -> "lyric_match_row" },
            ) { _, hit ->
                LyricMatchCard(hit = hit, onClick = { onPlayLyricMatch(hit) })
            }
        }

        // Bagian C — genre lokal lalu lagu NewPipe.
        if (ui.matchedGenres.isNotEmpty()) {
            item {
                SectionLabel("Genre & Mood")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ui.matchedGenres.forEach { genre ->
                        GenreShortcutCard(genre = genre, onClick = { onGenreClick(genre) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        val showLoading = (ui.isLoading || ui.catalogLoading) && safeSongs.isEmpty() && ui.artistResults.isEmpty() && ui.playlistResults.isEmpty()
        if (showLoading) {
            item {
                Row(modifier = Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Lagi nyari...", color = colors.textSecondary)
                }
            }
        }
        if (ui.error != null && safeSongs.isEmpty() && ui.artistResults.isEmpty() && ui.playlistResults.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(text = ui.error.orEmpty(), color = colors.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) { Text("Coba Lagi") }
                }
            }
        }
        val showTop = ui.selectedFilter == SearchFilter.ALL && ui.topResult != null
        if (showTop) {
            item { SectionLabel("Hasil Teratas") }
            item { TopResultCard(topResult = ui.topResult, onOpenArtist = onOpenArtist, onOpenPlaylist = onOpenRemotePlaylist, onPlayTrack = { onSongClick(catalogTrackToSong(it)) }) }
        }
        val showSongs = ui.selectedFilter == SearchFilter.ALL || ui.selectedFilter == SearchFilter.SONGS
        val showArtists = ui.selectedFilter == SearchFilter.ALL || ui.selectedFilter == SearchFilter.ARTISTS
        val showPlaylists = ui.selectedFilter == SearchFilter.ALL || ui.selectedFilter == SearchFilter.PLAYLISTS
        if (showSongs && safeSongs.isNotEmpty()) {
            if (ui.selectedFilter == SearchFilter.ALL) item { SectionLabel("Lagu") }
            itemsIndexed(safeSongs, key = { _, song -> songKey(song) }, contentType = { _, _ -> "track_row" }) { _, song ->
                LiveSongRow(song = song, isActive = song.id == playerState.song?.id, isPlaying = playerState.isPlaying, onClick = { onSongClick(song) }, onAddToPlaylist = onAddToPlaylist, onAddToQueue = onAddToQueue, onPlayNext = onPlayNext, onToggleLike = onToggleLike, onOpenArtist = onOpenArtist)
            }
        }
        if (showArtists && ui.artistResults.isNotEmpty()) {
            item { SectionLabel("Artis") }
            if (ui.selectedFilter == SearchFilter.ALL) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ui.artistResults.take(8).forEach { artist -> ArtistCircleCard(artist = artist, onClick = { onOpenArtist(artist.id, artist.name) }) }
                    }
                }
            } else {
                itemsIndexed(ui.artistResults, key = { _, a -> "artist_${a.id}" }, contentType = { _, _ -> "artist_row" }) { _, artist -> ArtistListRow(artist = artist, onClick = { onOpenArtist(artist.id, artist.name) }) }
            }
        }
        if (showPlaylists && ui.playlistResults.isNotEmpty()) {
            item { SectionLabel("Playlist") }
            itemsIndexed(ui.playlistResults, key = { _, p -> "playlist_${p.id}" }, contentType = { _, _ -> "playlist_row" }) { _, playlist -> PlaylistListRow(playlist = playlist, onClick = { onOpenRemotePlaylist(playlist.id) }) }
        }
        if (!showLoading && ui.error == null && ui.lastQuery.isNotBlank() && safeSongs.isEmpty() && ui.artistResults.isEmpty() && ui.playlistResults.isEmpty()) {
            item { Text(text = "Belum ada hasil untuk kata kunci ini.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
        }

    }
}

private fun catalogTrackToSong(track: SpotifyCatalogTrack): Song = Song(
    id = track.id, title = track.title, artist = track.artist, durationMs = track.durationMs,
    url = track.uri.takeIf { it.startsWith("http") }.orEmpty(),
    thumbnailUrl = track.thumbnailUrl, coverUrl = track.coverUrl,
    spotifyId = track.id, artistId = track.artistId, artistIds = track.artistIds, source = "SPOTIFY",
)

@Composable
private fun TopResultCard(topResult: SpotifyCatalogTopResult, onOpenArtist: (String, String) -> Unit, onOpenPlaylist: (String) -> Unit, onPlayTrack: (SpotifyCatalogTrack) -> Unit) {
    val colors = LocalDengerinColors.current
    when (topResult) {
        is SpotifyCatalogTopResult.Artist -> {
            val a = topResult.item
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceElevated).spotifyClickable { onOpenArtist(a.id, a.name) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = a.avatarUrl, contentDescription = a.name, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(CircleShape))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(a.name, color = colors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surface).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("Artis", color = colors.textPrimary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        is SpotifyCatalogTopResult.Playlist -> {
            val p = topResult.item
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceElevated).spotifyClickable { onOpenPlaylist(p.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = p.coverUrl, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.name, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    Text(p.ownerName.ifBlank { "Playlist" }, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surface).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("Playlist", color = colors.textPrimary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
        is SpotifyCatalogTopResult.Track -> {
            val t = topResult.item
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceElevated).spotifyClickable { onPlayTrack(t) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = t.coverUrl.ifBlank { t.thumbnailUrl }, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(t.title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(4.dp))
                    ArtistNameText(
                        artist = t.artist,
                        artistId = t.artistId,
                        artistIds = t.artistIds,
                        onOpenArtist = { id, name -> onOpenArtist(id.orEmpty(), name) },
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.surface).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("Lagu", color = colors.textPrimary, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

@Composable
private fun ArtistCircleCard(artist: SpotifyCatalogArtist, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Column(modifier = Modifier.width(96.dp).spotifyClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(model = artist.avatarUrl, contentDescription = artist.name, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(artist.name, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("Artis", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ArtistListRow(artist: SpotifyCatalogArtist, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Row(modifier = Modifier.fillMaxWidth().spotifyClickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = artist.avatarUrl, contentDescription = artist.name, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(CircleShape))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(artist.name, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Artis", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlaylistListRow(playlist: SpotifyCatalogPlaylist, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Row(modifier = Modifier.fillMaxWidth().spotifyClickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = playlist.coverUrl, contentDescription = playlist.name, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist · ${playlist.ownerName.ifBlank { "Spotify" }}", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    onTextClick: () -> Unit,
    onArrowClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onTextClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = suggestion,
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onArrowClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.NorthWest,
                contentDescription = "Isi kolom dengan \"$suggestion\"",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LyricMatchCard(
    hit: LyricSearchHit,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accentSoftContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FormatQuote,
                contentDescription = null,
                tint = colors.accentSoftContent,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.trackName,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hit.artistName,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Cocok dengan lirik",
                    color = colors.accentSoftContent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentSoftContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "“${hit.snippet}”",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GenreShortcutCard(
    genre: GenreCategory,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .width(136.dp)
            .height(72.dp)
            .clip(shape)
            .background(genre.backgroundColor())
            .spotifyClickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = genre.title,
            color = colors.onDarkSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Baris lagu live: 48 dp thumbnail, judul 15 sp, "Lagu • Artis", + dan ⋮. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveSongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    val likedIds = LocalLikedTrackIds.current
    val isLiked = song.isLikedIn(likedIds)
    val isCached = song.id in cachedIds
    val isCurrentlyPlaying = isActive && isPlaying
    var expanded by remember { mutableStateOf(false) }
    val coverModel = remember(song.id, song.displayThumbnailUrl) {
        songListImageRequest(context, song)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = if (isCached) {
                "Sampul ${song.title}, tersedia offline"
            } else {
                null
            },
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isActive) colors.accent else colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isCurrentlyPlaying) Modifier.basicMarquee() else Modifier,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lagu • ",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    maxLines = 1,
                )
                ArtistNameText(
                    artist = song.artist,
                    artistId = song.artistId,
                    artistIds = song.artistIds,
                    onOpenArtist = { id, name -> onOpenArtist(id.orEmpty(), name) },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        if (isCurrentlyPlaying) {
            SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { onToggleLike(song) }, modifier = Modifier.size(34.dp)) {
            SpotifyLikeIcon(liked = isLiked, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Opsi untuk ${song.title}",
                    tint = colors.textSecondary,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Lihat artis", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onOpenArtist(song.artistId, song.artist)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Putar berikutnya", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPlayNext(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tambahkan ke antrean", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AddToQueue,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAddToQueue(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tambah ke playlist", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAddToPlaylist(song)
                    },
                )
            }
        }
    }
}

@Composable
private fun GenreDetailScreen(
    genre: GenreCategory,
    coverUrl: String?,
    isFetching: Boolean,
    songs: List<Song>,
    isLoading: Boolean,
    error: String?,
    playerState: PlayerUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val headerBrush = Brush.verticalGradient(listOf(genre.backgroundColor(), colors.background))
    val safeSongs = remember(songs) { songs.toList() }

    Box(modifier = modifier.fillMaxSize().background(headerBrush)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = colors.onDarkSurface,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.onDarkSurface.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val context = LocalContext.current
                    if (coverUrl != null) {
                        AsyncImage(
                            model = remember(coverUrl) { genreCoverImageRequest(context, coverUrl) },
                            contentDescription = "Sampul ${genre.title}",
                            placeholder = darkSlatePlaceholder(),
                            error = painterResource(R.drawable.ic_music_placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                        )
                    } else if (isFetching) {
                        CircularProgressIndicator(
                            color = colors.onDarkSurface,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = colors.onDarkSurface.copy(alpha = 0.62f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = genre.title,
                        color = colors.onDarkSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(songs.size).append(" lagu")
                            if (genre.searchQuery.isNotBlank()) append("  ·  ").append(genre.searchQuery)
                        },
                        color = colors.onDarkSurface.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            when {
                isLoading && safeSongs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = colors.accent)
                            Spacer(Modifier.height(12.dp))
                            Text("Lagi nyari lagu…", color = colors.onDarkSurface)
                        }
                    }
                }

                error != null && safeSongs.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = error,
                                color = colors.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
                                ),
                            ) { Text("Coba Lagi") }
                        }
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(bottom = LocalBottomClearance.current),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (safeSongs.isEmpty()) {
                        item {
                            Text(
                                text = "Belum ada lagu ketemu untuk genre ini.",
                                color = colors.onDarkSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        items = safeSongs,
                        key = { _, song -> songKey(song) },
                        contentType = { _, _ -> "track_row" },
                    ) { _, song ->
                        GenreSongRow(
                            song = song,
                            isActive = song.id == playerState.song?.id,
                            isPlaying = playerState.isPlaying,
                            onClick = { onSongClick(song) },
                            onAddToPlaylist = onAddToPlaylist,
                            onAddToQueue = onAddToQueue,
                            onPlayNext = onPlayNext,
                            onToggleLike = onToggleLike,
                        )
                    }
                }
            }
        }
    }
}

/** Baris lagu hasil genre: thumbnail mqdefault 52 dp + menu ⋮ Tambah ke antrean. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenreSongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val cachedIds = LocalCachedTrackIds.current
    val likedIds = LocalLikedTrackIds.current
    val isLiked = song.isLikedIn(likedIds)
    val isCached = song.id in cachedIds
    val isCurrentlyPlaying = isActive && isPlaying
    var expanded by remember { mutableStateOf(false) }
    val coverModel = remember(song.id, song.displayThumbnailUrl) {
        songListImageRequest(context, song)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = if (isCached) {
                "Sampul ${song.title}, tersedia offline"
            } else {
                null
            },
            placeholder = darkSlatePlaceholder(),
            error = painterResource(R.drawable.ic_music_placeholder),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isActive) colors.accent else colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isCurrentlyPlaying) Modifier.basicMarquee() else Modifier,
            )
            Text(
                text = buildString {
                    append(song.artist)
                    if (song.durationMs > 0) append("  ·  ").append(formatDuration(song.durationMs))
                },
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onToggleLike(song) }, modifier = Modifier.size(34.dp)) {
            SpotifyLikeIcon(liked = isLiked, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Opsi untuk ${song.title}",
                    tint = colors.textSecondary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Putar berikutnya", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPlayNext(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tambahkan ke antrean", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AddToQueue,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAddToQueue(song)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tambah ke playlist", color = colors.textPrimary) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onAddToPlaylist(song)
                    },
                )
            }
        }
        if (isActive) {
            Spacer(Modifier.width(6.dp))
            if (isPlaying) {
                SpotifyEqualizer(playing = true, modifier = Modifier.size(14.dp))
            } else {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
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
