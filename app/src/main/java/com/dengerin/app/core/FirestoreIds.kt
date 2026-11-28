package com.dengerin.app.core

/**
 * Firestore membatasi ID dokumen/koleksi: tidak boleh kosong, mengandung '/', atau '.',
 * dan pancang total jalur adalah 1500 byte. Seluruh ID yang dipakai sebagai segmen
 * path wajib melalui [sanitize] lebih dulu supaya IllegalArgumentException tidak
 * pernah sampai ke UI (nilai kosong/URL/videoId mentah sering berisi karakter itu).
 */
object FirestoreIds {
    private const val MAX_SEGMENT_LENGTH = 256

    fun sanitize(raw: String?): String? = raw
        ?.trim()
        ?.replace('/', '_')
        ?.replace('.', '_')
        ?.take(MAX_SEGMENT_LENGTH)
        ?.takeIf { it.isNotBlank() && !it.startsWith("__") }

    fun require(raw: String?, label: String = "ID"): String =
        sanitize(raw) ?: throw IllegalArgumentException("$label tidak valid.")

    fun user(raw: String?) = require(raw, "uid")
    fun playlist(raw: String?) = require(raw, "playlistId")
    fun track(raw: String?) = require(raw, "trackId")
    fun entry(raw: String?) = require(raw, "entryId")
}
