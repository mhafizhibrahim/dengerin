package com.dengerin.app.core

import android.content.Context
import com.dengerin.app.data.artist.ArtistImageRepository
import com.dengerin.app.data.db.DengerinDatabase
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.lyrics.LyricsRepository
import com.dengerin.app.data.preferences.ThemePreferences
import com.dengerin.app.data.profile.ProfileRepository
import com.dengerin.app.data.recent.RecentSearchStore
import com.dengerin.app.data.recent.RecentlyPlayedStore
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.resolver.SongResolver
import com.dengerin.app.data.spotify.SpotifyPublicImportRepository
import com.dengerin.app.player.PlayerController

/** Manual DI: tanpa Hilt supaya build tetap cepat. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val themePreferences = ThemePreferences(appContext)
    val musicExtractorRepository = MusicExtractorRepository()
    val artistImageRepository = ArtistImageRepository()
    val recentlyPlayedStore = RecentlyPlayedStore()
    val recentSearchStore = RecentSearchStore(appContext)
    val profileRepository = ProfileRepository(appContext)

    private val db = DengerinDatabase.build(appContext)
    private val dao = db.dao()

    val spotifyPublicImportRepository = SpotifyPublicImportRepository()
    val songResolver = SongResolver(musicExtractorRepository, dao)
    val libraryRepository = LibraryRepository(dao, spotifyPublicImportRepository, songResolver)
    val lyricsRepository = LyricsRepository(dao)

    val playerController by lazy {
        PlayerController(
            appContext,
            musicExtractorRepository,
            recentlyPlayedStore,
            libraryRepository,
            songResolver,
            profileRepository,
        )
    }
}
