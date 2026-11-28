package com.dengerin.app

import com.dengerin.app.player.RetryBackoff
import org.junit.Assert.assertEquals
import org.junit.Test

/** Backoff retry murni JVM: tidak butuh Context maupun Media3. */
class RetryBackoffTest {

    @Test
    fun jedaBertahapDanMaksimalDuaDetik() {
        assertEquals(250L, RetryBackoff.delayMs(0))
        assertEquals(500L, RetryBackoff.delayMs(1))
        assertEquals(1_000L, RetryBackoff.delayMs(2))
        assertEquals(2_000L, RetryBackoff.delayMs(3))
        assertEquals(2_000L, RetryBackoff.delayMs(99))
    }

    @Test
    fun attemptNegatifMakaiJedaDasar() {
        assertEquals(RetryBackoff.BASE_DELAY_MS, RetryBackoff.delayMs(-1))
    }
}
