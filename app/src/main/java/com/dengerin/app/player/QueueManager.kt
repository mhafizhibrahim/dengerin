package com.dengerin.app.player

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import com.dengerin.app.data.model.Song
import java.util.UUID

/** Zona antrean. Urutan tampil selalu: USER_QUEUE → CONTEXT_QUEUE → AUTOPLAY_QUEUE. */
enum class QueueSourceType {
    USER_QUEUE,
    CONTEXT_QUEUE,
    AUTOPLAY_QUEUE,
}

/**
 * Identitas satu baris antrean. [queueId] unik per entri walaupun lagu yang sama
 * ditambahkan berkali-kali, sehingga LazyColumn dan operasi hapus tidak bentrok.
 */
@Immutable
data class QueueItem(
    val queueId: String = UUID.randomUUID().toString(),
    val track: Song,
    val sourceType: QueueSourceType,
    val contextTitle: String,
)

/**
 * Satu-satunya pemilik urutan antrean. Semua mutasi dijalankan di main looper karena
 * hasilnya langsung dipakai ExoPlayer/MediaController dan Compose.
 *
 * Urutan [activeItems] adalah satu daftar datar (indeks dipakai Media3), tetapi setiap
 * item diberi [sourceType] supaya tiga zona tetap terpisah secara logis. Invariannya:
 * lagu aktif, lalu seluruh USER_QUEUE, lalu sisa CONTEXT_QUEUE, lalu AUTOPLAY_QUEUE.
 */
/**
 * Threading bisa disuntik supaya mesin antrean diuji di JVM tanpa Robolectric.
 * Di aplikasi, semua mutasi tetap dipaksa ke main looper.
 */
private object QueueThreads {
    val handler by lazy { Handler(Looper.getMainLooper()) }
    fun isMain(): Boolean = Looper.myLooper() == Looper.getMainLooper()
    fun post(block: () -> Unit) {
        handler.post(block)
    }
}

