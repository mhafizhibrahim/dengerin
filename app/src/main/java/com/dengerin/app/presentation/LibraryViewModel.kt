package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val liked: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val playlists: List<LocalPlaylistEntity> = emptyList(),
)

data class LibraryActionState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val importedPlaylistId: String? = null,
    val error: String? = null,
    val progress: String? = null,
)

class LibraryViewModel(
    private val library: LibraryRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        library.liked,
        library.history,
        library.playlists,
    ) { liked, history, playlists ->
        LibraryUiState(liked, history, playlists)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val mutableAction = MutableStateFlow(LibraryActionState())
    val action: StateFlow<LibraryActionState> = mutableAction.asStateFlow()

    fun importLink(link: String) {
        viewModelScope.launch {
            mutableAction.update {
                it.copy(
                    isBusy = true,
                    error = null,
                    message = null,
                    importedPlaylistId = null,
                    progress = "Menyiapkan playlist…",
                )
            }
            library.importPlaylist(
                link = link,
                onProgress = { text -> mutableAction.update { it.copy(progress = text) } },
            ).fold(
                onSuccess = { id ->
                    mutableAction.update {
                        it.copy(
                            isBusy = false,
                            importedPlaylistId = id,
                            message = "Playlist berhasil diimpor.",
                            progress = null,
                        )
                    }
                },
                onFailure = { throwable ->
                    mutableAction.update {
                        it.copy(
                            isBusy = false,
                            progress = null,
                            error = throwable.message?.takeIf { m -> m.isNotBlank() }
                                ?: "Gagal mengimpor playlist. Cek linknya ya.",
                        )
                    }
                },
            )
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val id = library.createPlaylist(name)
            mutableAction.update {
                it.copy(isBusy = false, importedPlaylistId = id, message = "Playlist dibuat.")
            }
        }
    }

    fun consumeAction() = mutableAction.update {
        it.copy(message = null, error = null, importedPlaylistId = null, progress = null)
    }
}
