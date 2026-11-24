package com.dengerin.app.data.profile

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Profil opsional Dengerin. Disimpan di DataStore untuk akses instan dan di
 * `users/{uid}` Firestore sebagai cadangan cloud. Semua angka memakai Long agar
 * aman saat melewati batas Int.
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    /** Foto galeri yang sudah dipangkas 1:1, 256 px, JPEG, disimpan sebagai Base64. */
    val photoBase64: String = "",
    val bio: String = "",
    val totalListeningSeconds: Long = 0L,
    val totalXp: Long = 0L,
    val totalSongsPlayed: Long = 0L,
    val nightListeningMinutes: Long = 0L,
    val equippedFrameId: String = DEFAULT_FRAME_ID,
    val equippedTitle1Id: String = DEFAULT_TITLE_ID,
    val equippedTitle2Id: String = "",
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        KEY_UID to uid,
        KEY_DISPLAY_NAME to displayName,
        KEY_EMAIL to email,
        KEY_PHOTO_URL to photoUrl,
        KEY_PHOTO_BASE64 to photoBase64,
        KEY_BIO to bio,
        KEY_LISTENING to totalListeningSeconds,
        KEY_XP to totalXp,
        KEY_SONGS to totalSongsPlayed,
        KEY_NIGHT to nightListeningMinutes,
        KEY_FRAME to equippedFrameId,
        KEY_TITLE_1 to equippedTitle1Id,
        KEY_TITLE_2 to equippedTitle2Id,
    )

    companion object {
        const val DEFAULT_FRAME_ID = "default"
        const val DEFAULT_TITLE_ID = "new_listener"

        const val KEY_UID = "uid"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_EMAIL = "email"
        const val KEY_PHOTO_URL = "photoUrl"
        const val KEY_PHOTO_BASE64 = "photoBase64"
        const val KEY_BIO = "bio"
        const val KEY_LISTENING = "totalListeningSeconds"
        const val KEY_XP = "totalXp"
        const val KEY_SONGS = "totalSongsPlayed"
        const val KEY_NIGHT = "nightListeningMinutes"
        const val KEY_FRAME = "equippedFrameId"
        const val KEY_TITLE_1 = "equippedTitle1Id"
        const val KEY_TITLE_2 = "equippedTitle2Id"

        /** Kontrak level: floor(sqrt(totalXp / 40.0)) + 1. */
        fun levelFromXp(totalXp: Long): Int = floor(sqrt(totalXp / 40.0)).toInt() + 1

        /** XP minimum yang dibutuhkan untuk memasuki sebuah level. */
        fun xpForLevel(level: Int): Long {
            val steps = (level - 1).coerceAtLeast(0).toLong()
            return steps * steps * 40L
        }

        fun fromFirestoreMap(uid: String, data: Map<String, Any?>): UserProfile = UserProfile(
            uid = uid,
            displayName = data.string(KEY_DISPLAY_NAME),
            email = data.string(KEY_EMAIL),
            photoUrl = data.string(KEY_PHOTO_URL),
            photoBase64 = data.string(KEY_PHOTO_BASE64),
            bio = data.string(KEY_BIO),
            totalListeningSeconds = data.number(KEY_LISTENING),
            totalXp = data.number(KEY_XP),
            totalSongsPlayed = data.number(KEY_SONGS),
            nightListeningMinutes = data.number(KEY_NIGHT),
            equippedFrameId = data.string(KEY_FRAME).ifBlank { DEFAULT_FRAME_ID },
            equippedTitle1Id = data.string(KEY_TITLE_1).ifBlank { DEFAULT_TITLE_ID },
            equippedTitle2Id = data.string(KEY_TITLE_2),
        )

        private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

        private fun Map<String, Any?>.number(key: String): Long = when (val value = this[key]) {
            is Number -> value.toLong()
            else -> 0L
        }
    }
}
