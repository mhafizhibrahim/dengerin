package com.dengerin.app

import com.dengerin.app.data.lyrics.cleanTrackTitleForLyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsTextTest {

    @Test
    fun `membersihkan annotation video dari judul`() {
        assertEquals(
            "Hati Yang Lelah",
            cleanTrackTitleForLyrics("Hati Yang Lelah (Official Music Video)"),
        )
        assertEquals(
            "Senja",
            cleanTrackTitleForLyrics("Senja [Lyrics] HD"),
        )
        assertEquals(
            "Rumah",
            cleanTrackTitleForLyrics("Rumah - Official Audio"),
        )
    }

    @Test
    fun `mempertahankan feature dan judul tanpa annotation`() {
        assertEquals(
            "Peluk (feat. Ayu)",
            cleanTrackTitleForLyrics("Peluk (feat. Ayu) [Official Video]"),
        )
        assertEquals(
            "Hujan Mile",
            cleanTrackTitleForLyrics("Hujan Mile"),
        )
    }
}
