package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.recent.RecentSearchStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MusicExtractorRepository,
    private val recentSearchStore: RecentSearchStore,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(
        SearchUiState(recentQueries = recentSearchStore.all()),
    )
    val uiState: StateFlow<SearchUiState> = mutableUiState.asStateFlow()

    fun onQueryChange(query: String) {
        mutableUiState.update { it.copy(query = query, error = null) }
    }

    /** Dipakai chip riwayat, grid kategori, hashtag, dan kartu artis di Beranda. */
    fun prefillAndSearch(query: String) {
        val keyword = query.trim()
        mutableUiState.update { it.copy(query = keyword, error = null) }
        search(keyword)
    }

    fun search(query: String? = null) {
        val keyword = (query ?: mutableUiState.value.query).trim()
        if (keyword.isBlank()) return

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true,
                    lastQuery = keyword,
                )
            }
            repository.searchSongs(keyword).fold(
                onSuccess = { songs ->
                    recentSearchStore.add(keyword)
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            songs = songs,
                            recentQueries = recentSearchStore.all(),
                        )
                    }
                },
                onFailure = { throwable ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            songs = emptyList(),
                            error = throwable.message?.takeIf { message -> message.isNotBlank() }
                                ?: "Waduh, gagal nyari lagu. Coba lagi ya.",
                        )
                    }
                },
            )
        }
    }

    fun retry() = search()
}
