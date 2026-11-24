package com.dengerin.app.data.lyrics

import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.LyricsCacheEntity
import com.dengerin.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Hasil lirik siap dipakai UI: baris bersynkron (jika ada) atau lirik polos. */
data class LyricsBundle(
    val syncedLines: List<LyricLine>,
    val plainLyrics: String?,
    val plainLines: List<String>,
    val isSynced: Boolean,
)

/**
 * Cache Room + LRCLIB. Room dipakai lebih dulu supaya lirik langsung muncul saat lagu
 * diulang; API hanya dipanggil saat cache belum ada / sudah kedaluwarsa tanpa lirik sync.
 * Offset sinkronisasi disimpan per lagu selama sesi aplikasi berjalan.
 */
class LyricsRepository(
    private val dao: DengerinDao,
    private val service: LrclibService = LrclibService(),
) {

    private val syncOffsets = ConcurrentHashMap<String, Long>()

    fun cacheKey(song: Song): String {
        val artist = normalize(song.artist)
        val title = normalize(song.title)
        return "$artist|$title|${song.durationMs / 1000L}"
    }

    fun syncOffset(cacheKey: String): Long = syncOffsets[cacheKey] ?: 0L

    fun setSyncOffset(cacheKey: String, offsetMs: Long) {
        syncOffsets[cacheKey] = offsetMs
    }

    suspend fun load(song: Song): Result<LyricsBundle> = withContext(Dispatchers.IO) {
        val key = cacheKey(song)
        val cached = runCatching { dao.lyrics(key) }.getOrNull()
        if (cached != null && (cached.syncedLyrics != null || fresh(cached.fetchedAt))) {
            return@withContext Result.success(cached.toBundle())
        }

        val found = runCatching {
            service.find(song.title, song.artist, song.durationMs)
        }.getOrNull()

        if (found != null) {
            val entity = LyricsCacheEntity(
                cacheKey = key,
                title = song.title,
                artist = song.artist,
                syncedLyrics = found.syncedLyrics,
                plainLyrics = found.plainLyrics,
                instrumental = found.instrumental,
                fetchedAt = System.currentTimeMillis(),
            )
            runCatching { dao.upsertLyrics(entity) }
            return@withContext Result.success(
                LyricsBundle(
                    syncedLines = LrcParser.parse(found.syncedLyrics),
                    plainLyrics = found.plainLyrics,
                    plainLines = plainLines(found.plainLyrics),
                    isSynced = !found.syncedLyrics.isNullOrBlank(),
                ),
            )
        }

        if (cached != null) return@withContext Result.success(cached.toBundle())

        Result.failure(IllegalStateException("Lirik belum tersedia untuk lagu ini."))
    }

    private fun fresh(fetchedAt: Long): Boolean =
        System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS

    private fun LyricsCacheEntity.toBundle(): LyricsBundle {
        val parsed = LrcParser.parse(syncedLyrics)
        return LyricsBundle(
            syncedLines = parsed,
            plainLyrics = plainLyrics,
            plainLines = plainLines(plainLyrics),
            isSynced = parsed.isNotEmpty(),
        )
    }

    private fun plainLines(text: String?): List<String> =
        text?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""\([^)]*\)"""), "")
        .replace(Regex("""\[[^\]]*\]"""), "")
        .replace(Regex("""[^a-z0-9]"""), "")
        .trim()

    private companion object {
        const val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
