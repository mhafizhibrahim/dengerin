package com.dengerin.app

import com.dengerin.app.core.network.NetworkState
import com.dengerin.app.data.preferences.PlaybackSettings
import com.dengerin.app.player.PrefetchPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kebijakan prefetch murni JVM: tidak butuh Context maupun Media3. */
class PrefetchPolicyTest {

    private val settings = PlaybackSettings(prefetchEnabled = true)
    private val wifi = NetworkState(
        isCellular = false,
        isWifi = true,
        isMetered = false,
        downstreamKbps = 0,
        isSystemDataSaver = false,
    )
    private val cellular = wifi.copy(isCellular = true, isWifi = false, isMetered = true)

    @Test
    fun `default prefetch mati sehingga kuota nol MB`() {
        assertFalse(PrefetchPolicy.shouldPrefetch(wifi, PlaybackSettings(), cacheBytes = 0L))
    }

    @Test
    fun `seluler tidak pernah prefetch`() {
        assertFalse(PrefetchPolicy.shouldPrefetch(cellular, settings, cacheBytes = 0L))
    }

    @Test
    fun `data saver aplikasi memblokir prefetch di wifi`() {
        assertFalse(
            PrefetchPolicy.shouldPrefetch(
                wifi,
                settings.copy(dataSaver = true),
                cacheBytes = 0L,
            ),
        )
    }

    @Test
    fun `data saver sistem memblokir prefetch di wifi`() {
        val restricted = wifi.copy(isSystemDataSaver = true)
        assertFalse(PrefetchPolicy.shouldPrefetch(restricted, settings, cacheBytes = 0L))
    }

    @Test
    fun `jaringan tidak dikenal dan terukur tidak prefetch`() {
        val unknown = wifi.copy(isWifi = false, isMetered = true)
        assertFalse(PrefetchPolicy.shouldPrefetch(unknown, settings, cacheBytes = 0L))
    }

    @Test
    fun `wifi dengan ruang cache cukup boleh prefetch`() {
        assertTrue(
            PrefetchPolicy.shouldPrefetch(
                wifi,
                settings,
                cacheBytes = settings.cacheLimit.bytes - PrefetchPolicy.ESTIMATED_TRACK_BYTES,
            ),
        )
    }

    @Test
    fun `cache hampir penuh tidak prefetch`() {
        assertFalse(
            PrefetchPolicy.shouldPrefetch(
                wifi,
                settings,
                cacheBytes = settings.cacheLimit.bytes - PrefetchPolicy.ESTIMATED_TRACK_BYTES + 1L,
            ),
        )
    }
}
