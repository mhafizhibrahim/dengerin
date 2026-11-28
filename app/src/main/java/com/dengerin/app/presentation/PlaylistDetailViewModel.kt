package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.db.PlaylistTrackEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val playlistId: String,
    private val library: LibraryRepository,
) : ViewModel() {

    /** Entri dengan entryId unik, dipakai untuk menghapus tepat satu lagu duplikat. */
    val entries: StateFlow<List<PlaylistTrackEntity>> = library.playlistTrackEntries(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<Song>> = library.playlistTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val info: StateFlow<LocalPlaylistEntity?> = combine(
        library.playlists,
        flow { emit(library.playlistInfo(playlistId)) },
    ) { list, fallback ->
        list.firstOrNull { it.id == playlistId } ?: fallback
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Snapshot antrean untuk diputar. Tidak melakukan resolusi massal: untuk
     * playlist ribuan lagu itu akan memicu ribuan pencarian YouTube sekaligus.
     * PlayerController menyelesaikan URL YouTube per lagu saat lagu itu diputar.
     */
    fun resolvedTracks(): List<Song> = tracks.value.toList()

    fun rename(name: String, description: String) {
        if (isVirtual()) return
        viewModelScope.launch { library.renamePlaylist(playlistId, name, description) }
    }

    fun delete() {
        if (isVirtual()) return
        viewModelScope.launch { library.deletePlaylist(playlistId) }
    }

    fun removeEntry(entryId: String) {
        if (isVirtual()) return
        viewModelScope.launch { library.removeTrackEntry(playlistId, entryId) }
    }

    fun playlistId(): String = playlistId

    private fun isVirtual() = playlistId == LibraryRepository.PLAYLIST_LIKED ||
        playlistId == LibraryRepository.PLAYLIST_HISTORY
}
