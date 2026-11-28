@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.data.usage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.dengerin.app.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dengerin_usage",
)

/** Kuota pemakaian audio. Semua angka dalam byte; `lastDate`/`lastMonth` dipakai untuk reset otomatis. */
data class DataUsageSnapshot(
    val mobileBytesToday: Long = 0L,
    val mobileBytesMonth: Long = 0L,
    val wifiBytesToday: Long = 0L,
    val wifiBytesMonth: Long = 0L,
    val savedByCacheBytes: Long = 0L,
    val trackedSongsCount: Long = 0L,
    val lastDate: String = "",
    val lastMonth: String = "",
) {
    val totalBytesToday: Long get() = mobileBytesToday + wifiBytesToday

    /** Rata-rata kuota internet per lagu yang benar-benar di-stream. */
    val bytesPerSong: Long
        get() = if (trackedSongsCount > 0L) {
            (mobileBytesMonth + wifiBytesMonth) / trackedSongsCount
        } else {
            0L
        }
}

private data class PendingBytes(
    val mobile: Long,
    val wifi: Long,
    val cache: Long,
    val songs: Long,
) {
    val total: Long get() = mobile + wifi + cache + songs
}

/**
 * Pelacak kuota asli. TransferListener dipasang pada HttpDataSource (internet) dan
 * FileDataSource (cache lokal); byte dikumpulkan di memori lalu ditulis ke DataStore
 * tiap ~256 KB, saat pindah lagu, dan saat transfer selesai.
 */
