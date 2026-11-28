package com.dengerin.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import com.dengerin.app.core.network.NetworkState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dengerin_audio_settings",
)

enum class AudioQuality(val label: String, val quotaLabel: String) {
    AUTO("Otomatis", "Seluler: maks 2,5 MB/lagu • Wi-Fi: Tinggi"),
    LOW("Rendah", "~1 MB/lagu • Opus Hemat"),
    NORMAL("Normal", "~1,8–2,5 MB/lagu"),
    HIGH("Tinggi", "~3–3,5 MB/lagu"),
    VERY_HIGH("Sangat Tinggi", "Bitrate tertinggi"),
}

enum class CacheLimit(val label: String, val bytes: Long) {
    MB_250("250 MB", 250L * 1024 * 1024),
    MB_500("500 MB", 500L * 1024 * 1024),
    GB_1("1 GB", 1024L * 1024 * 1024),
}

data class PlaybackSettings(
    val dataSaver: Boolean = false,
    val wifiQuality: AudioQuality = AudioQuality.AUTO,
    val cellularQuality: AudioQuality = AudioQuality.AUTO,
    val autoAdjustQuality: Boolean = true,
    val cacheLimit: CacheLimit = CacheLimit.MB_500,
    /** Mode antrean dipertahankan lintas restart aplikasi. 0 = off, 1 = all, 2 = one. */
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    /** Radio otomatis saat antrean habis; aktif secara default. */
    val autoplay: Boolean = true,
    /** Prefetch lagu berikutnya; mati secara default supaya kuota 0 MB saat install. */
    val prefetchEnabled: Boolean = false,
)

/** Data Saver aplikasi dan sistem Android sama-sama membatasi kuota. */
fun PlaybackSettings.isDataSaverActive(network: NetworkState): Boolean =
    dataSaver || network.isSystemDataSaver

/**
 * Preferensi kuota & audio. DataStore tetap sumber kebenaran; StateFlow dipakai
 * supaya resolver/service bisa membaca konfigurasi tanpa suspend di jalur panas.
 */
class PlaybackPreferences(context: Context) {

    private val dataStore = context.applicationContext.playbackDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(PlaybackSettings())
    val settings: StateFlow<PlaybackSettings> = _settings.asStateFlow()

    private val _settingsLoaded = MutableStateFlow(false)
    /** false sampai DataStore selesai dibaca; pemanggil kritis harus menunggu ini. */
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    /** Preferensi radio otomatis. default = true, dibaca ulang dari DataStore. */
    val isAutoplayEnabled: Flow<Boolean> = settings
        .map { it.autoplay }
        .distinctUntilChanged()

    init {
        scope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { prefs ->
                    PlaybackSettings(
                        dataSaver = prefs[DATA_SAVER] ?: false,
                        wifiQuality = prefs[WIFI_QUALITY]?.let(::safeQuality)
                            ?: AudioQuality.AUTO,
                        cellularQuality = prefs[CELLULAR_QUALITY]?.let(::safeQuality)
                            ?: AudioQuality.AUTO,
                        autoAdjustQuality = prefs[AUTO_ADJUST] ?: true,
                        cacheLimit = prefs[CACHE_LIMIT]?.let(::safeCacheLimit)
                            ?: CacheLimit.MB_500,
                        shuffle = prefs[SHUFFLE] ?: false,
                        repeatMode = (prefs[REPEAT_MODE] ?: 0).coerceIn(0, 2),
                        autoplay = prefs[AUTOPLAY] ?: true,
                        prefetchEnabled = prefs[PREFETCH_ENABLED] ?: false,
                    )
                }
                .collect {
                    _settings.value = it
                    _settingsLoaded.value = true
                }
        }
    }

    suspend fun setDataSaver(enabled: Boolean) {
        runCatching { dataStore.edit { it[DATA_SAVER] = enabled } }
    }

    suspend fun setWifiQuality(quality: AudioQuality) {
        runCatching { dataStore.edit { it[WIFI_QUALITY] = quality.name } }
    }

    suspend fun setCellularQuality(quality: AudioQuality) {
        runCatching { dataStore.edit { it[CELLULAR_QUALITY] = quality.name } }
    }

    suspend fun setAutoAdjustQuality(enabled: Boolean) {
        runCatching { dataStore.edit { it[AUTO_ADJUST] = enabled } }
    }

    suspend fun setCacheLimit(limit: CacheLimit) {
        runCatching { dataStore.edit { it[CACHE_LIMIT] = limit.ordinal } }
    }

    suspend fun setShuffle(enabled: Boolean) {
        runCatching { dataStore.edit { it[SHUFFLE] = enabled } }
    }

    suspend fun setRepeatMode(mode: Int) {
        runCatching { dataStore.edit { it[REPEAT_MODE] = mode.coerceIn(0, 2) } }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        runCatching { dataStore.edit { it[AUTOPLAY] = enabled } }
    }

    suspend fun setPrefetchEnabled(enabled: Boolean) {
        runCatching { dataStore.edit { it[PREFETCH_ENABLED] = enabled } }
    }

    private fun safeQuality(name: String): AudioQuality =
        runCatching { AudioQuality.valueOf(name) }.getOrDefault(AudioQuality.AUTO)

    private fun safeCacheLimit(ordinal: Int): CacheLimit =
        CacheLimit.entries.getOrNull(ordinal) ?: CacheLimit.MB_500

    private companion object {
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val WIFI_QUALITY = stringPreferencesKey("wifi_quality")
        val CELLULAR_QUALITY = stringPreferencesKey("cellular_quality")
        val AUTO_ADJUST = booleanPreferencesKey("auto_adjust_quality")
        val CACHE_LIMIT = intPreferencesKey("cache_limit")
        val SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val AUTOPLAY = booleanPreferencesKey("autoplay_enabled")
        val PREFETCH_ENABLED = booleanPreferencesKey("prefetch_enabled")
    }
}
