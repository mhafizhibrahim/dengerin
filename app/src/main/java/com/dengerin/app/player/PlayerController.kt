package com.dengerin.app.player

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.recent.RecentlyPlayedStore
import com.dengerin.app.data.repository.LibraryRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Satu titik kontrol untuk UI; terhubung ke MusicPlaybackService lewat MediaController. */
class PlayerController(
    context: Context,
    private val repository: MusicExtractorRepository,
    private val recentlyPlayed: RecentlyPlayedStore,
    private val library: LibraryRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var mediaController: MediaController? = null
    private var connectionFuture: ListenableFuture<MediaController>? = null
    private var playRequestId = 0
    private var originalQueue: List<Song> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutableState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val state = mutableState.value
                if (state.repeatMode == PlayerUiState.REPEAT_ALL) {
                    skipNext()
                } else {
                    mutableState.update { it.copy(isPlaying = false, positionMs = 0L) }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer gagal memutar stream.", error)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = playbackErrorMessage(error),
                )
            }
        }
    }

    init {
        connect()
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(appContext, token)
            .buildAsync()
        connectionFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(playerListener)
                        startPositionTicker()
                    }
                    .onFailure {
                        mutableState.update { it.copy(error = "Pemutar belum tersedia. Coba lagi sebentar.") }
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun startPositionTicker() {
        scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val current = controller.currentPosition
                        val total = controller.duration
                        mutableState.update { state ->
                            state.copy(
                                positionMs = current,
                                durationMs = if (total > 0) total else state.durationMs,
                            )
                        }
                    }
                }
                // 160 ms: perpindahan baris lirik terasa pas ketukan tanpa membanjiri recomposition.
                delay(POSITION_TICK_MS)
            }
        }
    }

    /**
     * Putar satu lagu dari antrean. Saat Shuffle aktif, lagu yang dipilih tetap
     * diputar sekarang dan sisa antrean diacak (tidak memotong lagu berjalan).
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song), randomStart: Boolean = false) {
        val safeQueue = queue.ifEmpty { listOf(song) }.distinctSongs()
        originalQueue = safeQueue
        val shuffle = mutableState.value.isShuffle
        val actualQueue = when {
            !shuffle -> safeQueue
            randomStart -> safeQueue.shuffled()
            else -> shuffleKeeping(song, safeQueue)
        }
        val first = if (shuffle && randomStart) actualQueue.firstOrNull() ?: song else song
        playInternal(first, actualQueue)
    }

    /**
     * Dipakai tombol putar playlist: kalau Shuffle aktif, seluruh antrean diacak
     * dan lagu pertama dari hasil acakan yang langsung diputar.
     */
    fun playQueue(queue: List<Song>, shuffleStart: Boolean = false) {
        val safeQueue = queue.distinctSongs()
        if (safeQueue.isEmpty()) return
        val randomStart = shuffleStart && mutableState.value.isShuffle
        val first = if (randomStart) safeQueue.random() else safeQueue.first()
        playSong(first, safeQueue, randomStart = randomStart)
    }

    private fun playInternal(song: Song, queue: List<Song>) {
        val requestId = ++playRequestId
        mutableState.update {
            it.copy(
                song = song,
                queue = queue,
                isPlaying = false,
                isLoading = true,
                positionMs = 0L,
                durationMs = song.durationMs,
                error = null,
            )
        }

        scope.launch {
            val liked = runCatching { library.isLiked(song.spotifyId.ifBlank { song.id }) }.getOrDefault(false)
            if (requestId == playRequestId) mutableState.update { it.copy(isLiked = liked) }

            val resolved = if (song.url.isBlank()) {
                withContext(Dispatchers.IO) { library.resolve(song) }
            } else {
                Result.success(song)
            }
            if (requestId != playRequestId) return@launch

            resolved.fold(
                onSuccess = { ready ->
                    recentlyPlayed.add(ready)
                    scope.launch(Dispatchers.IO) { library.recordPlay(ready) }
                    val controller = mediaController
                    if (controller == null) {
                        mutableState.update { it.copy(isLoading = false, error = "Pemutar belum tersambung.") }
                        return@fold
                    }

                    val streamResult = withContext(Dispatchers.IO) { repository.getAudioStreamUrl(ready.url) }
                    if (requestId != playRequestId) return@fold
                    streamResult.fold(
                        onSuccess = { stream ->
                            StreamHeaderState.configure(stream.userAgent, stream.headers)
                            val mediaItem = MediaItem.Builder()
                                .setUri(stream.audioUrl)
                                .setMediaId(ready.id)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(ready.title)
                                        .setArtist(ready.artist)
                                        .setArtworkUri(android.net.Uri.parse(ready.thumbnailUrl))
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build(),
                                )
                                .build()
                            controller.setMediaItem(mediaItem)
                            controller.prepare()
                            controller.play()
                            mutableState.update {
                                it.copy(
                                    song = ready,
                                    isLoading = false,
                                    queue = it.queue.map { q -> if (q.id == song.id || q.spotifyId == song.spotifyId) ready else q },
                                )
                            }
                        },
                        onFailure = { throwable ->
                            mutableState.update {
                                it.copy(
                                    isLoading = false,
                                    error = throwable.message?.takeIf { m -> m.isNotBlank() }
                                        ?: "Gagal mengambil audio lagu ini.",
                                )
                            }
                        },
                    )
                },
                onFailure = { throwable ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message?.takeIf { m -> m.isNotBlank() }
                                ?: "Lagu ini belum bisa diputar sekarang.",
                        )
                    }
                },
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (mutableState.value.song == null) return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val duration = mutableState.value.durationMs
        val target = positionMs.coerceIn(0L, if (duration > 0) duration else positionMs)
        controller.seekTo(target)
        mutableState.update { it.copy(positionMs = target) }
    }

    fun skipNext() {
        val state = mutableState.value
        if (state.repeatMode == PlayerUiState.REPEAT_ONE) {
            state.song?.let { playInternal(it, state.queue) }
            return
        }
        skipBy(1)
    }

    fun skipPrevious() {
        val controller = mediaController
        if ((controller?.currentPosition ?: 0L) > 3_000L) {
            seekTo(0L)
            return
        }
        skipBy(-1)
    }

    private fun skipBy(offset: Int) {
        val state = mutableState.value
        val currentIndex = state.queue.indexOfFirst { sameSong(it, state.song) }
        if (currentIndex < 0) return
        val targetIndex = currentIndex + offset
        if (targetIndex !in state.queue.indices) {
            if (state.repeatMode == PlayerUiState.REPEAT_ALL && state.queue.isNotEmpty()) {
                playInternal(state.queue.first(), state.queue)
            }
            return
        }
        playInternal(state.queue[targetIndex], state.queue)
    }

    fun toggleLike() {
        val song = mutableState.value.song ?: return
        scope.launch {
            val liked = withContext(Dispatchers.IO) { library.toggleLike(song) }
            mutableState.update { it.copy(isLiked = liked) }
        }
    }

    fun toggleShuffle() {
        val next = !mutableState.value.isShuffle
        val current = mutableState.value.song
        mutableState.update { state ->
            val base = originalQueue.ifEmpty { state.queue }
            // Menyalakan: lagu saat ini tetap berjalan, sisa antrean diacak.
            // Mematikan: urutan asli dipulihkan, lagu saat ini tidak diganti.
            val queue = if (next) shuffleKeeping(current, base) else base
            state.copy(isShuffle = next, queue = queue)
        }
    }

    fun cycleRepeat() {
        val next = when (mutableState.value.repeatMode) {
            PlayerUiState.REPEAT_OFF -> PlayerUiState.REPEAT_ALL
            PlayerUiState.REPEAT_ALL -> PlayerUiState.REPEAT_ONE
            else -> PlayerUiState.REPEAT_OFF
        }
        mutableState.update { it.copy(repeatMode = next) }
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun release() {
        scope.cancel()
        mediaController?.removeListener(playerListener)
        connectionFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        connectionFuture = null
    }

    private fun List<Song>.distinctSongs(): List<Song> =
        distinctBy { it.spotifyId.ifBlank { it.id } }

    private fun shuffleKeeping(current: Song?, queue: List<Song>): List<Song> {
        if (queue.isEmpty()) return queue
        val rest = queue.filterNot { sameSong(it, current) }.shuffled()
        return if (current == null) rest else listOf(current) + rest
    }

    private fun sameSong(a: Song?, b: Song?): Boolean {
        if (a == null || b == null) return false
        return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
    }

    private fun playbackErrorMessage(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "Koneksi jaringan putus saat memutar audio. Coba lagi sebentar."

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Server audio menolak permintaan. Coba lagi sebentar."

            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "Format audio tidak dikenali oleh pemutar."

            else -> error.message?.takeIf { it.isNotBlank() } ?: "Gagal memutar audio."
        }

    private companion object {
        const val TAG = "DengerinPlayer"
        const val POSITION_TICK_MS = 160L
    }
}
