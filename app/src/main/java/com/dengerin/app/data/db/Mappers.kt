package com.dengerin.app.data.db

import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.data.model.Song

fun PlayHistoryEntity.toSong() = Song(
    id = videoUrl,
    title = title,
    artist = artist,
    durationMs = durationSeconds * 1000L,
    url = videoUrl,
    thumbnailUrl = upgradeThumbnailUrl(thumbnailUrl).orEmpty(),
    spotifyId = spotifyId,
)

fun LikedSongEntity.toSong() = Song(
    id = id,
    title = title,
    artist = artist,
    durationMs = durationMs,
    url = videoUrl,
    thumbnailUrl = upgradeThumbnailUrl(thumbnailUrl).orEmpty(),
    spotifyId = spotifyId,
    source = if (syncedFromSpotify) "SPOTIFY" else "YOUTUBE",
)

fun PlaylistTrackEntity.toSong() = Song(
    id = if (videoUrl.isNotBlank()) videoUrl else spotifyId.ifBlank { "track_$id" },
    title = title,
    artist = artist,
    durationMs = durationMs,
    url = videoUrl,
    thumbnailUrl = upgradeThumbnailUrl(thumbnailUrl).orEmpty(),
    spotifyId = spotifyId,
)
