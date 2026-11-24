package com.dengerin.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
import com.dengerin.app.data.profile.ProfileRepository
import com.dengerin.app.data.recent.RecentlyPlayedStore
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.resolver.SongResolver
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
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
    private val songResolver: SongResolver,
    private val profileRepository: ProfileRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var mediaController: MediaController? = null
    private var connectionFuture: ListenableFuture<MediaController>? = null
    private var playRequestId = 0

    // Antrean dikelola penuh di sini: URL audio YouTube diekstrak on-demand,
    // sehingga exoPlayer.shuffleModeEnabled tidak dipakai.
    private var originalQueue: List<Song> = emptyList()
    private var activeQueue: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var advancing = false

    // XP hanya dihitung dari waktu benar-benar playing, bukan dari position seek.
    private var listeningAccumulatorMs = 0L
    private var lastListeningMarkMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutableState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return

            if (mutableState.value.repeatMode == PlayerUiState.REPEAT_ONE) {
                val controller = mediaController
                if (controller == null) {
                    mutableState.update { it.copy(isPlaying = false, positionMs = 0L) }
                } else {
                    controller.seekTo(0L)
                    controller.play()
                    mutableState.update { it.copy(isPlaying = true, positionMs = 0L) }
                }
                return
            }

            playNext(fromAutoAdvance = true)
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
                        startListeningTracker()
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
     * Tracker XP. Tick 160 ms memakai elapsedRealtime supaya pause/seek tidak
     * menghasilkan XP; tiap 60 detik playing efektif masuk ke [profileRepository].
     */
    private fun startListeningTracker() {
        scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller?.isPlaying == true) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastListeningMarkMs != 0L) {
                        listeningAccumulatorMs += now - lastListeningMarkMs
                    }
                    lastListeningMarkMs = now

                    if (listeningAccumulatorMs >= LISTENING_TICK_MS) {
                        listeningAccumulatorMs -= LISTENING_TICK_MS
                        scope.launch { profileRepository.addListeningMinute() }
                    }
                } else {
                    lastListeningMarkMs = 0L
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    /**
     * Putar satu lagu dari antrean. Saat Shuffle aktif, lagu yang dipilih tetap
     * ditempatkan di indeks 0 dan sisanya diacak (tidak memotong lagu berjalan).
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song), randomStart: Boolean = false) {
        val safeQueue = queue.ifEmpty { listOf(song) }.distinctSongs()
        originalQueue = safeQueue
        activeQueue = when {
            !mutableState.value.isShuffle -> safeQueue
            randomStart -> safeQueue.shuffled()
            else -> shuffleKeeping(song, safeQueue)
        }
        if (activeQueue.isEmpty()) return

        val startIndex = activeQueue.indexOfFirst { sameSong(it, song) }
            .takeIf { it >= 0 } ?: 0
        scope.launch { playInternal(startIndex) }
    }

    /**
     * Dipakai tombol putar playlist: kalau Shuffle aktif, lagu pertama diambil
     * secara acak lalu ditempatkan di indeks 0 antrean aktif.
     */
    fun playQueue(queue: List<Song>, shuffleStart: Boolean = false) {
        val safeQueue = queue.distinctSongs()
        if (safeQueue.isEmpty()) return
        val randomStart = shuffleStart && mutableState.value.isShuffle
        val first = if (randomStart) safeQueue.random() else safeQueue.first()
        playSong(first, safeQueue)
    }

    private suspend fun playInternal(index: Int, suppressError: Boolean = false): Boolean {
        if (index !in activeQueue.indices) return false
        val song = activeQueue[index]
        currentIndex = index
        val requestId = ++playRequestId

        mutableState.update {
            it.copy(
                song = song,
                queue = activeQueue,
                isPlaying = false,
                isLoading = true,
                positionMs = 0L,
                durationMs = song.durationMs,
                error = null,
            )
        }

        val liked = runCatching { library.isLiked(song.spotifyId.ifBlank { song.id }) }.getOrDefault(false)
        if (requestId == playRequestId) mutableState.update { it.copy(isLiked = liked) }

        val resolved = try {
            if (song.url.isBlank()) {
                withContext(Dispatchers.IO) { songResolver.resolve(song) }
            } else {
                Result.success(song)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
        if (requestId != playRequestId) return false

        return resolved.fold(
            onSuccess = { ready ->
                if (requestId != playRequestId) return@fold false
                recentlyPlayed.add(ready)
                scope.launch(Dispatchers.IO) { library.recordPlay(ready) }

                val controller = mediaController
                if (controller == null) {
                    handlePlayFailure(IllegalStateException("Pemutar belum tersambung."), suppressError)
                    return@fold false
                }

                val streamResult = withContext(Dispatchers.IO) { repository.getAudioStreamUrl(ready.url) }
                if (requestId != playRequestId) return@fold false

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
                                    .setArtworkUri(Uri.parse(ready.thumbnailUrl))
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .build(),
                            )
                            .build()
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                        scope.launch { profileRepository.recordSongPlayed() }
                        markResolved(index, ready)
                        mutableState.update { it.copy(isLoading = false) }
                        true
                    },
                    onFailure = { throwable ->
                        handlePlayFailure(throwable, suppressError)
                        false
                    },
                )
            },
            onFailure = { throwable ->
                handlePlayFailure(throwable, suppressError)
                false
            },
        )
    }

    private fun markResolved(index: Int, resolved: Song) {
        if (index in activeQueue.indices) {
            activeQueue = activeQueue.toMutableList().also { it[index] = resolved }
        }
        originalQueue = originalQueue.map { item -> if (sameSong(item, resolved)) resolved else item }
        currentIndex = index
        mutableState.update { it.copy(song = resolved, queue = activeQueue) }
    }

    private fun handlePlayFailure(throwable: Throwable, suppressError: Boolean) {
        Log.w(TAG, "Gagal menyiapkan audio lagu berikutnya.", throwable)
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                isPlaying = false,
                error = if (suppressError) {
                    null
                } else {
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: "Lagu ini belum bisa diputar sekarang."
                },
            )
        }
    }

    /**
     * Lanjut ke lagu berikutnya di [activeQueue]. Kegagalan ekstraksi satu lagu
     * langsung dilewati, maksimal [AUTO_ADVANCE_ATTEMPTS] lagu berturut-turut.
     */
    fun playNext(fromAutoAdvance: Boolean = false) {
        if (advancing) return
        scope.launch {
            advancing = true
            try {
                if (activeQueue.isEmpty()) {
                    mutableState.update { it.copy(isPlaying = false, isLoading = false, positionMs = 0L) }
                    return@launch
                }

                val startIndex = currentIndex.takeIf { it in activeQueue.indices } ?: 0
                var cursor = startIndex
                var attempts = 0
                while (attempts < AUTO_ADVANCE_ATTEMPTS) {
                    val next = nextIndexAfter(cursor) ?: run {
                        mutableState.update {
                            it.copy(isPlaying = false, isLoading = false, positionMs = 0L)
                        }
                        return@launch
                    }
                    val suppressError = fromAutoAdvance && attempts < AUTO_ADVANCE_ATTEMPTS - 1
                    if (playInternal(next, suppressError)) return@launch
                    cursor = next
                    attempts++
                }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Beberapa lagu belum bisa diputar. Coba lagu lain ya.",
                    )
                }
            } finally {
                advancing = false
            }
        }
    }

    private fun nextIndexAfter(cursor: Int): Int? {
        val next = cursor + 1
        if (next in activeQueue.indices) return next
        if (mutableState.value.repeatMode != PlayerUiState.REPEAT_ALL) return null

        if (mutableState.value.isShuffle) {
            activeQueue = originalQueue.shuffled()
            mutableState.update { it.copy(queue = activeQueue) }
        }
        return 0
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
        if (mutableState.value.repeatMode == PlayerUiState.REPEAT_ONE) {
            val controller = mediaController ?: return
            controller.seekTo(0L)
            controller.play()
            mutableState.update { it.copy(positionMs = 0L, isPlaying = true) }
            return
        }
        playNext(fromAutoAdvance = false)
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
        if (activeQueue.isEmpty()) return
        val base = currentIndex.takeIf { it in activeQueue.indices } ?: 0
        var target = base + offset
        if (target !in activeQueue.indices) {
            if (mutableState.value.repeatMode != PlayerUiState.REPEAT_ALL) return
            target = if (target < 0) activeQueue.lastIndex else 0
        }
        val index = target
        scope.launch { playInternal(index) }
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
        val base = originalQueue.ifEmpty { activeQueue }

        // Menyalakan: lagu saat ini ditempatkan di indeks 0, sisa lagu diacak.
        // Mematikan: urutan asli dipulihkan dengan lagu saat ini tetap diputar.
        activeQueue = if (next) shuffleKeeping(current, base) else base
        currentIndex = activeQueue.indexOfFirst { sameSong(it, current) }
            .takeIf { it >= 0 } ?: 0
        mutableState.update { state -> state.copy(isShuffle = next, queue = activeQueue) }
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
        const val LISTENING_TICK_MS = 60_000L
        const val AUTO_ADVANCE_ATTEMPTS = 3
    }
}
