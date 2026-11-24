package com.dengerin.app.presentation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.components.AddToPlaylistSheet
import com.dengerin.app.presentation.components.MiniPlayer
import com.dengerin.app.presentation.screens.FullscreenLyricsScreen
import com.dengerin.app.presentation.screens.HomeScreen
import com.dengerin.app.presentation.screens.LibraryScreen
import com.dengerin.app.presentation.screens.NowPlayingScreen
import com.dengerin.app.presentation.screens.PlaylistDetailScreen
import com.dengerin.app.presentation.screens.SearchScreen

private const val ROUTE_TABS = "tabs"
private const val ROUTE_NOW_PLAYING = "now_playing"
private const val ROUTE_FULLSCREEN_LYRICS = "fullscreen_lyrics"
private const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}"

private fun playlistRoute(playlistId: String) = "playlist_detail/$playlistId"

@Composable
fun DengerinApp(container: AppContainer) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val playerState by container.playerController.uiState.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ViewModel dibuat di root supaya tidak reload saat pindah tab / buka player.
    val homeViewModel = remember {
        HomeViewModel(container.libraryRepository, container.musicExtractorRepository)
    }
    val libraryViewModel = remember { LibraryViewModel(container.libraryRepository) }
    val searchViewModel = remember {
        SearchViewModel(container.musicExtractorRepository, container.recentSearchStore)
    }
    val lyricsViewModel = remember {
        LyricsViewModel(container.playerController, container.lyricsRepository)
    }

    var sheetSong by remember { mutableStateOf<Song?>(null) }
    val addToPlaylistViewModel = remember(sheetSong?.id) {
        sheetSong?.let { song -> AddToPlaylistViewModel(song, container.libraryRepository) }
    }
    val onAddToPlaylist: (Song) -> Unit = { song -> sheetSong = song }
    val feedback: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    val showMiniPlayer = playerState.song != null &&
        currentRoute != ROUTE_NOW_PLAYING &&
        currentRoute != ROUTE_FULLSCREEN_LYRICS

    // Satu slot mini player dipakai Scaffold utama dan detail playlist.
    val miniPlayer: @Composable (showBottomBar: Boolean) -> Unit = { showBottomBar ->
        FloatingMiniPlayer(
            visible = showMiniPlayer,
            state = playerState,
            controller = container.playerController,
            onOpen = { navController.navigate(ROUTE_NOW_PLAYING) },
            showBottomBar = showBottomBar,
            onAddToPlaylist = onAddToPlaylist,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_TABS,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(ROUTE_TABS) {
                MainTabs(
                    container = container,
                    playerState = playerState,
                    homeViewModel = homeViewModel,
                    libraryViewModel = libraryViewModel,
                    searchViewModel = searchViewModel,
                    miniPlayer = miniPlayer,
                    onOpenPlaylist = { id -> navController.navigate(playlistRoute(id)) },
                    onAddToPlaylist = onAddToPlaylist,
                    onArtistClick = searchViewModel::prefillAndSearch,
                )
            }

            composable(ROUTE_NOW_PLAYING) {
                NowPlayingScreen(
                    state = playerState,
                    controller = container.playerController,
                    lyricsViewModel = lyricsViewModel,
                    onOpenLyrics = { navController.navigate(ROUTE_FULLSCREEN_LYRICS) },
                    onAddToPlaylist = onAddToPlaylist,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = ROUTE_FULLSCREEN_LYRICS,
                enterTransition = { slideInVertically { it } + fadeIn() },
                exitTransition = { slideOutVertically { it } + fadeOut() },
                popEnterTransition = { slideInVertically { it } + fadeIn() },
                popExitTransition = { slideOutVertically { it } + fadeOut() },
            ) {
                FullscreenLyricsScreen(
                    viewModel = lyricsViewModel,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = ROUTE_PLAYLIST_DETAIL,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
                val viewModel = remember(playlistId) {
                    PlaylistDetailViewModel(playlistId, container.libraryRepository)
                }
                PlaylistDetailScreen(
                    viewModel = viewModel,
                    playerState = playerState,
                    controller = container.playerController,
                    miniPlayer = { miniPlayer(false) },
                    onSongClick = { song, queue -> container.playerController.playSong(song, queue) },
                    onAddToPlaylist = onAddToPlaylist,
                    onClose = { navController.popBackStack() },
                )
            }
        }

        if (sheetSong != null && addToPlaylistViewModel != null) {
            AddToPlaylistSheet(
                song = sheetSong,
                viewModel = addToPlaylistViewModel,
                onDismiss = { sheetSong = null },
                onFeedback = feedback,
            )
        }
    }
}

@Composable
private fun FloatingMiniPlayer(
    visible: Boolean,
    state: PlayerUiState,
    controller: PlayerController,
    onOpen: () -> Unit,
    showBottomBar: Boolean,
    onAddToPlaylist: (Song) -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        MiniPlayer(
            state = state,
            controller = controller,
            onOpen = onOpen,
            onAddToPlaylist = onAddToPlaylist,
            modifier = Modifier
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = 6.dp,
                    bottom = if (showBottomBar) 6.dp else 8.dp,
                )
                .then(
                    if (showBottomBar) Modifier
                    else Modifier.navigationBarsPadding(),
                ),
        )
    }
}

@Composable
private fun MainTabs(
    container: AppContainer,
    playerState: PlayerUiState,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    searchViewModel: SearchViewModel,
    miniPlayer: @Composable (showBottomBar: Boolean) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onArtistClick: (String) -> Unit,
) {
    val colors = LocalDengerinColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        TabItem(stringResource(R.string.tab_home), Icons.Filled.Home),
        TabItem(stringResource(R.string.tab_search), Icons.Filled.Search),
        TabItem(stringResource(R.string.tab_collection), Icons.Filled.LibraryMusic),
    )

    fun playFromList(song: Song, queue: List<Song>) {
        val safeQueue = queue.ifEmpty { listOf(song) }
        container.playerController.playSong(song, safeQueue)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        bottomBar = {
            // Urutan wajib: mini player melayang dulu, baru navigation bar.
            Column {
                miniPlayer(true)
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = {
                                Text(tab.label, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.accent,
                                selectedTextColor = colors.accent,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                                indicatorColor = colors.accentSoftContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current

        when (selectedTab) {
            0 -> HomeScreen(
                viewModel = homeViewModel,
                playerState = playerState,
                onOpenSearch = { selectedTab = 1 },
                onImportLink = { selectedTab = 2 },
                onOpenPlaylist = onOpenPlaylist,
                onSongClick = { song, queue -> playFromList(song, queue) },
                onArtistClick = { artist ->
                    onArtistClick(artist)
                    selectedTab = 1
                },
                modifier = Modifier.padding(innerPadding),
            )

            1 -> SearchScreen(
                viewModel = searchViewModel,
                playerState = playerState,
                onSongClick = { song -> playFromList(song, searchViewModel.uiState.value.songs) },
                onAddToPlaylist = onAddToPlaylist,
                // Top inset sengaja tidak dikirim: SearchScreen memakai statusBarsPadding().
                modifier = Modifier.padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            )

            else -> LibraryScreen(
                viewModel = libraryViewModel,
                themePreferences = container.themePreferences,
                onOpenPlaylist = onOpenPlaylist,
                onSongClick = { song, queue -> playFromList(song, queue) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)
