package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoadingCatalog: Boolean = true,
    val history: List<Song> = emptyList(),
    val frequent: List<Song> = emptyList(),
    val liked: List<Song> = emptyList(),
    val playlists: List<LocalPlaylistEntity> = emptyList(),
    val hits: List<Song> = emptyList(),
    val chill: List<Song> = emptyList(),
    val catalogError: String? = null,
)

class HomeViewModel(
    private val library: LibraryRepository,
    private val extractor: MusicExtractorRepository,
) : ViewModel() {

    private val remote = MutableStateFlow(RemoteCatalog())
    val uiState: StateFlow<HomeUiState> = combine(
        library.history,
        library.frequent,
        library.liked,
        library.playlists,
        remote,
    ) { history, frequent, liked, playlists, remoteCatalog ->
        HomeUiState(
            isLoadingCatalog = remoteCatalog.loading,
            history = history,
            frequent = frequent,
            liked = liked,
            playlists = playlists,
            hits = remoteCatalog.hits,
            chill = remoteCatalog.chill,
            catalogError = remoteCatalog.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refreshCatalog()
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

    private data class RemoteCatalog(
        val loading: Boolean = true,
        val hits: List<Song> = emptyList(),
        val chill: List<Song> = emptyList(),
        val error: String? = null,
    )
}
