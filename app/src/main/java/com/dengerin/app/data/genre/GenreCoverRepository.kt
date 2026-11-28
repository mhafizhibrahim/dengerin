package com.dengerin.app.data.genre

import com.dengerin.app.data.extractor.MusicExtractorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Auto-fetch sampul genre lewat NewPipe. Request jaringan dibatasi maksimal dua
 * bersamaan oleh [MusicExtractorRepository] (Semaphore(2) di Dispatchers.IO).
 * Genre yang sedang diambil tidak diambil dua kali.
 */
class GenreCoverRepository(
    private val cache: GenreCoverCache,
    private val extractor: MusicExtractorRepository,
) {

    private val inFlight = mutableSetOf<String>()
    private val mutex = Mutex()

    /** Snapshot sinkron dari cache; aman dipanggil saat ViewModel dibuat. */
    fun snapshot(): Map<String, String> = cache.freshCovers()

    /** Isi cache untuk semua genre yang belum segar. */
    suspend fun preload(genres: List<GenreCategory>): Map<String, String> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                genres.filter { cache.getFresh(it.id) == null }
                    .map { genre -> async { fetch(genre) } }
                    .forEach { it.await() }
            }
            cache.freshCovers()
        }

    /**
     * Ambil satu sampul genre. Mengembalikan null bila request gagal supaya kartu
     * memakai placeholder miring; kegagalan tidak pernah mengganggu grid.
     */
    suspend fun fetch(genre: GenreCategory): String? = withContext(Dispatchers.IO) {
        cache.getFresh(genre.id)?.let { return@withContext it }

        val claimed = mutex.withLock { inFlight.add(genre.id) }
        if (!claimed) return@withContext cache.getFresh(genre.id)

        try {
            val url = extractor.searchGenreCover(genre.searchQuery).getOrNull()
            url?.let { cache.put(genre.id, it) }
            url
        } finally {
            mutex.withLock { inFlight.remove(genre.id) }
        }
    }
}
