package com.dengerin.app.player

import com.dengerin.app.core.network.NetworkState
import com.dengerin.app.data.preferences.PlaybackSettings
import com.dengerin.app.data.preferences.isDataSaverActive

/**
 * Kebijakan prefetch audio. Murni JVM supaya bisa diuji tanpa Android.
 *
 * Prefetch hanya boleh terjadi saat kuota aman: Wi-Fi/Ethernet, tidak ada
 * Data Saver (aplikasi maupun sistem), dan cache masih punya ruang untuk
 * satu lagu penuh. Seluler tidak pernah prefetch walaupun kualitasnya rendah.
 * Prefetch juga wajib diaktifkan secara eksplisit: default-nya mati (0 MB).
 */
object PrefetchPolicy {

    /** Perkiraan konservatif satu lagu Opus standar (~3,5 MB) + margin. */
    const val ESTIMATED_TRACK_BYTES: Long = 6L * 1024L * 1024L

    fun shouldPrefetch(
        network: NetworkState,
        settings: PlaybackSettings,
        cacheBytes: Long,
        reserveBytes: Long = ESTIMATED_TRACK_BYTES,
    ): Boolean {
        if (!settings.prefetchEnabled) return false
        if (!network.isWifiLike) return false
        if (settings.isDataSaverActive(network)) return false
        val limit = settings.cacheLimit.bytes
        if (limit <= 0L || reserveBytes <= 0L) return false
        return cacheBytes + reserveBytes <= limit
    }
}
