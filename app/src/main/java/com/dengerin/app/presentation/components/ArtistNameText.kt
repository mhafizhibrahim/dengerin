package com.dengerin.app.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengerin.app.core.theme.LocalDengerinColors

/**
 * Nama artis yang bisa diklik dari Now Playing maupun header lirik penuh.
 * Artis gabungan (`A & B`, `A, B`, `A ft. B`) memunculkan chooser kecil supaya
 * profil yang dibuka sesuai dengan nama yang dipilih.
 */
@Composable
fun ArtistNameText(
    artist: String,
    artistId: String,
    onOpenArtist: ((String?, String) -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight? = null,
    artistIds: List<String> = emptyList(),
) {
    val colors = LocalDengerinColors.current
    val namesWithIds = remember(artist, artistId, artistIds) {
        namesWithArtistIds(artist, artistId, artistIds)
    }
    var showChooser by remember { mutableStateOf(false) }
    val textColor = color ?: colors.textSecondary

    val clickable = onOpenArtist?.let { callback ->
        Modifier.clip(RoundedCornerShape(4.dp)).spotifyClickable {
            if (namesWithIds.size <= 1) {
                val entry = namesWithIds.firstOrNull()
                callback(entry?.second ?: artistId.ifBlank { null }, entry?.first ?: artist)
            } else {
                showChooser = true
            }
        }
    } ?: Modifier

    Text(
        text = artist,
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(clickable).padding(vertical = 2.dp),
    )

    if (showChooser && onOpenArtist != null) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            containerColor = colors.surface,
            title = { Text("Pilih artis", color = colors.textPrimary) },
            text = {
                androidx.compose.foundation.layout.Column {
                    namesWithIds.forEach { (name, id) ->
                        Text(
                            text = name,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .spotifyClickable {
                                    showChooser = false
                                    onOpenArtist(id, name)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }
}

private fun splitArtistNames(raw: String): List<String> =
    raw.split(
        Regex(
            "\\s*,\\s*|\\s*&\\s*|\\s*;\\s*|\\s*·\\s*|\\s+(?:feat\\.?|ft\\.?|featuring|x)\\s+",
            RegexOption.IGNORE_CASE,
        ),
    ).map { it.trim() }.filter { it.isNotBlank() }
        .ifEmpty { listOf(raw.trim()).filter { it.isNotBlank() } }

/** Pasangkan setiap nama kredit dengan ID artis yang sesuai, sejauh tersedia. */
fun namesWithArtistIds(
    artist: String,
    artistId: String,
    artistIds: List<String>,
): List<Pair<String, String?>> {
    val names = splitArtistNames(artist)
    return names.mapIndexed { index, name ->
        val id = when {
            names.size == 1 -> artistId.takeIf { it.isNotBlank() }
            name.equals(artist, ignoreCase = true) -> artistId.takeIf { it.isNotBlank() }
            else -> artistIds.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?: artistId.takeIf { it.isNotBlank() && index == 0 }
        }
        name to id
    }
}
