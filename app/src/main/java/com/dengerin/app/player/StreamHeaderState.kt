package com.dengerin.app.player

import com.dengerin.app.core.StreamHeaders
import com.dengerin.app.domain.model.StreamPick

/**
 * Media3 1.6.1 belum punya `MediaItem.setHttpHeaders`, jadi UA/header per lagu
 * disimpan di sini dan dibaca oleh [DynamicOkHttpDataSourceFactory] saat
 * ExoPlayer membuat koneksi. Satu lagu diputar dalam satu waktu, jadi aman.
 */
object StreamHeaderState {

    @Volatile
    private var snapshot: Snapshot = Snapshot(
        userAgent = StreamHeaders.NEW_PIPE_USER_AGENT,
        headers = StreamHeaders.YOUTUBE_HEADERS,
    )

    fun configure(
        userAgent: String,
        headers: Map<String, String>,
        cacheKey: String? = null,
        videoId: String? = null,
        videoUrl: String? = null,
        pick: StreamPick? = null,
    ) {
        snapshot = Snapshot(
            userAgent = userAgent.takeIf { it.isNotBlank() }
                ?: StreamHeaders.NEW_PIPE_USER_AGENT,
            headers = headers.ifEmpty { StreamHeaders.YOUTUBE_HEADERS },
            cacheKey = cacheKey,
            videoId = videoId,
            videoUrl = videoUrl,
            pick = pick,
        )
    }

    fun current(): Snapshot = snapshot

    data class Snapshot(
        val userAgent: String,
        val headers: Map<String, String>,
        /** Kunci cache stabil untuk lagu yang sedang diputar (diisi PlayerController). */
        val cacheKey: String? = null,
        val videoId: String? = null,
        val videoUrl: String? = null,
        val pick: StreamPick? = null,
    )
}
