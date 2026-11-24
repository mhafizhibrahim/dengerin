package com.dengerin.app.data.profile

/** Baris pengguna yang ditampilkan di Developer Panel. */
data class DeveloperUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String,
    val totalXp: Long,
) {
    val level: Int get() = UserProfile.levelFromXp(totalXp)
}
