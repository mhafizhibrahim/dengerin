@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.player

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dengerin.app.DengerinApplication
import com.dengerin.app.MainActivity
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Pemutar latar belakang. Media3 yang membuat & memperbarui notifikasi/lockscreen;
 * service hanya menyuplai metadata kaya dan menerjemahkan tombol sistem ke antrean.
 */
class DengerinPlaybackService : MediaSessionService() {

    private lateinit var container: AppContainer
    private var exoPlayer: ExoPlayer? = null
    private var forwardingPlayer: ForwardingPlayer? = null
    private var mediaSession: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null

    /** Cover JPEG 512 px, cukup untuk SystemUI tanpa menggandakan memori Bitmap. */
    private val artworkCache = object : LruCache<String, ByteArray>(MAX_ARTWORK_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val queueController: PlaybackQueueManager
        get() = container.playerController

    private val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)

    override fun onCreate() {
        // Notifikasi dibuat Media3: small icon dipasang lewat provider sebelum super.onCreate().
        val notificationProvider = DefaultMediaNotificationProvider(this).apply {
            setSmallIcon(R.drawable.ic_notification_dengerin)
        }
        setMediaNotificationProvider(notificationProvider)

        super.onCreate()
        container = (application as DengerinApplication).container
        try {
            installPlayer()
        } catch (error: Throwable) {
            // Jangan biarkan satu kegagalan inisialisasi membuat proses crash;
            // tetapi tetap catat agar masalah session tidak silent.
            Log.e(TAG, "Gagal menyiapkan sesi pemutaran", error)
        }
    }