class DataUsageTracker private constructor(
    context: Context,
    private val networkMonitor: NetworkMonitor,
) {

    private val dataStore = context.applicationContext.usageDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val _snapshot = MutableStateFlow(DataUsageSnapshot())
    val snapshot: StateFlow<DataUsageSnapshot> = _snapshot.asStateFlow()

    private var pendingMobile = 0L
    private var pendingWifi = 0L
    private var pendingCache = 0L
    private var pendingSongs = 0L
    private var activeSongKey: String? = null
    private var countedSongKey: String? = null
    private var loaded = false
    private var resetBarrier = false

    init {
        scope.launch {
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .map(::fromPreferences)
                .collect { stored ->
                    val pending: PendingBytes
                    var normalized: DataUsageSnapshot? = null
                    synchronized(lock) {
                        // Ambil dan kosongkan akumulasi di dalam lock supaya flush() yang
                        // terjadi bersamaan tidak menulis byte yang sama dua kali.
                        pending = takePendingLocked()
                        if (!resetBarrier) {
                            val base = normalize(stored)
                            normalized = base
                            _snapshot.value = base.copy(
                                mobileBytesToday = base.mobileBytesToday + pending.mobile,
                                mobileBytesMonth = base.mobileBytesMonth + pending.mobile,
                                wifiBytesToday = base.wifiBytesToday + pending.wifi,
                                wifiBytesMonth = base.wifiBytesMonth + pending.wifi,
                                savedByCacheBytes = base.savedByCacheBytes + pending.cache,
                                trackedSongsCount = base.trackedSongsCount + pending.songs,
                            )
                            loaded = true
                        }
                    }
                    if (pending.total > 0L) {
                        writePending(pending)
                    } else if (normalized != null && normalized != stored) {
                        writeSnapshot(requireNotNull(normalized))
                    }
                }
        }
    }

    /** Dipanggil sebelum DataStore dibaca/ditulis supaya angka hari/bulan lama tidak terbawa. */
    fun current(): DataUsageSnapshot {
        if (!loaded) return _snapshot.value
        val fresh = normalize(_snapshot.value)
        if (fresh != _snapshot.value) {
            synchronized(lock) { _snapshot.value = fresh }
            writeSnapshot(fresh)
        }
        return fresh
    }

    /** Dipanggil saat halaman kuota dibuka untuk memaksa normalisasi tanggal. */
    fun refresh() {
        current()
    }

    /** Listener untuk upstream HttpDataSource: hanya byte yang benar-benar lewat internet. */
    val networkTransferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            if (isNetwork) addNetworkBytes(bytesTransferred.toLong())
        }

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = flush()
    }

    /** Listener untuk FileDataSource/SimpleCache: byte yang tidak perlu diunduh ulang. */
    val cacheTransferListener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            if (!isNetwork) addCacheBytes(bytesTransferred.toLong())
        }

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = flush()
    }

    /** Menandai lagu aktif; flush akumulasi sebelumnya sebelum berpindah lagu. */
    fun setActiveSong(key: String?) {
        val pending: PendingBytes
        synchronized(lock) {
            activeSongKey = key
            if (key == null) countedSongKey = null
            pending = takePendingLocked()
        }
        writePending(pending)
    }

    fun addNetworkBytes(bytes: Long) {
        if (bytes <= 0L) return
        val state = networkMonitor.current()
        // NetworkMonitor membaca ConnectivityManager + NetworkCapabilities (CELLULAR / WIFI).
        val cellular = state.isCellular || (!state.isWifiLike && state.isMetered)
        val flushNeeded: Boolean
        synchronized(lock) {
            val fresh = if (loaded) normalize(_snapshot.value) else _snapshot.value
            if (activeSongKey != null && countedSongKey != activeSongKey) {
                pendingSongs += 1L
                countedSongKey = activeSongKey
                _snapshot.value = fresh.copy(trackedSongsCount = fresh.trackedSongsCount + 1L)
            } else {
                _snapshot.value = fresh
            }
            val current = _snapshot.value
            if (cellular) {
                pendingMobile += bytes
                _snapshot.value = current.copy(
                    mobileBytesToday = current.mobileBytesToday + bytes,
                    mobileBytesMonth = current.mobileBytesMonth + bytes,
                )
            } else {
                pendingWifi += bytes
                _snapshot.value = current.copy(
                    wifiBytesToday = current.wifiBytesToday + bytes,
                    wifiBytesMonth = current.wifiBytesMonth + bytes,
                )
            }
            flushNeeded = pendingMobile + pendingWifi + pendingCache + pendingSongs >= FLUSH_THRESHOLD_BYTES
        }
        if (flushNeeded) flush()
    }

    fun addCacheBytes(bytes: Long) {
        if (bytes <= 0L) return
        val flushNeeded: Boolean
        synchronized(lock) {
            val fresh = if (loaded) normalize(_snapshot.value) else _snapshot.value
            _snapshot.value = fresh.copy(savedByCacheBytes = fresh.savedByCacheBytes + bytes)
            pendingCache += bytes
            flushNeeded = pendingMobile + pendingWifi + pendingCache + pendingSongs >= FLUSH_THRESHOLD_BYTES
        }
        if (flushNeeded) flush()
    }

    /** Tulis akumulasi ke DataStore. Aman dipanggil dari thread pemutaran. */
    fun flush() {
        val pending: PendingBytes
        synchronized(lock) { pending = takePendingLocked() }
        writePending(pending)
    }

    /** Mengosongkan seluruh penghitung kuota (dipakai tombol Reset Statistik). */
    fun reset() {
        val fresh = DataUsageSnapshot(lastDate = today(), lastMonth = thisMonth())
        synchronized(lock) {
            pendingMobile = 0L
            pendingWifi = 0L
            pendingCache = 0L
            pendingSongs = 0L
            activeSongKey = null
            countedSongKey = null
            resetBarrier = true
            loaded = true
            _snapshot.value = fresh
        }
        writeSnapshot(fresh)
    }

    private fun takePendingLocked(): PendingBytes {
        val pending = PendingBytes(pendingMobile, pendingWifi, pendingCache, pendingSongs)
        pendingMobile = 0L
        pendingWifi = 0L
        pendingCache = 0L
        pendingSongs = 0L
        return pending
    }

    private fun writePending(pending: PendingBytes) {
        if (pending.total <= 0L) return
        val mobile = pending.mobile
        val wifi = pending.wifi
        val cache = pending.cache
        val songs = pending.songs
        scope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    val date = today()
                    val month = thisMonth()
                    val resetToday = prefs[KEY_DATE] != date
                    val resetMonth = prefs[KEY_MONTH] != month
                    prefs[KEY_MOBILE_TODAY] = (if (resetToday) 0L else prefs[KEY_MOBILE_TODAY] ?: 0L) + mobile
                    prefs[KEY_MOBILE_MONTH] = (if (resetMonth) 0L else prefs[KEY_MOBILE_MONTH] ?: 0L) + mobile
                    prefs[KEY_WIFI_TODAY] = (if (resetToday) 0L else prefs[KEY_WIFI_TODAY] ?: 0L) + wifi
                    prefs[KEY_WIFI_MONTH] = (if (resetMonth) 0L else prefs[KEY_WIFI_MONTH] ?: 0L) + wifi
                    prefs[KEY_SAVED_CACHE] = (prefs[KEY_SAVED_CACHE] ?: 0L) + cache
                    prefs[KEY_SONGS] = (prefs[KEY_SONGS] ?: 0L) + songs
                    prefs[KEY_DATE] = date
                    prefs[KEY_MONTH] = month
                }
            }
        }
    }

    private fun writeSnapshot(value: DataUsageSnapshot) {
        scope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[KEY_MOBILE_TODAY] = value.mobileBytesToday
                    prefs[KEY_MOBILE_MONTH] = value.mobileBytesMonth
                    prefs[KEY_WIFI_TODAY] = value.wifiBytesToday
                    prefs[KEY_WIFI_MONTH] = value.wifiBytesMonth
                    prefs[KEY_SAVED_CACHE] = value.savedByCacheBytes
                    prefs[KEY_SONGS] = value.trackedSongsCount
                    prefs[KEY_DATE] = value.lastDate.ifBlank { today() }
                    prefs[KEY_MONTH] = value.lastMonth.ifBlank { thisMonth() }
                }
            }
        }
    }

    private fun normalize(value: DataUsageSnapshot): DataUsageSnapshot {
        var result = value
        if (result.lastDate != today()) {
            result = result.copy(
                mobileBytesToday = 0L,
                wifiBytesToday = 0L,
                lastDate = today(),
            )
        }
        if (result.lastMonth != thisMonth()) {
            result = result.copy(
                mobileBytesMonth = 0L,
                wifiBytesMonth = 0L,
                lastMonth = thisMonth(),
            )
        }
        return result
    }

    private fun fromPreferences(prefs: Preferences): DataUsageSnapshot = DataUsageSnapshot(
        mobileBytesToday = prefs[KEY_MOBILE_TODAY] ?: 0L,
        mobileBytesMonth = prefs[KEY_MOBILE_MONTH] ?: 0L,
        wifiBytesToday = prefs[KEY_WIFI_TODAY] ?: 0L,
        wifiBytesMonth = prefs[KEY_WIFI_MONTH] ?: 0L,
        savedByCacheBytes = prefs[KEY_SAVED_CACHE] ?: 0L,
        trackedSongsCount = prefs[KEY_SONGS] ?: 0L,
        lastDate = prefs[KEY_DATE] ?: "",
        lastMonth = prefs[KEY_MONTH] ?: "",
    )

    companion object {
        private const val FLUSH_THRESHOLD_BYTES = 256L * 1024L

        private val KEY_MOBILE_TODAY = longPreferencesKey("mobileBytesToday")
        private val KEY_MOBILE_MONTH = longPreferencesKey("mobileBytesMonth")
        private val KEY_WIFI_TODAY = longPreferencesKey("wifiBytesToday")
        private val KEY_WIFI_MONTH = longPreferencesKey("wifiBytesMonth")
        private val KEY_SAVED_CACHE = longPreferencesKey("savedByCacheBytes")
        private val KEY_SONGS = longPreferencesKey("trackedSongsCount")
        private val KEY_DATE = stringPreferencesKey("lastDate")
        private val KEY_MONTH = stringPreferencesKey("lastMonth")

        @Volatile
        private var instance: DataUsageTracker? = null

        fun get(context: Context, networkMonitor: NetworkMonitor): DataUsageTracker =
            instance ?: synchronized(this) {
                instance ?: DataUsageTracker(context.applicationContext, networkMonitor).also { instance = it }
            }

        private fun today(): String = LocalDate.now().toString()

        private fun thisMonth(): String = YearMonth.now().toString()
    }
}
