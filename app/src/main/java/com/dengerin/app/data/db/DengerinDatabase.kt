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
    ],
    version = 2,
    exportSchema = false,
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

        fun build(context: Context): DengerinDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DengerinDatabase::class.java,
                "dengerin.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
