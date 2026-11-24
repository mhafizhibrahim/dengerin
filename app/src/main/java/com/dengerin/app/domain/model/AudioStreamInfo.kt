package com.dengerin.app.domain.model

import com.dengerin.app.core.StreamHeaders

data class AudioStreamInfo(
    val audioUrl: String,
    val formatName: String,
    val mimeType: String,
    val bitrateBps: Int,
    /** UA yang dipakai saat URL ini diekstraksi; dipakai ulang oleh ExoPlayer. */
    val userAgent: String = StreamHeaders.NEW_PIPE_USER_AGENT,
    /** Header tambahan yang dibutuhkan supaya server audio tidak menolak request. */
    val headers: Map<String, String> = StreamHeaders.YOUTUBE_HEADERS,
)
