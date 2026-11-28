package com.dengerin.app.core

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.dengerin.app.DengerinApplication
import com.dengerin.app.core.network.NetworkState
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.PlaybackSettings
import com.dengerin.app.data.preferences.isDataSaverActive

private const val LIST_SIZE = 256
/** Thumbnail baris lagu: cukup untuk 40–56 dp, aman saat fast scroll ratusan item. */
private const val TRACK_ROW_SIZE = 144
private const val CARD_SIZE = 400
private const val LIGHT_COVER_SIZE = 256
private const val GENRE_COVER_SIZE = 256

/**
 * Request dasar: memory + disk cache selalu aktif, crossfade halus. Gambar yang
 * sudah pernah dimuat tidak diunduh ulang (0 MB pada pemutaran ulang).
 */
private fun baseRequest(
    context: Context,
    url: String?,
    cacheKey: String? = null,
): ImageRequest.Builder = ImageRequest.Builder(context)
    .data(url.takeIf { !it.isNullOrBlank() })
    .memoryCachePolicy(CachePolicy.ENABLED)
    .diskCachePolicy(CachePolicy.ENABLED)
    .crossfade(true)
    .apply {
        // Kunci eksplisit berbasis id lagu: URL thumbnail yang berubah-ubah tidak
        // lagi menggandakan entri cache untuk sampul yang sama.
        if (!cacheKey.isNullOrBlank()) {
            memoryCacheKey(cacheKey)
            diskCacheKey(cacheKey)
        }
    }

/** Kunci cache stabil untuk sampul lagu pada ukuran tertentu. */
fun trackImageCacheKey(trackId: String, variant: String, size: Int): String =
    "track|$trackId|$variant|$size"

/** Daftar lagu (LazyColumn), Mini Player, dan baris playlist: decode 256x256. */
fun listImageRequest(
    context: Context,
    url: String?,
    size: Int = LIST_SIZE,
    cacheKey: String? = null,
): ImageRequest = baseRequest(context, url, cacheKey)
    .size(size, size)
    .build()

/**
 * Versi lagu untuk baris daftar (playlist, pencarian, antrean, mini player).
 * Decode dibatasi 144x144 dan crossfade dimatikan: saat fast fling, Coil tidak
 * boleh mengantri transisi/filter gambar HD untuk puluhan row sekaligus.
 * Kunci cache memakai [Song.id], bukan URL yang bisa berganti.
 */
fun songListImageRequest(
    context: Context,
    song: Song,
    url: String = song.displayThumbnailUrl,
): ImageRequest = baseRequest(
    context = context,
    url = url,
    cacheKey = trackImageCacheKey(song.id, "list", TRACK_ROW_SIZE),
).size(TRACK_ROW_SIZE, TRACK_ROW_SIZE)
    .crossfade(false)
    .build()

/** Kartu Beranda, grid genre, dan header playlist: decode 400x400 untuk layar xxhdpi/xxxhdpi. */
fun cardImageRequest(
    context: Context,
    url: String?,
    size: Int = CARD_SIZE,
    cacheKey: String? = null,
): ImageRequest = baseRequest(context, url, cacheKey)
    .size(size, size)
    .build()

/** Versi lagu untuk kartu/header dengan kunci cache berbasis id + ukuran. */
fun songCardImageRequest(
    context: Context,
    song: Song,
    size: Int = CARD_SIZE,
    url: String = song.displayCoverUrl,
): ImageRequest = cardImageRequest(
    context = context,
    url = url,
    size = size,
    cacheKey = trackImageCacheKey(song.id, "card", size),
)

/**
 * Sampul miring kartu genre: decode 256x256 dari URL mqdefault yang sudah
 * di-cache 7 hari. Memory + disk cache aktif, jadi kartu yang sama tidak
 * mengunduh ulang gambar.
 */
fun genreCoverImageRequest(context: Context, url: String?): ImageRequest =
    baseRequest(context, url, "genre|$url")
        .size(Size(GENRE_COVER_SIZE, GENRE_COVER_SIZE))
        .build()

data class NowPlayingCover(val url: String, val size: Size)

/**
 * Now Playing / Lirik memakai [Song.displayCoverUrl] dengan ukuran asli.
 * Hanya Data Saver + seluler yang turun ke [Song.displayThumbnailUrl].
 */
fun nowPlayingCover(
    settings: PlaybackSettings,
    network: NetworkState,
    song: Song,
): NowPlayingCover {
    val light = settings.isDataSaverActive(network) && network.isCellular
    return if (light) {
        NowPlayingCover(song.displayThumbnailUrl, Size(LIGHT_COVER_SIZE, LIGHT_COVER_SIZE))
    } else {
        NowPlayingCover(song.displayCoverUrl, Size.ORIGINAL)
    }
}

/** Versi tanpa parameter: dipakai pemanggil non-Compose (mis. MediaMetadata). */
fun nowPlayingCoverUrl(context: Context, song: Song): String =
    nowPlayingCover(
        settings = currentSettings(context),
        network = currentNetwork(context),
        song = song,
    ).url

/** Hanya layar Now Playing penuh yang boleh memuat HD. */
fun nowPlayingImageRequest(context: Context, song: Song): ImageRequest =
    nowPlayingImageRequest(
        context = context,
        song = song,
        settings = currentSettings(context),
        network = currentNetwork(context),
    )

/** Versi reaktif: Panggil dari Composable yang mengcollect settings & network. */
fun nowPlayingImageRequest(
    context: Context,
    song: Song,
    settings: PlaybackSettings,
    network: NetworkState,
): ImageRequest {
    val cover = nowPlayingCover(settings, network, song)
    val sizeLabel = if (cover.size == Size.ORIGINAL) {
        "original"
    } else {
        "${cover.size.width}x${cover.size.height}"
    }
    return baseRequest(context, cover.url, "track|${song.id}|now|$sizeLabel")
        .size(cover.size)
        .build()
}

private fun currentSettings(context: Context): PlaybackSettings =
    (context.applicationContext as? DengerinApplication)
        ?.container
        ?.playbackPreferences
        ?.settings
        ?.value
        ?: PlaybackSettings()

private fun currentNetwork(context: Context): NetworkState =
    (context.applicationContext as? DengerinApplication)
        ?.container
        ?.networkMonitor
        ?.current()
        ?: NetworkState(
            isCellular = false,
            isWifi = false,
            isMetered = true,
            downstreamKbps = 0,
            isSystemDataSaver = false,
        )
