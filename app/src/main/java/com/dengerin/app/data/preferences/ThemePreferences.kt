package com.dengerin.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dengerin.app.core.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "dengerin_settings")

private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

class ThemePreferences(context: Context) {
    private val dataStore = context.applicationContext.themeDataStore

    val themeMode: Flow<AppThemeMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            runCatching { AppThemeMode.valueOf(prefs[THEME_MODE_KEY] ?: AppThemeMode.SYSTEM.name) }
                .getOrDefault(AppThemeMode.SYSTEM)
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }
}
