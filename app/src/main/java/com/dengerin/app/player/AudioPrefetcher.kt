@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.resolver.StreamResolver
import com.dengerin.app.domain.model.StreamPick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Mengisi SimpleCache untuk lagu berikutnya tanpa memutarnya.
 *
 * Memakai CacheDataSource yang sama dengan pemutar, jadi kuncinya tetap videoId
 * dan byte yang sudah terunduh dipakai lagu tersebut saat Next ditekan (0 KB).
 * Tidak menyentuh [StreamHeaderState] global supaya header lagu yang sedang
 * diputar tidak tertimpa oleh prefetch.
 */
class AudioPrefetcher(
    private val cache: AudioCacheManager,
    private val resolver: StreamResolver,
    private val httpClient: OkHttpClient,
    private val networkListener: TransferListener? = null,
) {

    /** @return byte internet yang berhasil ditulis ke cache; 0 bila dibatalkan/gagal. */
    suspend fun prefetch(song: Song, pick: StreamPick): Long = withContext(Dispatchers.IO) {
        if (song.url.isBlank()) return@withContext 0L
        val videoId = cache.videoIdFor(song) ?: return@withContext 0L

        // URL sudah ada di cache RAM resolver bila lagu ini pernah disentuh; bila
        // belum, ekstraksi ringan dilakukan sekali di IO (bukan di loader ExoPlayer).
        val stream = resolver.resolve(videoId, song.url, pick).getOrNull()
            ?: return@withContext 0L

        val upstream = OkHttpDataSource.Factory(httpClient).apply {
            if (networkListener != null) setTransferListener(networkListener)
        }
        val factory = cache.createDataSourceFactory(
            upstream = upstream,
            cacheListener = null,
            streamResolver = resolver,
        )
        val dataSource: DataSource = factory.createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(Uri.parse(stream.audioUrl))
                // ResolvingDataSource membaca videoId dari kunci ini, bukan dari
                // StreamHeaderState milik lagu yang sedang diputar.
                .setKey(cache.cacheKeyForVideoId(videoId))
                .setPosition(0L)
                .build()

            if (dataSource.open(spec) < 0L) return@withContext 0L
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0L
            while (isActive && total < MAX_PREFETCH_BYTES) {
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT || read < 0) break
                if (read == 0) continue
                total += read
            }
            if (total > 0L) cache.refreshCachedTrackIds()
            total
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Prefetch bersifat best-effort: kegagalan tidak boleh mengganggu lagu aktif.
            Log.w(TAG, "Prefetch audio gagal untuk $videoId.", error)
            0L
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private companion object {
        const val TAG = "DengerinPrefetch"
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_PREFETCH_BYTES = 6L * 1024L * 1024L
    }
}
