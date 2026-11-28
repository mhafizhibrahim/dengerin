package com.dengerin.app.data.autoplay

import com.dengerin.app.data.extractor.MusicExtractorRepository
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.recent.RecentlyPlayedStore

/**
 * Radio otomatis: hanya mengambil metadata ringan (tanpa URL stream audio).
 *
 * Resolusi stream tetap terjadi di PlayerController saat lagu benar-benar menjadi
 * lagu aktif, jadi lagu yang masih menunggu di antrean tidak menghabiskan kuota.
 */
class AutoplayRepository(
    private val extractor: MusicExtractorRepository,
    private val recentlyPlayed: RecentlyPlayedStore,
) {
    suspend fun similarTo(seed: Song, limit: Int = DEFAULT_LIMIT): List<Song> {
        val query = listOf(seed.artist, seed.title, "mix")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return emptyList()

        val candidates = extractor.searchSongs(query).getOrNull().orEmpty()
        val playedKeys = buildSet {
            add(songKey(seed))
            recentlyPlayed.songs.value.forEach { add(songKey(it)) }
        }
        return candidates
            .filter { it.url.isNotBlank() }
            .filterNot { songKey(it) in playedKeys }
            .distinctBy(::songKey)
            .take(limit)
    }

    private fun songKey(song: Song): String =
        song.spotifyId.ifBlank { song.id }.ifBlank { song.url }

    private companion object {
        const val DEFAULT_LIMIT = 7
    }
}
