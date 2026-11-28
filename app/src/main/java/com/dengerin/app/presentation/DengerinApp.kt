package com.dengerin.app.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dengerin.app.BuildConfig
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.dengerin.app.core.LocalCachedTrackIds
import com.dengerin.app.core.ShortcutActions
import com.dengerin.app.core.LocalLikedTrackIds
import com.dengerin.app.core.likeKey
import com.dengerin.app.core.theme.BottomClearanceProvider
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.DarkDengerinColors
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.lyrics.LyricSearchHit
import com.dengerin.app.data.update.GitHubUpdateManager
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
import com.dengerin.app.presentation.components.AddToPlaylistSheet
import com.dengerin.app.presentation.components.AppDrawerContent
import com.dengerin.app.presentation.components.CreateSheet
import com.dengerin.app.presentation.components.SpotifyAccountImportSheet
import com.dengerin.app.presentation.components.SpotifyMiniPlayer
import com.dengerin.app.presentation.components.SpotifyWebLoginSheet
import com.dengerin.app.presentation.screens.ArtistDetailScreen
import com.dengerin.app.presentation.screens.FullscreenLyricsScreen
import com.dengerin.app.presentation.screens.HomeScreen
import com.dengerin.app.presentation.screens.LibraryScreen
import com.dengerin.app.presentation.screens.NowPlayingScreen
import com.dengerin.app.presentation.screens.PlaylistDetailScreen
import com.dengerin.app.presentation.screens.ProfileScreen
import com.dengerin.app.presentation.screens.RemotePlaylistDetailScreen
import com.dengerin.app.presentation.screens.SearchScreen
import com.dengerin.app.presentation.screens.SettingsPage
import com.dengerin.app.presentation.screens.SettingsScreen
import com.dengerin.app.presentation.profile.ProfileViewModel
import com.dengerin.app.presentation.update.ForceUpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.net.Uri

private const val ROUTE_TABS = "tabs"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
private const val ROUTE_ARTIST_DETAIL = "artist_detail?artistId={artistId}&artistName={artistName}"
private const val ROUTE_REMOTE_PLAYLIST_DETAIL = "remote_playlist_detail/{playlistId}"
private const val ROUTE_SETTINGS = "settings"

private const val TAB_HOME = 0
private const val TAB_SEARCH = 1
private const val TAB_LIBRARY = 2
private const val TAB_CREATE = 3

private fun playlistRoute(playlistId: String) = "playlist_detail/$playlistId"
private fun artistRoute(artistId: String?, artistName: String): String =
    "artist_detail?artistId=${Uri.encode(artistId.orEmpty())}&artistName=${Uri.encode(artistName)}"
private fun remotePlaylistRoute(playlistId: String): String =
    "remote_playlist_detail/${Uri.encode(playlistId)}"

