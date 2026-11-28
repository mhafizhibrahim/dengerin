package com.dengerin.app.presentation.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import com.dengerin.app.core.listImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/** Fallback elegan untuk kartu/layar lirik saat sampul gagal dimuat. */
val LyricsFallbackColor = Color(0xFF243447)

/**
 * Gradien dominan sampul album — satu-satunya warna dinamis di Dengerin.
 * Fallback lembut ke surface token bila sampul gagal dimuat.
 */
@Composable
fun rememberCoverGradient(imageUrl: String): State<Brush> {
    val colors = LocalDengerinColors.current
    val dominantRgb by rememberDominantRgb(imageUrl)

    return remember(dominantRgb, colors) {
        derivedStateOf {
            val top = dominantRgb
                ?.let { blend(colors.surface, Color(it), 0.45f) }
                ?: colors.surface
            Brush.verticalGradient(listOf(top, colors.surface, colors.background))
        }
    }
}

/**
 * Mini player memakai warna dominan sampul yang diredupkan: merah jadi marun, biru jadi
 * biru tua, sampul abu jadi abu gelap. Ini pengecualian terbatas untuk mini player.
 */
@Composable
fun rememberMiniPlayerTint(imageUrl: String): State<Color> {
    val colors = LocalDengerinColors.current
    val dominantRgb by rememberDominantRgb(imageUrl)
    return remember(dominantRgb, colors) {
        derivedStateOf {
            // Gelap pekat & tidak tembus pandang: 65% hitam, 35% warna dominan sampul.
            dominantRgb
                ?.let { lerp(Color(it), colors.miniPlayerDark, 0.65f) }
                ?: colors.miniPlayerDark
        }
    }
}

/**
 * Versi sedikit lebih berwarna untuk Detail Playlist: tetap diredupkan dan ditutup
 * dengan `#151B22` → `#0C1014` sesuai kontrak warna dinamis Dengerin.
 */
@Composable
fun rememberPlaylistCoverGradient(playlistId: String, imageUrl: String): State<Brush> {
    val colors = LocalDengerinColors.current
    // Kunci eksplisit: perubahan urutan/isi lagu tidak boleh mengulang Palette.
    val dominantRgb by rememberDominantRgb(
        imageUrl = imageUrl,
        cacheKey = "playlist|$playlistId|$imageUrl",
    )

    return remember(dominantRgb, colors) {
        derivedStateOf {
            val top = dominantRgb
                ?.let { blend(colors.surfaceElevated, Color(it), 0.58f) }
                ?: colors.surface
            Brush.verticalGradient(listOf(top, colors.surface, colors.background))
        }
    }
}

/**
 * Warna dominan sampul yang sudah di-tuning untuk lirik: saturasi tetap hidup, tapi
 * luminance ditekan sampai teks putih punya kontras minimal 4.5:1.
 */
@Composable
fun rememberAlbumColor(imageUrl: String): State<Color> {
    val dominantRgb by rememberDominantRgb(imageUrl)
    return remember(dominantRgb) {
        derivedStateOf {
            dominantRgb?.let { tuneForWhiteText(Color(it)) } ?: LyricsFallbackColor
        }
    }
}

@Composable
private fun rememberDominantRgb(
    imageUrl: String,
    cacheKey: String = imageUrl,
): State<Int?> {
    val context = LocalContext.current
    val dominant = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(cacheKey, imageUrl) {
        dominant.value = null
        if (imageUrl.isBlank()) return@LaunchedEffect
        val rgb = withContext(Dispatchers.IO) {
            runCatching {
                // Palette hanya butuh warna dominan: pakai thumbnail ringan dan
                // ImageLoader global supaya tidak mengunduh ulang gambar yang sudah di-cache.
                // allowHardware(false) wajib: bitmap hardware tidak bisa dibaca Palette.
                val request = listImageRequest(context, imageUrl).newBuilder()
                    .allowHardware(false)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?.takeIf { cover -> !cover.isRecycled }
                bitmap?.let { cover ->
                    Palette.from(cover).generate().let { palette ->
                        (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb
                    }
                }
            }.getOrNull()
        }
        dominant.value = rgb
    }

    return dominant
}

private fun blend(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = 1f,
)

private fun tuneForWhiteText(source: Color): Color {
    val hsv = floatArrayOf(0f, 0f, 0f)
    android.graphics.Color.colorToHSV(source.toArgb(), hsv)
    hsv[1] = hsv[1].coerceIn(0.42f, 0.82f)
    hsv[2] = hsv[2].coerceIn(0.28f, 0.46f)

    var tuned = Color(android.graphics.Color.HSVToColor(hsv))
    while (contrastWithWhite(tuned) < 4.5f && hsv[2] > 0.16f) {
        hsv[2] = (hsv[2] - 0.02f).coerceAtLeast(0.12f)
        tuned = Color(android.graphics.Color.HSVToColor(hsv))
    }
    return tuned
}

private fun contrastWithWhite(color: Color): Float =
    1.05f / (relativeLuminance(color) + 0.05f)

private fun relativeLuminance(color: Color): Float {
    fun linear(channel: Float): Float {
        val value = channel.coerceIn(0f, 1f)
        return if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * linear(color.red) +
        0.7152f * linear(color.green) +
        0.0722f * linear(color.blue)
}
