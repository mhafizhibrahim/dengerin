package com.dengerin.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "dengerin_sync")

private val UPLOADED_UIDS_KEY = stringPreferencesKey("uploaded_uids")

/**
 * Menyimpan daftar akun yang sudah pernah menerima upload koleksi tamu.
 * Dipakai supaya upload guest hanya terjadi sekali per akun, bukan tiap restart.
 */
class SyncPreferences(context: Context) {
    private val dataStore = context.applicationContext.syncDataStore

    val uploadedUids: Flow<Set<String>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[UPLOADED_UIDS_KEY]
                .orEmpty()
                .split(UID_SEPARATOR)
                .filter { it.isNotBlank() }
                .toSet()
        }

    suspend fun hasUploaded(uid: String): Boolean = uploadedUids.first().contains(uid)

    suspend fun markUploaded(uid: String) {
        dataStore.edit { prefs ->
            val current = prefs[UPLOADED_UIDS_KEY]
                .orEmpty()
                .split(UID_SEPARATOR)
                .filter { it.isNotBlank() }
                .toSet()
            prefs[UPLOADED_UIDS_KEY] = (current + uid).joinToString(UID_SEPARATOR)
        }
    }

    private companion object {
        const val UID_SEPARATOR = "\n"
    }
}