@Composable
fun DengerinApp(container: AppContainer) {
    val navController = rememberNavController()
    val colors = LocalDengerinColors.current
    val isDark = colors.background == DarkDengerinColors.background
    val playerState by container.playerController.uiState.collectAsState()
    // Dibaca sekali di root dari StateFlow IO; baris lagu tinggal cek `id in cachedIds`.
    val cachedIds by container.audioCacheManager.cachedTrackIds.collectAsState()
    // Satu koleksi liked untuk seluruh halaman; baris lagu tinggal cek ke Set ini.
    val likedSongs by container.libraryRepository.liked.collectAsState(initial = emptyList())
    val likedIds = remember(likedSongs) {
        likedSongs.map { it.spotifyId.ifBlank { it.id } }.toSet()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val authUser by container.profileRepository.authState.collectAsState()
    val cachedProfile by container.profileRepository.profile.collectAsState()
    val homeProfile: UserProfile? = if (authUser != null) cachedProfile else null
    val homeEmail = if (authUser != null) authUser?.email.orEmpty() else ""
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var forcedUpdate by remember { mutableStateOf<GitHubUpdateManager.ReleaseInfo?>(null) }

    // Tunggu DataStore terbaca supaya batas cache lama tidak ter-evict lebih dulu.
    LaunchedEffect(Unit) {
        combine(
            container.playbackPreferences.settingsLoaded,
            container.playbackPreferences.settings,
        ) { loaded, settings -> if (loaded) settings.cacheLimit else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { limit -> container.audioCacheManager.applyMaxBytes(limit.bytes) }
    }

    // Cek rilis GitHub sekali saat aplikasi dibuka. Gagal/offset diabaikan supaya
    // pemutaran offline tetap bisa dipakai (fail-silent).
    LaunchedEffect(Unit) {
        val release = GitHubUpdateManager.fetchLatest() ?: return@LaunchedEffect
        if (GitHubUpdateManager.isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
            forcedUpdate = release
        }
    }

    // Tab & posisi scroll dipegang di root supaya overlay bottom nav tetap bisa memanggilnya
    // dari rute mana pun, dan back bisa mengembalikan Cari/Koleksi ke Beranda.
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    val homeListState = rememberSavedListState()
    val searchListState = rememberSavedListState()
    val libraryListState = rememberSavedListState()
    val searchFocusRequester = remember { FocusRequester() }
    var searchFocusSignal by rememberSaveable { mutableIntStateOf(0) }
    var sheetSong by remember { mutableStateOf<Song?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showSpotifyLoginSheet by remember { mutableStateOf(false) }
    var showSpotifyImportSheet by remember { mutableStateOf(false) }
    var libraryActionRequest by remember { mutableIntStateOf(0) }
    var libraryInitialTab by remember { mutableStateOf("Playlist") }
    var homeHistoryRequest by remember { mutableIntStateOf(0) }
    var settingsInitialPage by remember { mutableStateOf(SettingsPage.HUB) }

    // Dua overlay dengan siklus hidup terpisah: Full Lyrics menutupi Now Playing,
    // tetapi menutup Full Lyrics tidak boleh menutup Now Playing. Mini player tetap terkunci.
    var isNowPlayingVisible by remember { mutableStateOf(false) }
    var isFullLyricsVisible by remember { mutableStateOf(false) }

    // Kartu notifikasi/lockscreen meminta sheet Now Playing lewat MainActivity.
    LaunchedEffect(Unit) {
        container.nowPlayingOpenRequests.collect {
            isFullLyricsVisible = false
            isNowPlayingVisible = true
        }
    }

    // App shortcuts (Cari / Lagu Disukai / Putar Acak) memakai jalur event MainActivity.
    LaunchedEffect(Unit) {
        container.shortcutRequests.collect { action ->
            when (action) {
                ShortcutActions.SEARCH -> {
                    navController.popBackStack(ROUTE_TABS, false)
                    selectedTab = TAB_SEARCH
                    searchFocusSignal++
                }

                ShortcutActions.LIKED -> {
                    navController.popBackStack(ROUTE_TABS, false)
                    libraryInitialTab = "Lagu Disukai"
                    selectedTab = TAB_LIBRARY
                    libraryListState.animateScrollToItem(0)
                }

                ShortcutActions.SHUFFLE -> {
                    isNowPlayingVisible = false
                    if (!container.playerController.isShuffle) {
                        container.playerController.toggleShuffle()
                    }
                    if (likedSongs.isNotEmpty()) {
                        container.playerController.playQueue(
                            likedSongs,
                            shuffleStart = true,
                            contextTitle = "Lagu Disukai",
                        )
                    }
                }
            }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val closeDrawer = { scope.launch { drawerState.close() } }
    val closeNowPlaying = { isNowPlayingVisible = false }

    LaunchedEffect(currentRoute) {
        if (currentRoute != ROUTE_TABS && drawerState.isOpen) drawerState.close()
    }

    // BackHandler Now Playing hanya aktif bila Full Lyrics tidak sedang tampil.
    BackHandler(enabled = isNowPlayingVisible && !isFullLyricsVisible) { closeNowPlaying() }
    BackHandler(
        enabled = currentRoute == ROUTE_TABS && selectedTab != TAB_HOME &&
            sheetSong == null && !showCreateSheet && !drawerState.isOpen &&
            !isNowPlayingVisible && !isFullLyricsVisible,
    ) {
        selectedTab = TAB_HOME
    }

    fun handleBottomTabClick(index: Int) {
        val fromTabs = currentRoute == ROUTE_TABS
        if (!fromTabs) navController.popBackStack(ROUTE_TABS, false)
        if (index == TAB_CREATE) {
            showCreateSheet = true
            return
        }
        if (selectedTab == index && fromTabs) {
            when (index) {
                TAB_HOME -> scope.launch { homeListState.animateScrollToItem(0) }
                TAB_SEARCH -> searchFocusSignal += 1
                TAB_LIBRARY -> scope.launch { libraryListState.animateScrollToItem(0) }
            }
        } else {
            selectedTab = index
        }
    }

    // ViewModel dibuat di root supaya tidak reload saat pindah tab / buka player.
    val homeViewModel = remember {
        HomeViewModel(
            container.libraryRepository,
            container.musicExtractorRepository,
            container.artistImageRepository,
        )
    }
    val libraryViewModel = remember { LibraryViewModel(container.libraryRepository) }
    val spotifyAccountViewModel = remember {
        SpotifyAccountViewModel(
            container.spotifySessionManager,
            container.spotifyAccountImportRepository,
            container.libraryRepository,
            container.applicationScope,
        )
    }
    val spotifyUi by spotifyAccountViewModel.ui.collectAsState()
    val searchViewModel = remember {
        SearchViewModel(
            repository = container.musicExtractorRepository,
            recentSearchStore = container.recentSearchStore,
            genreCoverRepository = container.genreCoverRepository,
            playbackPreferences = container.playbackPreferences,
            networkMonitor = container.networkMonitor,
            lyricSearchRepository = container.lyricSearchRepository,
            spotifyAccountImportRepository = container.spotifyAccountImportRepository,
        )
    }
    val lyricsViewModel = remember {
        LyricsViewModel(container.playerController, container.lyricsRepository)
    }
    val profileViewModel = remember {
        ProfileViewModel(container.profileRepository, container.libraryRepository)
    }

    // Navigasi ke playlist baru di-root: CreateSheet bisa dibuka dari tab mana pun,
    // jadi efek di dalam LibraryScreen tidak lagi diandalkan.
    val libraryAction by libraryViewModel.action.collectAsState()
    LaunchedEffect(libraryAction.importedPlaylistId) {
        libraryAction.importedPlaylistId?.let { id ->
            navController.navigate(playlistRoute(id))
            libraryViewModel.consumeNavigation()
        }
    }

    val addToPlaylistViewModel = remember(sheetSong?.id) {
        sheetSong?.let { song -> AddToPlaylistViewModel(song, container.libraryRepository) }
    }
    val onAddToPlaylist: (Song) -> Unit = { song -> sheetSong = song }
    val feedback: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val onToggleLike: (Song) -> Unit = { song ->
        scope.launch {
            val liked = withContext(Dispatchers.IO) { container.libraryRepository.toggleLike(song) }
            val currentKey = playerState.song?.likeKey()
            if (currentKey != null && song.likeKey() == currentKey) {
                container.playerController.setLikedState(liked)
            }
            feedback(
                if (liked) "Ditambahkan ke Lagu Disukai" else "Dihapus dari Lagu Disukai",
            )
        }
    }
    val onAddToQueue: (Song) -> Unit = { song ->
        container.playerController.addToQueue(song)
        feedback("Ditambahkan ke antrean")
    }
    val onPlayNext: (Song) -> Unit = { song ->
        container.playerController.playNext(song)
        feedback("Akan diputar berikutnya")
    }

    // Kartu "Cocok dengan lirik": simpan query, resolver NewPipe, lalu putar lagu aslinya.
    val onPlayLyricMatch: (LyricSearchHit) -> Unit = { hit ->
        scope.launch {
            val song = withContext(Dispatchers.IO) {
                container.recentSearchStore.add(hit.query)
                container.musicExtractorRepository
                    .searchSongs(hit.resolverQuery)
                    .getOrNull()
                    ?.firstOrNull()
            }
            if (song != null) {
                container.playerController.playSong(song, listOf(song))
            } else {
                feedback("Lagu belum ketemu, coba kata kunci lain ya")
            }
        }
    }

    // Nav bawah permanen: disembunyikan saat sheet Now Playing / Full Lyrics & Settings.
    val showBottomOverlay = !isNowPlayingVisible &&
        !isFullLyricsVisible &&
        currentRoute != ROUTE_SETTINGS

    // 4 stop: background dipasang SEBELUM windowInsetsPadding agar hitam/gradien menyatu
    // sampai ke dasar area tombol navigasi Android (3 tombol maupun gesture bar).
    val bottomGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            0.28f to colors.navGradientEarly,
            colors.navGradientMidStop to colors.navGradientMid,
            1.00f to colors.navGradientEnd,
        ),
    )

    val openLibraryAction: (Int) -> Unit = { request ->
        showCreateSheet = false
        selectedTab = TAB_LIBRARY
        libraryActionRequest = request
    }

    // Dipanggil dari Pengaturan: kembali ke tab dulu supaya dialog Koleksi benar-benar tampil.
    val openLibraryActionFromSettings: (Int) -> Unit = { request ->
        navController.popBackStack(ROUTE_TABS, false)
        openLibraryAction(request)
    }

    val openSpotifyLogin: () -> Unit = {
        showCreateSheet = false
        showSpotifyLoginSheet = true
    }
    val openSpotifyImport: () -> Unit = {
        showCreateSheet = false
        showSpotifyImportSheet = true
        spotifyAccountViewModel.refresh()
    }
    // Putuskan akun: tutup sheet koleksi, jangan buka dialog login atau muat ulang token.
    val logoutSpotify: () -> Unit = {
        spotifyAccountViewModel.logout()
        showSpotifyLoginSheet = false
        showSpotifyImportSheet = false
        showCreateSheet = false
    }
    // Status murni dari DataStore; error fetch tidak boleh memutus koneksi.
    val spotifyConnected = spotifyUi.isConnected

    CompositionLocalProvider(
        LocalCachedTrackIds provides cachedIds,
        LocalLikedTrackIds provides likedIds,
    ) {
    BottomClearanceProvider(
        withBottomNav = showBottomOverlay,
        miniPlayerVisible = showBottomOverlay && playerState.song != null,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == ROUTE_TABS,
        drawerContent = {
            AppDrawerContent(
                profile = homeProfile,
                email = homeEmail,
                onViewProfile = {
                    closeDrawer()
                    navController.navigate(ROUTE_PROFILE)
                },
                onAddAccount = {
                    closeDrawer()
                    navController.navigate(ROUTE_PROFILE)
                },
                onRecentlyPlayed = {
                    closeDrawer()
                    selectedTab = TAB_HOME
                    homeHistoryRequest += 1
                },
                onStatistics = {
                    closeDrawer()
                    settingsInitialPage = SettingsPage.STATISTICS
                    navController.navigate(ROUTE_SETTINGS)
                },
                onSettings = {
                    closeDrawer()
                    settingsInitialPage = SettingsPage.HUB
                    navController.navigate(ROUTE_SETTINGS)
                },
            )
        },
    ) {
        // Layer 1: konten halaman mengalir penuh ke tepi bawah (tanpa Scaffold bottomBar).
        // Layer 2: satu Column BottomCenter berisi mini player + nav 4 tab.
        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_TABS,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(ROUTE_TABS) {
                    MainTabs(
                        behindNowPlaying = isNowPlayingVisible,
                        container = container,
                        selectedTab = selectedTab,
                        playerState = playerState,
                        homeViewModel = homeViewModel,
                        libraryViewModel = libraryViewModel,
                        searchViewModel = searchViewModel,
                        homeListState = homeListState,
                        searchListState = searchListState,
                        libraryListState = libraryListState,
                        searchFocusRequester = searchFocusRequester,
                        searchFocusSignal = searchFocusSignal,
                        onOpenPlaylist = { id -> navController.navigate(playlistRoute(id)) },
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onToggleLike = onToggleLike,
                        onPlayLyricMatch = onPlayLyricMatch,
                        homeProfile = homeProfile,
                        homeHistoryRequest = homeHistoryRequest,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        libraryActionRequest = libraryActionRequest,
                        libraryInitialTab = libraryInitialTab,
                        onActionRequestConsumed = { libraryActionRequest = 0 },
                        onCreatePlaylist = { showCreateSheet = true },
                        onConnectSpotify = openSpotifyLogin,
                        onOpenSpotifyImport = openSpotifyImport,
                        spotifyConnected = spotifyConnected,
                        onOpenArtist = { artistId, artistName ->
                            isNowPlayingVisible = false
                            navController.navigate(artistRoute(artistId, artistName))
                        },
                        onOpenRemotePlaylist = { playlistId ->
                            navController.navigate(remotePlaylistRoute(playlistId))
                        },
                    )
                }

                composable(ROUTE_PROFILE) {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onClose = { navController.popBackStack() },
                        onOpenSettings = {
                            settingsInitialPage = SettingsPage.HUB
                            navController.navigate(ROUTE_SETTINGS)
                        },
                    )
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        container = container,
                        libraryViewModel = libraryViewModel,
                        spotifyViewModel = spotifyAccountViewModel,
                        onClose = { navController.popBackStack() },
                        onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                        onConnectSpotify = openSpotifyLogin,
                        onOpenSpotifyImport = openSpotifyImport,
                        onSpotifyDisconnected = logoutSpotify,
                        onImportSpotify = { openLibraryActionFromSettings(2) },
                        onPlaySong = { song, queue ->
                            container.playerController.playSong(song, queue, contextTitle = "Riwayat putar")
                        },
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        initialPage = settingsInitialPage,
                        onUpdateAvailable = { release -> forcedUpdate = release },
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
                        onSongClick = { song, queue, title ->
                            container.playerController.playSong(song, queue, contextTitle = title)
                        },
                        onAddToPlaylist = onAddToPlaylist,
                        onToggleLike = onToggleLike,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onFindSongs = {
                            navController.popBackStack(ROUTE_TABS, false)
                            selectedTab = TAB_SEARCH
                            searchFocusSignal += 1
                        },
                        onClose = { navController.popBackStack() },
                        onDeleted = {
                            navController.popBackStack()
                            feedback("Playlist dihapus")
                        },
                    )
                }

                composable(
                    route = ROUTE_ARTIST_DETAIL,
                    arguments = listOf(
                        navArgument("artistId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("artistName") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getString("artistId").orEmpty()
                    val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
                    ArtistDetailScreen(
                        artistId = artistId,
                        artistName = artistName,
                        repository = container.spotifyAccountImportRepository,
                        playerState = playerState,
                        onClose = { navController.popBackStack() },
                        onPlaySong = { song, queue ->
                            container.playerController.playSong(song, queue, contextTitle = artistName)
                        },
                        onOpenPlaylist = { playlistId -> navController.navigate(remotePlaylistRoute(playlistId)) },
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onToggleLike = onToggleLike,
                        onOpenArtist = { artistId, artistName ->
                            isNowPlayingVisible = false
                            navController.navigate(artistRoute(artistId, artistName))
                        },
                    )
                }

                composable(
                    route = ROUTE_REMOTE_PLAYLIST_DETAIL,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
                    RemotePlaylistDetailScreen(
                        playlistId = playlistId,
                        repository = container.spotifyAccountImportRepository,
                        libraryRepository = container.libraryRepository,
                        playerState = playerState,
                        onClose = { navController.popBackStack() },
                        onPlaySong = { song, queue -> container.playerController.playSong(song, queue) },
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onToggleLike = onToggleLike,
                    )
                }
            }

            if (showBottomOverlay) {
                SpotifyBottomNavOverlay(
                    showMiniPlayer = playerState.song != null,
                    gradientBrush = bottomGradientBrush,
                    selectedTab = selectedTab,
                    playerState = playerState,
                    controller = container.playerController,
                    onOpenPlayer = { isNowPlayingVisible = true },
                    onToggleLike = onToggleLike,
                    onTabClick = { handleBottomTabClick(it) },
                )
            }

            // iOS sheet depth: halaman utama tetap hidup di belakang dan menyusut halus.
            if (isNowPlayingVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable(onClick = closeNowPlaying),
                )
            }
            AnimatedVisibility(
                visible = isNowPlayingVisible,
                enter = slideInVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                ) { it },
                exit = slideOutVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                ) { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(
                            RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp,
                            ),
                        ),
                ) {
                    NowPlayingScreen(
                        container = container,
                        state = playerState,
                        controller = container.playerController,
                        lyricsViewModel = lyricsViewModel,
                        onOpenLyrics = { isFullLyricsVisible = true },
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onToggleLike = onToggleLike,
                        onClose = closeNowPlaying,
                        onOpenArtist = { artistId, artistName ->
                            isNowPlayingVisible = false
                            navController.navigate(artistRoute(artistId, artistName))
                        },
                        onSongSelected = { selected, queue ->
                            container.playerController.playSong(selected, queue)
                        },
                        onRemoveUpcomingItem = { queueId ->
                            container.playerController.removeUpcomingItem(queueId)
                        },
                        onMoveUpcomingItem = { from, to ->
                            container.playerController.moveUpcomingItem(from, to)
                        },
                        onSearch = {
                            closeNowPlaying()
                            handleBottomTabClick(TAB_SEARCH)
                        },
                    )
                }
            }

            // Lapisan paling atas: Full Lyrics. Back sistem & tombol tutup di dalam
            // layar hanya menutup layer ini; Now Playing tetap terbuka di bawahnya.
            BackHandler(enabled = isFullLyricsVisible) { isFullLyricsVisible = false }
            AnimatedVisibility(
                visible = isFullLyricsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                FullscreenLyricsScreen(
                    viewModel = lyricsViewModel,
                    timeline = container.playerController.timeline,
                    isPlaying = playerState.isPlaying,
                    onClose = { isFullLyricsVisible = false },
                    onSearchLyrics = {
                        isFullLyricsVisible = false
                        isNowPlayingVisible = false
                        navController.popBackStack(ROUTE_TABS, false)
                        selectedTab = TAB_SEARCH
                        searchFocusSignal += 1
                    },
                    onOpenArtist = { artistId, artistName ->
                        isFullLyricsVisible = false
                        isNowPlayingVisible = false
                        navController.navigate(artistRoute(artistId, artistName))
                    },
                )
            }

            if (sheetSong != null && addToPlaylistViewModel != null) {
                AddToPlaylistSheet(
                    song = sheetSong,
                    viewModel = addToPlaylistViewModel,
                    onDismiss = { sheetSong = null },
                    onFeedback = feedback,
                )
            }

            if (showCreateSheet) {
                CreateSheet(
                    onDismiss = { showCreateSheet = false },
                    onCreatePlaylist = { name, description ->
                        showCreateSheet = false
                        libraryViewModel.createPlaylist(name, description)
                    },
                    onImportSpotify = { openLibraryAction(2) },
                    onConnectSpotify = openSpotifyLogin,
                    onOpenSpotifyImport = openSpotifyImport,
                    spotifyConnected = spotifyConnected,
                )
            }

            if (showSpotifyLoginSheet) {
                SpotifyWebLoginSheet(
                    onDismiss = { showSpotifyLoginSheet = false },
                    clearCookiesOnOpen = !spotifyConnected,
                    onBeforeCommit = { container.spotifySessionManager.markConnected() },
                    // Buka sheet impor lebih dulu supaya dialog login selalu kembali ke
                    // Dengerin walau fetch profil/DataStore gagal di tengah jalan.
                    onSessionCaptured = { cookie, token, expiresAtMs, clientId ->
                        showSpotifyImportSheet = true
                        feedback("Akun Spotify tersambung")
                        spotifyAccountViewModel.connectWithSession(cookie, token, expiresAtMs, clientId)
                    },
                )
            }

            if (showSpotifyImportSheet) {
                SpotifyAccountImportSheet(
                    viewModel = spotifyAccountViewModel,
                    onDismiss = { showSpotifyImportSheet = false },
                    onLogout = logoutSpotify,
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = LocalBottomClearance.current),
            )
        }

        forcedUpdate?.let { release ->
            ForceUpdateDialog(release = release)
        }
    }
    }
    }
}

