package com.dengerin.app.data.lyrics

/** Satu baris lirik dengan waktu absolut dalam milidetik. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * Parser LRC ringan untuk `syncedLyrics` dari LRCLIB.
 * Mendukung banyak timestamp di satu baris (`[00:12.00][00:48.00]teks`),
 * tag metadata (`[ar:...]`, `[ti:...]`) diabaikan, dan format `mm:ss.xx` / `mm:ss:xx`.
 */
object LrcParser {

    private val timestampTag = Regex("""^(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?$""")

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.first() != '[') return@forEach

            var cursor = 0
            while (cursor < line.length && line[cursor] == '[') {
                val close = line.indexOf(']', cursor)
                if (close < 0) break
                val tag = line.substring(cursor + 1, close)
                val match = timestampTag.matchEntire(tag)

                if (match == null) {
                    // Tag metadata seperti [ar:Artist] / [offset:500].
                    cursor = close + 1
                    continue
                }

                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when {
                    fraction.isEmpty() -> 0L
                    fraction.length >= 3 -> fraction.take(3).toLong()
                    else -> fraction.padEnd(3, '0').toLong()
                }
                val timestampMs = minutes * 60_000L + seconds * 1_000L + millis
                val text = line.substring(close + 1).trim()
                if (text.isNotEmpty()) lines += LyricLine(timestampMs, text)
                cursor = close + 1
            }
        }

        return lines
            .distinctBy { it.timestampMs }
            .sortedBy { it.timestampMs }
    }

    /** Indeks baris terakhir yang waktunya sudah tercapai. -1 bila belum ada. */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timestampMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }
}
