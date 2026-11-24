package com.dengerin.app.presentation

import com.dengerin.app.data.model.Song

data class SearchUiState(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val lastQuery: String = "",
)
