package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.artist.ArtistImageRepository
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
import java.util.concurrent.ConcurrentHashMap

data class ArtistItem(
    val name: String,
    /** Fallback berupa thumbnail lagu saat foto profil tidak tersedia. */
    val thumbnailUrl: String,
    /** Foto wajah asli dari Deezer. */
    val imageUrl: String = "",
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
    val selectedMood: String = "Semua",
    val moodRecommendations: List<Song> = emptyList(),
    val isLoadingMood: Boolean = false,
)

class HomeViewModel(
    private val library: LibraryRepository,
    private val extractor: MusicExtractorRepository,
    private val artistImages: ArtistImageRepository,
) : ViewModel() {

    private val remote = MutableStateFlow(RemoteCatalog())
    private val artistImageUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    private val moodState = MutableStateFlow(MoodSelection())

    /** Cache memori per mood supaya pindah chip terasa instan. */
    private val moodMemory = ConcurrentHashMap<String, List<Song>>()
    private val loadingMoods = ConcurrentHashMap.newKeySet<String>()
    private val requestedArtists = ConcurrentHashMap.newKeySet<String>()

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
        artistImageUrls,
        moodState,
    ) { snapshot, remoteCatalog, imageUrls, mood ->
        HomeUiState(
            isLoadingCatalog = remoteCatalog.loading,
            history = snapshot.history,
            frequent = snapshot.frequent,
            liked = snapshot.liked,
            playlists = snapshot.playlists,
            hits = remoteCatalog.hits,
            chill = remoteCatalog.chill,
            artists = buildArtists(snapshot.history, snapshot.liked, snapshot.playlistSongs, imageUrls),
            catalogError = remoteCatalog.error,
            selectedMood = mood.mood,
            moodRecommendations = mood.songs,
            isLoadingMood = mood.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshCatalog()
        observeArtistImages()
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

    /**
     * Ambil foto wajah artis yang tampil di rak (riwayat/playlist + bawaan).
     * Pemanggilan Deezer cukup sekali per nama artis berkat cache repository + [requestedArtists].
     */
    private fun observeArtistImages() {
        viewModelScope.launch {
            librarySnapshot.collect { snapshot ->
                val names = buildList {
                    addAll(snapshot.history.map { it.artist })
                    addAll(snapshot.playlistSongs.map { it.artist })
                    addAll(snapshot.liked.map { it.artist })
                    addAll(BUILT_IN_ARTISTS)
                }
                    .map(::cleanArtistName)
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .take(10)

                names.filter { requestedArtists.add(it.lowercase()) }
                    .map { name -> async(Dispatchers.IO) { name to artistImages.getImageUrl(name) } }
                    .awaitAll()
                    .forEach { (name, url) ->
                        if (url.isNotBlank()) {
                            artistImageUrls.update { current -> current + (name.lowercase() to url) }
                        }
                    }
            }
        }
    }

    /** Chip mood: langsung pakai cache bila sudah pernah dimuat. */
    fun selectMood(mood: String) {
        val cached = moodMemory[mood]
        moodState.update {
            MoodSelection(
                mood = mood,
                songs = cached.orEmpty(),
                loading = mood != "Semua" && cached == null,
            )
        }
        if (mood == "Semua" || cached != null || !loadingMoods.add(mood)) return

        viewModelScope.launch {
            val songs = runCatching {
                MOOD_QUERIES[mood].orEmpty()
                    .let { query -> extractor.searchSongs(query).getOrNull().orEmpty().take(12) }
            }.getOrDefault(emptyList())
            moodMemory[mood] = songs
            loadingMoods.remove(mood)
            if (moodState.value.mood == mood) {
                moodState.update { it.copy(songs = songs, loading = false) }
            }
        }
    }

    private fun buildArtists(
        history: List<Song>,
        liked: List<Song>,
        playlistSongs: List<Song>,
        imageUrls: Map<String, String>,
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
                    imageUrl = imageUrls[name.lowercase()].orEmpty(),
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
                    thumbnailUrl = "",
                    imageUrl = imageUrls[name.lowercase()].orEmpty(),
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

    private data class MoodSelection(
        val mood: String = "Semua",
        val songs: List<Song> = emptyList(),
        val loading: Boolean = false,
    )

    private companion object {
        val MOOD_QUERIES = linkedMapOf(
            "Santai" to "lagu santai indonesia akustik",
            "Semangat" to "lagu semangat indonesia upbeat",
            "Fokus" to "lagu instrumental fokus belajar",
            "Perjalanan" to "lagu perjalanan roadtrip indonesia",
            "Galau" to "lagu galau sedih indonesia",
        )

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
