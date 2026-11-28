package com.dengerin.app.player

/**
 * Jeda sebelum setiap percobaan ulang stream. Murni JVM supaya bisa diuji
 * tanpa Android/Media3. Pola: 250 → 500 → 1000 → 2000 ms (maksimal).
 */
object RetryBackoff {

    const val BASE_DELAY_MS = 250L
    const val MAX_DELAY_MS = 2_000L

    fun delayMs(attempt: Int): Long {
        if (attempt <= 0) return BASE_DELAY_MS
        // Batasi shift supaya tidak overflow untuk attempt yang tidak wajar.
        val shifted = attempt.coerceAtMost(20)
        return (BASE_DELAY_MS shl shifted).coerceAtMost(MAX_DELAY_MS)
    }
}
