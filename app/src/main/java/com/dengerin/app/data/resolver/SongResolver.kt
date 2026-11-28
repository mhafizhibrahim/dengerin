package com.dengerin.app.data.resolver

import com.dengerin.app.core.hdCoverUrl
import com.dengerin.app.core.isYouTubeImage
import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.core.youtubeListThumbnailUrl
import com.dengerin.app.core.youtubeThumbnailUrl
import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.SpotifyMatchCacheEntity
import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Jembatan Spotify → YouTube.
 *
 * Cek cache Room dulu, lalu cari di YouTube dan pilih hasil dengan kemiripan
 * judul/artis tertinggi serta selisih durasi terdekat. Setelah cocok, sampul
 * sementara Spotify di Room diganti thumbnail HD YouTube.
 */
class SongResolver(
    private val extractor: MusicExtractorRepository,
    private val dao: DengerinDao,
) {

    /**
     * Versi offline dari [resolve]: hanya membaca cache Room, TIDAK memanggil
     * NewPipeExtractor. Dipakai sebelum memutuskan play dari SimpleCache penuh.
     */
    suspend fun resolveCached(song: Song): Song? = withContext(Dispatchers.IO) {
        if (song.url.isNotBlank()) return@withContext song
        val key = song.spotifyId.ifBlank { "${song.title}|${song.artist}" }
        dao.match(key)?.let { cached ->
            song.copy(
                id = cached.youtubeVideoUrl,
                url = cached.youtubeVideoUrl,
                source = "YOUTUBE",
            )
        }
    }

    suspend fun resolve(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        runCatching {
            if (song.url.isNotBlank()) return@runCatching song

            val key = song.spotifyId.ifBlank { "${song.title}|${song.artist}" }
            dao.match(key)?.let { cached ->
                val resolved = song.withYouTubeUrl(cached.youtubeVideoUrl)
                persistResolvedThumbnail(song, resolved)
                return@runCatching resolved
            }

            val candidates = try {
                extractor.searchSongs("${song.title} ${song.artist}").getOrThrow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw IllegalStateException("Gagal mencari lagu di YouTube: ${error.message}", error)
            }
            if (candidates.isEmpty()) throw IllegalStateException("Lagu ini belum ketemu di YouTube.")

            val best = candidates.maxByOrNull { candidate ->
                score(song, candidate)
            } ?: throw IllegalStateException("Lagu ini belum ketemu di YouTube.")

            dao.saveMatch(
                SpotifyMatchCacheEntity(
                    spotifyTrackId = key,
                    youtubeVideoUrl = best.url,
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.durationMs,
                ),
            )
            val resolved = song.withYouTubeUrl(best.url, best.thumbnailUrl)
            persistResolvedThumbnail(song, resolved)
            resolved
        }
    }

    /**
     * Dipakai saat impor: semua lagu dicocokkan sekaligus dengan konkurensi
     * terbatas. Satu lagu gagal tidak membatalkan impor; lagu itu dibiarkan
     * tanpa thumbnail sampai dicoba lagi. Progres dikirim per lagu yang selesai.
     */
    suspend fun resolveAll(
        songs: List<Song>,
        onProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching<List<Song>> {
            if (songs.isEmpty()) return@runCatching emptyList()
            val permits = Semaphore(IMPORT_CONCURRENCY)
            val finished = AtomicInteger(0)
            coroutineScope {
                songs.map { song ->
                    async(Dispatchers.IO) {
                        permits.withPermit {
                            val resolved: Song = try {
                                resolve(song).getOrThrow()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                song
                            }
                            onProgress?.invoke(finished.incrementAndGet(), songs.size)
                            resolved
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /** Pakai gambar hasil pencarian YouTube, atau turunkan dari ID video bila kosong. */
    private fun Song.withYouTubeUrl(url: String, youtubeThumbnail: String = ""): Song {
        val source = listOf(youtubeThumbnail, youtubeListThumbnailUrl(url))
            .firstOrNull { isYouTubeImage(it) }
        val small = source?.let(::listThumbnailUrl).takeIf { !it.isNullOrBlank() }
            ?: thumbnailUrl
        val hd = source?.let(::hdCoverUrl)
            ?: youtubeThumbnailUrl(url)
            ?: coverUrl
        return copy(
            id = url,
            url = url,
            durationMs = if (durationMs > 0) durationMs else 0L,
            thumbnailUrl = small,
            coverUrl = hd,
            source = "YOUTUBE",
        )
    }

    /** Simpan versi ringan ke Room; cover HD diturunkan dari URL saat diputar. */
    private suspend fun persistResolvedThumbnail(original: Song, resolved: Song) {
        val thumbnail = resolved.thumbnailUrl
            .takeIf { isYouTubeImage(it) }
            ?.let(::listThumbnailUrl)
            ?: return
        val spotifyId = original.spotifyId.ifBlank { original.id }

        dao.updateHistoryThumbnail(
            videoUrl = resolved.url,
            spotifyId = spotifyId,
            thumbnailUrl = thumbnail,
        )
        dao.updatePlaylistTrackResolved(
            videoUrl = resolved.url,
            spotifyId = spotifyId,
            thumbnailUrl = thumbnail,
        )
        dao.updateLikedSongThumbnail(
            songId = spotifyId,
            videoUrl = resolved.url,
            spotifyId = spotifyId,
            thumbnailUrl = thumbnail,
        )
    }

    private fun score(target: Song, candidate: Song): Double {
        val titleScore = similarity(normalize(target.title), normalize(candidate.title))
        val artistScore = similarity(normalize(target.artist), normalize(candidate.artist))
        val durationScore = when {
            target.durationMs <= 0 || candidate.durationMs <= 0 -> 0.5
            else -> {
                val diff = kotlin.math.abs(target.durationMs - candidate.durationMs)
                1.0 - (diff.toDouble() / target.durationMs.toDouble()).coerceIn(0.0, 1.0)
            }
        }
        return titleScore * 0.6 + artistScore * 0.3 + durationScore * 0.1
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.9
        val tokensA = a.split(' ').filter { it.length > 1 }.toSet()
        val tokensB = b.split(' ').filter { it.length > 1 }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB).size
        return intersection.toDouble() / maxOf(tokensA.size, tokensB.size)
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""[^a-z0-9\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    private companion object {
        const val IMPORT_CONCURRENCY = 4
    }
}
