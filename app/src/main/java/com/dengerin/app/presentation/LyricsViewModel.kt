package com.dengerin.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.lyrics.LrcParser
import com.dengerin.app.data.lyrics.LyricsRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.player.PlayerController
import com.dengerin.app.player.PlayerUiState
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
class LyricsViewModel(
    private val playerController: PlayerController,
    private val repository: LyricsRepository,
) : ViewModel() {

    private val playerState = MutableStateFlow(PlayerUiState())
    private val mutableLyrics = MutableStateFlow(LyricsUiState())
    private var currentKey: String = ""

    val uiState: StateFlow<LyricsUiState> = combine(playerState, mutableLyrics) { player, lyrics ->
        val effectivePosition = player.positionMs + lyrics.syncOffsetMs
        val activeIndex = if (lyrics.isSynced) {
            LrcParser.activeIndex(lyrics.lines, effectivePosition)
        } else {
            -1
        }
        lyrics.copy(
            song = player.song,
            positionMs = player.positionMs,
            durationMs = if (player.durationMs > 0) player.durationMs else player.song?.durationMs ?: 0L,
            isPlaying = player.isPlaying,
            activeLineIndex = activeIndex,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LyricsUiState())

    init {
        viewModelScope.launch {
            playerController.uiState.collect { playerState.value = it }
        }
        viewModelScope.launch {
            playerState.map { it.song }
                .distinctUntilChanged(::sameSong)
                .collect { song -> loadFor(song) }
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
                        ?: "Lirik belum tersedia untuk lagu ini.",
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

    fun seekToLine(index: Int) {
        val lyrics = mutableLyrics.value
        val line = lyrics.lines.getOrNull(index) ?: return
        playerController.seekTo(line.timestampMs + lyrics.syncOffsetMs)
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
