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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
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
 * Versi sedikit lebih berwarna untuk Detail Playlist: tetap diredupkan dan ditutup
 * dengan `#151B22` → `#0C1014` sesuai kontrak warna dinamis Dengerin.
 */
@Composable
fun rememberPlaylistCoverGradient(imageUrl: String): State<Brush> {
    val colors = LocalDengerinColors.current
    val dominantRgb by rememberDominantRgb(imageUrl)

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
private fun rememberDominantRgb(imageUrl: String): State<Int?> {
    val context = LocalContext.current
    val dominant = remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(imageUrl) {
        dominant.value = null
        val hdUrl = upgradeThumbnailUrl(imageUrl)
        if (hdUrl.isNullOrBlank()) return@LaunchedEffect
        val rgb = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(hdUrl)
                    .allowHardware(false)
                    .build()
                val drawable = ImageLoader.Builder(context).build().execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
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
