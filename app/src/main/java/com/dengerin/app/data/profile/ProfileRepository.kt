package com.dengerin.app.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import com.dengerin.app.core.FirestoreIds
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale

/**
 * Satu fasad untuk Firebase Auth + Firestore + cache DataStore.
 *
 * Semua mutasiXP/counter masuk ke cache lokal dulu. Firestore hanya ditulis saat
 * naik level, ganti identitas/kosmetik, tiap 15 menit dengar, atau logout.
 */
class ProfileRepository(context: Context) {
    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val preferences = ProfilePreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutationMutex = Mutex()

    private val _authState = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val authState: StateFlow<FirebaseUser?> = _authState

    val profile: StateFlow<UserProfile?> = preferences.profile.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /** Detik sejak sync terakhir; dipakai untuk ambang 15 menit pemutaran. */
    private var unsyncedListeningSeconds = 0L

    init {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser
        }
        // Pendaftaran listener bisa gagal saat Firebase belum terinisialisasi (offline /
        // proses awal). Jangan sampai itu membunuh Application.onCreate().
        runCatching { auth.addAuthStateListener(listener) }

        scope.launch {
            _authState.collect { user ->
                // hydrateForUser sengaja tidak menyentuh Firestore saat user null;
                // semua jalur cloud juga dibungkus runCatching di dalamnya.
                runCatching { hydrateForUser(user) }
            }
        }
    }

    /** Tukar idToken Credential Manager menjadi sesi Firebase, lalu tarik profil cloud. */
    suspend fun signInWithGoogle(idToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(credential).await().user
                ?: error("Akun Google tidak dapat dibaca. Coba lagi ya.")
            _authState.value = user

            val remote = runCatching { fetchRemote(FirestoreIds.user(user.uid)) }.getOrNull()
            val local = preferences.profile.first()?.takeIf { it.uid == user.uid }
            // Email dari Auth selalu menang supaya Dev Panel memakai identitas aktif.
            val result = forIdentity(remote ?: local ?: newProfile(user), user.email)
            mutationMutex.withLock { preferences.save(result) }
            syncEmail(user)
            result
        }
    }

    /** Tarik ulang profil dari Firestore (dipakai saat login di perangkat baru). */
    suspend fun refreshFromCloud(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Belum masuk ke akun Google.")
            val remote = fetchRemote(FirestoreIds.user(user.uid))
                ?: error("Profil cloud belum tersedia.")
            mutationMutex.withLock { preferences.save(remote) }
            remote
        }
    }

    /** Edit profil/bingkai/title: simpan instan ke cache lalu sync sekali ini saja. */
    suspend fun updateProfile(transform: (UserProfile) -> UserProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            val local = runCatching {
                mutationMutex.withLock {
                    val current = preferences.profile.first() ?: return@withLock
                    preferences.save(transform(current))
                }
            }
            if (local.isFailure) {
                Result.failure(local.exceptionOrNull() ?: IllegalStateException("Gagal menyimpan profil lokal."))
            } else {
                syncNow()
            }
        }

    /**
     * Foto dari Android Photo Picker: crop 1:1, resize 256x256, JPEG kualitas 75–80,
     * lalu disimpan sebagai `photoBase64` di cache dan `users/{uid}` Firestore.
     * Tidak memakai Firebase Storage (tanpa risiko billing).
     */
    @Suppress("DEPRECATION")
    suspend fun saveProfilePhoto(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Masuk ke akun Google dulu ya.")
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Foto tidak dapat dibaca. Coba pilih lagi.")
            val base64 = encodeAvatar(cropSquare(bytes))
            mutationMutex.withLock {
                val current = preferences.profile.first() ?: error("Profil lokal belum tersedia.")
                preferences.save(
                    current.copy(
                        uid = user.uid,
                        email = user.email.orEmpty().ifBlank { current.email },
                        photoBase64 = base64,
                    ),
                )
            }
            syncNow().getOrThrow()
            base64
        }
    }

    /** Hapus foto galeri/URL agar avatar kembali ke inisial. */
    suspend fun clearProfilePhoto(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutationMutex.withLock {
                val current = preferences.profile.first() ?: return@withLock
                preferences.save(current.copy(photoUrl = "", photoBase64 = ""))
            }
            syncNow().getOrThrow()
        }
    }

    /**
     * Satu tick = 60 detik mendengar efektif. Hanya dihitung saat Firebase user ada;
     * PlayerController sudah memastikan ExoPlayer benar-benar playing.
     */
    suspend fun addListeningMinute() = withContext(Dispatchers.IO) {
        runCatching {
            if (auth.currentUser == null) return@runCatching
            var levelUp: Int? = null

            mutationMutex.withLock {
                val current = preferences.profile.first() ?: return@withLock
                val night = isNightTime()
                val next = current.copy(
                    totalListeningSeconds = current.totalListeningSeconds + LISTENING_TICK_SECONDS,
                    totalXp = current.totalXp + XP_PER_MINUTE,
                    nightListeningMinutes = current.nightListeningMinutes + if (night) 1L else 0L,
                )
                preferences.save(next)

                val before = UserProfile.levelFromXp(current.totalXp)
                val after = UserProfile.levelFromXp(next.totalXp)
                if (after > before) levelUp = after
                unsyncedListeningSeconds += LISTENING_TICK_SECONDS
            }

            val promotedLevel = levelUp
            if (promotedLevel != null) {
                withContext(Dispatchers.Main) {
                    runCatching {
                        Toast.makeText(appContext, "Naik ke Lv. $promotedLevel!", Toast.LENGTH_SHORT).show()
                    }
                }
                syncNow()
            } else if (unsyncedListeningSeconds >= SYNC_INTERVAL_SECONDS) {
                syncNow()
            }
        }
    }

    /** Hitung lagu yang benar-benar mulai diputar saat pengguna sedang login. */
    suspend fun recordSongPlayed() = withContext(Dispatchers.IO) {
        // Dipanggil dari PlayerController tanpa runCatching di pemanggil, jadi
        // kegagalan DataStore/Firestore harus berhenti di sini.
        runCatching {
            if (auth.currentUser == null) return@runCatching
            mutationMutex.withLock {
                val current = preferences.profile.first() ?: return@withLock
                preferences.save(current.copy(totalSongsPlayed = current.totalSongsPlayed + 1))
            }
        }
    }

    /**
     * Dev-only: set level sendiri. Cache diperbarui dulu, lalu profil lengkap ditulis
     * langsung ke `users/{uid}` lewat [syncNow].
     */
    suspend fun setOwnLevel(level: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser ?: error("Belum masuk ke akun Google.")
            requireDeveloper(user.email)
            val targetLevel = level.coerceIn(MIN_DEV_LEVEL, MAX_DEV_LEVEL)
            val targetXp = UserProfile.xpForLevel(targetLevel)

            mutationMutex.withLock {
                val current = preferences.profile.first() ?: error("Profil lokal belum tersedia.")
                preferences.save(current.copy(totalXp = targetXp, email = user.email.orEmpty()))
            }
            syncNow().getOrThrow()
            targetLevel
        }
    }

    /** Dev-only: cari pengguna di `users` untuk diubah levelnya. */
    suspend fun loadDeveloperUsers(query: String): Result<List<DeveloperUser>> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireDeveloper(auth.currentUser?.email)
                val needle = query.trim().lowercase(Locale.getDefault())
                firestore.collection(FirestoreIds.require(COLLECTION_USERS))
                    .orderBy(UserProfile.KEY_DISPLAY_NAME)
                    .limit(MAX_DEV_USERS)
                    .get()
                    .await()
                    .map { document ->
                        val data = document.data
                        DeveloperUser(
                            uid = document.id,
                            displayName = data.stringValue(UserProfile.KEY_DISPLAY_NAME),
                            email = data.stringValue(UserProfile.KEY_EMAIL),
                            photoUrl = data.stringValue(UserProfile.KEY_PHOTO_URL),
                            totalXp = data.numberValue(UserProfile.KEY_XP),
                        )
                    }
                    .filter { user ->
                        needle.isBlank() ||
                            user.displayName.lowercase(Locale.getDefault()).contains(needle) ||
                            user.email.lowercase(Locale.getDefault()).contains(needle)
                    }
            }
        }

    /** Dev-only: ubah hanya field `totalXp` dokumen pengguna lain. */
    suspend fun setUserLevel(uid: String, level: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            requireDeveloper(auth.currentUser?.email)
            val safeUid = FirestoreIds.user(uid)
            val targetLevel = level.coerceIn(MIN_DEV_LEVEL, MAX_DEV_LEVEL)
            firestore.collection(FirestoreIds.require(COLLECTION_USERS))
                .document(safeUid)
                .set(
                    mapOf(UserProfile.KEY_XP to UserProfile.xpForLevel(targetLevel)),
                    SetOptions.merge(),
                )
                .await()
            targetLevel
        }
    }

    /** Set(merge) ke `users/{uid}` — satu-satunya jalur tulis Firestore. */
    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth.currentUser
            val snapshot = preferences.profile.first()
            if (user == null || snapshot == null) {
                unsyncedListeningSeconds = 0L
                return@runCatching Unit
            }
            firestore.collection(FirestoreIds.require(COLLECTION_USERS))
                .document(FirestoreIds.user(user.uid))
                .set(snapshot.toFirestoreMap(), SetOptions.merge())
                .await()
            unsyncedListeningSeconds = 0L
        }
    }

    /** Sync terakhir sebelum sesi dilepas, lalu hapus cache agar beranda netral lagi. */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val synced = syncNow()
        runCatching { auth.signOut() }
        _authState.value = null
        runCatching {
            mutationMutex.withLock {
                preferences.clear()
                unsyncedListeningSeconds = 0L
            }
        }
        synced
    }

    private suspend fun hydrateForUser(user: FirebaseUser?) {
        if (user == null) {
            // Tidak ada Firestore call saat logout/sesi belum ada.
            runCatching {
                mutationMutex.withLock {
                    preferences.clear()
                    unsyncedListeningSeconds = 0L
                }
            }
            return
        }
        if (auth.currentUser == null) return
        runCatching { syncEmail(user) }

        val local = preferences.profile.first()
        if (local?.uid == user.uid) {
            val withIdentity = forIdentity(local, user.email)
            if (withIdentity != local) {
                runCatching { mutationMutex.withLock { preferences.save(withIdentity) } }
            }
            return
        }

        // Perangkat baru / akun berganti: cloud yang menang, Google jadi fallback.
        val remote = runCatching { fetchRemote(FirestoreIds.user(user.uid)) }.getOrNull()
        val initial = forIdentity(remote ?: newProfile(user), user.email)
        runCatching { mutationMutex.withLock { preferences.save(initial) } }
    }

    private suspend fun fetchRemote(uid: String): UserProfile? {
        val safeUid = FirestoreIds.user(uid)
        val snapshot = firestore.collection(FirestoreIds.require(COLLECTION_USERS))
            .document(safeUid)
            .get()
            .await()
        return if (snapshot.exists()) {
            UserProfile.fromFirestoreMap(safeUid, snapshot.data.orEmpty())
        } else {
            null
        }
    }

    private fun newProfile(user: FirebaseUser): UserProfile = UserProfile(
        uid = user.uid,
        displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Pendengar Baru",
        email = user.email.orEmpty(),
        photoUrl = user.photoUrl?.toString().orEmpty(),
        equippedFrameId = UserProfile.DEFAULT_FRAME_ID,
        equippedTitle1Id = UserProfile.DEFAULT_TITLE_ID,
    )

    /** Samakan cache dengan identitas Auth dan buang title dev dari akun non-developer. */
    private fun forIdentity(profile: UserProfile, email: String?): UserProfile {
        val resolvedEmail = email.orEmpty()
        if (isDeveloper(resolvedEmail)) return profile.copy(email = resolvedEmail)
        return profile.copy(
            email = resolvedEmail,
            equippedTitle1Id = profile.equippedTitle1Id
                .takeIf { it != CREATOR_TITLE_ID }
                ?: UserProfile.DEFAULT_TITLE_ID,
            equippedTitle2Id = profile.equippedTitle2Id.takeIf { it != CREATOR_TITLE_ID }.orEmpty(),
        )
    }

    /** Email Auth ditulis ke dokumen sendiri pada setiap login / restore sesi. */
    private suspend fun syncEmail(user: FirebaseUser) {
        val email = user.email?.takeIf { it.isNotBlank() } ?: return
        firestore.collection(FirestoreIds.require(COLLECTION_USERS))
            .document(FirestoreIds.user(user.uid))
            .set(mapOf(UserProfile.KEY_EMAIL to email), SetOptions.merge())
            .await()
    }

    private fun requireDeveloper(email: String?) {
        if (!isDeveloper(email)) error("Akses Developer Panel hanya untuk akun developer.")
    }

    @Suppress("DEPRECATION")
    private fun cropSquare(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / AVATAR_SIZE)
        val source = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Format foto tidak didukung. Coba JPEG atau PNG.")

        val rotation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (degrees == 0f) source else {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                Matrix().apply { postRotate(degrees) },
                true,
            )
        }

        val side = minOf(upright.width, upright.height)
        val square = Bitmap.createBitmap(
            upright,
            (upright.width - side) / 2,
            (upright.height - side) / 2,
            side,
            side,
        )
        return if (side == AVATAR_SIZE) {
            square
        } else {
            Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        }
    }

    /** Turunkan kualitas lalu ukuran sampai file JPEG di bawah 45 KB. */
    private fun encodeAvatar(bitmap: Bitmap): String {
        var quality = 80
        var side = AVATAR_SIZE
        var lastEncoded = ""
        repeat(12) {
            val output = ByteArrayOutputStream()
            val working = if (side == AVATAR_SIZE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, side, side, true)
            }
            working.compress(Bitmap.CompressFormat.JPEG, quality, output)
            lastEncoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            if (output.size() <= MAX_AVATAR_FILE_BYTES) return lastEncoded
            if (quality > 60) quality -= 5 else side = maxOf(96, (side * 0.9f).toInt())
        }
        return lastEncoded
    }

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 4
    }

    private fun Map<String, Any?>.stringValue(key: String): String = this[key] as? String ?: ""

    private fun Map<String, Any?>.numberValue(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        else -> 0L
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val LISTENING_TICK_SECONDS = 60L
        const val XP_PER_MINUTE = 10L
        const val SYNC_INTERVAL_SECONDS = 900L
        const val MIN_DEV_LEVEL = 1
        const val MAX_DEV_LEVEL = 999
        const val MAX_DEV_USERS = 200L
        const val AVATAR_SIZE = 256
        const val MAX_AVATAR_FILE_BYTES = 45 * 1024
    }
}
