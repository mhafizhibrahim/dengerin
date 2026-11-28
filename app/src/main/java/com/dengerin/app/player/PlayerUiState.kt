package com.dengerin.app.player

import androidx.compose.runtime.Immutable
import com.dengerin.app.data.model.Song

@Immutable
data class PlayerUiState(
    val song: Song? = null,
    /** Snapshot lagu untuk komponen lama; `queueItems` dipakai QueueSheet untuk kunci unik. */
    val queue: List<Song> = emptyList(),
    val queueItems: List<QueueItem> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: Int = REPEAT_OFF, // 0 off, 1 all, 2 one
    /** Radio otomatis saat lagu terakhir selesai (dari DataStore). */
    val isAutoplay: Boolean = true,
    /** Nama playlist/artis/pencarian asal antrean konteks. */
    val contextTitle: String = "",
    /** ID entri antrean yang sedang diputar; stabil walaupun lagu duplikat. */
    val currentQueueId: String? = null,
    /** True saat audio sedang dibaca dari cache disk (preview kuota di Now Playing). */
    val isPlayingFromCache: Boolean = false,
) {
    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }
}