/**
 * Overlay bawah Fase 5: mini player + nav adalah SATU `Column` di `BottomCenter`.
 * Gradasi dipasang sebelum `windowInsetsPadding(navigationBars)` agar menyatu sampai
 * ke dasar area sistem. Tidak ada drag/dock/morphing.
 */
@Composable
private fun BoxScope.SpotifyBottomNavOverlay(
    showMiniPlayer: Boolean,
    gradientBrush: Brush,
    selectedTab: Int,
    playerState: PlayerUiState,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onTabClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // Urutan wajib: gradasi dulu, baru insets system navigation bar.
            .background(brush = gradientBrush)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(160)) { it } + fadeOut(tween(120)),
        ) {
            SpotifyMiniPlayer(
                state = playerState,
                controller = controller,
                onOpen = onOpenPlayer,
                onToggleLike = onToggleLike,
                onPrevious = { controller.skipPrevious() },
                onNext = { controller.skipNext() },
            )
        }
        SpotifyBottomNavBar(selectedTab = selectedTab, onTabClick = onTabClick)
    }
}

/** 4 tab Spotify: tanpa pill aktif, ikon filled/outlined, label 11.sp. */
@Composable
private fun SpotifyBottomNavBar(
    selectedTab: Int,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val tabs = listOf(
        TabItem(stringResource(R.string.tab_home), Icons.Filled.Home, Icons.Outlined.Home),
        TabItem(stringResource(R.string.tab_search), Icons.Filled.Search, Icons.Outlined.Search),
        TabItem(
            stringResource(R.string.tab_collection),
            Icons.Filled.LibraryMusic,
            Icons.Outlined.LibraryMusic,
        ),
        TabItem(stringResource(R.string.tab_create), Icons.Filled.Add, Icons.Outlined.Add),
    )

    // Tinggi kunci 60.dp: NavigationBar tidak pernah menyusut jadi 0.dp saat lagu diputar.
    Box(modifier = modifier.fillMaxWidth().height(60.dp)) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            containerColor = Color.Transparent,
            contentColor = colors.navActive,
            tonalElevation = 0.dp,
            // Insets sudah dipakai sekali oleh overlay nav — jangan dikonsumsi lagi di sini.
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = selectedTab == index && index != TAB_CREATE
                val contentColor = if (active) colors.navActive else colors.navInactive
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                // Tekan 90ms mengecil, lepas kembali dengan kurva iOS tanpa pantulan.
                val iconScale by animateFloatAsState(
                    targetValue = if (pressed) 0.88f else 1f,
                    animationSpec = if (pressed) {
                        tween(90)
                    } else {
                        spring(dampingRatio = 0.88f, stiffness = 380f)
                    },
                    label = "bottom-nav-icon-scale-$index",
                )
                NavigationBarItem(
                    selected = active,
                    onClick = { onTabClick(index) },
                    interactionSource = interaction,
                    icon = {
                        Icon(
                            imageVector = if (active) tab.activeIcon else tab.inactiveIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            color = contentColor,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.navActive,
                        unselectedIconColor = colors.navInactive,
                        selectedTextColor = colors.navActive,
                        unselectedTextColor = colors.navInactive,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    behindNowPlaying: Boolean,
    container: AppContainer,
    selectedTab: Int,
    playerState: PlayerUiState,
    homeViewModel: HomeViewModel,
    libraryViewModel: LibraryViewModel,
    searchViewModel: SearchViewModel,
    homeListState: LazyListState,
    searchListState: LazyListState,
    libraryListState: LazyListState,
    searchFocusRequester: FocusRequester,
    searchFocusSignal: Int,
    onOpenPlaylist: (String) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onToggleLike: (Song) -> Unit,
    onPlayLyricMatch: (LyricSearchHit) -> Unit,
    homeProfile: UserProfile?,
    homeHistoryRequest: Int,
    onOpenDrawer: () -> Unit,
    libraryActionRequest: Int,
    libraryInitialTab: String,
    onActionRequestConsumed: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onConnectSpotify: () -> Unit,
    onOpenSpotifyImport: () -> Unit,
    spotifyConnected: Boolean,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onOpenRemotePlaylist: (String) -> Unit = {},
) {
    val colors = LocalDengerinColors.current
    val behindScale by animateFloatAsState(
        targetValue = if (behindNowPlaying) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 360f),
        label = "now-playing-behind-scale",
    )
    val behindCorner by animateDpAsState(
        // Kritis (ζ = 1) + clamp: spring underdamped bisa overshoot ke nilai negatif
        // kecil saat sheet ditutup, dan RoundedCornerShape menolak corner < 0 px.
        targetValue = (if (behindNowPlaying) 18.dp else 0.dp).coerceAtLeast(0.dp),
        animationSpec = spring(dampingRatio = 1f, stiffness = 360f),
        label = "now-playing-behind-corner",
    )

    fun playFromList(song: Song, queue: List<Song>) {
        val safeQueue = queue.ifEmpty { listOf(song) }
        val contextTitle = when (selectedTab) {
            TAB_SEARCH -> "Pencarian"
            TAB_LIBRARY -> "Koleksi Kamu"
            else -> "Beranda"
        }
        container.playerController.playSong(song, safeQueue, contextTitle = contextTitle)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(behindCorner.coerceAtLeast(0.dp)))
            .background(colors.background)
            .graphicsLayer {
                scaleX = behindScale
                scaleY = behindScale
            },
    ) {
        // Konten tidak diberi innerPadding: mini player + nav melayang di atasnya.
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                label = "tab-crossfade",
            ) { tab ->
                when (tab) {
                    TAB_HOME -> HomeScreen(
                        container = container,
                        viewModel = homeViewModel,
                        playerState = playerState,
                        onOpenPlaylist = onOpenPlaylist,
                        onSongClick = { song, queue -> playFromList(song, queue) },
                        profile = homeProfile,
                        onOpenDrawer = onOpenDrawer,
                        historyRequest = homeHistoryRequest,
                        onRetry = { homeViewModel.refreshCatalog() },
                        listState = homeListState,
                        modifier = Modifier.fillMaxSize(),
                    )

                    TAB_SEARCH -> SearchScreen(
                        viewModel = searchViewModel,
                        playerState = playerState,
                        onSongClick = { song ->
                            val state = searchViewModel.uiState.value
                            val queue = if (state.activeGenre != null) state.genreSongs else state.songs
                            playFromList(song, queue)
                        },
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onToggleLike = onToggleLike,
                        onPlayLyricMatch = onPlayLyricMatch,
                        focusRequester = searchFocusRequester,
                        focusSignal = searchFocusSignal,
                        profile = homeProfile,
                        onOpenDrawer = onOpenDrawer,
                        onOpenArtist = onOpenArtist,
                        onOpenRemotePlaylist = onOpenRemotePlaylist,
                        listState = searchListState,
                        modifier = Modifier.fillMaxSize(),
                    )

                    TAB_LIBRARY -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenPlaylist = onOpenPlaylist,
                        onSongClick = { song, queue -> playFromList(song, queue) },
                        profile = homeProfile,
                        onOpenDrawer = onOpenDrawer,
                        actionRequest = libraryActionRequest,
                        initialTab = libraryInitialTab,
                        onActionRequestConsumed = onActionRequestConsumed,
                        onCreatePlaylist = onCreatePlaylist,
                        onAddToPlaylist = onAddToPlaylist,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onToggleLike = onToggleLike,
                        onConnectSpotify = onConnectSpotify,
                        onOpenSpotifyImport = onOpenSpotifyImport,
                        spotifyConnected = spotifyConnected,
                        listState = libraryListState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Posisi scroll tiap tab ikut tersimpan saat Activity direcreated / pindah rute. */
@Composable
private fun rememberSavedListState(): LazyListState =
    rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

private data class TabItem(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
)
