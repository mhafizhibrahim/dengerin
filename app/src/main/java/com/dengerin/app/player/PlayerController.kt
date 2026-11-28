@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dengerin.app.core.StreamHeaders
import com.dengerin.app.core.network.NetworkMonitor
import com.dengerin.app.core.nowPlayingCoverUrl
import com.dengerin.app.data.autoplay.AutoplayRepository
import com.dengerin.app.data.listening.ListeningHistoryRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.PlaybackPreferences
import com.dengerin.app.data.preferences.isDataSaverActive
import com.dengerin.app.data.profile.ProfileRepository
import com.dengerin.app.data.recent.RecentlyPlayedStore
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.resolver.SongResolver
import com.dengerin.app.data.resolver.StreamFailureKind
import com.dengerin.app.data.resolver.StreamFailureTracker
import com.dengerin.app.data.resolver.StreamResolver
import com.dengerin.app.data.usage.DataUsageTracker
import com.dengerin.app.domain.model.StreamPick
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Satu titik kontrol untuk UI; terhubung ke DengerinPlaybackService lewat MediaController. */
class PlayerController(
    context: Context,
    private val recentlyPlayed: RecentlyPlayedStore,
    private val library: LibraryRepository,
    private val songResolver: SongResolver,
    private val streamResolver: StreamResolver,
    private val profileRepository: ProfileRepository,
    private val playbackPreferences: PlaybackPreferences,
    private val networkMonitor: NetworkMonitor,
    private val audioCacheManager: AudioCacheManager,
    private val dataUsageTracker: DataUsageTracker,
    private val listeningHistory: ListeningHistoryRepository,
    private val autoplayRepository: AutoplayRepository,
    private val failureTracker: StreamFailureTracker,
    private val prefetcher: AudioPrefetcher,
) : PlaybackQueueManager {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    /**
     * Posisi/durasi dipisahkan dari [uiState] supaya ticker ~160 ms tidak pernah
     * mengganti objek state yang dibaca seluruh pohon Compose. Seekbar & kartu lirik
     * membaca ini lewat `rememberTimelineProviders`.
     */
    private val mutableTimeline = MutableStateFlow(0L to 0L)

    init {
        // Shuffle/repeat ikut dipulihkan dari DataStore saat aplikasi dibuka ulang.
        scope.launch {
            playbackPreferences.settings.collect { settings ->
                mutableState.update { state ->
                    if (state.isShuffle == settings.shuffle &&
                        state.repeatMode == settings.repeatMode
                    ) {
                        state
                    } else {
                        state.copy(
                            isShuffle = settings.shuffle,
                            repeatMode = settings.repeatMode.coerceIn(0, 2),
                        )
                    }
                }
            }
        }
        scope.launch {
            playbackPreferences.isAutoplayEnabled.collect { enabled ->
                mutableState.update { state ->
                    if (state.isAutoplay == enabled) state else state.copy(isAutoplay = enabled)
                }
            }
        }
    }

    /**
     * Hanya posisi & durasi. Seekbar/teks waktu mengcollect ini sendiri supaya
     * gradient, sampul, dan kartu lirik tidak ikut recompose tiap tick.
     */
    val timeline: StateFlow<Pair<Long, Long>> = mutableTimeline.asStateFlow()

    // Kontrak untuk service: state dibaca langsung dari sumber kebenaran yang sama
    // dengan UI, sehingga kontrol lockscreen/headset sinkron tanpa salinan state.
    override val isLiked: Boolean get() = mutableState.value.isLiked
    override val isShuffle: Boolean get() = mutableState.value.isShuffle
    override val hasNext: Boolean
        get() = activeQueue.isNotEmpty() &&
            (mutableState.value.repeatMode == PlayerUiState.REPEAT_ALL ||
                mutableState.value.repeatMode == PlayerUiState.REPEAT_ONE ||
                queueManager.upcomingItems.isNotEmpty())
    override val hasPrevious: Boolean get() = activeQueue.isNotEmpty()

    override fun skipPrevious() {
        val position = connectedController()
            ?.let { runCatching { it.currentPosition }.getOrNull() }
            ?: 0L
        if (position > 3_000L) {
            seekTo(0L)
            return
        }
        skipBy(-1)
    }

    override fun togglePlayPause() {
        if (mutableState.value.song == null) return
        cancelAutoplay()
        runOnController { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    override fun toggleLike() {
        val song = mutableState.value.song ?: return
        scope.launch {
            val liked = withContext(Dispatchers.IO) { library.toggleLike(song) }
            setLikedState(liked)
        }
    }

    override fun toggleShuffle() {
        val next = !mutableState.value.isShuffle
        // Hanya CONTEXT_QUEUE yang diacak; lagu aktif & USER_QUEUE tidak disentuh.
        queueManager.toggleShuffle(next)
        publishQueue()
        mutableState.update { it.copy(isShuffle = next) }
        scope.launch { playbackPreferences.setShuffle(next) }
    }

    override fun stopPlayback() {
        cancelAutoplay()
        cancelPrefetch()
        playRequestId++
        runOnController { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
        queueManager.setQueue(emptyList(), false, null)
        listeningHistory.finishSong()
        dataUsageTracker.flush()
        mutableState.update { state ->
            state.copy(
                song = null,
                queue = emptyList(),
                queueItems = emptyList(),
                isPlaying = false,
                isLoading = false,
                positionMs = 0L,
                durationMs = 0L,
                error = null,
                currentQueueId = null,
                isPlayingFromCache = false,
            )
        }
        publishTimeline(0L, 0L)
    }

    private fun publishTimeline(positionMs: Long, durationMs: Long) {
        val next = positionMs to durationMs
        if (mutableTimeline.value != next) mutableTimeline.value = next
    }

    private var mediaController: MediaController? = null
    private var connectionFuture: ListenableFuture<MediaController>? = null
    private var playRequestId = 0

    /** Posisi terakhir yang sah, dipakai saat auto-retry harus menyambung lagi. */
    private var lastPositionMs = 0L
    private var retryAttempts = 0
    private var retryInProgress = false

    /** true hanya saat MediaItem berasal dari SimpleCache penuh (tanpa jaringan). */
    private var playingFullyFromCache = false

    /**
     * Media3 hanya boleh dipanggil dari main looper DAN saat sesi masih terhubung.
     * Controller lama setelah service di-recreate bisa melempar IllegalStateException.
     */
    private fun connectedController(): MediaController? = mediaController?.takeIf {
        runCatching { it.isConnected() }.getOrDefault(false)
    }

    private fun runOnController(block: (MediaController) -> Unit) {
        val controller = connectedController() ?: return
        runCatching { block(controller) }
    }

    // QueueManager satu-satunya pemilik urutan antrean (main-thread safe). URL audio
    // YouTube diekstrak on-demand sehingga exoPlayer.shuffleModeEnabled tidak dipakai.
    private val queueManager = QueueManager()
    private var originalQueue: List<Song>
        get() = queueManager.originalSongs
        set(value) = queueManager.replaceOriginal(value)
    private var activeQueue: List<Song>
        get() = queueManager.activeSongs
        set(value) = queueManager.replaceActive(value)
    private var currentIndex: Int
        get() = queueManager.currentIndex
        set(value) = queueManager.setCurrentIndex(value)
    private var advancing = false
    private var autoplayJob: Job? = null
    private var prefetchJob: Job? = null

    private fun publishQueue() {
        val snapshot = activeQueue
        mutableState.update {
            it.copy(
                queue = snapshot,
                queueItems = queueManager.items,
                contextTitle = queueManager.contextTitle,
                currentQueueId = queueManager.currentQueueId,
            )
        }
    }

    /** Aksi pengguna membatalkan radio otomatis yang sedang mencari metadata. */
    private fun cancelAutoplay() {
        autoplayJob?.cancel()
        autoplayJob = null
    }

    /** Batalkan unduhan lagu berikutnya yang sudah tidak relevan lagi. */
    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * Isi cache lagu berikutnya hanya bila Wi-Fi, tanpa Data Saver, dan cache
     * masih punya ruang untuk satu lagu. Best-effort: gagal prefetch tidak
     * boleh memengaruhi lagu yang sedang diputar.
     */
    private fun schedulePrefetch() {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // Beri waktu ExoPlayer mengisi buffer lagu aktif lebih dulu.
            delay(PREFETCH_DELAY_MS)
            if (!isActive) return@launch
            val next = queueManager.upcomingItems.firstOrNull()?.track ?: return@launch
            val settings = playbackPreferences.settings.value
            val network = networkMonitor.current()
            val cacheBytes = withContext(Dispatchers.IO) { audioCacheManager.cacheSizeBytes() }
            if (!PrefetchPolicy.shouldPrefetch(network, settings, cacheBytes)) return@launch
            val alreadyCached = withContext(Dispatchers.IO) { audioCacheManager.isCached(next) }
            if (alreadyCached || next.url.isBlank()) return@launch

            val bytes = prefetcher.prefetch(next, currentPick())
            if (bytes > 0L) {
                Log.i(TAG, "Prefetch ${audioCacheManager.videoIdFor(next)} selesai: ${bytes / 1024} KB.")
            }
        }
    }

    // XP hanya dihitung dari waktu benar-benar playing, bukan dari position seek.
    private var listeningAccumulatorMs = 0L
    private var lastListeningMarkMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            mutableState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            dataUsageTracker.flush()

            if (mutableState.value.repeatMode == PlayerUiState.REPEAT_ONE) {
                val controller = connectedController()
                if (controller == null) {
                    mutableState.update { it.copy(isPlaying = false, positionMs = 0L) }
                } else {
                    runOnController { active ->
                        active.seekTo(0L)
                        active.play()
                    }
                    mutableState.update { it.copy(isPlaying = true, positionMs = 0L) }
                }
                publishTimeline(0L, mutableState.value.durationMs)
                return
            }

            listeningHistory.finishSong(completed = true)
            val state = mutableState.value
            if (state.repeatMode == PlayerUiState.REPEAT_OFF &&
                state.isAutoplay &&
                queueManager.upcomingItems.isEmpty()
            ) {
                startAutoplay()
            } else {
                playNext(fromAutoAdvance = true)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer gagal memutar stream.", error)
            val song = mutableState.value.song
            val videoId = song?.let(audioCacheManager::videoIdFor)
            val cacheDamaged = isCacheDamage(error)

            // Catat dulu, sebelum invalidate/reset cache mengubah kondisi.
            if (videoId != null) {
                failureTracker.record(
                    videoId,
                    classifyFailure(error, cacheDamaged),
                    StreamHeaderState.current().pick,
                )
            }

            // Self-healing: status UI jadi non-cache. Byte rusak dibuang di IO
            // SEBELUM retry disiapkan supaya MediaItem baru tidak membaca span lama.
            if (cacheDamaged && videoId != null) {
                playingFullyFromCache = false
                mutableState.update { it.copy(isPlayingFromCache = false) }
            }

            if (isPermanentSourceError(error) || song == null || song.url.isBlank()) {
                videoId?.let(streamResolver::invalidate)
                showUnavailableAndNext()
                return
            }

            // Error kedua yang masuk saat retry masih berjalan duplikat; abaikan.
            if (retryInProgress) return

            val retryable = error.errorCode in RETRYABLE_ERROR_CODES || cacheDamaged
            if (!retryable || retryAttempts >= MAX_STREAM_RETRIES) {
                videoId?.let(streamResolver::invalidate)
                showUnavailableAndNext()
                return
            }

            // Hingga 3 percobaan: Opus sesuai jaringan, Opus profil lain, lalu M4A.
            // Urutannya diatur ulang dari catatan kegagalan terakhir di tracker.
            retryInProgress = true
            val resumeAt = lastPositionMs
            val damagedVideoId = if (cacheDamaged) videoId else null
            scope.launch {
                try {
                    if (damagedVideoId != null) {
                        withContext(Dispatchers.IO) {
                            audioCacheManager.removeCorrupted(damagedVideoId)
                        }
                    }
                    val recovered = retryCurrentStream(song, resumeAt, damagedVideoId != null)
                    if (!recovered) showUnavailableAndNext()
                } finally {
                    retryInProgress = false
                }
            }
        }
    }

    init {
        connect()
        scope.launch {
            audioCacheManager.playingFromCache.collect { cached ->
                mutableState.update { it.copy(isPlayingFromCache = cached) }
            }
        }
    }

    private fun connect() {
        val token = SessionToken(appContext, ComponentName(appContext, DengerinPlaybackService::class.java))
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
                connectedController()?.let { controller ->
                    val snapshot = runCatching {
                        if (controller.isPlaying) controller.currentPosition to controller.duration else null
                    }.getOrNull()
                    if (snapshot != null) {
                        val (current, total) = snapshot
                        if (current > 0L) lastPositionMs = current
                        val knownDuration = if (total > 0) total else mutableState.value.durationMs
                        publishTimeline(current, knownDuration)
                        // Durasi biasanya sudah benar sejak lagu dimuat; tulis ulang hanya
                        // bila Media3 melaporkan nilai berbeda (jarang, 1× per lagu).
                        if (total > 0 && mutableState.value.durationMs != total) {
                            mutableState.update { it.copy(durationMs = total) }
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
                val isPlaying = connectedController()
                    ?.let { runCatching { it.isPlaying }.getOrDefault(false) }
                    ?: false
                if (isPlaying) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastListeningMarkMs != 0L) {
                        val delta = now - lastListeningMarkMs
                        listeningAccumulatorMs += delta
                        // Durasi dengar asli per lagu (hanya saat audio benar-benar berjalan).
                        listeningHistory.onPlaybackTick(delta)
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
    fun playSong(
        song: Song,
        queue: List<Song> = listOf(song),
        randomStart: Boolean = false,
        contextTitle: String = "",
    ) {
        cancelAutoplay()
        val safeQueue = queue.ifEmpty { listOf(song) }.distinctSongs()
        if (safeQueue.isEmpty()) return
        val effectiveTitle = contextTitle.ifBlank { mutableState.value.contextTitle }
        queueManager.setQueue(safeQueue, mutableState.value.isShuffle, song, effectiveTitle)
        if (randomStart && activeQueue.size > 1) {
            activeQueue = activeQueue.shuffled()
            currentIndex = queueManager.indexOf(song).takeIf { it >= 0 } ?: 0
        }
        publishQueue()
        scope.launch { playInternal(currentIndex) }
    }

    /**
     * Dipakai tombol putar playlist: kalau Shuffle aktif, lagu pertama diambil
     * secara acak lalu ditempatkan di indeks 0 antrean aktif.
     */
    fun playQueue(
        queue: List<Song>,
        shuffleStart: Boolean = false,
        contextTitle: String = "",
    ) {
        val safeQueue = queue.distinctSongs()
        if (safeQueue.isEmpty()) return
        val randomStart = shuffleStart && mutableState.value.isShuffle
        val first = if (randomStart) safeQueue.random() else safeQueue.first()
        playSong(first, safeQueue, contextTitle = contextTitle)
    }

    /** Tambah ke urutan terakhir USER_QUEUE, sebelum sisa playlist/konteks. */
    fun addToQueue(song: Song, title: String = mutableState.value.contextTitle) {
        queueManager.addToQueue(song, title)
        publishQueue()
    }

    /** Putar berikutnya: sisip di urutan pertama USER_QUEUE tepat setelah lagu aktif. */
    fun playNext(song: Song, title: String = mutableState.value.contextTitle) {
        queueManager.playNext(song, title)
        publishQueue()
    }

    /** Hapus satu lagu menunggu berdasarkan queueId (lagu aktif tidak bisa dihapus). */
    fun removeUpcomingItem(queueId: String) {
        cancelAutoplay()
        queueManager.removeUpcomingItem(queueId)
        publishQueue()
    }

    /** Alihkan urutan di dalam zona mendatang yang sama. */
    fun moveUpcomingItem(fromIndex: Int, toIndex: Int) {
        queueManager.moveUpcomingItem(fromIndex, toIndex)
        publishQueue()
    }

    /** Hapus seluruh USER_QUEUE; konteks & autoplay tidak tersentuh. */
    fun clearUserQueue() {
        cancelAutoplay()
        queueManager.clearUserQueue()
        publishQueue()
    }

    /** Kompatibilitas pemanggil lama berbasis index. */
    fun removeFromQueue(index: Int) {
        queueManager.getQueueItem(index)?.let { removeUpcomingItem(it.queueId) }
    }

    fun moveInQueue(from: Int, to: Int) = moveUpcomingItem(from, to)

    fun clearUpcoming() = clearUserQueue()

    /** Putar lagu pada index antrean yang dipilih dari sheet Antrean. */
    fun playQueueIndex(index: Int) {
        if (index !in activeQueue.indices) return
        cancelAutoplay()
        scope.launch { playInternal(index) }
    }

    /** Lompat langsung ke entri antrean dan resolusi stream-nya secara on-demand. */
    fun jumpToQueueItem(queueId: String) {
        val index = queueManager.indexOfQueueId(queueId)
        if (index >= 0) playQueueIndex(index)
    }

    /** Aktif/mati radio otomatis; ditulis ke DataStore. */
    fun setAutoplay(enabled: Boolean) {
        mutableState.update { it.copy(isAutoplay = enabled) }
        scope.launch { playbackPreferences.setAutoplay(enabled) }
        if (!enabled) cancelAutoplay()
    }

    /** Dipanggil saat lagu terakhir selesai: metadata ringan dulu, stream saat aktif. */
    private fun startAutoplay() {
        if (autoplayJob?.isActive == true || advancing) return
        val seed = mutableState.value.song ?: return
        val requestAtStart = playRequestId
        mutableState.update { it.copy(isLoading = true, isPlaying = false) }
        autoplayJob = scope.launch {
            val suggestions = withTimeoutOrNull(AUTOPLAY_TIMEOUT_MS) {
                autoplayRepository.similarTo(seed)
            }.orEmpty()
            if (!isActive || requestAtStart != playRequestId) return@launch
            autoplayJob = null
            if (suggestions.isEmpty()) {
                mutableState.update { it.copy(isLoading = false, isPlaying = false) }
                return@launch
            }
            val firstIndex = queueManager.setAutoplayQueue(
                suggestions,
                "Putar Otomatis · Lagu Serupa",
            )
            publishQueue()
            if (firstIndex >= 0) playInternal(firstIndex)
        }
    }

    private suspend fun playInternal(index: Int, suppressError: Boolean = false): Boolean {
        if (index !in activeQueue.indices) return false
        cancelPrefetch()
        val song = activeQueue[index]
        currentIndex = index
        val requestId = ++playRequestId
        lastPositionMs = 0L
        retryAttempts = 0
        retryInProgress = false

        mutableState.update {
            it.copy(
                song = song,
                queue = activeQueue,
                queueItems = queueManager.items,
                contextTitle = queueManager.contextTitle,
                currentQueueId = queueManager.currentQueueId,
                isPlaying = false,
                isLoading = true,
                isPlayingFromCache = false,
                positionMs = 0L,
                durationMs = song.durationMs,
                // Auto-advance setelah kegagalan tidak boleh menghapus pesan singkat.
                error = if (suppressError) mutableState.value.error else null,
            )
        }
        publishTimeline(0L, song.durationMs)

        val liked = runCatching { library.isLiked(song.spotifyId.ifBlank { song.id }) }.getOrDefault(false)
        if (requestId == playRequestId) mutableState.update { it.copy(isLiked = liked) }

        // Prioritas tertinggi: cache penuh = 0 KB internet. Cek ini SEBELUM
        // SongResolver/SongExtractor supaya tidak ada request YouTube sama sekali.
        // Room lookup + SimpleCache index boleh di IO: jalur ini dipanggil dari
        // coroutine Main.immediate dan tidak boleh menahan frame saat lagu diganti.
        val offlineSong = withContext(Dispatchers.IO) {
            songResolver.resolveCached(song).takeIf { cached ->
                cached != null && audioCacheManager.isCached(cached)
            }
        }
        if (offlineSong != null) {
            playingFullyFromCache = true
            return startLocalCachePlayback(index, offlineSong, requestId, suppressError)
        }
        playingFullyFromCache = false

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
                val videoId = audioCacheManager.videoIdFor(ready)
                if (videoId == null) {
                    handlePlayFailure(IllegalStateException("Video YouTube tidak ditemukan."), suppressError)
                    return@fold false
                }

                recentlyPlayed.add(ready)
                scope.launch(Dispatchers.IO) { library.recordPlay(ready) }

                val controller = connectedController()
                if (controller == null) {
                    handlePlayFailure(IllegalStateException("Pemutar belum tersambung."), suppressError)
                    return@fold false
                }

                val pick = currentPick()
                val streamResult = withContext(Dispatchers.IO) {
                    streamResolver.resolve(videoId, ready.url, pick)
                }
                if (requestId != playRequestId) return@fold false

                streamResult.fold(
                    onSuccess = { stream ->
                        failureTracker.clear(videoId)
                        val cacheKey = audioCacheManager.cacheKeyForVideoId(videoId)
                        StreamHeaderState.configure(
                            stream.userAgent,
                            stream.headers,
                            cacheKey,
                            videoId,
                            ready.url,
                            pick,
                        )
                        audioCacheManager.beginPlayback(ready)
                        // Batas lagu: flush kuota & tutup sesi dengar sebelumnya.
                        dataUsageTracker.setActiveSong(cacheKey)
                        listeningHistory.onSongStarted(ready)
                        lastListeningMarkMs = 0L
                        val mediaItem = buildMediaItem(ready, stream.audioUrl, cacheKey)
                        val prepared = runCatching {
                            controller.setMediaItem(mediaItem)
                            controller.prepare()
                            controller.play()
                        }
                        if (prepared.isFailure) {
                            failureTracker.record(
                                videoId,
                                classifyFailure(prepared.exceptionOrNull()),
                                pick,
                            )
                            handlePlayFailure(
                                prepared.exceptionOrNull() ?: IllegalStateException("Pemutar gagal disiapkan."),
                                suppressError,
                            )
                            return@fold false
                        }
                        scope.launch { profileRepository.recordSongPlayed() }
                        markResolved(index, ready)
                        schedulePrefetch()
                        mutableState.update { it.copy(isLoading = false, error = null) }
                        true
                    },
                    onFailure = { throwable ->
                        failureTracker.record(videoId, StreamFailureKind.SOURCE, pick)
                        handlePlayFailure(throwable, suppressError)
                        false
                    },
                )
            },
            onFailure = { throwable ->
                audioCacheManager.videoIdFor(song)?.let { id ->
                    failureTracker.record(id, StreamFailureKind.SOURCE, currentPick())
                }
                handlePlayFailure(throwable, suppressError)
                false
            },
        )
    }

    /** Putar/langsung baca dari SimpleCache: URI lokal + customCacheKey videoId. */
    private fun startLocalCachePlayback(
        index: Int,
        song: Song,
        requestId: Int,
        suppressError: Boolean,
    ): Boolean {
        val controller = connectedController()
        if (controller == null) {
            handlePlayFailure(IllegalStateException("Pemutar belum tersambung."), suppressError)
            return false
        }
        val videoId = audioCacheManager.videoIdFor(song) ?: return false
        val cacheKey = audioCacheManager.cacheKeyForVideoId(videoId)
        StreamHeaderState.configure(
            StreamHeaders.NEW_PIPE_USER_AGENT,
            StreamHeaders.YOUTUBE_HEADERS,
            cacheKey,
            videoId,
            song.url,
            StreamPick.STANDARD_OPUS,
        )
        audioCacheManager.beginPlayback(song)
        dataUsageTracker.setActiveSong(cacheKey)
        listeningHistory.onSongStarted(song)
        lastListeningMarkMs = 0L

        val mediaItem = buildMediaItem(song, audioCacheManager.localCacheUri(videoId).toString(), cacheKey)
        val prepared = runCatching {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        if (prepared.isFailure) {
            handlePlayFailure(
                prepared.exceptionOrNull() ?: IllegalStateException("Cache audio gagal dibaca."),
                suppressError,
            )
            return false
        }
        if (requestId != playRequestId) return false
        recentlyPlayed.add(song)
        scope.launch(Dispatchers.IO) { library.recordPlay(song) }
        scope.launch { profileRepository.recordSongPlayed() }
        markResolved(index, song)
        mutableState.update {
            it.copy(isLoading = false, isPlaying = true, isPlayingFromCache = true, error = null)
        }
        // Sengaja tidak prefetch: lagu dari cache penuh harus benar-benar 0 KB internet.
        Log.i(TAG, "Memutar dari cache penuh $videoId (0 KB internet).")
        return true
    }

    /** Seluler / Data Saver → Opus hemat; Wi-Fi tanpa penghemat → Opus standar. */
    private fun currentPick(): StreamPick {
        val settings = playbackPreferences.settings.value
        val network = networkMonitor.current()
        val constrained = settings.isDataSaverActive(network) ||
            network.isCellular ||
            !network.isWifiLike
        return if (constrained) StreamPick.ECONOMY_OPUS else StreamPick.STANDARD_OPUS
    }

    private fun markResolved(index: Int, resolved: Song) {
        if (index in activeQueue.indices) {
            activeQueue = activeQueue.toMutableList().also { it[index] = resolved }
        }
        originalQueue = originalQueue.map { item -> if (sameSong(item, resolved)) resolved else item }
        currentIndex = index
        mutableState.update {
            it.copy(
                song = resolved,
                queue = activeQueue,
                queueItems = queueManager.items,
                contextTitle = queueManager.contextTitle,
                currentQueueId = queueManager.currentQueueId,
            )
        }
    }

    private fun handlePlayFailure(throwable: Throwable, suppressError: Boolean) {
        Log.w(TAG, "Gagal menyiapkan audio lagu berikutnya.", throwable)
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                isPlaying = false,
                isPlayingFromCache = false,
                error = if (suppressError) {
                    // Pertahankan pesan "Lagu tidak tersedia" saat auto-advance
                    // mencoba lagu berikutnya; pesan dihapus saat ada lagu yang sukses.
                    state.error
                } else {
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: "Lagu ini belum bisa diputar sekarang."
                },
            )
        }
    }

    /** MediaItem dipakai ulang oleh auto-retry supaya metadata notifikasi tetap sama. */
    private fun buildMediaItem(song: Song, streamUrl: String, cacheKey: String): MediaItem {
        val artworkUri = nowPlayingCoverUrl(appContext, song)
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        return MediaItem.Builder()
            .setUri(streamUrl)
            // Wajib: kunci cache permanen berbasis videoId, bukan URL googlevideo.com.
            .setCustomCacheKey(cacheKey)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    // Song tidak punya kolom album; konteks putar adalah pengganti terbaik.
                    .setAlbumTitle(mutableState.value.contextTitle.ifBlank { "Dengerin" })
                    .setDurationMs(song.durationMs.takeIf { it > 0L } ?: C.TIME_UNSET)
                    .setArtworkUri(artworkUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            )
            .build()
    }

    /**
     * Auto-retry hingga [MAX_STREAM_RETRIES] percobaan:
     * 1. Opus sesuai jaringan (atau profil lain bila format sebelumnya gagal).
     * 2. Opus profil alternatif.
     * 3. M4A / audio lain dengan bitrate terkecil bila Opus ditolak perangkat.
     *
     * Setiap percobaan membuang RAM cache lama supaya URL 403 diganti, lalu
     * menyambung dari detik terakhir. Bila cache rusak, byte lama sudah dibuang
     * oleh pemanggil SEBELUM fungsi ini dipanggil.
     */
    private suspend fun retryCurrentStream(
        song: Song,
        resumeAtMs: Long,
        clearCacheBeforeRetry: Boolean = false,
    ): Boolean {
        val videoId = audioCacheManager.videoIdFor(song) ?: return false
        val picks = StreamFailureTracker.retryPicks(
            base = currentPick(),
            lastFailure = failureTracker.last(videoId),
        )
        var lastFailure: Throwable? = null

        for ((attempt, pick) in picks.withIndex()) {
            if (attempt >= MAX_STREAM_RETRIES) break
            // Jaringan putus butuh jeda sebelum resolve ulang; tanpa ini retry
            // bertubi-tubi memakai URL yang sama dan menguras baterai.
            delay(RetryBackoff.delayMs(attempt))
            mutableState.update { it.copy(isLoading = true, isPlaying = false, error = null) }

            // 403 → URL lama harus diganti; cache RAM 4 jam di-reset untuk video ini.
            // Percobaan dengan format lain juga membuang byte Opus lama supaya HTTP
            // Range tidak menyambung dua container yang tidak cocok.
            streamResolver.invalidate(videoId)
            if (attempt > 0 || clearCacheBeforeRetry) {
                withContext(Dispatchers.IO) { audioCacheManager.resetCacheFor(videoId) }
            }
            val streamResult = withContext(Dispatchers.IO) {
                streamResolver.resolve(videoId, song.url, pick)
            }
            val stream = streamResult.getOrNull()
            if (stream == null) {
                lastFailure = streamResult.exceptionOrNull()
                failureTracker.record(videoId, StreamFailureKind.SOURCE, pick)
                retryAttempts++
                continue
            }

            val controller = connectedController()
            if (controller == null) {
                lastFailure = IllegalStateException("Pemutar belum tersambung.")
                failureTracker.record(videoId, StreamFailureKind.NETWORK, pick)
                retryAttempts++
                continue
            }

            val cacheKey = audioCacheManager.cacheKeyForVideoId(videoId)
            StreamHeaderState.configure(
                stream.userAgent,
                stream.headers,
                cacheKey,
                videoId,
                song.url,
                pick,
            )
            audioCacheManager.beginPlayback(song)
            playingFullyFromCache = false
            dataUsageTracker.setActiveSong(cacheKey)
            val prepared = runCatching {
                controller.setMediaItem(buildMediaItem(song, stream.audioUrl, cacheKey))
                controller.prepare()
                controller.seekTo(resumeAtMs)
                controller.play()
            }
            if (prepared.isSuccess) {
                retryAttempts = attempt + 1
                failureTracker.clear(videoId)
                lastPositionMs = resumeAtMs
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = true,
                        isPlayingFromCache = false,
                        error = null,
                        positionMs = resumeAtMs,
                    )
                }
                publishTimeline(resumeAtMs, mutableState.value.durationMs)
                schedulePrefetch()
                Log.i(TAG, "Auto-retry $pick berhasil di ${resumeAtMs}ms (percobaan ${attempt + 1}).")
                return true
            }
            lastFailure = prepared.exceptionOrNull()
            failureTracker.record(videoId, classifyFailure(lastFailure), pick)
            retryAttempts++
        }
        Log.w(TAG, "Auto-retry gagal setelah $retryAttempts percobaan.", lastFailure)
        return false
    }

    /**
     * Lanjut ke lagu berikutnya di [activeQueue]. Kegagalan ekstraksi satu lagu
     * langsung dilewati, maksimal [AUTO_ADVANCE_ATTEMPTS] lagu berturut-turut.
     */
    fun playNext(fromAutoAdvance: Boolean = false) {
        if (advancing) return
        if (!fromAutoAdvance) cancelAutoplay()
        scope.launch {
            advancing = true
            try {
                if (activeQueue.isEmpty()) {
                    mutableState.update { it.copy(isPlaying = false, isLoading = false, positionMs = 0L) }
                    publishTimeline(0L, mutableState.value.durationMs)
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
                        publishTimeline(0L, mutableState.value.durationMs)
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
        val repeatAll = mutableState.value.repeatMode == PlayerUiState.REPEAT_ALL
        val next = queueManager.nextIndexAfter(cursor, repeatAll) ?: return null
        publishQueue()
        return next
    }

    fun seekTo(positionMs: Long) {
        val controller = connectedController() ?: return
        val duration = mutableState.value.durationMs
        val target = positionMs.coerceIn(0L, if (duration > 0) duration else positionMs)
        runCatching {
            controller.seekTo(target)
            lastPositionMs = target
            mutableState.update { it.copy(positionMs = target) }
            publishTimeline(target, duration)
        }
    }

    override fun skipNext() {
        if (mutableState.value.repeatMode == PlayerUiState.REPEAT_ONE) {
            val controller = connectedController() ?: return
            val restarted = runCatching {
                controller.seekTo(0L)
                controller.play()
            }
            if (restarted.isSuccess) {
                mutableState.update { it.copy(positionMs = 0L, isPlaying = true) }
                publishTimeline(0L, mutableState.value.durationMs)
            }
            return
        }
        playNext(fromAutoAdvance = false)
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
        cancelAutoplay()
        scope.launch { playInternal(index) }
    }

    /** Dipakai root saat tombol suka di daftar/results menyentuh lagu yang sedang diputar. */
    fun setLikedState(liked: Boolean) {
        mutableState.update { it.copy(isLiked = liked) }
    }

    fun cycleRepeat() {
        val next = when (mutableState.value.repeatMode) {
            PlayerUiState.REPEAT_OFF -> PlayerUiState.REPEAT_ALL
            PlayerUiState.REPEAT_ALL -> PlayerUiState.REPEAT_ONE
            else -> PlayerUiState.REPEAT_OFF
        }
        mutableState.update { it.copy(repeatMode = next) }
        scope.launch { playbackPreferences.setRepeatMode(next) }
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun release() {
        cancelAutoplay()
        cancelPrefetch()
        listeningHistory.finishSong()
        dataUsageTracker.flush()
        scope.cancel()
        runCatching { mediaController?.removeListener(playerListener) }
        connectionFuture?.let { future -> runCatching { MediaController.releaseFuture(future) } }
        mediaController = null
        connectionFuture = null
    }

    private fun List<Song>.distinctSongs(): List<Song> =
        distinctBy { it.spotifyId.ifBlank { it.id } }

    private fun sameSong(a: Song?, b: Song?): Boolean {
        if (a == null || b == null) return false
        return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
    }

    /** Pesan singkat wajib setelah retry habis / sumber permanen tidak tersedia. */
    private fun showUnavailableAndNext() {
        mutableState.update {
            it.copy(isLoading = false, isPlaying = false, error = "Lagu tidak tersedia")
        }
        playNext(fromAutoAdvance = true)
    }

    /** Klasifikasi ringan untuk memilih profil retry berikutnya. */
    private fun classifyFailure(
        throwable: Throwable?,
        cacheDamaged: Boolean = false,
    ): StreamFailureKind {
        val error = throwable as? PlaybackException
        return when {
            cacheDamaged || (error != null && isCacheDamage(error)) -> StreamFailureKind.CACHE
            error?.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                StreamFailureKind.STALE_URL
            error?.errorCode in FORMAT_ERROR_CODES -> StreamFailureKind.FORMAT
            else -> StreamFailureKind.NETWORK
        }
    }

    /**
     * Deteksi kerusakan file cache. Media3 1.6.x tidak punya `CacheDataSourceException`,
     * jadi kelas cache lama dikenali lewat nama juga; penggantinya adalah
     * Cache.CacheException / CacheDataSink.CacheDataSinkException. ParserException
     * mencakup UnrecognizedInputFormatException. Bad HTTP status hanya dianggap
     * rusak bila lagu memang diputar dari cache penuh (partial cache wajar 403).
     */
    private fun isCacheDamage(error: PlaybackException): Boolean {
        val badHttpFromFullCache =
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
                playingFullyFromCache
        val cacheReadActive = playingFullyFromCache || audioCacheManager.playingFromCache.value
        val damagedCause = generateSequence<Throwable>(error) { it.cause }.any { cause ->
            val cacheIo = cause is Cache.CacheException ||
                cause is CacheDataSink.CacheDataSinkException ||
                cause.javaClass.name.contains("CacheDataSourceException", ignoreCase = true)
            val malformedFromCache = cacheReadActive &&
                (cause is ParserException || cause is UnrecognizedInputFormatException)
            cacheIo || malformedFromCache
        }
        return badHttpFromFullCache || damagedCause
    }

    /** Video dihapus/privat tidak perlu di-retry: langsung lanjut ke lagu berikutnya. */
    private fun isPermanentSourceError(error: PlaybackException): Boolean {
        val text = buildString {
            append(error.message.orEmpty())
            var cause = error.cause
            while (cause != null) {
                append(' ').append(cause.message.orEmpty())
                cause = cause.cause
            }
        }.lowercase()
        return PERMANENT_SOURCE_MARKERS.any(text::contains)
    }

    private companion object {
        const val TAG = "DengerinPlayer"
        const val POSITION_TICK_MS = 160L
        const val LISTENING_TICK_MS = 60_000L
        const val AUTO_ADVANCE_ATTEMPTS = 3
        const val MAX_STREAM_RETRIES = 3
        const val AUTOPLAY_TIMEOUT_MS = 8_000L
        const val PREFETCH_DELAY_MS = 1_500L

        val FORMAT_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )

        val RETRYABLE_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )

        val PERMANENT_SOURCE_MARKERS = listOf(
            "private video",
            "video is private",
            "video unavailable",
            "video has been removed",
            "video doesn't exist",
            "not available",
            "tidak tersedia",
            "dihapus",
            "privat",
            "404",
        )
    }
}
