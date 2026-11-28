package com.dengerin.app.presentation.components

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.presentation.profile.avatarInitial

/**
 * Avatar bulat di header tab utama. Foto Google / galeri dipakai bila ada, selain itu
 * inisial nama dengan aksen hijau. Ketuk membuka side drawer ala Spotify.
 */
@Composable
fun ProfileAvatarButton(
    profile: UserProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val colors = LocalDengerinColors.current
    val photoBytes = remember(profile?.photoBase64) {
        profile?.photoBase64?.takeIf { it.isNotBlank() }?.let {
            runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
        }
    }
    val photoModel = photoBytes ?: profile?.photoUrl?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Buka menu profil" },
        contentAlignment = Alignment.Center,
    ) {
        if (photoModel != null) {
            AsyncImage(
                model = photoModel,
                contentDescription = "Foto profil",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            val initial = avatarInitial(
                profile?.displayName?.takeIf { it.isNotBlank() } ?: "Dengerin",
            ).take(2)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.accentSoftContainer)
                    .border(1.dp, colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    color = colors.accent,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
