package com.dengerin.app.data.profile

/** Akun yang diizinkan memakai Developer & Testing Panel. */
const val DEVELOPER_EMAIL = "ibrahimhafiz840@gmail.com"

/** ID title eksklusif yang dipakai katalog dan Repository saat sanitasi profil. */
const val CREATOR_TITLE_ID = "creator_dev"

/**
 * Satu-satunya pemeriksaan identitas developer di aplikasi. Dipakai UI dan repository;
 * aturan Firestore tetap memeriksa email dari token Auth sebagai pagar akhir.
 */
fun isDeveloper(email: String?): Boolean = email.equals(DEVELOPER_EMAIL, ignoreCase = true)
