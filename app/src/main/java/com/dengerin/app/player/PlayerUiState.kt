package com.dengerin.app.player

import com.dengerin.app.data.model.Song

data class PlayerUiState(
    val song: Song? = null,
    val queue: List<Song> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: Int = REPEAT_OFF, // 0 off, 1 all, 2 one
) {
    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }
}
