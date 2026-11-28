package com.dengerin.app

import com.dengerin.app.data.resolver.StreamFailureKind
import com.dengerin.app.data.resolver.StreamFailureTracker
import com.dengerin.app.domain.model.StreamPick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Log kegagalan stream murni JVM: clock disuntik supaya tidak butuh Android. */
class StreamFailureTrackerTest {

    @Test
    fun `format yang baru gagal didorong ke akhir urutan retry`() {
        val tracker = StreamFailureTracker(clockMs = { 0L })
        tracker.record("v1", StreamFailureKind.FORMAT, StreamPick.ECONOMY_OPUS)

        val picks = StreamFailureTracker.retryPicks(
            base = StreamPick.ECONOMY_OPUS,
            lastFailure = tracker.last("v1"),
        )

        assertEquals(
            listOf(StreamPick.STANDARD_OPUS, StreamPick.COMPATIBILITY, StreamPick.ECONOMY_OPUS),
            picks,
        )
    }

    @Test
    fun `ekstraksi gagal juga menggeser profil yang sama`() {
        val picks = StreamFailureTracker.retryPicks(
            base = StreamPick.STANDARD_OPUS,
            lastFailure = StreamFailureTracker.Failure(
                kind = StreamFailureKind.SOURCE,
                pick = StreamPick.STANDARD_OPUS,
                atMs = 0L,
            ),
        )

        assertEquals(
            listOf(StreamPick.ECONOMY_OPUS, StreamPick.COMPATIBILITY, StreamPick.STANDARD_OPUS),
            picks,
        )
    }

    @Test
    fun `URL kedaluwarsa tidak mengubah urutan retry`() {
        val picks = StreamFailureTracker.retryPicks(
            base = StreamPick.ECONOMY_OPUS,
            lastFailure = StreamFailureTracker.Failure(
                kind = StreamFailureKind.STALE_URL,
                pick = StreamPick.ECONOMY_OPUS,
                atMs = 0L,
            ),
        )

        assertEquals(
            listOf(StreamPick.ECONOMY_OPUS, StreamPick.STANDARD_OPUS, StreamPick.COMPATIBILITY),
            picks,
        )
    }

    @Test
    fun `catatan kedaluwarsa tidak dipakai lagi`() {
        var now = 0L
        val tracker = StreamFailureTracker(clockMs = { now })

        tracker.record("v1", StreamFailureKind.NETWORK, StreamPick.STANDARD_OPUS)
        assertEquals(StreamFailureKind.NETWORK, tracker.last("v1")?.kind)

        now = StreamFailureTracker.DEFAULT_TTL_MS + 1
        assertNull(tracker.last("v1"))
    }

    @Test
    fun `entri dibatasi dan clear menghapus satu video`() {
        val tracker = StreamFailureTracker(clockMs = { 0L }, maxEntries = 3)
        repeat(5) { index -> tracker.record("v$index", StreamFailureKind.NETWORK) }

        assertEquals(3, tracker.snapshot().size)
        assertNull(tracker.last("v0"))

        tracker.record("v4", StreamFailureKind.FORMAT)
        tracker.clear("v4")
        assertNull(tracker.last("v4"))
    }
}
