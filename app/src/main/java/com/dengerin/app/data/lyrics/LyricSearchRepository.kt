package com.dengerin.app.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satu hasil LRCLIB yang sudah diringkas untuk kartu "Cocok dengan lirik". */
data class LyricSearchHit(
    val trackName: String,
    val artistName: String,
    val snippet: String,
    /** Teks yang diketik pengguna, disimpan ke riwayat saat kartu diketuk. */
    val query: String,
) {
    /** Keyword resolvers NewPipe: judul asli + artis asli dari LRCLIB. */
    val resolverQuery: String get() = listOf(trackName, artistName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
}

/**
 * Menerjemahkan potongan lirik yang diketik pengguna menjadi judul lagu.
 * Hanya 1–2 hasil unik yang dikembalikan supaya kartu tetap ringkas.
 */
class LyricSearchRepository(
    private val service: LrclibService = LrclibService(),
) {

    suspend fun search(query: String): List<LyricSearchHit> = withContext(Dispatchers.IO) {
        runCatching { service.searchByQuery(query) }
            .getOrElse { emptyList() }
            .asSequence()
            .filter { !it.instrumental && !it.plainLyrics.isNullOrBlank() }
            .mapNotNull { track ->
                val snippet = findSnippet(track.plainLyrics.orEmpty(), query) ?: return@mapNotNull null
                LyricSearchHit(
                    trackName = track.title.trim(),
                    artistName = track.artist.trim(),
                    snippet = snippet,
                    query = query.trim(),
                )
            }
            .filter { it.trackName.isNotBlank() && it.artistName.isNotBlank() }
            .distinctBy { normalize(it.trackName) to normalize(it.artistName) }
            .take(MAX_RESULTS)
            .toList()
    }

    /** Baris lirik pertama yang memuat salah satu kata kunci (min. 3 karakter). */
    private fun findSnippet(lyrics: String, query: String): String? {
        val words = normalize(query)
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
        if (words.isEmpty()) return null
        return lyrics.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                val lower = normalize(line)
                words.any { word -> lower == word || lower.contains(word) }
            }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private companion object {
        const val MAX_RESULTS = 2
    }
}
