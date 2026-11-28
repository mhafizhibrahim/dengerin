package com.dengerin.app.domain.model

/** Satu lagu dari Spotify (Opsi 1 / Opsi 2) sebelum dicocokkan ke YouTube. */
data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String = "",
    /** Versi HD bila tersedia; backward-compatible dengan import lama. */
    val coverUrl: String = "",
    val uri: String = "",
)
