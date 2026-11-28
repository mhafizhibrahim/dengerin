package com.dengerin.app.player

/**
 * Kontrak yang dipakai service (MediaSession/ForwardingPlayer) untuk mengontrol
 * antrean tanpa perlu tahu implementasi QueueManager/PlayerController.
 *
 * Satu-satunya pemilik state ada di [PlayerController] (singleton di AppContainer),
 * sehingga perubahan yang berasal dari notifikasi, lockscreen, atau headset langsung
 * tercermin di Mini Player, Now Playing, dan Lirik.
 */
interface PlaybackQueueManager {
    val isLiked: Boolean
    val isShuffle: Boolean
    val hasNext: Boolean
    val hasPrevious: Boolean

    fun skipNext()
    fun skipPrevious()
    fun togglePlayPause()
    fun toggleLike()
    fun toggleShuffle()

    /** Hentian permanen: kosongkan antrean dan lepaskan sumber daya pemutaran. */
    fun stopPlayback()
}
