package com.dengerin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DengerinDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun observeHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playCount DESC, playedAt DESC LIMIT 30")
    fun observeFrequent(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM liked_songs ORDER BY addedAt DESC")
    fun observeLiked(): Flow<List<LikedSongEntity>>

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: String): Flow<List<PlaylistTrackEntity>>

    /** Semua isi playlist (termasuk hasil impor) dipakai HomeScreen untuk rak artis. */
    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observeAllPlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("UPDATE play_history SET playedAt = :playedAt, playCount = playCount + 1, title = :title, artist = :artist, thumbnailUrl = :thumbnailUrl, durationSeconds = :durationSeconds, spotifyId = :spotifyId WHERE videoUrl = :videoUrl")
    suspend fun bumpHistory(
        videoUrl: String,
        spotifyId: String,
        title: String,
        artist: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        playedAt: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: PlayHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLiked(song: LikedSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLikedAll(songs: List<LikedSongEntity>)

    @Query("DELETE FROM liked_songs WHERE id = :id")
    suspend fun removeLiked(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :id)")
    suspend fun isLiked(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: LocalPlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: String): LocalPlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun playlistTrackCount(playlistId: String): Int

    /**
     * Satu lagu dianggap sama kalau spotifyId atau videoUrl cocok. NULLIF mencegah
     * dua lagu lokal tanpa spotifyId tersamakan hanya karena sama-sama kosong.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlist_tracks
            WHERE playlistId = :playlistId AND (
                (NULLIF(:spotifyId, '') IS NOT NULL AND spotifyId = :spotifyId) OR
                (NULLIF(:videoUrl, '') IS NOT NULL AND videoUrl = :videoUrl)
            )
        )
        """,
    )
    suspend fun playlistContainsSong(playlistId: String, spotifyId: String, videoUrl: String): Boolean

    @Query(
        """
        DELETE FROM playlist_tracks
        WHERE playlistId = :playlistId AND (
            (NULLIF(:spotifyId, '') IS NOT NULL AND spotifyId = :spotifyId) OR
            (NULLIF(:videoUrl, '') IS NOT NULL AND videoUrl = :videoUrl)
        )
        """,
    )
    suspend fun removePlaylistSong(playlistId: String, spotifyId: String, videoUrl: String)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAt: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun tracksSnapshot(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT * FROM spotify_match_cache WHERE spotifyTrackId = :spotifyTrackId")
    suspend fun match(spotifyTrackId: String): SpotifyMatchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMatch(match: SpotifyMatchCacheEntity)

    /**
     * Resolver YouTube mengganti sampul sementara Spotify di tiga tempat sekaligus.
     * NULLIF mencegah spotifyId kosong tersamakan dengan lagu lokal.
     */
    @Query("UPDATE play_history SET thumbnailUrl = :thumbnailUrl WHERE videoUrl = :videoUrl OR spotifyId = NULLIF(:spotifyId, '')")
    suspend fun updateHistoryThumbnail(
        videoUrl: String,
        spotifyId: String,
        thumbnailUrl: String,
    ): Int

    /**
     * Saat impor, URL YouTube juga disimpan supaya lagu bisa langsung diputar
     * tanpa menunggu resolver dipanggil ulang saat diketuk.
     */
    @Query("UPDATE playlist_tracks SET videoUrl = :videoUrl, thumbnailUrl = :thumbnailUrl WHERE videoUrl = :videoUrl OR spotifyId = NULLIF(:spotifyId, '')")
    suspend fun updatePlaylistTrackResolved(
        videoUrl: String,
        spotifyId: String,
        thumbnailUrl: String,
    ): Int

    @Query("UPDATE liked_songs SET thumbnailUrl = :thumbnailUrl WHERE videoUrl = :videoUrl OR spotifyId = NULLIF(:spotifyId, '') OR id = NULLIF(:songId, '')")
    suspend fun updateLikedSongThumbnail(
        songId: String,
        videoUrl: String,
        spotifyId: String,
        thumbnailUrl: String,
    ): Int

    @Query("SELECT * FROM lyrics_cache WHERE cacheKey = :cacheKey")
    suspend fun lyrics(cacheKey: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(lyrics: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE fetchedAt < :before")
    suspend fun clearStaleLyrics(before: Long)
}
