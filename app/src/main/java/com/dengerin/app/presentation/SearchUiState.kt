package com.dengerin.app.presentation

import androidx.compose.runtime.Immutable
import com.dengerin.app.data.genre.GenreCatalog
import com.dengerin.app.data.genre.GenreCategory
import com.dengerin.app.data.lyrics.LyricSearchHit
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.spotify.SpotifyCatalogArtist
import com.dengerin.app.data.spotify.SpotifyCatalogPlaylist
import com.dengerin.app.data.spotify.SpotifyCatalogTopResult
import com.dengerin.app.data.spotify.SpotifyCatalogTrack

enum class SearchFilter(val label: String) {
    ALL("Semua"),
    SONGS("Lagu"),
    PLAYLISTS("Playlist"),
    ARTISTS("Artis"),
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val lastQuery: String = "",
    val recentQueries: List<String> = emptyList(),

    /** Mode Active Search: top bar khusus + hasil langsung tanpa menekan Enter. */
    val isSearchActive: Boolean = false,
    /** Maksimal 5 saran autocomplete NewPipe. */
    val suggestions: List<String> = emptyList(),
    /** Maksimal 2 lagu yang liriknya cocok dengan ketikan (LRCLIB). */
    val lyricMatches: List<LyricSearchHit> = emptyList(),

    /** Genre/alias yang cocok dengan ketikan; tampil paling atas hasil pencarian. */
    val matchedGenres: List<GenreCategory> = emptyList(),
    /** genreId -> URL mqdefault dari cache 7 hari. */
    val genreCovers: Map<String, String> = emptyMap(),
    val genreFetching: Set<String> = emptySet(),
    /** false saat Data Saver aktif di seluler: auto-fetch ditunda sampai Wi-Fi. */
    val autoFetchCovers: Boolean = true,

    val activeGenreId: String? = null,
    val genreSongs: List<Song> = emptyList(),
    val genreLoading: Boolean = false,
    val genreError: String? = null,

    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val topResult: SpotifyCatalogTopResult? = null,
    val artistResults: List<SpotifyCatalogArtist> = emptyList(),
    val playlistResults: List<SpotifyCatalogPlaylist> = emptyList(),
    val trackResults: List<SpotifyCatalogTrack> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
) {
    val activeGenre: GenreCategory? get() = GenreCatalog.find(activeGenreId)
}
