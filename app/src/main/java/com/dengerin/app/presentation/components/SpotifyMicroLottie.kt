package com.dengerin.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dengerin.app.R
import com.dengerin.app.core.theme.LocalDengerinColors

/** Equalizer 3 batang `#1ED760` dari `lottie-compose`, dipakai pada lagu aktif. */
@Composable
fun SpotifyEqualizer(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.spotify_equalizer),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = playing,
        iterations = LottieConstants.IterateForever,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
    )
}

/**
 * Status suka instan: `+` saat belum disukai, `CheckCircle` `#1ED760` setelahnya.
 * Memakai vektor Material agar warnanya mengikuti token dan tidak bergantung aset Lottie.
 */
@Composable
fun SpotifyLikeIcon(
    liked: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    Icon(
        imageVector = if (liked) Icons.Filled.CheckCircle else Icons.Filled.Add,
        contentDescription = if (liked) "Hapus dari Lagu Disukai" else "Tambahkan ke Lagu Disukai",
        tint = if (liked) colors.accent else colors.textPrimary,
        modifier = modifier,
    )
}
