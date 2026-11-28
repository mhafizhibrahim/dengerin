package com.dengerin.app.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PlaylistOption(
    val playlist: LocalPlaylistEntity,
    val songCount: Int,
    val containsSong: Boolean,
)

data class AddToPlaylistUiState(
    val playlists: List<PlaylistOption> = emptyList(),
    val isLiked: Boolean = false,
    val isBusy: Boolean = false,
)

/** Satu ViewModel dipakai AddToPlaylistSheet untuk satu lagu yang sedang dipilih. */
class AddToPlaylistViewModel(
    private val song: Song,
    private val library: LibraryRepository,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AddToPlaylistUiState())
    val uiState: StateFlow<AddToPlaylistUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            library.playlists.collect { playlists -> refresh(playlists) }
        }
    }

    private suspend fun refresh() = refresh(library.playlists.first())

    private suspend fun refresh(playlists: List<LocalPlaylistEntity>) {
        val options = playlists.map { playlist ->
            PlaylistOption(
                playlist = playlist,
                songCount = library.playlistSongCount(playlist.id),
                containsSong = library.isSongInPlaylist(playlist.id, song),
            )
        }
        val liked = library.isLiked(song.spotifyId.ifBlank { song.id })
        mutableUiState.update { it.copy(playlists = options, isLiked = liked) }
    }

    fun createPlaylistAndAdd(name: String, onDone: (String) -> Unit) {
        val playlistName = name.trim()
        if (playlistName.isBlank() || mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isBusy = true) }
            runCatching { library.createPlaylistWithSong(playlistName, song) }
                .onSuccess {
                    refresh()
                    onDone("Playlist '$playlistName' dibuat · lagu langsung masuk")
                }
                .onFailure { onDone("Gagal membuat playlist. Coba lagi ya.") }
            mutableUiState.update { it.copy(isBusy = false) }
        }
    }

    fun toggleLiked(onDone: (String) -> Unit) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isBusy = true) }
            val liked = runCatching { library.toggleLike(song) }.getOrDefault(false)
            mutableUiState.update { it.copy(isLiked = liked, isBusy = false) }
            onDone(if (liked) "Masuk Lagu Disukai" else "Dihapus dari Lagu Disukai")
        }
    }

    fun togglePlaylist(option: PlaylistOption, onDone: (String) -> Unit) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isBusy = true) }
            val result = runCatching { library.toggleSongInPlaylist(option.playlist.id, song) }
            refresh()
            mutableUiState.update { it.copy(isBusy = false) }
            result.fold(
                onSuccess = { added ->
                    val verb = if (added) "Ditambahkan ke" else "Dihapus dari"
                    onDone("$verb ${option.playlist.name}")
                },
                onFailure = { onDone("Gagal mengubah playlist. Coba lagi ya.") },
            )
        }
    }
}
