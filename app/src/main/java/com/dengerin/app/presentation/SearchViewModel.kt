package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.core.network.NetworkMonitor
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.genre.GenreCatalog
import com.dengerin.app.data.genre.GenreCategory
import com.dengerin.app.data.genre.GenreCoverRepository
import com.dengerin.app.data.lyrics.LyricSearchHit
import com.dengerin.app.data.lyrics.LyricSearchRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.PlaybackPreferences
import com.dengerin.app.data.preferences.isDataSaverActive
import com.dengerin.app.data.recent.RecentSearchStore
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyCatalogTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.FlowPreview::class)
class SearchViewModel(
    private val repository: MusicExtractorRepository,
    private val recentSearchStore: RecentSearchStore,
    private val genreCoverRepository: GenreCoverRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val networkMonitor: NetworkMonitor,
    private val lyricSearchRepository: LyricSearchRepository,
    private val spotifyAccountImportRepository: SpotifyAccountImportRepository? = null,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        SearchUiState(genreCovers = genreCoverRepository.snapshot()),
    )
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()

    /** Sumber ketikan untuk live search; UI state diperbarui seketika, request tidak. */
    private val queryFlow = MutableStateFlow("")

    private var fullSearchJob: Job? = null
    private var genreJob: Job? = null
    private var coverJob: Job? = null
    private var generation = 0
    private var liveEnabled = true
    private var genreToken = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recentSearchStore.migrateLegacy()
            mutableUiState.update { it.copy(genreCovers = genreCoverRepository.snapshot()) }
        }

        viewModelScope.launch {
            recentSearchStore.recentQueries.collect { queries ->
                mutableUiState.update { it.copy(recentQueries = queries) }
            }
        }

        // Tunggu DataStore terbaca supaya keputusan Data Saver tidak keliru.
        viewModelScope.launch {
            combine(
                playbackPreferences.settingsLoaded,
                playbackPreferences.settings,
                networkMonitor.state,
            ) { loaded, settings, network ->
                loaded to (settings.isDataSaverActive(network) && network.isCellular)
            }
                .filter { (loaded, _) -> loaded }
                .map { (_, restricted) -> restricted }
                .distinctUntilChanged()
                .collect { restricted ->
                    mutableUiState.update { it.copy(autoFetchCovers = !restricted) }
                    if (!restricted) preloadGenreCovers()
                }
        }

        // Debounce 350 ms + distinct + collectLatest: ketikan lama otomatis dibatalkan.
        viewModelScope.launch {
            queryFlow
                .debounce(LIVE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { raw -> runLive(raw.trim()) }
        }
    }

    /** Memanggil ini saat kolom cari difocus: tampilkan Top Bar aktif. */
    fun activateSearch() {
        mutableUiState.update { it.copy(isSearchActive = true) }
    }

    /** Panah kembali: tutup keyboard (UI), kosongkan query, balik ke grid. */
    fun closeSearch() {
        generation++
        liveEnabled = false
        queryFlow.value = ""
        fullSearchJob?.cancel()
        mutableUiState.update {
            it.copy(
                query = "",
                isSearchActive = false,
                suggestions = emptyList(),
                lyricMatches = emptyList(),
                matchedGenres = emptyList(),
                songs = emptyList(),
                isLoading = false,
                error = null,
                hasSearched = false,
                lastQuery = "",
            )
        }
    }

    /**
     * Ketikan langsung memicu live search setelah debounce. Tidak perlu Enter.
     * Semua hasil dibatalkan otomatis oleh [collectLatest] bila pengguna mengetik lagi.
     */
    fun onQueryChange(query: String) {
        generation++
        liveEnabled = true
        mutableUiState.update {
            it.copy(
                query = query,
                isSearchActive = true,
                error = null,
                matchedGenres = emptyList(),
                suggestions = emptyList(),
                lyricMatches = emptyList(),
            )
        }
        if (query.isBlank()) {
            fullSearchJob?.cancel()
            mutableUiState.update {
                it.copy(
                    songs = emptyList(),
                    trackResults = emptyList(),
                    artistResults = emptyList(),
                    playlistResults = emptyList(),
                    topResult = null,
                    catalogLoading = false,
                    catalogError = null,
                    isLoading = false,
                    hasSearched = false,
                    lastQuery = "",
                    error = null,
                )
            }
        }
        queryFlow.value = query
    }

    fun selectFilter(filter: SearchFilter) {
        mutableUiState.update { it.copy(selectedFilter = filter) }
    }

    /** Enter/Search, chip riwayat, hashtag, atau kartu artis di Beranda. */
    fun submitSearch() = search()

    /** Dipakai saran, riwayat, hashtag, dan prefill dari halaman lain. */
    fun prefillAndSearch(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank()) return
        mutableUiState.update { it.copy(query = keyword, isSearchActive = true) }
        queryFlow.value = keyword
        runFullSearch(keyword, saveHistory = true)
    }

    /** Isi kolom dari saran; [saveHistory] false untuk panah ↖ (keyboard tetap terbuka). */
    fun useSuggestion(suggestion: String, saveHistory: Boolean) {
        val keyword = suggestion.trim()
        if (keyword.isBlank()) return
        mutableUiState.update { it.copy(query = keyword, isSearchActive = true) }
        queryFlow.value = keyword
        runFullSearch(keyword, saveHistory = saveHistory)
    }

    fun search(query: String? = null) {
        val keyword = (query ?: mutableUiState.value.query).trim()
        if (keyword.isBlank()) return
        runFullSearch(keyword, saveHistory = true)
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { recentSearchStore.remove(query) }
    }

    fun retry() {
        val genre = mutableUiState.value.activeGenre
        if (genre != null) openGenre(genre) else search()
    }

    fun openGenre(genre: GenreCategory) {
        generation++
        liveEnabled = false
        queryFlow.value = ""
        fullSearchJob?.cancel()
        val token = ++genreToken
        mutableUiState.update {
            it.copy(
                activeGenreId = genre.id,
                matchedGenres = emptyList(),
                genreSongs = emptyList(),
                genreLoading = true,
                genreError = null,
            )
        }

        // Ketukan genre adalah pengecualian: tetap ambil sampul walau Data Saver aktif.
        coverJob?.cancel()
        coverJob = viewModelScope.launch(Dispatchers.IO) {
            mutableUiState.update { state -> state.copy(genreFetching = state.genreFetching + genre.id) }
            val url = genreCoverRepository.fetch(genre)
            if (url != null) {
                mutableUiState.update { state ->
                    state.copy(genreCovers = state.genreCovers + (genre.id to url))
                }
            }
            mutableUiState.update { state ->
                state.copy(genreFetching = state.genreFetching - genre.id)
            }
        }

        genreJob?.cancel()
        genreJob = viewModelScope.launch {
            val result = try {
                repository.searchSongs(genre.searchQuery)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            if (token != genreToken) return@launch
            result.fold(
                onSuccess = { songs ->
                    recentSearchStore.add(genre.searchQuery)
                    mutableUiState.update {
                        it.copy(
                            genreSongs = songs,
                            genreLoading = false,
                            genreError = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    mutableUiState.update {
                        it.copy(
                            genreSongs = emptyList(),
                            genreLoading = false,
                            genreError = throwable.message?.takeIf { message -> message.isNotBlank() }
                                ?: "Waduh, gagal memuat lagu genre. Coba lagi ya.",
                        )
                    }
                },
            )
        }
    }

    fun closeGenre() {
        genreToken++
        genreJob?.cancel()
        mutableUiState.update {
            it.copy(
                activeGenreId = null,
                genreSongs = emptyList(),
                genreLoading = false,
                genreError = null,
            )
        }
    }

    /** Auto-fetch semua sampul yang belum ter-cache; dilewati saat Data Saver + seluler. */
    private fun preloadGenreCovers() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!mutableUiState.value.autoFetchCovers) return@launch
            val covers = genreCoverRepository.preload(GenreCatalog.all)
            mutableUiState.update { it.copy(genreCovers = covers) }
        }
    }

    /** Live search paralel: saran + lirik + lagu, dibatalkan oleh ketikan berikutnya. */
    private suspend fun runLive(keyword: String) {
        if (!liveEnabled) return
        val currentGeneration = generation
        if (keyword.length < 2) {
            if (keyword.isBlank()) {
                mutableUiState.update {
                    it.copy(
                        suggestions = emptyList(),
                        lyricMatches = emptyList(),
                        matchedGenres = emptyList(),
                        songs = emptyList(),
                        trackResults = emptyList(),
                        artistResults = emptyList(),
                        playlistResults = emptyList(),
                        topResult = null,
                        catalogLoading = false,
                        catalogError = null,
                        isLoading = false,
                        error = null,
                        hasSearched = false,
                        lastQuery = "",
                    )
                }
            } else {
                mutableUiState.update {
                    it.copy(
                        suggestions = emptyList(),
                        lyricMatches = emptyList(),
                        songs = emptyList(),
                        trackResults = emptyList(),
                        artistResults = emptyList(),
                        playlistResults = emptyList(),
                        topResult = null,
                        catalogLoading = false,
                        isLoading = false,
                    )
                }
            }
            return
        }

        mutableUiState.update {
            it.copy(
                isLoading = true,
                catalogLoading = true,
                error = null,
                catalogError = null,
                hasSearched = true,
                lastQuery = keyword,
                matchedGenres = GenreCatalog.matches(keyword),
            )
        }

        val live = withContext(Dispatchers.IO) {
            coroutineScope {
                val suggestionsDeferred = async { repository.suggestions(keyword).getOrElse { emptyList() } }
                val lyricsDeferred = async {
                    if (shouldSearchLyrics(keyword)) lyricSearchRepository.search(keyword) else emptyList()
                }
                val catalogDeferred = async {
                    spotifyAccountImportRepository?.searchSpotifyCatalog(keyword)
                        ?.getOrNull()
                }
                val songsDeferred = async { repository.searchSongs(keyword).getOrElse { emptyList() } }
                LiveSearchPayload(
                    suggestions = suggestionsDeferred.await(),
                    lyrics = lyricsDeferred.await(),
                    catalog = catalogDeferred.await(),
                    fallbackSongs = songsDeferred.await(),
                )
            }
        }

        if (currentGeneration != generation || !liveEnabled) return
        val catalogTracks = live.catalog?.tracks.orEmpty()
        val songs = if (catalogTracks.isNotEmpty()) catalogTracks.map(::catalogTrackToSong) else live.fallbackSongs
        mutableUiState.update {
            it.copy(
                suggestions = live.suggestions.take(LIVE_SUGGESTIONS),
                lyricMatches = live.lyrics,
                songs = songs.take(LIVE_RESULTS),
                trackResults = catalogTracks.take(LIVE_RESULTS),
                artistResults = live.catalog?.artists.orEmpty(),
                playlistResults = live.catalog?.playlists.orEmpty(),
                topResult = live.catalog?.topResult,
                isLoading = false,
                catalogLoading = false,
                catalogError = null,
                error = null,
            )
        }
    }

    private data class LiveSearchPayload(
        val suggestions: List<String>,
        val lyrics: List<LyricSearchHit>,
        val catalog: com.dengerin.app.data.spotify.SpotifyCatalogSearchResult?,
        val fallbackSongs: List<Song>,
    )

    /** Enter menampilkan hasil penuh (15–20 lagu), bukan potongan live search. */
    private fun runFullSearch(keyword: String, saveHistory: Boolean) {
        generation++
        liveEnabled = false
        fullSearchJob?.cancel()
        val currentGeneration = generation
        fullSearchJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    query = keyword,
                    isSearchActive = true,
                    isLoading = true,
                    catalogLoading = true,
                    error = null,
                    catalogError = null,
                    hasSearched = true,
                    lastQuery = keyword,
                    matchedGenres = GenreCatalog.matches(keyword),
                    suggestions = emptyList(),
                    lyricMatches = emptyList(),
                )
            }
            val query = mutableUiState.value.matchedGenres.firstOrNull()?.searchQuery ?: keyword
            val payload = withContext(Dispatchers.IO) {
                coroutineScope {
                    val catalogDeferred = async { spotifyAccountImportRepository?.searchSpotifyCatalog(query)?.getOrNull() }
                    val songsDeferred = async { repository.searchSongs(query) }
                    catalogDeferred.await() to songsDeferred.await()
                }
            }
            if (currentGeneration != generation) return@launch
            val (catalog, result) = payload
            val catalogTracks = catalog?.tracks.orEmpty()
            result.fold(
                onSuccess = { fallbackSongs ->
                    if (saveHistory) recentSearchStore.add(keyword)
                    val songs = if (catalogTracks.isNotEmpty()) catalogTracks.map(::catalogTrackToSong) else fallbackSongs
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            catalogLoading = false,
                            songs = songs.take(FULL_RESULTS),
                            trackResults = catalogTracks.take(FULL_RESULTS),
                            artistResults = catalog?.artists.orEmpty(),
                            playlistResults = catalog?.playlists.orEmpty(),
                            topResult = catalog?.topResult,
                            error = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    if (catalogTracks.isNotEmpty()) {
                        if (saveHistory) recentSearchStore.add(keyword)
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                catalogLoading = false,
                                songs = catalogTracks.map(::catalogTrackToSong).take(FULL_RESULTS),
                                trackResults = catalogTracks.take(FULL_RESULTS),
                                artistResults = catalog?.artists.orEmpty(),
                                playlistResults = catalog?.playlists.orEmpty(),
                                topResult = catalog?.topResult,
                                error = null,
                            )
                        }
                    } else {
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                catalogLoading = false,
                                songs = emptyList(),
                                error = throwable.message?.takeIf { message -> message.isNotBlank() }
                                    ?: "Waduh, gagal nyari lagu. Coba lagi ya.",
                            )
                        }
                    }
                },
            )
        }
    }

    private fun catalogTrackToSong(track: SpotifyCatalogTrack): Song = Song(
        id = track.id,
        title = track.title,
        artist = track.artist,
        durationMs = track.durationMs,
        url = track.uri.takeIf { it.startsWith("http") }.orEmpty(),
        thumbnailUrl = track.thumbnailUrl,
        coverUrl = track.coverUrl,
        spotifyId = track.id,
        artistId = track.artistId,
        artistIds = track.artistIds,
        source = "SPOTIFY",
    )

    /** Lirik dicari hanya untuk kalimat (≥3 kata / ≥12 karakter) dan bukan nama genre. */
    private fun shouldSearchLyrics(keyword: String): Boolean {
        val normalized = normalize(keyword)
        val isGenre = GenreCatalog.all.any { genre ->
            normalize(genre.title) == normalized || genre.aliases.any { normalize(it) == normalized }
        }
        if (isGenre) return false
        val words = keyword.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size >= 3 || keyword.trim().length >= 12
    }

    private fun normalize(value: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    override fun onCleared() {
        fullSearchJob?.cancel()
        genreJob?.cancel()
        coverJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val LIVE_DEBOUNCE_MS = 350L
        const val LIVE_SUGGESTIONS = 5
        const val LIVE_RESULTS = 8
        const val FULL_RESULTS = 20
    }
}
