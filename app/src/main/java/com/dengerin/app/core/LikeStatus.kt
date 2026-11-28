package com.dengerin.app.core

import androidx.compose.runtime.compositionLocalOf
import com.dengerin.app.data.model.Song

/** ID lagu yang sudah disukai, dibaca sekali di root supaya tiap baris tetap ringan. */
val LocalLikedTrackIds = compositionLocalOf<Set<String>> { emptySet() }

/** Cocokkan baik id lokal maupun spotifyId dengan kunci dokumen `liked_tracks`. */
fun Song.isLikedIn(ids: Set<String>): Boolean =
    id in ids || (spotifyId.isNotBlank() && spotifyId in ids)

/** Kunci yang dipakai [com.dengerin.app.data.repository.LibraryRepository.toggleLike]. */
fun Song.likeKey(): String = spotifyId.ifBlank { id }
