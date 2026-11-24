package com.dengerin.app.data.recent

import android.content.Context

/** Riwayat kata kunci pencarian: cukup ringan memakai SharedPreferences. */
class RecentSearchStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun all(): List<String> = prefs.getString(KEY_QUERIES, "")
        .orEmpty()
        .split(SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(MAX_ENTRIES)

    fun add(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank()) return
        val updated = (listOf(keyword) + all())
            .distinctBy { it.lowercase() }
            .take(MAX_ENTRIES)
        prefs.edit().putString(KEY_QUERIES, updated.joinToString(SEPARATOR)).apply()
    }

    fun clear() = prefs.edit().remove(KEY_QUERIES).apply()

    private companion object {
        const val PREFS_NAME = "dengerin_search"
        const val KEY_QUERIES = "recent_queries"
        const val MAX_ENTRIES = 5
        const val SEPARATOR = "\u001F"
    }
}
