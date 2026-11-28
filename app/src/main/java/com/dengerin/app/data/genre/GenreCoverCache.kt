package com.dengerin.app.data.genre

import android.content.Context

/**
 * Cache URL sampul genre: `genreId -> (thumbnailUrl, fetchedAtMs)` disimpan di
 * SharedPreferences selama [TTL_MS]. Kalau masih segar, UI langsung memakai URL
 * tanpa membuka request jaringan (0 MB).
 */
class GenreCoverCache(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getFresh(genreId: String, nowMs: Long = System.currentTimeMillis()): String? {
        val key = keyFor(genreId) ?: return null
        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split(SEPARATOR, limit = 2)
        if (parts.size != 2) {
            prefs.edit().remove(key).apply()
            return null
        }
        val fetchedAt = parts[0].toLongOrNull()
        val url = parts[1].takeIf { it.isNotBlank() }
        if (fetchedAt == null || url == null || nowMs - fetchedAt >= TTL_MS) {
            prefs.edit().remove(key).apply()
            return null
        }
        return url
    }

    fun put(genreId: String, url: String, nowMs: Long = System.currentTimeMillis()) {
        val key = keyFor(genreId) ?: return
        if (url.isBlank()) return
        runCatching {
            prefs.edit()
                .putString(key, "$nowMs$SEPARATOR$url")
                .apply()
        }
    }

    /** Semua URL yang masih segar, dipakai saat halaman Cari dibuka. */
    fun freshCovers(nowMs: Long = System.currentTimeMillis()): Map<String, String> =
        buildMap {
            prefs.all.forEach { (key, value) ->
                if (!key.startsWith(KEY_PREFIX) || value !is String) return@forEach
                val parts = value.split(SEPARATOR, limit = 2)
                val fetchedAt = parts.firstOrNull()?.toLongOrNull() ?: return@forEach
                val url = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@forEach
                if (nowMs - fetchedAt < TTL_MS) put(key.removePrefix(KEY_PREFIX), url)
            }
        }

    fun clear() {
        runCatching { prefs.edit().clear().apply() }
    }

    private fun keyFor(genreId: String): String? =
        genreId.trim().takeIf { it.isNotBlank() }?.let { KEY_PREFIX + it }

    companion object {
        /** Tujuh hari, sesuai kontrak cache sampul genre. */
        const val TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val PREFS_NAME = "dengerin_genre_covers"
        private const val KEY_PREFIX = "cover_"
        private const val SEPARATOR = "\u001F"
    }
}
