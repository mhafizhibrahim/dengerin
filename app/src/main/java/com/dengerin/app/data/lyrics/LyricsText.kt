package com.dengerin.app.data.lyrics

/**
 * Judul lagu dari YouTube sering membawa annotation (`Official Video`, `[Lyrics]`,
 * `(Audio)`, `HD`) yang membuat pencarian LRCLIB meleset. Fungsi ini membuang hanya
 * annotation yang jelas bukan bagian dari judul, dan mempertahankan feature/remix
 * yang merupakan bagian identitas lagu.
 */
fun cleanTrackTitleForLyrics(rawTitle: String): String {
    var title = rawTitle.trim()

    title = SQUARE_TAG.replace(title) { match ->
        if (NOISE_WORD.containsMatchIn(match.value)) "" else match.value
    }
    title = PAREN_TAG.replace(title) { match ->
        if (NOISE_WORD.containsMatchIn(match.value)) "" else match.value
    }
    title = TRAILING_NOISE.replace(title, "")
    title = TRAILING_QUALITY.replace(title, "")
    title = TRAILING_SEPARATOR.replace(title, "")

    val cleaned = WHITESPACE.replace(title, " ").trim()
    return cleaned.ifBlank { rawTitle.trim() }
}

private val NOISE_WORD = Regex(
    """(?i)\b(official|music|video|lyrics?|audio|hd|hq|4k|8k|live|visualizer|karaoke|full\s*song|remaster(?:ed)?)\b""",
)
private val SQUARE_TAG = Regex("""\s*\[[^\[\]]*\]""")
private val PAREN_TAG = Regex("""\s*\([^()]*\)""")
private val TRAILING_NOISE = Regex(
    """(?i)\s*(?:[-–—|:]\s*)?(?:official\s*)?(?:music\s*)?(?:video|audio|lyrics?|visualizer|karaoke|full\s*song)\s*$""",
)
private val TRAILING_QUALITY = Regex(
    """(?i)\s*(?:[-–—|:]\s*)?(?:hd|hq|4k|8k|remaster(?:ed)?(?:\s*\d{4})?)\s*$""",
)
private val TRAILING_SEPARATOR = Regex("""\s*[-–—|:]+\s*$""")
private val WHITESPACE = Regex("""\s+""")
