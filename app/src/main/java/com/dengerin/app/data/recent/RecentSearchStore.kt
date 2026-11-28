package com.dengerin.app.data.recent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dengerin_search",
)

/**
 * Riwayat kata kunci pencarian di DataStore Preferences, maksimal 10 item.
 * Data lama dari implementasi SharedPreferences dipindahkan sekali saat ViewModel
 * dibuat supaya riwayat pengguna tidak hilang.
 */
class RecentSearchStore(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.recentSearchDataStore
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    val recentQueries: Flow<List<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> parse(prefs[KEY_QUERIES]) }

    suspend fun migrateLegacy() {
        runCatching {
            val raw = legacyPrefs.getString(LEGACY_KEY, null) ?: return
            val legacy = parse(raw)
            if (legacy.isNotEmpty()) {
                dataStore.edit { prefs ->
                    val merged = (legacy + parse(prefs[KEY_QUERIES]))
                        .distinctBy { it.lowercase() }
                        .take(MAX_ENTRIES)
                    write(prefs, merged)
                }
            }
            legacyPrefs.edit().remove(LEGACY_KEY).apply()
        }
    }

    suspend fun add(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank()) return
        runCatching {
            dataStore.edit { prefs ->
                val updated = (listOf(keyword) + parse(prefs[KEY_QUERIES]))
                    .distinctBy { it.lowercase() }
                    .take(MAX_ENTRIES)
                write(prefs, updated)
            }
        }
    }

    suspend fun remove(query: String) {
        val keyword = query.trim()
        if (keyword.isBlank()) return
        runCatching {
            dataStore.edit { prefs ->
                val updated = parse(prefs[KEY_QUERIES]).filterNot { it.equals(keyword, ignoreCase = true) }
                write(prefs, updated)
            }
        }
    }

    suspend fun clear() {
        runCatching { dataStore.edit { prefs -> prefs.remove(KEY_QUERIES) } }
    }

    private fun write(prefs: MutablePreferences, queries: List<String>) {
        if (queries.isEmpty()) prefs.remove(KEY_QUERIES)
        else prefs[KEY_QUERIES] = queries.joinToString(SEPARATOR)
    }

    private fun parse(raw: String?): List<String> = raw.orEmpty()
        .split(SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(MAX_ENTRIES)

    private companion object {
        const val MAX_ENTRIES = 10
        const val SEPARATOR = "\u001F"
        const val LEGACY_PREFS = "dengerin_search"
        const val LEGACY_KEY = "recent_queries"
        val KEY_QUERIES = stringPreferencesKey("recent_queries")
    }
}
