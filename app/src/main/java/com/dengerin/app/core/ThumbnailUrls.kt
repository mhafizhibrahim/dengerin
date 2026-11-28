package com.dengerin.app.core

/**
 * Dua ukuran gambar untuk setiap lagu:
 * - [listThumbnailUrl]: mqdefault 320x180 (±12–15 KB) untuk list/LazyColumn/LazyRow.
 * - [hdCoverUrl]: hqdefault 480x360 / Spotify 640x640, hanya untuk Now Playing.
 *
 * YouTube `default.jpg` 120x90 selalu dinaikkan ke `mqdefault.jpg`. Spotify
 * `i.scdn.co` tidak punya suffix ukuran; ukuran dipilih dari `images[]` saat
 * import (medium untuk thumbnail, terbesar untuk cover).
 */

/** Naikkan ke versi HD. Kompatibel dengan pemanggil lama. */
fun upgradeThumbnailUrl(rawUrl: String?): String? = hdCoverUrl(rawUrl)

/**
 * Naikkan URL lama dari riwayat/DB ke resolusi yang dibutuhkan saat dirender.
 *
 * - YouTube (`i.ytimg.com` / `img.youtube.com`): `default.jpg` / `sddefault.jpg`
 *   / `mqdefault.jpg` otomatis jadi `hqdefault.jpg` untuk kartu (`targetPx >= 360`)
 *   atau `mqdefault.jpg` untuk daftar kecil/Mini Player.
 * - Google/lh3: `=w60-h60` → `=w${targetPx}-h${targetPx}`.
 * - Spotify `i.scdn.co`: tidak punya suffix ukuran; pemanggil harus mengirim
 *   `coverUrl` bila `thumbnailUrl` terlalu kecil.
 */
fun upgradeThumbnailUrl(url: String?, targetPx: Int): String? {
    val raw = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return when {
        isYouTubeImageHost(raw) -> {
            val videoId = youtubeImageVideoId(raw)
            if (videoId == null) {
                raw
            } else {
                val variant = if (targetPx >= 360) "hqdefault.jpg" else "mqdefault.jpg"
                "https://i.ytimg.com/vi/$videoId/$variant"
            }
        }

        isGoogleAvatar(raw) ->
            raw.replace(Regex("""=w\d+-h\d+"""), "=w$targetPx-h$targetPx")

        else -> raw
    }
}

/** Versi ringan untuk daftar. Aman dipanggil berulang pada URL yang sudah kecil. */
fun listThumbnailUrl(rawUrl: String?): String? {
    val url = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (isGoogleAvatar(url)) {
        return url.replace(Regex("""=[ws]\d+.*$"""), "=w96-h96-l90-rj")
    }
    val videoId = youtubeImageVideoId(url)
    if (videoId != null) return "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    return url
}

/** Versi HD (640x640 / hqdefault) untuk layar Now Playing penuh. */
fun hdCoverUrl(rawUrl: String?): String? {
    val url = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (isGoogleAvatar(url)) {
        return url.replace(Regex("""=[ws]\d+.*$"""), "=w640-h640-l90-rj")
    }
    val videoId = youtubeImageVideoId(url)
    if (videoId != null) return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    return url
}

/** ID video dari format watch, youtu.be, embed, shorts, live, atau /v/. */
private val VIDEO_ID_REGEX =
    Regex("""(?:[?&]v=|youtu\.be/|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})""")

/** ID video dari URL gambar i.ytimg.com, termasuk varian hq720 / vi_webp / 0.jpg. */
private val YOUTUBE_IMAGE_ID_REGEX =
    Regex("""/(?:vi|vi_webp|sb)/([A-Za-z0-9_-]{11})/""")

fun youtubeVideoId(rawUrl: String?): String? =
    rawUrl?.let { VIDEO_ID_REGEX.find(it)?.groupValues?.get(1) }

fun youtubeImageVideoId(rawUrl: String?): String? =
    rawUrl?.takeIf(::isYouTubeImageHost)
        ?.let { YOUTUBE_IMAGE_ID_REGEX.find(it)?.groupValues?.get(1) }

private fun isYouTubeImageHost(url: String): Boolean =
    url.contains("i.ytimg.com") || url.contains("img.youtube.com")

/** Thumbnail daftar YouTube yang bisa dipakai walau hasil pencarian belum punya gambar. */
fun youtubeListThumbnailUrl(videoUrl: String?): String? =
    youtubeVideoId(videoUrl)?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }

/** Cover HD YouTube yang bisa dipakai walau hasil pencarian belum punya gambar. */
fun youtubeThumbnailUrl(videoUrl: String?): String? =
    youtubeVideoId(videoUrl)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

private fun isGoogleAvatar(url: String): Boolean =
    url.contains("lh3.googleusercontent.com") ||
        url.contains("yt3.ggpht.com") ||
        url.contains("yt3.googleusercontent.com")

internal fun isYouTubeImage(rawUrl: String?): Boolean =
    rawUrl?.let { isYouTubeImageHost(it) || isGoogleAvatar(it) } ?: false
