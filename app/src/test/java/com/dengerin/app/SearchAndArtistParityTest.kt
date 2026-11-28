package com.dengerin.app

import com.dengerin.app.data.spotify.SpotifyCatalogArtist
import com.dengerin.app.data.spotify.SpotifyCatalogSearchResult
import com.dengerin.app.data.spotify.SpotifyCatalogTopResult
import com.dengerin.app.data.spotify.SpotifyCatalogTrack
import com.dengerin.app.data.spotify.TrackOwnershipCandidate
import com.dengerin.app.data.spotify.applySearchGuardrails
import com.dengerin.app.data.spotify.isFullNameMatch
import com.dengerin.app.data.spotify.isTrackOwnedByArtist
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paritas pencarian & profil artis: skenario Spotify yang paling rawan salah. */
class SearchAndArtistParityTest {

    private fun spotifyArtistId(seed: String): String = seed.lowercase().padEnd(22, '0').take(22)

    private fun artist(
        id: String,
        name: String,
        avatarUrl: String = "",
        featured: Boolean = false,
        verified: Boolean = false,
        audienceSize: Long = 0L,
        officialChannel: Boolean = false,
        subscriberCount: Long = 0L,
    ) = SpotifyCatalogArtist(
        id = id,
        uri = if (id.startsWith("spotify:")) id else "spotify:artist:$id",
        name = name,
        avatarUrl = avatarUrl,
        verified = verified,
        audienceSize = audienceSize,
        subscriberCount = subscriberCount,
        isFeaturedTopResult = featured,
        isOfficialMusicChannel = officialChannel,
    )

    private fun track(
        id: String,
        title: String,
        artist: String,
        artistIds: List<String>,
        uri: String = "spotify:track:$id",
    ) = SpotifyCatalogTrack(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        artistId = artistIds.firstOrNull().orEmpty(),
        artistIds = artistIds,
    )

    @Test
    fun `query mengapa dengan artis kecil terverifikasi top result tetap lagu`() {
        val smallArtist = artist(
            id = spotifyArtistId("mengapademikian"),
            name = "Mengapa Demikian",
            avatarUrl = "https://i.scdn.co/avatar.jpg",
            verified = true,
        )
        val popularTrack = track(
            id = "track-mengapa",
            title = "Mengapa",
            artist = "Rony Parulian",
            artistIds = listOf(spotifyArtistId("ronyparulian")),
        )
        val raw = SpotifyCatalogSearchResult(
            topResult = SpotifyCatalogTopResult.Artist(smallArtist),
            orderedTopResults = listOf(
                SpotifyCatalogTopResult.Artist(smallArtist),
                SpotifyCatalogTopResult.Track(popularTrack),
            ),
            artists = listOf(smallArtist),
            tracks = listOf(popularTrack),
        )

        val result = applySearchGuardrails(raw, "mengapa")

        assertTrue(result.topResult is SpotifyCatalogTopResult.Track)
        assertFalse(result.artists.any { it.name == "Mengapa Demikian" })
    }

    @Test
    fun `nama lengkap dan typo ringan artis kecil tetap muncul dan layak jadi top result`() {
        val smallArtist = artist(
            id = spotifyArtistId("mengapademikian"),
            name = "Mengapa Demikian",
            avatarUrl = "https://i.scdn.co/avatar.jpg",
            verified = true,
        )
        listOf("mengapa demikian", "mengapa demikain").forEach { query ->
            assertTrue(isFullNameMatch(query, smallArtist.name))

            val unrelatedTrack = track(
                id = "track-full-name-$query",
                title = "Lagu Populer",
                artist = "Artis Lain",
                artistIds = listOf(spotifyArtistId("artislain")),
            )
            val raw = SpotifyCatalogSearchResult(
                topResult = SpotifyCatalogTopResult.Track(unrelatedTrack),
                orderedTopResults = listOf(
                    SpotifyCatalogTopResult.Track(unrelatedTrack),
                    SpotifyCatalogTopResult.Artist(smallArtist),
                ),
                artists = listOf(smallArtist),
                tracks = listOf(unrelatedTrack),
            )

            val result = applySearchGuardrails(raw, query)

            assertTrue(result.artists.any { it.name == "Mengapa Demikian" })
            assertTrue(result.topResult is SpotifyCatalogTopResult.Artist)
        }
    }

