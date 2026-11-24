package com.dengerin.app.data.model

/**
 * Lagu di seluruh aplikasi.
 *
 * `url` kosong berupa lagu dari Spotify yang belum dicocokkan ke YouTube;
 * [com.dengerin.app.data.resolver.SongResolver] yang akan mengisinya saat diputar.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val url: String,
    val thumbnailUrl: String = "",
    val spotifyId: String = "",
    val source: String = "YOUTUBE",
)
