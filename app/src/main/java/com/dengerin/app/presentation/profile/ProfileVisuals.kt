package com.dengerin.app.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import coil.compose.AsyncImage
import com.dengerin.app.core.theme.CosmeticGradient
import com.dengerin.app.core.theme.LocalDengerinColors

private val RibbonShape = GenericShape { size, _ ->
    val cut = size.height * 0.42f
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

private val HexShape = GenericShape { size, _ ->
    moveTo(size.width * 0.25f, 0f)
    lineTo(size.width * 0.75f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width * 0.75f, size.height)
    lineTo(size.width * 0.25f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

/** Inisial estetik untuk avatar default pengguna yang sudah login. */
fun avatarInitial(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifBlank { "D" }
}

/** Decode Base64 sekali per nilai, di-cache agar mulus saat scroll. */
private val avatarBitmapCache = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private fun decodeAvatarBase64(value: String?): Bitmap? {
    if (value.isNullOrBlank()) return null
    avatarBitmapCache.get(value)?.let { return it }
    // String Base64 di DataStore bisa korup; decode di dalam composition tidak
    // boleh menjatuhkan seluruh layar Profil.
    val bytes = runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull() ?: return null
    val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        ?: return null
    avatarBitmapCache.put(value, bitmap)
    return bitmap
}

/**
 * Avatar + bingkai berlapis: ring dalam tipis, ring utama gradien yang berputar halus,
 * glow konsentris, serta partikel cahaya untuk tier tinggi. Foto tidak ikut beranimasi
 * dan selalu lebih kecil dari ring supaya tidak tertutup.
 */
@Composable
fun FramedAvatar(
    photoUrl: String?,
    frame: AvatarFrame,
    size: Dp = 76.dp,
    photoBase64: String? = null,
    defaultInitial: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val rotation by rememberInfiniteTransition(label = "avatar-frame").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "frame-rotation",
    )
    val cachedPhoto = remember(photoBase64) { decodeAvatarBase64(photoBase64) }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val diameter = size.toPx()
            val center = Offset(diameter / 2f, diameter / 2f)
            val compact = diameter < 60.dp.toPx()
            val photoInset = if (compact) 3.dp.toPx() else 5.dp.toPx()
            val photoRadius = diameter / 2f - photoInset
            val mainStroke = if (compact) 2.dp.toPx() else 3.dp.toPx()
            val mainRadius = photoRadius + mainStroke / 2f + (if (compact) 0.5f else 1f).dp.toPx()
            val palette = frame.gradient?.let { colors.cosmetics.gradient(it) }

            if (palette == null) {
                drawCircle(color = colors.border, radius = mainRadius, style = Stroke(width = mainStroke))
                drawCircle(
                    color = colors.surfaceElevated,
                    radius = photoRadius + 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                )
            } else {
                if (frame.glow) {
                    drawCircle(
                        brush = Brush.sweepGradient(palette, center),
                        radius = mainRadius + 1.dp.toPx(),
                        alpha = 0.16f,
                        style = Stroke(width = (if (compact) 2 else 5).dp.toPx()),
                    )
                    drawCircle(
                        brush = Brush.sweepGradient(palette, center),
                        radius = mainRadius - 1.dp.toPx(),
                        alpha = 0.10f,
                        style = Stroke(width = (if (compact) 2 else 4).dp.toPx()),
                    )
                }

                rotate(rotation, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(palette, center),
                        radius = mainRadius,
                        style = Stroke(width = mainStroke),
                    )
                    // Kilau kecil yang mengikuti putaran ring (bukan blur/glow berlebih).
                    drawArc(
                        color = palette.last().copy(alpha = 0.8f),
                        startAngle = -70f,
                        sweepAngle = 36f,
                        useCenter = false,
                        topLeft = Offset(center.x - mainRadius, center.y - mainRadius),
                        size = Size(mainRadius * 2f, mainRadius * 2f),
                        style = Stroke(width = mainStroke, cap = StrokeCap.Round),
                    )
                    if (frame.sparkles && !compact) {
                        val sparkleRadius = diameter / 2f - 2.dp.toPx()
                        for (index in 0 until 6) {
                            val angle = Math.toRadians((index * 60.0) - rotation * 0.3)
                            drawCircle(
                                color = palette[index % palette.size].copy(alpha = 0.72f),
                                radius = (if (index % 2 == 0) 1.7f else 1.1f).dp.toPx(),
                                center = Offset(
                                    center.x + cos(angle).toFloat() * sparkleRadius,
                                    center.y + sin(angle).toFloat() * sparkleRadius,
                                ),
                            )
                        }
                    }
                }

                // Ring dalam memisahkan foto dari gradien tanpa menutup wajah.
                drawCircle(
                    color = colors.surfaceElevated.copy(alpha = 0.9f),
                    radius = photoRadius + 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        val imageSize = size - if (size.value >= 60f) 10.dp else 6.dp
        when {
            cachedPhoto != null -> Image(
                bitmap = cachedPhoto.asImageBitmap(),
                contentDescription = "Foto profil dari galeri",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated),
            )

            !photoUrl.isNullOrBlank() -> AsyncImage(
                model = photoUrl,
                contentDescription = "Foto profil Google",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated),
            )

            defaultInitial != null -> Box(
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.surfaceElevated, colors.cosmetics.emerald.copy(alpha = 0.38f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = defaultInitial,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (imageSize.value >= 60f) 22.sp else 14.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }

            else -> Box(
                modifier = Modifier
                    .size(imageSize)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Foto profil default",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(imageSize * 0.52f),
                )
            }
        }
    }
}

/** Pita dengan ujung miring/terpotong, memakai token gradien kosmetik. */
@Composable
fun CosmeticRibbon(
    text: String,
    gradient: CosmeticGradient,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalDengerinColors.current
    val palette = colors.cosmetics.gradient(gradient)
    Box(
        modifier = modifier
            .clip(RibbonShape)
            .background(Brush.horizontalGradient(palette))
            .border(1.dp, colors.border, RibbonShape)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.textPrimary,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Lencana hexagon berisi level, dipakai di samping ribbons. */
@Composable
fun HexLevelBadge(level: Int, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    Box(
        modifier = modifier
            .size(width = 58.dp, height = 48.dp)
            .clip(HexShape)
            .background(colors.surfaceElevated)
            .border(1.dp, colors.accent, HexShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Lv. $level",
            color = colors.accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Pill level ringkas: latar background + border emerald, angka 3 digit pun utuh. */
@Composable
fun SmallLevelBadge(level: Int, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.accent, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Lv. $level",
            color = colors.accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Lencana eksklusif developer: ikon `</>` + DEV di atas pill neon. */
@Composable
fun DevBadge(compact: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    val palette = listOf(
        colors.cosmetics.emerald,
        colors.cosmetics.cyberCyan,
        colors.cosmetics.emerald,
    )
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.horizontalGradient(palette))
            .border(1.dp, colors.accent, shape)
            .padding(
                horizontal = if (compact) 4.dp else 8.dp,
                vertical = if (compact) 1.dp else 3.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                tint = colors.background,
                modifier = Modifier.size(if (compact) 9.dp else 13.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "DEV",
                color = colors.background,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
