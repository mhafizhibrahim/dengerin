package com.dengerin.app

import com.dengerin.app.data.lyrics.YouTubeLyricsExtractor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeCaptionParserTest {

    private val extractor = YouTubeLyricsExtractor(OkHttpClient())

    @Test
    fun `mem-parse WebVTT dan membuang baris timing ganda`() {
        val vtt = """
            WEBVTT
            Kind: captions
            Language: id

            00:00:01.000 --> 00:00:03.000 align:start position:0%
            00:00:01.000 --> 00:00:03.000 align:start position:0%
            <c>Kau</c> adalah <i>hatiku</i>

            00:00:04.500 --> 00:00:06.000
            [Musik]

            00:01:02.250 --> 00:01:04.000
            Sampai jumpa &amp; tetap &quot;bersinar&quot;
        """.trimIndent()

        val lines = extractor.parse(vtt)

        assertEquals(3, lines.size)
        assertEquals(1_000L to "Kau adalah hatiku", lines[0].timestampMs to lines[0].text)
        assertEquals(4_500L to "♪", lines[1].timestampMs to lines[1].text)
        assertEquals(62_250L to "Sampai jumpa & tetap \"bersinar\"", lines[2].timestampMs to lines[2].text)
    }

    @Test
    fun `mem-parse TTML dengan waktu begin dan span`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:02.100" end="00:00:04.000">Hujan <span>turus</span></p>
                <p begin="5.5s" end="7s">[Music]</p>
                <p begin="00:00:08.000">Langit &lt;indah&gt; &#39;malam&#39;</p>
              </div></body>
            </tt>
        """.trimIndent()

        val lines = extractor.parse(ttml)

        assertEquals(3, lines.size)
        assertEquals(2_100L, lines[0].timestampMs)
        assertEquals("Hujan turus", lines[0].text)
        assertEquals(5_500L, lines[1].timestampMs)
        assertEquals("♪", lines[1].text)
        assertEquals("Langit <indah> 'malam'", lines[2].text)
    }

    @Test
    fun `mem-parse XML SRV dan JSON3 lalu menggabungkan fragmen`() {
        val xml = """
            <transcript>
              <text start="1.20" dur="1.5">Kita</text>
              <text start="1.30" dur="1.2">berdua</text>
              <text start="4.000" dur="2"> ♪ </text>
              <text start="4.050" dur="2">[Musik]</text>
            </transcript>
        """.trimIndent()
        val json3 = """
            {"events":[
              {"tStartMs":900,"dDurationMs":1200,"segs":[{"utf8":"Di "},{"utf8":"sini"}]},
              {"tStartMs":3000,"dDurationMs":1000,"segs":[{"utf8":"sendiri"}]}
            ]}
        """.trimIndent()

        val xmlLines = extractor.parse(xml)
        val jsonLines = extractor.parse(json3)

        assertEquals(2, xmlLines.size)
        assertEquals(1_200L, xmlLines[0].timestampMs)
        assertEquals("Kita berdua", xmlLines[0].text)
        assertEquals("♪", xmlLines[1].text)
        assertEquals(2, jsonLines.size)
        assertEquals(900L, jsonLines[0].timestampMs)
        assertEquals("Di sini", jsonLines[0].text)
        assertEquals(3_000L, jsonLines[1].timestampMs)
    }

    @Test
    fun `mengurutkan baris dan melewati instrumen duplikat berurutan`() {
        val vtt = """
            WEBVTT

            00:00:03.000 --> 00:00:04.000
            Baris ketiga

            00:00:01.000 --> 00:00:02.000
            Baris pertama

            00:00:02.000 --> 00:00:02.900
            [Music]

            00:00:02.050 --> 00:00:02.800
            [Musik]
        """.trimIndent()

        val lines = extractor.parse(vtt)

        assertEquals(3, lines.size)
        assertEquals(1_000L, lines[0].timestampMs)
        assertEquals(2_000L, lines[1].timestampMs)
        assertEquals("♪", lines[1].text)
        assertEquals(3_000L, lines[2].timestampMs)
    }
}
