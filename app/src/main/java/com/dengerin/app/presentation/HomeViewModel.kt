package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ArtistItem(
    val name: String,
    val thumbnailUrl: String,
    val fromLibrary: Boolean = false,
)

data class HomeUiState(
    val isLoadingCatalog: Boolean = true,
    val history: List<Song> = emptyList(),
    val frequent: List<Song> = emptyList(),
    val liked: List<Song> = emptyList(),
    val playlists: List<LocalPlaylistEntity> = emptyList(),
    val hits: List<Song> = emptyList(),
    val chill: List<Song> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val catalogError: String? = null,
)

class HomeViewModel(
    private val library: LibraryRepository,
    private val extractor: MusicExtractorRepository,
) : ViewModel() {

    private val remote = MutableStateFlow(RemoteCatalog())
    private val artistThumbnails = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Cache memori agar avatar artis tidak di-fetch ulang tiap recomposisi/buka tab. */
    private val artistThumbnailMemory = ConcurrentHashMap<String, String>()

    private val librarySnapshot: StateFlow<LibrarySnapshot> = combine(
        library.history,
        library.frequent,
        library.liked,
        library.playlists,
        library.allPlaylistSongs,
    ) { history, frequent, liked, playlists, playlistSongs ->
        LibrarySnapshot(history, frequent, liked, playlists, playlistSongs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySnapshot())

    val uiState: StateFlow<HomeUiState> = combine(
        librarySnapshot,
        remote,
        artistThumbnails,
    ) { snapshot, remoteCatalog, thumbs ->
        HomeUiState(
            isLoadingCatalog = remoteCatalog.loading,
            history = snapshot.history,
            frequent = snapshot.frequent,
            liked = snapshot.liked,
            playlists = snapshot.playlists,
            hits = remoteCatalog.hits,
            chill = remoteCatalog.chill,
            artists = buildArtists(snapshot.history, snapshot.liked, snapshot.playlistSongs, thumbs),
            catalogError = remoteCatalog.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshCatalog()
        prefetchArtistThumbnails()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            remote.update { it.copy(loading = true, error = null) }
            val hits = if (remote.value.hits.isNotEmpty()) remote.value.hits
            else extractor.searchSongs("lagu indonesia hits 2026").getOrNull().orEmpty().take(12)
            val chill = if (remote.value.chill.isNotEmpty()) remote.value.chill
            else extractor.searchSongs("musik santai indonesia akustik").getOrNull().orEmpty().take(12)
            remote.value = RemoteCatalog(
                loading = false,
                hits = hits,
                chill = chill,
                error = if (hits.isEmpty() && chill.isEmpty()) "Katalog belum bisa dimuat. Coba lagi ya." else null,
            )
        }
    }

    private fun prefetchArtistThumbnails() {
        viewModelScope.launch {
            BUILT_IN_ARTISTS.chunked(4).forEach { batch ->
                batch.map { name ->
                    async(Dispatchers.IO) {
                        name to fetchArtistThumbnail(name)
                    }
                }.awaitAll().forEach { (name, thumbnail) ->
                    if (thumbnail.isNotBlank()) {
                        artistThumbnailMemory[name] = thumbnail
                        artistThumbnails.update { current -> current + (name to thumbnail) }
                    }
                }
            }
        }
    }

    private suspend fun fetchArtistThumbnail(name: String): String = withContext(Dispatchers.IO) {
        runCatching {
            extractor.searchSongs(name)
                .getOrNull()
                .orEmpty()
                .firstOrNull { it.thumbnailUrl.isNotBlank() }
                ?.thumbnailUrl
                .orEmpty()
        }.getOrDefault("")
    }

    private fun buildArtists(
        history: List<Song>,
        liked: List<Song>,
        playlistSongs: List<Song>,
        thumbnails: Map<String, String>,
    ): List<ArtistItem> {
        val result = mutableListOf<ArtistItem>()
        val seen = HashSet<String>()

        fun add(nameRaw: String, thumbnailUrl: String) {
            val name = cleanArtistName(nameRaw)
            if (name.isBlank()) return
            if (seen.add(name.lowercase())) {
                result += ArtistItem(
                    name = name,
                    thumbnailUrl = thumbnailUrl,
                    fromLibrary = true,
                )
            }
        }

        // Sumber utama: riwayat putar & isi playlist/liked dari Room.
        (history + playlistSongs + liked).forEach { add(it.artist, it.thumbnailUrl) }

        // Sisanya dilengkapi katalog bawaan supaya rak selalu hidup.
        BUILT_IN_ARTISTS.forEach { name ->
            if (seen.add(name.lowercase())) {
                result += ArtistItem(
                    name = name,
                    thumbnailUrl = thumbnails[name].orEmpty(),
                    fromLibrary = false,
                )
            }
        }
        return result.take(10)
    }

    private fun cleanArtistName(raw: String): String = raw.trim()
        .replace(Regex("""\s*-\s*Topic$"""), "")
        .replace(Regex("""\s*\(?(?:Official|OFFICIAL|VEVO|Vevo)\)?$"""), "")
        .trim()

    private data class LibrarySnapshot(
        val history: List<Song> = emptyList(),
        val frequent: List<Song> = emptyList(),
        val liked: List<Song> = emptyList(),
        val playlists: List<LocalPlaylistEntity> = emptyList(),
        val playlistSongs: List<Song> = emptyList(),
    )

    private data class RemoteCatalog(
        val loading: Boolean = true,
        val hits: List<Song> = emptyList(),
        val chill: List<Song> = emptyList(),
        val error: String? = null,
    )

    private companion object {
        val BUILT_IN_ARTISTS = listOf(
            "Tulus",
            "Bernadya",
            "Hindia",
            "Denny Caknan",
            "Nadin Amizah",
            "Sheila On 7",
            "Bruno Mars",
            "Billie Eilish",
        )
    }
}
