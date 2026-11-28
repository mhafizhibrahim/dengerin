package com.dengerin.app.domain.model

/**
 * Hasil ekstraksi URL audio yang disimpan di RAM selama ~4 jam.
 *
 * Kuncinya adalah videoId YouTube (bukan URL googlevideo.com yang bertanda tangan),
 * sehingga pemutaran ulang / pemulihan cepat tidak mengulang request halaman YouTube.
 */
data class ResolvedAudioStream(
    val audioUrl: String,
    val userAgent: String,
    val headers: Map<String, String>,
    val bitrateBps: Int,
    /** SystemClock.elapsedRealtime() saat entri kedaluwarsa. */
    val expiresAtMs: Long,
    /** Profil pemilihan yang dipakai, supaya retry kompatibilitas tidak memakai cache lama. */
    val pick: StreamPick,
)
