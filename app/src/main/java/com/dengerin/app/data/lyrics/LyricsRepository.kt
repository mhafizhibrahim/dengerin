package com.dengerin.app.data.lyrics

import com.dengerin.app.core.youtubeVideoId
import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.LyricsCacheEntity
import com.dengerin.app.data.db.VideoLyricsCacheEntity
import com.dengerin.app.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/** Hasil lirik siap dipakai UI: baris bersynkron (jika ada) atau lirik polos. */
data class LyricsBundle(
    val syncedLines: List<LyricLine>,
    val plainLyrics: String?,
    val plainLines: List<String>,
    val isSynced: Boolean,
    /** Badge kecil di UI, misalnya "Lirik tersinkronisasi · YouTube CC". */
    val sourceLabel: String? = null,
)

const val SOURCE_LRCLIB = "Lirik tersinkronisasi · LRCLIB"
const val SOURCE_YOUTUBE_CC = "Lirik tersinkronisasi · YouTube CC"
const val NO_LYRICS_MESSAGE = "Lirik tersinkronisasi belum tersedia untuk lagu ini."

/**
 * Urutan sumber lirik: cache per videoId → LRCLIB (judul dibersihkan) → Closed Captions
 * YouTube via NewPipeExtractor → plain lyrics LRCLIB → YouTube Music → pesan kosong.
 *
 * Hasil final disimpan di cache Room per videoId, jadi putar ulang langsung dari lokal
 * (0 KB kuota, instan). Semua request berada di `Dispatchers.IO` dan gagal-silent supaya
 * tidak memblokir pemutaran audio.
 */
class LyricsRepository(
    private val dao: DengerinDao,
    private val service: LrclibService = LrclibService(),
    private val youtubeMusic: YouTubeMusicLyricsSource = YouTubeMusicLyricsSource(OkHttpClient()),
    private val youtubeCaptions: YouTubeLyricsExtractor = YouTubeLyricsExtractor(OkHttpClient()),
) {

    private val syncOffsets = ConcurrentHashMap<String, Long>()

    /** Kunci offset sinkronisasi: tetap berdasarkan identitas lagu, bukan sumber lirik. */
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
        val videoId = youtubeVideoId(song.url)?.takeIf { it.isNotBlank() }

        // 1. Cache lokal per videoId: tidak ada request sama sekali saat lagu diulang.
        if (videoId != null) {
            val cached = runCatching { dao.videoLyrics(videoId) }.getOrNull()
            if (cached != null && cached.hasLyrics()) {
                return@withContext Result.success(cached.toBundle())
            }
        }

        // 2. LRCLIB memakai judul yang sudah dibersihkan dari annotation YouTube.
        val cleanTitle = cleanTrackTitleForLyrics(song.title)
        val found = runCatching { service.find(cleanTitle, song.artist, song.durationMs) }
            .getOrNull()

        val synced = found?.syncedLyrics?.takeIf { it.isNotBlank() }
        if (synced != null) {
            val parsed = LrcParser.parse(synced)
            if (parsed.isNotEmpty()) {
                val bundle = LyricsBundle(
                    syncedLines = parsed,
                    plainLyrics = found.plainLyrics,
                    plainLines = plainLines(found.plainLyrics),
                    isSynced = true,
                    sourceLabel = SOURCE_LRCLIB,
                )
                cache(videoId, synced, found.plainLyrics, SOURCE_LRCLIB)
                return@withContext Result.success(bundle)
            }
        }

        val plainFallback = found?.plainLyrics?.takeIf { it.isNotBlank() }

        // 3. YouTube CC: caption video yang diputar, lalu satu pencarian ringan bila kosong.
        if (videoId != null) {
            val captionLines = runCatching {
                youtubeCaptions.load(videoId, cleanTitle, song.artist)
            }.getOrNull().orEmpty()
            if (captionLines.isNotEmpty()) {
                val bundle = LyricsBundle(
                    syncedLines = captionLines,
                    plainLyrics = found?.plainLyrics,
                    plainLines = plainLines(found?.plainLyrics),
                    isSynced = true,
                    sourceLabel = SOURCE_YOUTUBE_CC,
                )
                // Disimpan sebagai LRC supaya cache Room yang sudah ada dipakai ulang.
                cache(videoId, LrcParser.serialize(captionLines), found?.plainLyrics, SOURCE_YOUTUBE_CC)
                return@withContext Result.success(bundle)
            }
        }

        // 4. YouTube CC kosong: pakai plainLyrics LRCLIB bila tersedia.
        if (plainFallback != null) {
            val bundle = LyricsBundle(
                syncedLines = emptyList(),
                plainLyrics = plainFallback,
                plainLines = plainLines(plainFallback),
                isSynced = false,
            )
            cache(videoId, null, plainFallback, null)
            return@withContext Result.success(bundle)
        }

        // 5. Cadangan terakhir: lirik polos dari YouTube Music bila keduanya tidak ada.
        if (videoId != null) {
            val fromYtm = runCatching { youtubeMusic.load(videoId) }.getOrNull()
            if (fromYtm != null && fromYtm.lyrics.isNotBlank()) {
                val bundle = LyricsBundle(
                    syncedLines = emptyList(),
                    plainLyrics = fromYtm.lyrics,
                    plainLines = plainLines(fromYtm.lyrics),
                    isSynced = false,
                    sourceLabel = fromYtm.sourceLabel,
                )
                cache(videoId, null, fromYtm.lyrics, fromYtm.sourceLabel)
                return@withContext Result.success(bundle)
            }
        }

        // Lagu tanpa videoId (mis. lokal) masih bisa memakai cache LRCLIB lama.
        val legacy = runCatching { dao.lyrics(cacheKey(song)) }.getOrNull()
        if (legacy != null && legacy.hasLyrics()) {
            return@withContext Result.success(legacy.toBundle())
        }

        Result.failure(IllegalStateException(NO_LYRICS_MESSAGE))
    }

    private suspend fun cache(
        videoId: String?,
        syncedLyrics: String?,
        plainLyrics: String?,
        sourceLabel: String?,
    ) {
        if (videoId == null) return
        val entity = VideoLyricsCacheEntity(
            videoId = videoId,
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            sourceLabel = sourceLabel,
        )
        if (!entity.hasLyrics()) return
        runCatching { dao.upsertVideoLyrics(entity) }
    }

    private fun LyricsCacheEntity.hasLyrics(): Boolean =
        !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    private fun VideoLyricsCacheEntity.hasLyrics(): Boolean =
        !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    private fun LyricsCacheEntity.toBundle(): LyricsBundle {
        val parsed = LrcParser.parse(syncedLyrics)
        return LyricsBundle(
            syncedLines = parsed,
            plainLyrics = plainLyrics,
            plainLines = plainLines(plainLyrics),
            isSynced = parsed.isNotEmpty(),
        )
    }

    private fun VideoLyricsCacheEntity.toBundle(): LyricsBundle {
        val parsed = LrcParser.parse(syncedLyrics)
        val plain = plainLyrics?.takeIf { it.isNotBlank() }
        return LyricsBundle(
            syncedLines = parsed,
            plainLyrics = plain,
            plainLines = plainLines(plain),
            isSynced = parsed.isNotEmpty(),
            sourceLabel = sourceLabel,
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
}
