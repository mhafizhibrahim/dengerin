package com.dengerin.app.domain.model

import com.dengerin.app.core.StreamHeaders

/**
 * Strategi pemilihan stream saat ekstraksi.
 *
 * - [ECONOMY_OPUS]: WEBMA_OPUS 48–70 kbps untuk seluler / Penghemat Data.
 * - [STANDARD_OPUS]: WEBMA_OPUS maksimal ~128 kbps untuk Wi-Fi tanpa penghemat data.
 * - [COMPATIBILITY]: M4A / audio lain dengan bitrate terkecil, dipakai hanya bila
 *   percobaan Opus gagal. Video muxed tetap dihindari kecuali tidak ada audio sama sekali.
 */
enum class StreamPick {
    ECONOMY_OPUS,
    STANDARD_OPUS,
    COMPATIBILITY,
}

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
