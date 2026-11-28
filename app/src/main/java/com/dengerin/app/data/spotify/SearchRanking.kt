package com.dengerin.app.data.spotify

import java.text.Normalizer
import java.util.Locale

/**
 * Aturan peringkat pencarian dan validasi kepemilikan lagu.
 *
 * File ini sengaja murni JVM: tidak mengimpor Android/Compose supaya bisa diuji
 * langsung di `testDebugUnitTest` tanpa dependency tambahan.
 */

private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
private val SEARCH_PUNCTUATION_REGEX = Regex("[,.'\\-&]+")
private val NON_ALNUM_REGEX = Regex("[^\\p{L}\\p{N}\\s]+")
private val WHITESPACE_REGEX = Regex("\\s+")
private val CREDIT_SEPARATOR_REGEX = Regex(
    "(?i)(?:\\s*(?:,|&|;|·)\\s*|\\s+(?:feat\\.?|ft\\.?|featuring|x)\\s+)",
)
private val YOUTUBE_SUFFIX_REGEX = Regex("(?i)\\s*(?:[-–—]\\s*)?(?:topic|vevo)\\s*$")
private val OFFICIAL_CHANNEL_REGEX = Regex(
    "(?i)(?:-\\s*topic|vevo|official\\s*music|music\\s*official|musik\\s*resmi|official\\s*channel)",
)

private const val POPULAR_AUDIENCE_THRESHOLD = 25_000L
const val NEW_PIPE_OFFICIAL_SUBSCRIBER_THRESHOLD = 50_000L
private const val TOP_TRACK_WINDOW = 8

