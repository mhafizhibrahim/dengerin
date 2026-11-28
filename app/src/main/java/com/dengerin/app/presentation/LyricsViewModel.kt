package com.dengerin.app.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.lyrics.NO_LYRICS_MESSAGE
import com.dengerin.app.data.lyrics.LyricsRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Satu state lirik untuk kartu pratinjau dan layar fullscreen. Posisi lagu diambil terus
 * dari [PlayerController] yang sudah men-update tiap ~160 ms, jadi baris aktif berpindah
 * bersama ketukan tanpa perlu timer tambahan.
 */
@Immutable
data class LyricsPreview(
    val hasLyrics: Boolean = false,
    val isLoading: Boolean = false,
)

class LyricsViewModel(
    private val playerController: PlayerController,
    private val repository: LyricsRepository,
) : ViewModel() {

    private val mutableLyrics = MutableStateFlow(LyricsUiState())
    private var currentKey: String = ""

    /**
     * Hanya lagu yang diteruskan dari player. Timeline tidak ikut digabung di sini:
     * komputasi baris aktif dipindah ke UI (`derivedStateOf`) supaya perubahan posisi
     * tidak memicu recomposition kartu/layar lirik.
     */
    private val currentSong = playerController.uiState
        .map { it.song }
        .distinctUntilChanged(::sameSong)

    val uiState: StateFlow<LyricsUiState> = combine(currentSong, mutableLyrics) { song, lyrics ->
        lyrics.copy(song = song)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LyricsUiState())

    /** Hanya dipakai NowPlaying untuk memutuskan render kartu; tidak ikut tick posisi. */
    val preview: StateFlow<LyricsPreview> = uiState
        .map { LyricsPreview(it.hasLyrics, it.isLoading) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LyricsPreview())

    init {
        viewModelScope.launch {
            currentSong.collect { song -> loadFor(song) }
        }
    }

    private suspend fun loadFor(song: Song?) {
        currentKey = if (song == null) "" else repository.cacheKey(song)
        if (song == null || currentKey.isBlank()) {
            mutableLyrics.value = LyricsUiState()
            return
        }
        mutableLyrics.value = LyricsUiState(
            song = song,
            isLoading = true,
            syncOffsetMs = repository.syncOffset(currentKey),
        )
        repository.load(song).fold(
            onSuccess = { bundle ->
                if (currentKey != repository.cacheKey(song)) return@fold
                mutableLyrics.value = LyricsUiState(
                    song = song,
                    lines = bundle.syncedLines,
                    plainLines = bundle.plainLines,
                    isSynced = bundle.isSynced,
                    sourceLabel = bundle.sourceLabel,
                    isLoading = false,
                    syncOffsetMs = repository.syncOffset(currentKey),
                )
            },
            onFailure = { throwable ->
                if (currentKey != repository.cacheKey(song)) return@fold
                mutableLyrics.value = LyricsUiState(
                    song = song,
                    isLoading = false,
                    error = throwable.message?.takeIf { it.isNotBlank() }
                        ?: NO_LYRICS_MESSAGE,
                    syncOffsetMs = repository.syncOffset(currentKey),
                )
            },
        )
    }

    /** Geser waktu lirik kelipatan 500 ms untuk mengatasi selisih intro audio YouTube. */
    fun adjustSyncOffset(deltaMs: Long) {
        if (currentKey.isBlank()) return
        val next = (mutableLyrics.value.syncOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        repository.setSyncOffset(currentKey, next)
        mutableLyrics.value = mutableLyrics.value.copy(syncOffsetMs = next)
    }

    fun resetSyncOffset() = adjustSyncOffset(-mutableLyrics.value.syncOffsetMs)

    /** Tap baris lirik = seek ke timestamp asli baris tersebut. */
    fun seekToLine(index: Int) {
        val line = mutableLyrics.value.lines.getOrNull(index) ?: return
        playerController.seekTo(line.timestampMs)
    }

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun togglePlayPause() = playerController.togglePlayPause()

    private fun sameSong(a: Song?, b: Song?): Boolean {
        if (a == null || b == null) return a == b
        return a.id == b.id ||
            (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId) ||
            (a.title.equals(b.title, ignoreCase = true) && a.artist.equals(b.artist, ignoreCase = true))
    }
}
