package com.dengerin.app.data.resolver

import android.os.SystemClock
import android.util.LruCache
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.domain.model.ResolvedAudioStream
import com.dengerin.app.domain.model.StreamPick
import com.dengerin.app.player.StreamHeaderState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * "StreamResolver" aplikasi: menyimpan hasil ekstraksi per videoId di RAM selama 4 jam.
 *
 * Cache ini hanya menyimpan URL + header (beberapa KB). Byte audio tetap menjadi
 * tanggung jawab [com.dengerin.app.player.AudioCacheManager]. URL YouTube bertanda
 * tangan bisa kedaluwarsa, jadi entri lama dibuang lebih awal lewat [invalidate]
 * saat pemutar menerima 403.
 */
class StreamResolver(
    private val extractor: MusicExtractorRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val urlCache = LruCache<String, ResolvedAudioStream>(MAX_ENTRIES)
    private val videoUrls = ConcurrentHashMap<String, String>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Ambil stream untuk pemutaran/auto-retry; cache RAM dipakai bila masih segar. */
    suspend fun resolve(
        videoId: String,
        videoUrl: String,
        pick: StreamPick,
    ): Result<ResolvedAudioStream> = withContext(dispatcher) {
        runCatching {
            videoUrls[videoId] = videoUrl
            val now = SystemClock.elapsedRealtime()
            val fresh = urlCache.get(videoId)
            if (fresh != null && fresh.expiresAtMs > now && fresh.pick == pick) {
                return@runCatching fresh
            }

            // Satu video hanya boleh diekstrak sekali: retry dan resolve dari loader
            // ExoPlayer tidak boleh mengirim dua request halaman YouTube yang sama.
            val mutex = locks.computeIfAbsent(videoId) { Mutex() }
            mutex.withLock {
                val cached = urlCache.get(videoId)
                if (cached != null && cached.expiresAtMs > now && cached.pick == pick) {
                    return@withLock cached
                }
                val stream = extractor.getAudioStreamUrl(videoUrl, pick).getOrThrow()
                val resolved = ResolvedAudioStream(
                    audioUrl = stream.audioUrl,
                    userAgent = stream.userAgent,
                    headers = stream.headers,
                    bitrateBps = stream.bitrateBps,
                    expiresAtMs = SystemClock.elapsedRealtime() + URL_TTL_MS,
                    pick = pick,
                )
                urlCache.put(videoId, resolved)
                resolved
            }
        }
    }

    /** Dipanggil ResolvingDataSource dari thread loader ExoPlayer (bukan coroutine). */
    fun resolveBlocking(videoId: String, pick: StreamPick? = null): ResolvedAudioStream? {
        peek(videoId)?.let { return it }
        val videoUrl = videoUrls[videoId] ?: return null
        // Ekstraksi ulang dari loader ExoPlayer harus memakai profil lagu yang sedang
        // diputar, bukan default Wi-Fi, supaya seluler/Data Saver tetap hemat kuota.
        val activePick = pick ?: StreamHeaderState.current().pick ?: StreamPick.STANDARD_OPUS
        return runBlocking(dispatcher) {
            resolve(videoId, videoUrl, activePick).getOrNull()
        }
    }

    /** Cache hit tanpa ekstraksi; dipakai jalur cache penuh & pengecekan URL. */
    fun peek(videoId: String): ResolvedAudioStream? {
        val stream = urlCache.get(videoId) ?: return null
        if (stream.expiresAtMs <= SystemClock.elapsedRealtime()) {
            urlCache.remove(videoId)
            return null
        }
        return stream
    }

    /** URL 403 / format ditolak: buang RAM cache dan paksa ekstraksi ulang. */
    fun invalidate(videoId: String) {
        urlCache.remove(videoId)
        videoUrls[videoId]?.let(extractor::invalidate)
    }

    fun clear() {
        urlCache.evictAll()
        videoUrls.clear()
        extractor.clearCache()
    }

    private companion object {
        const val MAX_ENTRIES = 48
        const val URL_TTL_MS = 4L * 60L * 60L * 1000L
    }
}