fun normalizeSearchText(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(DIACRITIC_REGEX, "")
    .replace(SEARCH_PUNCTUATION_REGEX, " ")
    .replace(NON_ALNUM_REGEX, " ")
    .replace(WHITESPACE_REGEX, " ")
    .trim()

fun levenshteinDistance(first: String, second: String): Int {
    if (first == second) return 0
    if (first.isEmpty()) return second.length
    if (second.isEmpty()) return first.length

    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    for (i in first.indices) {
        current[0] = i + 1
        for (j in second.indices) {
            val substitutionCost = if (first[i] == second[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + substitutionCost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[second.length]
}

fun normalizedLevenshteinSimilarity(first: String, second: String): Double {
    val longest = maxOf(first.length, second.length)
    if (longest == 0) return 1.0
    return (longest - levenshteinDistance(first, second)).toDouble() / longest
}

fun isFullNameMatch(query: String, artistName: String): Boolean {
    val normalizedQuery = normalizeSearchText(query)
    val normalizedArtist = normalizeSearchText(artistName)
    if (normalizedQuery.isBlank() || normalizedArtist.isBlank()) return false
    if (normalizedQuery == normalizedArtist) return true

    val queryWords = normalizedQuery.split(' ')
    val artistWords = normalizedArtist.split(' ')
    if (queryWords.size != artistWords.size) return false

    return if (artistWords.size >= 2) {
        normalizedLevenshteinSimilarity(normalizedQuery, normalizedArtist) >= 0.85
    } else {
        normalizedQuery.length >= 5 && levenshteinDistance(normalizedQuery, normalizedArtist) <= 1
    }
}

fun isArtistRelevantToQuery(query: String, artistName: String): Boolean {
    val normalizedQuery = normalizeSearchText(query)
    val normalizedArtist = normalizeSearchText(artistName)
    if (normalizedQuery.isBlank() || normalizedArtist.isBlank()) return false
    if (normalizedQuery == normalizedArtist) return true

    val queryWords = normalizedQuery.split(' ')
    val artistWords = normalizedArtist.split(' ')
    if (queryWords.size == 1) {
        return artistWords.firstOrNull() == normalizedQuery ||
            normalizedArtist.startsWith("$normalizedQuery ")
    }
    return queryWords.all { it in artistWords } ||
        normalizedLevenshteinSimilarity(normalizedQuery, normalizedArtist) >= 0.85
}

fun isValidSpotifyArtistId(value: String): Boolean = normalizeSpotifyArtistId(value) != null

fun normalizeSpotifyArtistId(value: String): String? {
    val raw = value.trim()
    val id = if (raw.startsWith("spotify:artist:", ignoreCase = true)) {
        raw.substringAfterLast(':')
    } else {
        raw
    }
    if (id.length != 22) return null
    return id.takeIf { candidate ->
        candidate.all { char ->
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'
        }
    }?.lowercase(Locale.ROOT)
}

fun isOfficialMusicChannelName(name: String): Boolean =
    OFFICIAL_CHANNEL_REGEX.containsMatchIn(name)

/** Bersihkan akhiran otomatis YouTube sebelum membandingkan kredit artis. */
fun stripYouTubeArtistSuffix(value: String): String =
    value.replace(YOUTUBE_SUFFIX_REGEX, "").trim()

fun splitArtistCredits(value: String): List<String> = value
    .split(CREDIT_SEPARATOR_REGEX)
    .map(::stripYouTubeArtistSuffix)
    .filter { it.isNotBlank() }

private fun normalizedArtistCredit(value: String): String =
    normalizeSearchText(stripYouTubeArtistSuffix(value))

data class TrackOwnershipCandidate(
    val title: String = "",
    val artist: String,
    val artistIds: List<String> = emptyList(),
    val isNewPipe: Boolean = false,
)

/**
 * Validasi kredit lagu. Judul lagu sengaja tidak pernah dipakai: kemiripan judul
 * bukan bukti kepemilikan artis.
 */
fun isTrackOwnedByArtist(
    track: TrackOwnershipCandidate,
    targetArtistId: String,
    targetArtistName: String,
): Boolean {
    val targetId = normalizeSpotifyArtistId(targetArtistId)
    val trackIds = track.artistIds.mapNotNull(::normalizeSpotifyArtistId).distinct()
    if (targetId != null && trackIds.isNotEmpty()) {
        return targetId in trackIds
    }

    val targetName = normalizedArtistCredit(targetArtistName)
    if (targetName.isBlank()) return false
    return splitArtistCredits(track.artist).any { name ->
        normalizedArtistCredit(name) == targetName
    }
}

data class ArtistPopularitySignals(
    val featuredTopResult: Boolean = false,
    val topTrackCoOccurrence: Boolean = false,
    val audienceSize: Long = 0L,
    val isOfficialMusicChannel: Boolean = false,
    /** Dibaca dari payload hanya untuk dicek secara eksplisit: tidak pernah jadi syarat tunggal. */
    val verified: Boolean = false,
)

private fun isKeywordCollision(query: String, artistName: String): Boolean {
    val normalizedQuery = normalizeSearchText(query)
    val normalizedArtist = normalizeSearchText(artistName)
    val queryWords = normalizedQuery.split(' ')
    val artistWords = normalizedArtist.split(' ')
    return queryWords.size == 1 &&
        artistWords.size >= 2 &&
        artistWords.first() == queryWords.first()
}

fun isPopularArtist(
    query: String,
    artistName: String,
    isSpotifyArtist: Boolean,
    signals: ArtistPopularitySignals,
): Boolean {
    if (!isArtistRelevantToQuery(query, artistName)) return false

    val featured = signals.featuredTopResult
    val coOccurrence = signals.topTrackCoOccurrence
    // Query 1 kata yang hanya jadi awalan nama multi-kata tidak boleh lolos
    // hanya karena verified/followers tanpa dukungan slot atas maupun lagu.
    if (isKeywordCollision(query, artistName) && !featured && !coOccurrence) return false

    return if (isSpotifyArtist) {
        featured || coOccurrence || signals.audienceSize >= POPULAR_AUDIENCE_THRESHOLD
    } else {
        // Channel YouTube: butuh penanda resmi/subscriber besar DAN lagu pendukung.
        signals.isOfficialMusicChannel && coOccurrence
    }
}

private fun isTopResultAllowed(
    topResult: SpotifyCatalogTopResult,
    query: String,
    artistsById: Map<String, SpotifyCatalogArtist>,
): Boolean = when (topResult) {
    is SpotifyCatalogTopResult.Artist -> {
        val artist = artistsById[topResult.item.id] ?: topResult.item
        (artist.isPopularArtist && isArtistRelevantToQuery(query, artist.name)) ||
            isFullNameMatch(query, artist.name)
    }

    is SpotifyCatalogTopResult.Playlist -> true
    is SpotifyCatalogTopResult.Track -> true
}

/** Guardrail akhir: hormati urutan backend, tapi blokir artis non-populer. */
fun applySearchGuardrails(
    result: SpotifyCatalogSearchResult,
    query: String,
): SpotifyCatalogSearchResult {
    val topTracks = result.tracks.take(TOP_TRACK_WINDOW)
    val topIds = topTracks.flatMap { track ->
        track.artistIds.mapNotNull(::normalizeSpotifyArtistId)
    }.toSet()
    val topNames = topTracks.flatMap { track ->
        splitArtistCredits(track.artist).map(::normalizedArtistCredit)
    }.toSet()
    val allIdCounts = result.tracks.flatMap { track ->
        track.artistIds.mapNotNull(::normalizeSpotifyArtistId)
    }.groupingBy { it }.eachCount()
    val allNameCounts = result.tracks.flatMap { track ->
        splitArtistCredits(track.artist).map(::normalizedArtistCredit)
    }.groupingBy { it }.eachCount()

    val updatedArtists = result.artists.map { artist ->
        val artistId = normalizeSpotifyArtistId(artist.id)
        val artistName = normalizedArtistCredit(artist.name)
        val coOccurrence = (artistId != null && artistId in topIds) ||
            artistName in topNames ||
            (artistId != null && (allIdCounts[artistId] ?: 0) >= 2) ||
            (allNameCounts[artistName] ?: 0) >= 2
        val officialChannel = artist.isOfficialMusicChannel ||
            isOfficialMusicChannelName(artist.name) ||
            artist.subscriberCount >= NEW_PIPE_OFFICIAL_SUBSCRIBER_THRESHOLD
        val popular = isPopularArtist(
            query = query,
            artistName = artist.name,
            isSpotifyArtist = artistId != null,
            signals = ArtistPopularitySignals(
                featuredTopResult = artist.isFeaturedTopResult && artist.avatarUrl.isNotBlank(),
                topTrackCoOccurrence = coOccurrence,
                audienceSize = artist.audienceSize,
                isOfficialMusicChannel = officialChannel,
                verified = artist.verified,
            ),
        )
        artist.copy(isOfficialMusicChannel = officialChannel, isPopularArtist = popular)
    }

    val artistsById = updatedArtists.associateBy { it.id }
    val visibleArtists = updatedArtists.filter { artist ->
        artist.isPopularArtist || isFullNameMatch(query, artist.name)
    }
    val orderedTop = result.orderedTopResults.ifEmpty {
        listOfNotNull(result.topResult)
    }
    // Nama lengkap/typo ringan mengizinkan artis menang atas lagu yang kebetulan
    // berada di slot atas backend atau fallback.
    val exactArtistTop = orderedTop.firstOrNull { candidate ->
        candidate is SpotifyCatalogTopResult.Artist &&
            isFullNameMatch(query, (artistsById[candidate.item.id] ?: candidate.item).name)
    }
    val chosenTop = exactArtistTop
        ?: orderedTop.firstOrNull { isTopResultAllowed(it, query, artistsById) }
        ?: result.tracks.firstOrNull()?.let(SpotifyCatalogTopResult::Track)
        ?: visibleArtists.firstOrNull()?.let(SpotifyCatalogTopResult::Artist)
        ?: result.playlists.firstOrNull()?.let(SpotifyCatalogTopResult::Playlist)

    return result.copy(topResult = chosenTop, artists = visibleArtists)
}
