package com.dengerin.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val durationMs: Long = 0L,
    val syncedFromSpotify: Boolean = false,
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
data class LocalPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val source: String = "LOCAL",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Isi playlist. Lagu Spotify belum tentu punya `videoUrl` sebelum dicocokkan. */
@Entity(
    tableName = "playlist_tracks",
    indices = [Index(value = ["playlistId"])],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String,
    val position: Int,
    val videoUrl: String = "",
    val spotifyId: String = "",
    val title: String,
    val artist: String,
    val thumbnailUrl: String = "",
    val durationMs: Long = 0L,
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
