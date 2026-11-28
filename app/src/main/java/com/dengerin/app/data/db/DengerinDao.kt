package com.dengerin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Hanya kolom yang dipakai untuk mencocokkan lagu Spotify yang sudah tersimpan. */
data class TrackKeySnapshot(
    val spotifyId: String,
    val title: String,
    val artist: String,
)

data class LikedKeySnapshot(
    val id: String,
    val spotifyId: String,
    val title: String,
    val artist: String,
)

@Dao
interface DengerinDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC")
    fun observeHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playCount DESC, playedAt DESC LIMIT 30")
    fun observeFrequent(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM liked_songs ORDER BY addedAt DESC")
    fun observeLiked(): Flow<List<LikedSongEntity>>

    /** Pin selalu di atas, lalu aktivitas terakhir. Dipakai LibraryScreen. */
    @Query("SELECT * FROM playlists ORDER BY isPinned DESC, updatedAt DESC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeTracks(playlistId: String): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId AND entryId = :entryId")
    suspend fun trackEntry(playlistId: String, entryId: String): PlaylistTrackEntity?

    /** Snapshot entri (bukan Song) dipakai menu per lagu agar duplikat tetap unik. */
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun trackEntries(playlistId: String): List<PlaylistTrackEntity>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistsAll(playlists: List<LocalPlaylistEntity>)

    /** Satu transaksi untuk kerangka playlist yang langsung muncul di Koleksi Kamu. */
    @Transaction
    suspend fun upsertPlaylists(playlists: List<LocalPlaylistEntity>) {
        if (playlists.isNotEmpty()) upsertPlaylistsAll(playlists)
    }

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: String): LocalPlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    /** Hapus playlist dan isinya dalam satu transaksi (dipakai jalur snapshot cloud). */
    @Transaction
    suspend fun deletePlaylistCascade(id: String) {
        clearTracks(id)
        deletePlaylist(id)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity)

    /** Satu transaksi Room: snapshot Firestore mengganti seluruh isi playlist tanpa setengah jadi. */
    @Transaction
    suspend fun replaceTracks(playlistId: String, tracks: List<PlaylistTrackEntity>) {
        clearTracks(playlistId)
        if (tracks.isNotEmpty()) insertTracks(tracks)
    }

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

    @Query(
        """
        UPDATE playlists
        SET name = :name, description = :description, updatedAt = :updatedAt
        WHERE id = :playlistId
        """,
    )
    suspend fun renamePlaylist(playlistId: String, name: String, description: String, updatedAt: Long)

    @Query("UPDATE playlists SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun setPlaylistPinned(playlistId: String, isPinned: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE playlists
        SET trackCount = :trackCount, totalDurationMs = :totalDurationMs, updatedAt = :updatedAt
        WHERE id = :playlistId
        """,
    )
    suspend fun updatePlaylistAggregates(
        playlistId: String,
        trackCount: Int,
        totalDurationMs: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND entryId = :entryId")
    suspend fun deleteTrackEntry(playlistId: String, entryId: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun tracksSnapshot(playlistId: String): List<PlaylistTrackEntity>

    @Query("SELECT spotifyId, title, artist FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun playlistTrackKeys(playlistId: String): List<TrackKeySnapshot>

    @Query("SELECT id, spotifyId, title, artist FROM liked_songs")
    suspend fun likedTrackKeys(): List<LikedKeySnapshot>

    // ---- Outbox sinkronisasi Firestore -------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSync(operation: SyncOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueSyncAll(operations: List<SyncOperationEntity>)

    @Query("SELECT * FROM sync_operations ORDER BY id ASC LIMIT :limit")
    suspend fun syncOperations(limit: Int): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE kind = :kind ORDER BY id ASC LIMIT :limit")
    suspend fun syncOperationsByKind(kind: String, limit: Int): List<SyncOperationEntity>

    @Query("SELECT * FROM sync_operations WHERE playlistId = :playlistId ORDER BY id ASC")
    suspend fun syncOperationsForPlaylist(playlistId: String): List<SyncOperationEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_operations WHERE kind = :kind AND playlistId = :playlistId)")
    suspend fun hasPendingOperation(kind: String, playlistId: String): Boolean

    @Query("DELETE FROM sync_operations WHERE id IN (:ids)")
    suspend fun removeSyncOperations(ids: List<Long>)

    @Query("DELETE FROM sync_operations")
    suspend fun clearSyncOperations()

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

    @Query("SELECT * FROM video_lyrics_cache WHERE videoId = :videoId")
    suspend fun videoLyrics(videoId: String): VideoLyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoLyrics(lyrics: VideoLyricsCacheEntity)

    @Query("SELECT * FROM listening_events ORDER BY playedAtMillis DESC, id DESC")
    fun observeListeningEvents(): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM recent_plays ORDER BY playedAtMillis DESC")
    fun observeRecentPlays(): Flow<List<RecentPlayEntity>>

    @Query("SELECT * FROM recent_plays ORDER BY playedAtMillis DESC LIMIT 1")
    suspend fun latestRecentPlay(): RecentPlayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningEvent(event: ListeningEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentPlay(play: RecentPlayEntity)

    @Query("UPDATE listening_events SET listenedDurationMs = listenedDurationMs + :delta WHERE id = :id")
    suspend fun addEventListening(id: Long, delta: Long)

    @Query("DELETE FROM listening_events WHERE playedAtMillis < :before")
    suspend fun pruneListeningEvents(before: Long)

    @Query("SELECT trackId FROM recent_plays ORDER BY playedAtMillis DESC LIMIT -1 OFFSET :keep")
    suspend fun overflowRecentIds(keep: Int): List<String>

    @Query("DELETE FROM recent_plays WHERE trackId IN (:ids)")
    suspend fun deleteRecentPlays(ids: List<String>)

    @Query("DELETE FROM listening_events")
    suspend fun clearListeningEvents()

    @Query("DELETE FROM recent_plays")
    suspend fun clearRecentPlays()
}