class QueueManager(
    private val isMainThread: () -> Boolean = QueueThreads::isMain,
    private val postToMain: (() -> Unit) -> Unit = QueueThreads::post,
) {
    private var activeItems: MutableList<QueueItem> = mutableListOf()

    /** Salinan urutan asli konteks, dipakai saat Shuffle dimatikan. */
    private var originalContext: List<QueueItem> = emptyList()

    /** Hanya diakses/mutasi dari main looper. */
    var currentIndex: Int = -1
        private set

    val currentQueueId: String? get() = activeItems.getOrNull(currentIndex)?.queueId
    val originalSongs: List<Song> get() = originalContext.map { it.track }
    val activeSongs: List<Song> get() = activeItems.map { it.track }
    val items: List<QueueItem> get() = activeItems.toList()
    val upcomingItems: List<QueueItem> get() = activeItems.filterIndexed { index, _ -> index > currentIndex }
    val isEmpty: Boolean get() = activeItems.isEmpty()
    val lastIndex: Int get() = activeItems.lastIndex

    val contextTitle: String
        get() = activeItems.firstOrNull { it.sourceType == QueueSourceType.CONTEXT_QUEUE }?.contextTitle
            ?: activeItems.firstOrNull { it.sourceType == QueueSourceType.AUTOPLAY_QUEUE }?.contextTitle
            ?: ""

    fun get(index: Int): Song? = activeItems.getOrNull(index)?.track

    fun getQueueItem(index: Int): QueueItem? = activeItems.getOrNull(index)

    /** Ganti konteks putar (playlist/artis/pencarian). Zona user & autoplay ikut dibersihkan. */
    fun setQueue(songs: List<Song>, shuffle: Boolean, selected: Song?, title: String = "") {
        mutate {
            val context = songs.map { newItem(it, QueueSourceType.CONTEXT_QUEUE, title) }
            originalContext = context.toList()
            activeItems = if (shuffle && context.isNotEmpty()) {
                val headIndex = context.indexOfFirst { sameSong(it.track, selected) }
                val head = if (headIndex >= 0) context[headIndex] else context.first()
                (listOf(head) + context.filterNot { it.queueId == head.queueId }.shuffled()).toMutableList()
            } else {
                context.toMutableList()
            }
            currentIndex = activeItems.indexOfFirst { sameSong(it.track, selected) }
                .takeIf { it >= 0 } ?: 0
        }
    }

    /** Tulis ulang zona AUTOPLAY_QUEUE di akhir daftar; lagu yang sedang diputar tidak berubah. */
    fun setAutoplayQueue(songs: List<Song>, title: String): Int {
        var firstIndex = -1
        mutate {
            val removed = activeItems.filter { it.sourceType == QueueSourceType.AUTOPLAY_QUEUE }
            activeItems = activeItems.filterNot { it.sourceType == QueueSourceType.AUTOPLAY_QUEUE }.toMutableList()
            if (currentIndex >= removed.size) {
                currentIndex -= removed.size
            } else {
                activeItems.indices.forEach { index ->
                    if (activeItems[index].queueId == removed.getOrNull(currentIndex)?.queueId) {
                        currentIndex = index
                    }
                }
            }
            if (songs.isNotEmpty()) {
                firstIndex = activeItems.size
                songs.forEach { song ->
                    activeItems.add(newItem(song, QueueSourceType.AUTOPLAY_QUEUE, title))
                }
            }
            if (currentIndex !in activeItems.indices) currentIndex = activeItems.lastIndex
        }
        return firstIndex
    }

    /** Putar berikutnya: sisip di urutan pertama USER_QUEUE tepat setelah lagu aktif. */
    fun playNext(song: Song, title: String = contextTitle) {
        mutate { insertUser(newItem(song, QueueSourceType.USER_QUEUE, title), atFirst = true) }
    }

    /** Tambah ke antrean: taruh di urutan terakhir USER_QUEUE, sebelum konteks. */
    fun addToQueue(song: Song, title: String = contextTitle) {
        mutate { insertUser(newItem(song, QueueSourceType.USER_QUEUE, title), atFirst = false) }
    }

    private fun insertUser(item: QueueItem, atFirst: Boolean) {
        if (activeItems.isEmpty()) {
            activeItems.add(item)
            currentIndex = 0
            return
        }
        val zoneStart = (currentIndex + 1).coerceIn(0, activeItems.size)
        var zoneEnd = zoneStart
        while (zoneEnd < activeItems.size &&
            activeItems[zoneEnd].sourceType == QueueSourceType.USER_QUEUE
        ) {
            zoneEnd++
        }
        activeItems.add(if (atFirst) zoneStart else zoneEnd, item)
    }

    /** Hapus satu lagu menunggu berdasarkan ID; lagu aktif tidak pernah terhapus. */
    fun removeUpcomingItem(queueId: String) {
        mutate {
            val index = activeItems.indexOfFirst { it.queueId == queueId }
            if (index < 0 || index == currentIndex) return@mutate
            activeItems.removeAt(index)
            if (index < currentIndex) currentIndex--
        }
    }

    /** Ubah urutan di dalam satu zona mendatang. Tidak boleh melintasi zona atau lagu aktif. */
    fun moveUpcomingItem(fromIndex: Int, toIndex: Int) {
        mutate {
            if (fromIndex == toIndex) return@mutate
            if (fromIndex !in activeItems.indices || toIndex !in activeItems.indices) return@mutate
            if (fromIndex <= currentIndex || toIndex <= currentIndex) return@mutate
            if (activeItems[fromIndex].sourceType != activeItems[toIndex].sourceType) return@mutate
            val boundedTo = toIndex.coerceIn(currentIndex + 1, activeItems.lastIndex)
            activeItems.add(boundedTo, activeItems.removeAt(fromIndex))
        }
    }

    /** Kosongkan hanya USER_QUEUE; sisa playlist/konteks dan autoplay tetap utuh. */
    fun clearUserQueue() {
        mutate {
            var removedBeforeCurrent = 0
            val kept = mutableListOf<QueueItem>()
            activeItems.forEachIndexed { index, item ->
                if (item.sourceType == QueueSourceType.USER_QUEUE && index != currentIndex) {
                    if (index < currentIndex) removedBeforeCurrent++
                } else {
                    kept.add(item)
                }
            }
            activeItems = kept
            currentIndex -= removedBeforeCurrent
            if (currentIndex !in activeItems.indices) currentIndex = activeItems.lastIndex
        }
    }

    fun indexOfQueueId(queueId: String): Int = activeItems.indexOfFirst { it.queueId == queueId }

    /**
     * Shuffle hanya menyentuh CONTEXT_QUEUE. Lagu aktif tetap di indeksnya dan
     * USER_QUEUE tidak pernah diacak. Matikan shuffle mengembalikan salinan asli.
     */
    fun toggleShuffle(enable: Boolean): Pair<List<Song>, Int> {
        mutate {
            val contextSlots = upcomingItems.filter { it.sourceType == QueueSourceType.CONTEXT_QUEUE }
            if (contextSlots.isEmpty()) return@mutate
            val slots = contextSlots.map { activeItems.indexOf(it) }
            if (enable) {
                val shuffled = contextSlots.shuffled()
                slots.forEachIndexed { position, slot -> activeItems[slot] = shuffled[position] }
            } else {
                val currentIds = contextSlots.map { it.queueId }.toSet()
                val restored = originalContext.filter { it.queueId in currentIds }
                slots.forEachIndexed { position, slot ->
                    restored.getOrNull(position)?.let { activeItems[slot] = it }
                }
            }
            val currentId = currentQueueId
            if (currentId != null) {
                currentIndex = activeItems.indexOfFirst { it.queueId == currentId }
                    .takeIf { it >= 0 } ?: currentIndex
            }
        }
        return activeSongs to currentIndex
    }

    fun setCurrentIndex(index: Int) {
        mutate { currentIndex = index.coerceIn(-1, activeItems.lastIndex) }
    }

    fun replaceActive(songs: List<Song>) {
        mutate {
            activeItems = activeItems.mapIndexed { index, item ->
                val replacement = songs.getOrNull(index)
                if (replacement != null) item.copy(track = replacement) else item
            }.toMutableList()
            currentIndex = currentIndex.coerceIn(-1, activeItems.lastIndex)
        }
    }

    fun replaceOriginal(songs: List<Song>) {
        mutate {
            originalContext = originalContext.mapIndexed { index, item ->
                val replacement = songs.getOrNull(index)
                if (replacement != null) item.copy(track = replacement) else item
            }
        }
    }

    fun replaceAt(index: Int, song: Song) {
        mutate {
            if (index !in activeItems.indices) return@mutate
            activeItems[index] = activeItems[index].copy(track = song)
        }
    }

    fun mapOriginal(transform: (Song) -> Song) {
        mutate { originalContext = originalContext.map { it.copy(track = transform(it.track)) } }
    }

    fun indexOf(song: Song?): Int {
        if (song == null) return -1
        return activeItems.indexOfFirst { sameSong(it.track, song) }
    }

    /**
     * Lagu berikutnya. Saat REPEAT_ALL melewati akhir daftar, rekomendasi autoplay
     * dibuang supaya tidak terputar ulang, lalu kembali ke lagu pertama konteks.
     */
    fun nextIndexAfter(cursor: Int, repeatAll: Boolean): Int? {
        val next = cursor + 1
        if (next in activeItems.indices) return next
        if (!repeatAll) return null
        mutate {
            activeItems = activeItems
                .filterNot { it.sourceType == QueueSourceType.AUTOPLAY_QUEUE }
                .toMutableList()
            if (currentIndex > activeItems.lastIndex) currentIndex = activeItems.lastIndex
        }
        return if (activeItems.isEmpty()) null else 0
    }

    private fun newItem(song: Song, type: QueueSourceType, title: String): QueueItem = QueueItem(
        track = song,
        sourceType = type,
        contextTitle = title,
    )

    private inline fun mutate(crossinline block: () -> Unit) {
        if (isMainThread()) {
            block()
        } else {
            postToMain { block() }
        }
    }

    private fun sameSong(a: Song?, b: Song?): Boolean {
        if (a == null || b == null) return false
        return a.id == b.id || (a.spotifyId.isNotBlank() && a.spotifyId == b.spotifyId)
    }
}
