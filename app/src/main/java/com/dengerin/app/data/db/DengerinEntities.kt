package com.dengerin.app.data.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Riwayat lagu yang berhasil diputar. `playCount` dipakai rak "Sering Kamu Putar". */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val videoUrl: String,
    val spotifyId: String = "",
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0L,
    val playedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
)

/** Lagu disukai, baik dari tombol Hati lokal maupun sinkronisasi Spotify. */
@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val id: String,
    val videoUrl: String = "",
    val spotifyId: String = "",
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    /** Sampul HD untuk dokumen `liked_tracks` di Firestore. */
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val syncedFromSpotify: Boolean = false,
    /** Juga dipakai sebagai `updatedAt` cloud agar mirror dua arah tidak saling menimpa. */
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Playlist lokal, hasil sinkronisasi Spotify, atau hasil impor link publik.
 * `source`: LOCAL / SPOTIFY_SYNC / SPOTIFY_IMPORT.
 */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["source"])],
)
@Immutable
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val source: String = "LOCAL",
    /** Playlist pin tampil di atas koleksi. Ikut tersinkron ke metadata Firestore. */
    val isPinned: Boolean = false,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    /** Versi konflik untuk sinkronisasi dua arah Room ↔ Firestore. */
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Isi playlist. Lagu Spotify belum tentu punya `videoUrl` sebelum dicocokkan. */
@Entity(
    tableName = "playlist_tracks",
    indices = [
        Index(value = ["playlistId"]),
        // entryId unik per playlist: lagu yang sama boleh ditambahkan berkali-kali.
        Index(value = ["playlistId", "entryId"], unique = true),
    ],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** ID dokumen `tracks/{entryId}` di Firestore. Stabil saat lagu duplikat dihapus. */
    val entryId: String = UUID.randomUUID().toString(),
    val playlistId: String,
    val position: Int,
    val videoUrl: String = "",
    val spotifyId: String = "",
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Peta spotifyTrackId → youtubeVideoUrl supaya tidak perlu cari ulang. */
@Entity(tableName = "spotify_match_cache")
data class SpotifyMatchCacheEntity(
    @PrimaryKey val spotifyTrackId: String,
    val youtubeVideoUrl: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val matchedAt: Long = System.currentTimeMillis(),
)

/**
 * Cache hasil LRCLIB. `cacheKey` = artist|judul|durasi (detik) ternormalisasi,
 * supaya lagu yang sama tidak perlu memanggil API Tiap kali dibuka.
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val cacheKey: String,
    val title: String,
    val artist: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val instrumental: Boolean,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/**
 * Cache lirik final per `videoId` (LRCLIB atau YouTube Music). Dipakai lebih dulu saat
 * lagu diputar ulang supaya tidak ada request jaringan sama sekali (0 MB & instan).
 * Tabel lama `lyrics_cache` tetap dipertahankan sebagai fallback lagu tanpa videoId.
 */
@Entity(tableName = "video_lyrics_cache")
data class VideoLyricsCacheEntity(
    @PrimaryKey val videoId: String,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val sourceLabel: String? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/**
 * Satu baris per sesi dengar yang sudah memenuhi ambang 10 detik (atau selesai diputar).
 * Dipakai untuk statistik mingguan/keseluruhan, streak, dan Top 5.
 */
@Entity(
    tableName = "listening_events",
    indices = [Index(value = ["playedAtMillis"])],
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val coverUrl: String,
    val durationMs: Long,
    val playedAtMillis: Long,
    val listenedDurationMs: Long,
)

/**
 * Daftar "Baru Diputar": satu baris per lagu, maksimal 100, diurutkan dari yang terakhir
 * diputar. Lagu yang sama diputar berurutan cukup memperbarui [playedAtMillis].
 */
@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val coverUrl: String,
    val durationMs: Long,
    val playedAtMillis: Long,
    val listenedDurationMs: Long,
)

/**
 * Outbox sinkronisasi. Room tetap sumber kebenaran: setiap mutasi koleksi ditulis ke
 * tabel ini lebih dulu. Saat pengguna login, antrean ini dikirim ke Firestore; gagal
 * jaringan tidak pernah menggulirkan UI karena operasi tetap tersimpan di disk.
 */
@Entity(
    tableName = "sync_operations",
    indices = [Index(value = ["uid"])],
)
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String = "",
    val kind: String,
    val playlistId: String = "",
    val entryId: String = "",
    val trackId: String = "",
    val name: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val spotifyId: String = "",
    val durationMs: Long = 0L,
    val addedAt: Long = 0L,
    val orderIndex: Int = 0,
    val isPinned: Boolean = false,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
)
