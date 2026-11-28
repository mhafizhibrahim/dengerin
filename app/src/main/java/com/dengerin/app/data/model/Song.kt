package com.dengerin.app.data.model

import androidx.compose.runtime.Immutable
import com.dengerin.app.core.hdCoverUrl
import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.core.youtubeVideoId

/**
 * Lagu di seluruh aplikasi.
 *
 * `url` kosong berupa lagu dari Spotify yang belum dicocokkan ke YouTube;
 * [com.dengerin.app.data.resolver.SongResolver] yang akan mengisinya saat diputar.
 */
@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val url: String,
    /** Versi medium (~300px / mqdefault 320x180) untuk daftar; decode Coil 256x256. */
    val thumbnailUrl: String = "",
    /** Versi HD (640x640 / hqdefault 480x360) untuk Now Playing & Lirik. */
    val coverUrl: String = "",
    val spotifyId: String = "",
    val source: String = "YOUTUBE",
    /** ID artis utama untuk navigasi profil; kosong jika tidak diketahui. */
    val artistId: String = "",
    /** Semua ID artis pada kredit lagu. Bisa berisi ID Spotify maupun channel NewPipe. */
    val artistIds: List<String> = emptyList(),
) {
    /**
     * Fallback untuk entity/kode lama yang belum menyimpan [coverUrl]: turunkan
     * HD dari ID video YouTube, lalu dari thumbnail yang ada. List tetap memakai
     * [thumbnailUrl], hanya Now Playing yang memanggil ini.
     */
    val displayCoverUrl: String get() = coverUrl.ifBlank {
        youtubeVideoId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            ?: hdCoverUrl(thumbnailUrl).orEmpty()
    }.ifBlank { thumbnailUrl }

    /** Fallback untuk kode lama yang belum punya [thumbnailUrl]: turunkan cover ke ukuran list. */
    val displayThumbnailUrl: String get() = thumbnailUrl.ifBlank {
        listThumbnailUrl(coverUrl).orEmpty()
    }
}

/**
 * Kunci stabil untuk item daftar. Menggabungkan identitas lokal, Spotify, dan URL
 * sehingga lagu duplikat tidak saling menimpa saat daftar di-filter/diurut ulang.
 */
fun songKey(song: Song): String = buildString {
    append(song.id).append('|').append(song.spotifyId).append('|').append(song.url)
}