    @Test
    fun `sheila on 7 dari slot feature tetap populer dan jadi top result`() {
        val sheila = artist(
            id = spotifyArtistId("sheilaon7"),
            name = "Sheila On 7",
            avatarUrl = "https://i.scdn.co/sheila.jpg",
            featured = true,
        )
        val tracks = (1..3).map { index ->
            track("track-sheila-$index", "Lagu Sheila $index", "Sheila On 7", listOf(sheila.id))
        }
        val raw = SpotifyCatalogSearchResult(
            topResult = SpotifyCatalogTopResult.Artist(sheila),
            orderedTopResults = listOf(SpotifyCatalogTopResult.Artist(sheila)),
            artists = listOf(sheila),
            tracks = tracks,
        )

        val result = applySearchGuardrails(raw, "sheila")

        assertTrue(result.artists.first { it.name == "Sheila On 7" }.isPopularArtist)
        assertTrue(result.topResult is SpotifyCatalogTopResult.Artist)
    }

    @Test
    fun `fallback newpipe channel kecil disembunyikan dan top result tetap lagu`() {
        val channel = artist(
            id = "UCmengapaKecil",
            name = "Mengapa Demikian",
            avatarUrl = "https://yt3.ggpht.com/avatar.jpg",
            subscriberCount = 240,
        )
        val song = track(
            id = "video-mengapa",
            title = "Mengapa",
            artist = "Mengapa Demikian",
            artistIds = listOf(channel.id),
            uri = "https://www.youtube.com/watch?v=video-mengapa",
        )
        val raw = SpotifyCatalogSearchResult(
            topResult = SpotifyCatalogTopResult.Artist(channel),
            orderedTopResults = listOf(
                SpotifyCatalogTopResult.Artist(channel),
                SpotifyCatalogTopResult.Track(song),
            ),
            artists = listOf(channel),
            tracks = listOf(song),
        )

        val result = applySearchGuardrails(raw, "mengapa")

        assertTrue(result.topResult is SpotifyCatalogTopResult.Track)
        assertFalse(result.artists.any { it.name == "Mengapa Demikian" })
    }

    @Test
    fun `validator kepemilikan lagu menolak tabrakan judul dan menerima kredit asli`() {
        val targetId = spotifyArtistId("mengapademikian")
        val otherId = spotifyArtistId("bandlain")

        assertFalse(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(title = "Mengapa", artist = "Artis Lain"),
                targetId,
                "Mengapa Demikian",
            ),
        )
        assertFalse(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(
                    title = "Mengapa Demikian",
                    artist = "Band Lain",
                    artistIds = listOf(otherId),
                ),
                targetId,
                "Mengapa Demikian",
            ),
        )
        assertTrue(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(
                    title = "Lagu Apa Saja",
                    artist = "Mengapa Demikian",
                    artistIds = listOf(targetId),
                ),
                targetId,
                "Mengapa Demikian",
            ),
        )
        assertTrue(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(
                    title = "Kolaborasi",
                    artist = "Penyanyi A feat. Mengapa Demikian",
                    artistIds = listOf(spotifyArtistId("penyanyia"), targetId),
                ),
                targetId,
                "Mengapa Demikian",
            ),
        )
        assertTrue(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(
                    title = "Mengapa",
                    artist = "Mengapa Demikian - Topic",
                    artistIds = listOf("UCmengapaTopic"),
                    isNewPipe = true,
                ),
                targetId,
                "Mengapa Demikian",
            ),
        )
        assertFalse(
            isTrackOwnedByArtist(
                TrackOwnershipCandidate(
                    title = "Mengapa",
                    artist = "Mengapa Demikian",
                    artistIds = listOf(otherId),
                ),
                targetId,
                "Mengapa Demikian",
            ),
        )
    }
}
