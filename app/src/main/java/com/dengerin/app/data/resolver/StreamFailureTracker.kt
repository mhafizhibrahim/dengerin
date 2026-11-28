package com.dengerin.app.data.resolver

import com.dengerin.app.domain.model.StreamPick

/** Alasan kegagalan stream, dipakai untuk memilih percobaan berikutnya. */
enum class StreamFailureKind {
    /** URL bertanda tangan kedaluwarsa / HTTP 403 — format masih bisa dipakai ulang. */
    STALE_URL,

    /** Container atau content-type ditolak pemutar — format harus diganti. */
    FORMAT,

    /** Jaringan putus / timeout. */
    NETWORK,

    /** Ekstraksi YouTube gagal sebelum URL audio didapat. */
    SOURCE,

    /** Byte cache rusak atau tercampur antarformat. */
    CACHE,
}

/**
 * Catatan kegagalan stream lokal (RAM, tanpa jaringan dan tanpa analytics).
 *
 * Tujuannya bukan menyimpan riwayat permanen, melainkan mencegah retry mengulang
 * strategi yang baru saja gagal. Entri dibatasi dan kedaluwarsa supaya tidak
 * menyimpan state lagu lama selamanya.
 */
class StreamFailureTracker(
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = MAX_ENTRIES,
) {

    data class Failure(
        val kind: StreamFailureKind,
        val pick: StreamPick?,
        val atMs: Long,
    )

    private val failures = LinkedHashMap<String, Failure>()

    @Synchronized
    fun record(videoId: String, kind: StreamFailureKind, pick: StreamPick? = null) {
        if (videoId.isBlank()) return
        failures.remove(videoId)
        failures[videoId] = Failure(kind, pick, clockMs())
        while (failures.size > maxEntries) {
            failures.remove(failures.keys.first())
        }
    }

    /** Null bila tidak ada catatan atau catatan sudah kedaluwarsa. */
    @Synchronized
    fun last(videoId: String): Failure? {
        val failure = failures[videoId] ?: return null
        if (clockMs() - failure.atMs >= ttlMs) {
            failures.remove(videoId)
            return null
        }
        return failure
    }

    @Synchronized
    fun clear(videoId: String) {
        failures.remove(videoId)
    }

    @Synchronized
    fun snapshot(): Map<String, Failure> = failures.toMap()

    companion object {
        const val DEFAULT_TTL_MS = 60_000L
        private const val MAX_ENTRIES = 48

        /**
         * Urutan retry: Opus sesuai jaringan → Opus profil lain → M4A/kompatibilitas.
         *
         * Bila kegagalan terakhir berupa FORMAT/SOURCE, profil yang sama didorong ke
         * belakang supaya percobaan berikutnya langsung memakai container berbeda.
         * STALE_URL/CACHE sengaja tidak mengubah urutan: URL lama sudah di-invalidate
         * dan byte cache sudah dibuang sebelum retry dijalankan.
         */
        fun retryPicks(base: StreamPick, lastFailure: Failure?): List<StreamPick> {
            val alternate = when (base) {
                StreamPick.ECONOMY_OPUS -> StreamPick.STANDARD_OPUS
                else -> StreamPick.ECONOMY_OPUS
            }
            val ordered = listOf(base, alternate, StreamPick.COMPATIBILITY).distinct()
            val rejected = when (lastFailure?.kind) {
                StreamFailureKind.FORMAT, StreamFailureKind.SOURCE -> lastFailure.pick
                else -> null
            } ?: return ordered
            return ordered.filterNot { it == rejected } + rejected
        }
    }
}
