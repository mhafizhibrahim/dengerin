package com.dengerin.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Satu sumber keadaan jaringan aktif. Dipakai resolver kualitas, buffer ExoPlayer,
 * dan UI (misalnya sampul Now Playing saat Data Saver aktif di seluler).
 */
data class NetworkState(
    val isCellular: Boolean,
    val isWifi: Boolean,
    val isMetered: Boolean,
    val downstreamKbps: Int,
    val isSystemDataSaver: Boolean,
) {
    /** Wi-Fi/Ethernet dianggap aman; jaringan tidak dikenal dibuat konservatif. */
    val isWifiLike: Boolean get() = !isCellular && (isWifi || !isMetered)

    /** Bandwidth rendah tapi nyata (0 = tidak diketahui, jangan diturunkan). */
    val isLowBandwidth: Boolean get() = downstreamKbps in 1..LOW_BANDWIDTH_KBPS

    companion object {
        const val LOW_BANDWIDTH_KBPS = 700
    }
}

class NetworkMonitor(context: Context) {

    private val connectivityManager: ConnectivityManager? = context.applicationContext.getSystemService()
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<NetworkState> = _state

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _state.value = readState()
        }

        override fun onLost(network: Network) {
            _state.value = readState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _state.value = readState()
        }
    }

    init {
        runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
    }

    /** Snapshot sinkron untuk pemilihan bitrate/load control yang tidak bisa suspend. */
    fun current(): NetworkState = _state.value

    private fun readState(): NetworkState {
        val manager = connectivityManager ?: return NetworkState(
            isCellular = false,
            isWifi = false,
            isMetered = true,
            downstreamKbps = 0,
            isSystemDataSaver = false,
        )
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        val cellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val dataSaver = runCatching {
            manager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)
        return NetworkState(
            isCellular = cellular,
            isWifi = wifi,
            isMetered = manager.isActiveNetworkMetered,
            downstreamKbps = capabilities?.linkDownstreamBandwidthKbps ?: 0,
            isSystemDataSaver = dataSaver,
        )
    }

    fun close() {
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
