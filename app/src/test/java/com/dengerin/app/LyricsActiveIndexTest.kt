package com.dengerin.app

import com.dengerin.app.data.lyrics.LrcParser
import com.dengerin.app.data.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsActiveIndexTest {

    private val lines = listOf(
        LyricLine(1_000L, "baris satu"),
        LyricLine(2_500L, "baris dua"),
        LyricLine(4_000L, "baris tiga"),
    )

    @Test
    fun `daftar kosong tidak punya baris aktif`() {
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 3_000L))
    }

    @Test
    fun `sebelum baris pertama return minus satu`() {
        assertEquals(-1, LrcParser.activeIndex(lines, 999L))
    }

    @Test
    fun `tepat di timestamp memakai baris tersebut`() {
        assertEquals(1, LrcParser.activeIndex(lines, 2_500L))
    }

    @Test
    fun `di antara timestamp memakai baris terakhir yang tercapai`() {
        assertEquals(1, LrcParser.activeIndex(lines, 3_999L))
    }

    @Test
    fun `setelah baris terakhir tetap di baris terakhir`() {
        assertEquals(2, LrcParser.activeIndex(lines, 60_000L))
    }
}