    private fun installPlayer() {
        val player = buildPlayer()
        exoPlayer = player

        val bridge = object : ForwardingPlayer(player) {
            /**
             * Timeline ExoPlayer sengaja hanya berisi satu item: antrean metadata-only
             * dipecah di [PlaybackQueueManager]. Akan tetapi perintah transport tetap
             * harus aktif supaya tombol lockscreen/headset tidak abu-abu.
             */
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()

            override fun getNextMediaItemIndex(): Int =
                if (queueController.hasNext) player.currentMediaItemIndex + 1 else C.INDEX_UNSET

            override fun getPreviousMediaItemIndex(): Int =
                if (queueController.hasPrevious) maxOf(0, player.currentMediaItemIndex - 1) else C.INDEX_UNSET

            override fun seekToNext() {
                queueController.skipNext()
            }

            override fun seekToNextMediaItem() {
                queueController.skipNext()
            }

            override fun seekToPrevious() {
                queueController.skipPrevious()
            }

            override fun seekToPreviousMediaItem() {
                queueController.skipPrevious()
            }
        }
        forwardingPlayer = bridge

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshMetadata()

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Durasi asli stream baru diketahui setelah player masuk STATE_READY.
                if (playbackState == Player.STATE_READY) refreshMetadata()
            }
        })

        val openPlayer = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openPlayer,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, bridge)
            .setId(SESSION_ID)
            .setSessionActivity(pendingIntent)
            .setCallback(SessionCallback())
            .build()
        publishButtons()

        scope.launch {
            container.playerController.uiState
                .map { it.isLiked to it.isShuffle }
                .distinctUntilChanged()
                .collect { publishButtons() }
        }
        container.dataUsageTracker.flush()
    }

    private fun buildPlayer(): ExoPlayer {
        // Satu pool koneksi dengan prefetch: tidak ada HTTP client ganda di proses.
        val streamClient = container.streamClient

        // TransferListener tetap terpasang di OkHttpDataSource.Factory. Meski
        // dibungkus ResolvingDataSource → CacheDataSource, listener ini menerima
        // semua byte internet saat streaming maupun saat fallback auto-retry.
        val upstream = DynamicOkHttpDataSourceFactory(
            streamClient,
            container.dataUsageTracker.networkTransferListener,
        )
        // Resolver 4 jam ada di dalam CacheDataSource (lihat AudioCacheManager).
        val cached = container.audioCacheManager.createDataSourceFactory(
            upstream,
            container.dataUsageTracker.cacheTransferListener,
            container.streamResolver,
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cached)

        // Smart Buffer ketat: cukup untuk audio, tidak menyedot puluhan MB saat Next/Skip.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_AUDIO_BUFFER_SIZE)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            // WAKE_LOCK hanya dipakai saat streaming; tidak ada wake lock saat pause.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /**
     * Metadata sudah diisi PlayerController (title/artist/album/duration/artworkUri).
     * Service menambahkan artwork bytes & duration final setelah stream benar-benar
     * terdeteksi, lewat replaceMediaItem yang tidak memutus playback.
     */
    private fun refreshMetadata() {
        val item = exoPlayer?.currentMediaItem ?: return
        val index = exoPlayer?.currentMediaItemIndex ?: -1
        if (index < 0) return

        artworkJob?.cancel()
        artworkJob = scope.launch {
            val url = item.mediaMetadata.artworkUri?.toString()
            val bytes = if (url.isNullOrBlank()) {
                null
            } else {
                withContext(Dispatchers.IO) { loadArtwork(url) }
            }
            if (!isActive) return@launch

            val player = exoPlayer ?: return@launch
            val current = player.currentMediaItem ?: return@launch
            if (current.mediaId != item.mediaId || player.currentMediaItemIndex != index) {
                return@launch
            }
            val duration = player.duration.takeIf { it > 0 }
                ?: current.mediaMetadata.durationMs?.takeIf { it > 0 }
                ?: C.TIME_UNSET
            // Hanya tulis ulang MediaItem jika ada nilai yang benar-benar berubah.
            // Tanpa ini, kegagalan memuat cover (bytes == null) membuat replaceMediaItem
            // terpicu lagi oleh onMediaItemTransition → loop tak berhenti.
            val needsDuration = duration != C.TIME_UNSET &&
                current.mediaMetadata.durationMs != duration
            val needsArtwork = bytes != null &&
                current.mediaMetadata.artworkData?.let { bytes.contentEquals(it) } != true
            if (!needsDuration && !needsArtwork) return@launch

            val metadata = current.mediaMetadata.buildUpon()
            if (needsArtwork) {
                metadata.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            if (needsDuration) metadata.setDurationMs(duration)
            runCatching {
                player.replaceMediaItem(
                    index,
                    current.buildUpon().setMediaMetadata(metadata.build()).build(),
                )
            }
        }
    }

    private suspend fun loadArtwork(url: String): ByteArray? = withContext(Dispatchers.IO) {
        artworkCache.get(url)?.let { return@withContext it }
        val request = ImageRequest.Builder(this@DengerinPlaybackService)
            .data(url)
            .size(ARTWORK_SIZE_PX, ARTWORK_SIZE_PX)
            .allowHardware(false)
            .build()
        val result = container.imageLoader.execute(request) as? SuccessResult
            ?: return@withContext null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) return@withContext null
        val bytes = output.toByteArray()
        artworkCache.put(url, bytes)
        runCatching { output.close() }
        bytes
    }

    /** Ikon memakai konstanta Media3: SystemUI mewarnainya otomatis, tanpa drawable baru. */
    @Suppress("DEPRECATION")
    private fun publishButtons() {
        val session = mediaSession ?: return
        val liked = queueController.isLiked
        val shuffle = queueController.isShuffle
        val buttons = listOf(
            CommandButton.Builder(
                if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
            )
                .setDisplayName(getString(if (liked) R.string.action_like_remove else R.string.action_like_add))
                .setSessionCommand(likeCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(
                if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
            )
                .setDisplayName(getString(if (shuffle) R.string.action_shuffle_off else R.string.action_shuffle_on))
                .setSessionCommand(shuffleCommand)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
        // Android 13+ membaca preferences; notifikasi/legacy masih memakai custom layout.
        session.setMediaButtonPreferences(buttons)
        session.setCustomLayout(buttons)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(likeCommand)
                .add(shuffleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LIKE -> queueController.toggleLike()
                ACTION_TOGGLE_SHUFFLE -> queueController.toggleShuffle()
            }
            publishButtons()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Ditutup dari task switcher = hentikan pemutaran permanen. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        queueController.stopPlayback()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        scope.cancel()
        container.dataUsageTracker.flush()
        container.listeningHistoryRepository.finishSong()
        runCatching {
            mediaSession?.release()
            exoPlayer?.release()
        }
        mediaSession = null
        forwardingPlayer = null
        exoPlayer = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "DengerinPlayback"
        const val SESSION_ID = "dengerin_playback"
        const val ACTION_TOGGLE_LIKE = "com.dengerin.app.action.TOGGLE_LIKE"
        const val ACTION_TOGGLE_SHUFFLE = "com.dengerin.app.action.TOGGLE_SHUFFLE"

        const val MIN_BUFFER_MS = 6_000
        const val MAX_BUFFER_MS = 15_000
        const val BUFFER_FOR_PLAYBACK_MS = 1_500
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500

        const val ARTWORK_SIZE_PX = 512
        const val JPEG_QUALITY = 85
        const val MAX_ARTWORK_CACHE_BYTES = 8 * 1024 * 1024
    }
}
