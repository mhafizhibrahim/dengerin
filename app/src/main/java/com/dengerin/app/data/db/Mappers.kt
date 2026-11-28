package com.dengerin.app.data.db

import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.data.model.Song

/** Room menyimpan versi ringan; cover HD diturunkan dari URL saat diputar. */
fun PlayHistoryEntity.toSong() = Song(
    id = videoUrl,
    title = title,
    artist = artist,
    durationMs = durationSeconds * 1000L,
    url = videoUrl,
    thumbnailUrl = listThumbnailUrl(thumbnailUrl).orEmpty(),
    spotifyId = spotifyId,
)

fun LikedSongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    url = videoUrl,
    thumbnailUrl = listThumbnailUrl(thumbnailUrl).orEmpty(),
    coverUrl = coverUrl,
    spotifyId = spotifyId,
    source = if (syncedFromSpotify) "SPOTIFY" else "YOUTUBE",
)

fun PlaylistTrackEntity.toSong() = Song(
    id = if (videoUrl.isNotBlank()) videoUrl else spotifyId.ifBlank { entryId.ifBlank { "track_$id" } },
    title = title,
    artist = artist,
    durationMs = durationMs,
    url = videoUrl,
    thumbnailUrl = listThumbnailUrl(thumbnailUrl).orEmpty(),
    coverUrl = coverUrl,
    spotifyId = spotifyId,
)
