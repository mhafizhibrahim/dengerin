package com.dengerin.app.presentation

import com.dengerin.app.data.lyrics.LyricLine
import com.dengerin.app.data.model.Song

/** State yang dipakai kartu lirik di Now Playing sekaligus layar fullscreen. */
data class LyricsUiState(
    val song: Song? = null,
    val lines: List<LyricLine> = emptyList(),
    val plainLines: List<String> = emptyList(),
    val isSynced: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val syncOffsetMs: Long = 0L,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val activeLineIndex: Int = -1,
) {
    val hasLyrics: Boolean get() = if (isSynced) lines.isNotEmpty() else plainLines.isNotEmpty()
}
