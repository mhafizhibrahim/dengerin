package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val library: LibraryRepository,
) : ViewModel() {

    val tracks: StateFlow<List<Song>> = library.playlistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val info: StateFlow<LocalPlaylistEntity?> = flow { emit(library.playlistInfo(playlistId)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Lagu Spotify yang belum punya videoUrl dicocokkan dulu sebelum diputar. */
    suspend fun resolvedTracks(): List<Song> = withContext(Dispatchers.IO) {
        val current = tracks.value
        library.resolve(current).getOrElse { current }
    }

    fun playlistId(): String = playlistId
}
