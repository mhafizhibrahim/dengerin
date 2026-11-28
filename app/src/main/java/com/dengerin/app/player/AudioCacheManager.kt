@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.dengerin.app.core.youtubeVideoId
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.resolver.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicLong

/**
 * Satu-satunya pemilik [SimpleCache] audio. Singleton wajib: dua instance pada
 * folder yang sama akan memicu "Another SimpleCache instance uses the folder".
 */
class AudioCacheManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, AUDIO_CACHE_DIR).apply { mkdirs() }

    // Jangan evict saat SimpleCache memuat index lama: batas asli baru diketahui
    // setelah DataStore selesai dibaca. Kalau tidak, preferensi 1 GB bisa
    // terpotong ke 500 MB sebelum sempat diterapkan.
    private val evictor = DynamicLruEvictor(UNBOUNDED_MAX_BYTES)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val simpleCache: SimpleCache = SimpleCache(
        cacheDir,
        evictor,
        StandaloneDatabaseProvider(appContext),
    )

    private val _playingFromCache = MutableStateFlow(false)
    val playingFromCache: StateFlow<Boolean> = _playingFromCache

    /**
     * Status cache untuk UI: dibangun sekali di Dispatchers.IO dan diperbarui saat
     * entri masuk/dibuang. Composable cukup membaca `id in cachedTrackIds` dari
     * memori — tidak ada SimpleCache/File I/O saat fast scroll.
     */
    private val _cachedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val cachedTrackIds: StateFlow<Set<String>> = _cachedTrackIds.asStateFlow()

    @Volatile
    private var streamResolver: StreamResolver? = null

    private val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        dataSpec.key?.takeIf { it.startsWith(KEY_PREFIX) }
            ?: keyFromUri(dataSpec.uri)
    }

    init {
        // Kegagalan membaca indeks cache tidak boleh menggugurkan Application.onCreate().
        runCatching { evictor.setMaxBytes(UNBOUNDED_MAX_BYTES, simpleCache) }
        refreshCachedTrackIds()
    }

    /** Hitung ulang himpunan id dari indeks SimpleCache. Selalu di IO, never di UI. */
    fun refreshCachedTrackIds() {
        scope.launch {
            val ids = runCatching {
                simpleCache.keys.mapNotNull { key ->
                    key.takeIf { it.startsWith(KEY_PREFIX) }
                        ?.removePrefix(KEY_PREFIX)
                        ?.takeIf { it.isNotBlank() }
                }.toSet()
            }.getOrDefault(emptySet())
            _cachedTrackIds.value = ids
        }
    }

    private fun markCached(vararg ids: String?) {
        val valid = ids.mapNotNull { it?.takeIf { id -> id.isNotBlank() } }.toSet()
        if (valid.isNotEmpty()) _cachedTrackIds.update { it + valid }
    }

    private fun markUncached(vararg ids: String?) {
        val valid = ids.mapNotNull { it?.takeIf { id -> id.isNotBlank() } }.toSet()
        if (valid.isNotEmpty()) _cachedTrackIds.update { it - valid }
    }

    /**
     * Kunci cache permanen: videoId YouTube. URL googlevideo.com bertanda tangan
     * tidak pernah dipakai sebagai kunci karena berubah setiap ekstraksi.
     */
    fun videoIdFor(song: Song): String? =
        youtubeVideoId(song.url) ?: youtubeVideoId(song.id)

    fun cacheKeyForVideoId(videoId: String): String = "$KEY_PREFIX${sanitize(videoId)}"

    /** Kunci stabil dari videoId; spotifyId hanya fallback untuk lagu yang belum cocok. */
    fun cacheKeyFor(song: Song): String {
        val id = videoIdFor(song)
            ?: song.spotifyId.takeIf { it.isNotBlank() }
            ?: song.id
        return cacheKeyForVideoId(id)
    }

    /** URI lokal penanda "putar dari cache penuh" — tidak membuka jaringan. */
    fun localCacheUri(videoId: String): Uri = Uri.parse("$LOCAL_CACHE_SCHEME://$videoId")

    /** Reset indikator sebelum memutar lagu baru, lalu isi dari state cache nyata. */
    fun beginPlayback(song: Song) {
        val cached = cachedBytes(cacheKeyFor(song)) > 0L
        _playingFromCache.value = cached
        if (cached) markCached(videoIdFor(song), song.id)
    }

    /** Entri <= 64 KB (sisa skip cepat) otomatis dibuang saat status dicek. */
    fun isCached(song: Song): Boolean = isTrackKeyCached(cacheKeyFor(song)).also { cached ->
        if (cached) markCached(videoIdFor(song), song.id)
    }

    /**
     * true hanya bila cache benar-benar lengkap (content-length terpenuhi) dan
     * ukurannya > 64 KB. Partial cache tetap dilaporkan false supaya pemutar
     * mengambil URL baru dan menyambung sisa byte lewat HTTP Range.
     */
    fun isTrackCached(videoId: String): Boolean =
        isTrackKeyCached(cacheKeyForVideoId(videoId)).also { cached ->
            if (cached) markCached(videoId)
        }

    private fun isTrackKeyCached(key: String): Boolean {
        val bytes = cachedBytes(key)
        if (bytes <= MIN_VALID_BYTES) return false
        val contentLength = runCatching {
            ContentMetadata.getContentLength(simpleCache.getContentMetadata(key))
        }.getOrDefault(UNSET_LENGTH)
        // Tanpa content-length tidak bisa dibuktikan 100%; anggap partial (aman untuk kuota).
        if (contentLength == UNSET_LENGTH || contentLength <= MIN_VALID_BYTES) return false
        if (bytes < contentLength) return false
        // Byte melebihi content-length berarti span dari container/URL lain ikut
        // menumpuk pada kunci yang sama. Buang SEKARANG, jangan tunggu ExoPlayer error.
        if (bytes > contentLength + CACHE_LENGTH_TOLERANCE_BYTES) {
            runCatching { simpleCache.removeResource(key) }
            return false
        }
        return true
    }

    /** Hapus entri rusak; dipanggil dari Dispatchers.IO saat ExoPlayer error. */
    fun removeCorrupted(videoId: String): Boolean = resetCacheFor(videoId)

    /**
     * Buang byte lama saat format stream berubah (Opus → M4A pada retry ke-2).
     * Tanpa ini, HTTP Range akan menyambung byte Opus ke file M4A yang berbeda.
     */
    fun resetCacheFor(videoId: String): Boolean {
        val key = cacheKeyForVideoId(videoId)
        val removed = runCatching {
            simpleCache.removeResource(key)
            true
        }.getOrDefault(false)
        if (removed) {
            _playingFromCache.value = false
            markUncached(videoId)
        }
        return removed
    }

    fun markNotCached() {
        _playingFromCache.value = false
    }

    fun cacheSizeBytes(): Long = runCatching { simpleCache.cacheSpace }.getOrDefault(0L)

    /** Evict di IO: penghapusan span bisa menyentuh banyak file. */
    fun applyMaxBytes(bytes: Long) {
        val target = bytes.coerceAtLeast(MIN_CACHE_BYTES)
        scope.launch {
            evictor.setMaxBytes(target, simpleCache)
            refreshCachedTrackIds()
        }
    }

    /**
     * Urutan penting: OkHttpDataSource → ResolvingDataSource → CacheDataSource.
     *
     * CacheDataSource memakai `MediaItem.customCacheKey` (videoId) sehingga cache
     * hit membaca file lokal tanpa jaringan. Bila ada hole/byte sisa, resolver
     * mengganti URI lama dengan URL segar; CacheDataSource lalu melanjutkan
     * unduhan dari posisi sisa saja (HTTP Range), bukan mengulang dari 0%.
     */
    fun createDataSourceFactory(
        upstream: DataSource.Factory,
        cacheListener: TransferListener? = null,
        streamResolver: StreamResolver? = null,
    ): DataSource.Factory {
        this.streamResolver = streamResolver
        val cacheReadFactory = FileDataSource.Factory()
        if (cacheListener != null) cacheReadFactory.setListener(cacheListener)

        val resolving = ResolvingDataSource.Factory(upstream) { dataSpec ->
            val key = dataSpec.key?.takeIf { it.startsWith(KEY_PREFIX) }
                ?: StreamHeaderState.current().cacheKey
            val videoId = key?.removePrefix(KEY_PREFIX)
            if (videoId.isNullOrBlank()) {
                throw IOException("Kunci cache audio tidak tersedia.")
            }
            if (dataSpec.uri.scheme == LOCAL_CACHE_SCHEME) {
                // Jalur cache penuh tidak boleh membuka koneksi, walau cache berubah
                // antara pengecekan status dan open().
                throw IOException("Cache audio tidak lengkap; putar ulang lagu ini.")
            }
            val resolved = streamResolver?.resolveBlocking(videoId)
                ?: throw IOException("URL stream audio belum tersedia.")
            dataSpec.buildUpon()
                .setUri(resolved.audioUrl)
                .setHttpRequestHeaders(resolved.headers + ("User-Agent" to resolved.userAgent))
                .build()
        }

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setCacheReadDataSourceFactory(cacheReadFactory)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(simpleCache))
            .setUpstreamDataSourceFactory(resolving)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, totalBytes: Long) {
                    if (cacheSizeBytes > 0L) _playingFromCache.value = true
                }

                override fun onCacheIgnored(reason: Int) {
                    if (reason == CacheDataSource.CACHE_IGNORED_REASON_ERROR) {
                        _playingFromCache.value = false
                    }
                }
            })
    }

    /** Bersihkan semua span audio tanpa menutup SimpleCache (lock tetap aman). */
    fun clear() {
        runCatching {
            simpleCache.keys.toList().forEach(simpleCache::removeResource)
        }
        streamResolver?.clear()
        _playingFromCache.value = false
        _cachedTrackIds.value = emptySet()
    }

    private fun cachedBytes(key: String): Long {
        val bytes = runCatching { simpleCache.getCachedBytes(key, 0L, UNSET_LENGTH) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)
        if (bytes in 1L..MIN_VALID_BYTES) {
            // Sisa skip cepat: 1 byte .. 64 KB tidak berguna dan bikin resolver
            // mengira lagu sudah sebagian ter-cache. Buang sekarang juga.
            runCatching { simpleCache.removeResource(key) }
            return 0L
        }
        return bytes
    }

    private fun keyFromUri(uri: Uri): String {
        val raw = uri.getQueryParameter("v")
            ?: youtubeVideoId(uri.toString())
            ?: uri.toString().take(160)
        return "$KEY_PREFIX${sanitize(raw)}"
    }

    private fun sanitize(id: String): String =
        id.replace(REGEX_UNSAFE, "_").take(200)

    /**
     * LRU dengan batas yang bisa diubah saat pengguna mengganti ukuran cache.
     * Tidak membuat SimpleCache baru, jadi tidak ada konflik lock file.
     */
    private class DynamicLruEvictor(initialMaxBytes: Long) : CacheEvictor {

        private val maxBytes = AtomicLong(initialMaxBytes)
        private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
            val byTouch = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
            if (byTouch != 0) byTouch else a.compareTo(b)
        }
        private var currentSize = 0L
        private var cache: Cache? = null

        @Synchronized
        fun setMaxBytes(bytes: Long, targetCache: Cache) {
            cache = targetCache
            maxBytes.set(bytes)
            evict()
        }

        @Synchronized
        override fun requiresCacheSpanTouches(): Boolean = true

        @Synchronized
        override fun onCacheInitialized() = Unit

        @Synchronized
        override fun onStartFile(cache: Cache, key: String, position: Long, maxLength: Long) {
            this.cache = cache
            if (maxLength != C.LENGTH_UNSET.toLong()) evict()
        }

        @Synchronized
        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            this.cache = cache
            leastRecentlyUsed.add(span)
            currentSize += span.length
            evict()
        }

        @Synchronized
        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            this.cache = cache
            leastRecentlyUsed.remove(span)
            currentSize = (currentSize - span.length).coerceAtLeast(0L)
        }

        @Synchronized
        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        private fun evict() {
            val target = cache ?: return
            while (currentSize > maxBytes.get() && leastRecentlyUsed.isNotEmpty()) {
                target.removeSpan(leastRecentlyUsed.first())
            }
        }
    }

    companion object {
        private const val AUDIO_CACHE_DIR = "audio_cache"
        private const val KEY_PREFIX = "audio:"
        private const val LOCAL_CACHE_SCHEME = "dengerin-cache"
        private const val MIN_VALID_BYTES = 64L * 1024L
        /** Toleransi span cache yang sedikit melebihi Content-Length. */
        private const val CACHE_LENGTH_TOLERANCE_BYTES = 64L * 1024L
        private const val MIN_CACHE_BYTES = 16L * 1024 * 1024
        private const val UNBOUNDED_MAX_BYTES = Long.MAX_VALUE / 4
        private val UNSET_LENGTH = C.LENGTH_UNSET.toLong()
        private val REGEX_UNSAFE = Regex("[^A-Za-z0-9_-]")

        @Volatile
        private var instance: AudioCacheManager? = null

        fun get(context: Context): AudioCacheManager = instance ?: synchronized(this) {
            instance ?: AudioCacheManager(context.applicationContext).also { instance = it }
        }
    }
}
