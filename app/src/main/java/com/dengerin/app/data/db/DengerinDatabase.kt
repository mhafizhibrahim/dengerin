package com.dengerin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlayHistoryEntity::class,
        LikedSongEntity::class,
        LocalPlaylistEntity::class,
        PlaylistTrackEntity::class,
        SpotifyMatchCacheEntity::class,
        LyricsCacheEntity::class,
        VideoLyricsCacheEntity::class,
        ListeningEventEntity::class,
        RecentPlayEntity::class,
        SyncOperationEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class DengerinDatabase : RoomDatabase() {

    abstract fun dao(): DengerinDao

    companion object {
        @Volatile
        private var instance: DengerinDatabase? = null

        /** V1 → V2: tabel cache lirik LRCLIB. Data lama (riwayat/liked/playlist) tetap utuh. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                        `cacheKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `syncedLyrics` TEXT,
                        `plainLyrics` TEXT,
                        `instrumental` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`cacheKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** V2 → V3: tabel sesi dengar & daftar Baru Diputar untuk statistik asli. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `coverUrl` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `playedAtMillis` INTEGER NOT NULL,
                        `listenedDurationMs` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_listening_events_playedAtMillis` ON `listening_events` (`playedAtMillis`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_plays` (
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `coverUrl` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `playedAtMillis` INTEGER NOT NULL,
                        `listenedDurationMs` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** V3 → V4: cache lirik final per videoId (LRCLIB + YouTube Music). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `video_lyrics_cache` (
                        `videoId` TEXT NOT NULL,
                        `syncedLyrics` TEXT,
                        `plainLyrics` TEXT,
                        `sourceLabel` TEXT,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`videoId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * V4 → V5: metadata playlist unlimited, `entryId` unik per entri lagu, dan outbox
         * sinkronisasi. Baris lama diberi entryId sintetis supaya index UNIQUE tetap valid.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `trackCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `totalDurationMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `entryId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `coverUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `addedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `liked_songs` ADD COLUMN `coverUrl` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `playlist_tracks` SET `entryId` = 'legacy_' || `id` WHERE `entryId` = ''")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_entryId` " +
                        "ON `playlist_tracks` (`playlistId`, `entryId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sync_operations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `playlistId` TEXT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `coverUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `videoUrl` TEXT NOT NULL,
                        `spotifyId` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `isPinned` INTEGER NOT NULL,
                        `trackCount` INTEGER NOT NULL,
                        `totalDurationMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_operations_uid` ON `sync_operations` (`uid`)")
                db.execSQL(
                    """
                    UPDATE `playlists` SET
                        `trackCount` = (SELECT COUNT(*) FROM `playlist_tracks` WHERE `playlist_tracks`.`playlistId` = `playlists`.`id`),
                        `totalDurationMs` = (SELECT IFNULL(SUM(`durationMs`), 0) FROM `playlist_tracks` WHERE `playlist_tracks`.`playlistId` = `playlists`.`id`)
                    """.trimIndent(),
                )
            }
        }

        @Suppress("DEPRECATION")
        fun build(context: Context): DengerinDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DengerinDatabase::class.java,
                "dengerin.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
