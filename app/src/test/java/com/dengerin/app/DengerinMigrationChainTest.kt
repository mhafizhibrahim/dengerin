package com.dengerin.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardrails Tahap 1 pra-rilis v1.3.0.
 *
 * Sengaja berjalan di JVM (testDebugUnitTest) tanpa room-testing karena
 * MigrationTestHelper membutuhkan instrumentation. Fokus: metadata versi,
 * rantai migrasi, dan jaminan bahwa tidak ada jalur destruktif.
 */
class DengerinMigrationChainTest {

    private val db = File("src/main/java/com/dengerin/app/data/db/DengerinDatabase.kt").readText()
    private val gradle = File("build.gradle.kts").readText()
    private val catalog = File("../gradle/libs.versions.toml").readText()

    @Test
    fun metadataRilisDanSchemaExportBenar() {
        assertTrue(gradle.contains("versionCode = 5"))
        assertTrue(gradle.contains("versionName = \"1.3.0\""))
        assertTrue(gradle.contains("arg(\"room.schemaLocation\", \"\$projectDir/schemas\")"))
        assertTrue(db.contains("version = 5"))
        assertTrue(db.contains("exportSchema = true"))
    }

    @Test
    fun fallbackDestruktifDihapusTotal() {
        assertFalse(db.contains("fallbackToDestructiveMigration"))
        assertFalse(db.contains("DROP TABLE"))
        assertFalse(db.contains("DELETE FROM"))

        val createTable = Regex("CREATE TABLE").findAll(db).count()
        val createTableAman = Regex("CREATE TABLE IF NOT EXISTS").findAll(db).count()
        assertTrue("Ada CREATE TABLE tanpa IF NOT EXISTS", createTable > 0 && createTable == createTableAman)

        val createIndex = Regex("CREATE (UNIQUE )?INDEX").findAll(db).count()
        val createIndexAman = Regex("CREATE (UNIQUE )?INDEX IF NOT EXISTS").findAll(db).count()
        assertTrue("Ada CREATE INDEX tanpa IF NOT EXISTS", createIndex > 0 && createIndex == createIndexAman)
    }

    @Test
    fun rantaiMigrasiLengkapDanTerdaftar() {
        listOf(
            "MIGRATION_1_2 = object : Migration(1, 2)",
            "MIGRATION_2_3 = object : Migration(2, 3)",
            "MIGRATION_3_4 = object : Migration(3, 4)",
            "MIGRATION_4_5 = object : Migration(4, 5)",
            ".addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)",
        ).forEach { assertTrue(it, db.contains(it)) }
    }

    @Test
    fun sqlMenjagaDataLama() {
        listOf(
            "lyrics_cache", "listening_events", "recent_plays",
            "video_lyrics_cache", "sync_operations",
            "isPinned", "trackCount", "totalDurationMs",
            "entryId", "coverUrl", "addedAt",
        ).forEach { assertTrue(it, db.contains("`$it`")) }

        assertTrue(db.contains("'legacy_' || `id`"))
        assertTrue(db.contains("index_playlist_tracks_playlistId_entryId"))
    }

    @Test
    fun schemaJsonVersion5Terbangkitkan() {
        val schema = File("schemas/com.dengerin.app.data.db.DengerinDatabase/5.json")
        assertTrue("Schema belum dibuat: ${schema.absolutePath}", schema.exists())
        val text = schema.readText()
        assertTrue(text.contains("\"formatVersion\""))
        assertTrue(text.contains("\"sync_operations\""))
        assertTrue(text.contains("\"video_lyrics_cache\""))
    }

    @Test
    fun toolchainDanR8TidakBerubah() {
        assertTrue(catalog.contains("agp = \"8.13.2\""))
        assertTrue(catalog.contains("kotlin = \"2.4.0\""))
        assertTrue(catalog.contains("ksp = \"2.3.14\""))
        assertFalse(catalog.contains("room-testing"))
        assertTrue(gradle.contains("-Xmetadata-version=2.2.0"))
        assertTrue(gradle.contains("isMinifyEnabled = false"))
        assertTrue(gradle.contains("signingConfig = signingConfigs.getByName(\"release\")"))
    }
}
