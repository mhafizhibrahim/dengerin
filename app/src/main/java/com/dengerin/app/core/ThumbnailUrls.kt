package com.dengerin.app.core

/**
 * Naikkan resolusi thumbnail YouTube/Google tanpa mengubah URL penyedia lain
 * (misalnya sampul `i.scdn.co` dari Spotify).
 */
fun upgradeThumbnailUrl(rawUrl: String?): String? {
    val url = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        url.contains("lh3.googleusercontent.com") ||
            url.contains("yt3.ggpht.com") ||
            url.contains("yt3.googleusercontent.com") ->
            url.replace(Regex("""=[ws]\d+.*$"""), "=w544-h544-l90-rj")

        url.contains("i.ytimg.com/vi/") ->
            url.replace(
                Regex("""(?:/(?:default|mqdefault))\.jpg(?:\?.*)?$"""),
                "/hqdefault.jpg",
            )

        else -> url
    }
}

/** ID video dari format watch, youtu.be, embed, shorts, live, atau /v/. */
private val VIDEO_ID_REGEX =
    Regex("""(?:[?&]v=|youtu\.be/|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})""")

fun youtubeVideoId(rawUrl: String?): String? =
    rawUrl?.let { VIDEO_ID_REGEX.find(it)?.groupValues?.get(1) }

/** URL thumbnail YouTube yang bisa dipakai walau hasil pencarian belum punya gambar. */
fun youtubeThumbnailUrl(videoUrl: String?): String? =
    youtubeVideoId(videoUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

internal fun isYouTubeImage(rawUrl: String?): Boolean =
    rawUrl?.let {
        it.contains("i.ytimg.com") ||
            it.contains("lh3.googleusercontent.com") ||
            it.contains("yt3.ggpht.com") ||
            it.contains("yt3.googleusercontent.com")
    } ?: false
