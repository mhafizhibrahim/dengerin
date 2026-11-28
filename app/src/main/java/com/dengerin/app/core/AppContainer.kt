package com.dengerin.app.core

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.dengerin.app.data.autoplay.AutoplayRepository
import com.dengerin.app.data.artist.ArtistImageRepository
import com.dengerin.app.data.db.DengerinDatabase
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.genre.GenreCoverCache
import com.dengerin.app.data.genre.GenreCoverRepository
import com.dengerin.app.data.listening.ListeningHistoryRepository
import com.dengerin.app.data.lyrics.LyricSearchRepository
import com.dengerin.app.data.lyrics.LrclibService
import com.dengerin.app.data.lyrics.LyricsRepository
import com.dengerin.app.data.lyrics.YouTubeLyricsExtractor
import com.dengerin.app.data.lyrics.YouTubeMusicLyricsSource
import com.dengerin.app.data.preferences.PlaybackPreferences
import com.dengerin.app.data.preferences.SyncPreferences
import com.dengerin.app.data.preferences.ThemePreferences
import com.dengerin.app.data.profile.ProfileRepository
import com.dengerin.app.data.quality.AudioQualityEngine
import com.dengerin.app.data.recent.RecentSearchStore
import com.dengerin.app.data.recent.RecentlyPlayedStore
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.resolver.SongResolver
import com.dengerin.app.data.resolver.StreamFailureTracker
import com.dengerin.app.data.resolver.StreamResolver
import com.dengerin.app.data.spotify.HeadlessSpotifyTokenRefresher
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyClientTokenManager
import com.dengerin.app.data.spotify.SpotifyPathfinderHashResolver
import com.dengerin.app.data.spotify.SpotifyPublicImportRepository
import com.dengerin.app.data.spotify.SpotifySessionManager
import com.dengerin.app.data.sync.LibraryCloudSync
import com.dengerin.app.data.usage.DataUsageTracker
import com.dengerin.app.core.network.NetworkMonitor
import com.dengerin.app.player.AudioCacheManager
import com.dengerin.app.player.AudioPrefetcher
import com.dengerin.app.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual DI: tanpa Hilt supaya build tetap cepat. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Impor Spotify bertahan saat sheet ditutup; logout membatalkan Job-nya. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Permintaan membuka Now Playing dari kartu notifikasi. Memakai SharedFlow supaya
     * Activity yang sudah ada (singleTop) tetap menerima event lewat onNewIntent.
     */
    private val _nowPlayingOpenRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val nowPlayingOpenRequests: SharedFlow<Unit> = _nowPlayingOpenRequests.asSharedFlow()

    fun requestOpenNowPlaying() {
        _nowPlayingOpenRequests.tryEmit(Unit)
    }

    /** Pintasan launcher (Cari / Disukai / Acak) memakai jalur event yang sama. */
    private val _shortcutRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val shortcutRequests: SharedFlow<String> = _shortcutRequests.asSharedFlow()

    fun requestShortcut(action: String) {
        _shortcutRequests.tryEmit(action)
    }

    val themePreferences = ThemePreferences(appContext)
    val playbackPreferences = PlaybackPreferences(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val audioQualityEngine = AudioQualityEngine(playbackPreferences, networkMonitor)
    val audioCacheManager = AudioCacheManager.get(appContext)
    val dataUsageTracker = DataUsageTracker.get(appContext, networkMonitor)

    /** Satu pool koneksi untuk streaming dan prefetch audio. */
    val streamClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val musicExtractorRepository = MusicExtractorRepository(audioQualityEngine)
    /** Cache URL ekstraksi per videoId (~4 jam) untuk pemutaran ulang & auto-retry. */
    val streamResolver = StreamResolver(musicExtractorRepository)
    /** Catatan kegagalan stream lokal untuk mengurutkan fallback retry. */
    val streamFailureTracker = StreamFailureTracker()
    val audioPrefetcher = AudioPrefetcher(
        audioCacheManager,
        streamResolver,
        streamClient,
        dataUsageTracker.networkTransferListener,
    )
    val genreCoverCache = GenreCoverCache(appContext)
    val genreCoverRepository = GenreCoverRepository(genreCoverCache, musicExtractorRepository)
    val artistImageRepository = ArtistImageRepository()
    val recentlyPlayedStore = RecentlyPlayedStore()
    val autoplayRepository = AutoplayRepository(musicExtractorRepository, recentlyPlayedStore)
    val recentSearchStore = RecentSearchStore(appContext)
    val lyricSearchRepository = LyricSearchRepository()
    val profileRepository = ProfileRepository(appContext)

    /** Satu koneksi bersama untuk LRCLIB + youtubei; timeout ketat ada di masing-masing source. */
    private val lyricsClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Satu ImageLoader untuk seluruh aplikasi: CoverGradient (Palette), AsyncImage
     * dan daftar berbagi memory + disk cache yang sama.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, IMAGE_CACHE_DIR))
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    private val db = DengerinDatabase.build(appContext)
    private val dao = db.dao()

    val spotifyPublicImportRepository = SpotifyPublicImportRepository()
    private val spotifyTokenRefresher = HeadlessSpotifyTokenRefresher(appContext)
    val spotifySessionManager = SpotifySessionManager(
        appContext,
        spotifyTokenRefresher,
        profileRepository.authState,
    )
    val spotifyClientTokenManager = SpotifyClientTokenManager(appContext, spotifySessionManager)
    val spotifyPathfinderHashResolver = SpotifyPathfinderHashResolver(appContext)
    val spotifyAccountImportRepository = SpotifyAccountImportRepository(
        spotifySessionManager,
        spotifyClientTokenManager,
        spotifyPathfinderHashResolver,
        musicExtractorRepository,
    )
    val songResolver = SongResolver(musicExtractorRepository, dao)

    // Offline-first: Room tetap sumber kebenaran; cloud hanya mirror saat pengguna login.
    val syncPreferences = SyncPreferences(appContext)
    val libraryCloudSync = LibraryCloudSync(
        dao,
        profileRepository.authState,
        syncPreferences,
        spotifySessionManager,
    ).also { it.start() }
    val libraryRepository = LibraryRepository(
        dao,
        db,
        spotifyPublicImportRepository,
        spotifyAccountImportRepository,
        songResolver,
        libraryCloudSync,
    )
    val lyricsRepository = LyricsRepository(
        dao = dao,
        service = LrclibService(lyricsClient),
        youtubeMusic = YouTubeMusicLyricsSource(lyricsClient),
        youtubeCaptions = YouTubeLyricsExtractor(lyricsClient),
    )
    val listeningHistoryRepository = ListeningHistoryRepository(db)

    val playerController by lazy {
        PlayerController(
            appContext,
            recentlyPlayedStore,
            libraryRepository,
            songResolver,
            streamResolver,
            profileRepository,
            playbackPreferences,
            networkMonitor,
            audioCacheManager,
            dataUsageTracker,
            listeningHistoryRepository,
            autoplayRepository,
            streamFailureTracker,
            audioPrefetcher,
        )
    }

    private companion object {
        const val IMAGE_CACHE_DIR = "image_cache"
        const val IMAGE_CACHE_BYTES = 100L * 1024 * 1024
    }
}
