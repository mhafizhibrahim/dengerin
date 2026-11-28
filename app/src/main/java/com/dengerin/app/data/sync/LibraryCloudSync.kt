package com.dengerin.app.data.sync

import android.util.Log
import com.dengerin.app.core.FirestoreIds
import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.LikedSongEntity
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.db.PlaylistTrackEntity
import com.dengerin.app.data.db.SyncOperationEntity
import com.dengerin.app.data.preferences.SyncPreferences
import com.dengerin.app.data.spotify.SpotifySessionManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Jembatan dua arah Room ↔ Firestore untuk koleksi tanpa batas.
 *
 * Prinsipnya: UI tidak pernah menunggu jaringan. Room ditulis lebih dulu, lalu operasi
 * masuk outbox dan dikirim saat sesi Firebase tersedia. Snapshot listener hanya menulis
 * ke Room bila versi `updatedAt` cloud lebih baru, sehingga echo dari tulisan lokal
 * tidak pernah menimpa perubahan pengguna yang sedang berjalan.
 */
class LibraryCloudSync(
    private val dao: DengerinDao,
    private val authState: StateFlow<FirebaseUser?>,
    private val preferences: SyncPreferences,
    private val spotify: SpotifySessionManager,
) {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val uploadMutex = Mutex()
    private val drainMutex = Mutex()

    /** Aturan yang sama persis dengan helper di LibraryRepository.kt. */
    internal fun isCloudPlaylist(playlistId: String, source: String?): Boolean =
        com.dengerin.app.data.repository.isCloudPlaylist(playlistId, source)

    private var currentUid: String? = null
    /** App di background: listener Firestore dilepas, sinkronisasi lokal tetap jalan. */
    @Volatile private var listenersPaused = false
    private var started = false
    private var batchDepth = 0
    private var playlistsListener: ListenerRegistration? = null
    private var likedListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null
    private val trackListeners = mutableMapOf<String, ListenerRegistration>()

    /** Dipanggil sekali dari AppContainer. Aman bila dipanggil ulang. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            authState.collect { user ->
                runCatching { handleAuth(user) }
                    .onFailure { Log.w(TAG, "Gagal menyiapkan sinkronisasi koleksi: ${it.message}") }
            }
        }
    }

    /** Background: lepas listener Firestore, tapi authState & outbox tetap hidup. */
    fun pauseListeners() {
        if (listenersPaused) return
        listenersPaused = true
        stopListeners()
    }

    /** Foreground: pasang ulang listener tanpa mengulang upload/drain. */
    fun resumeListeners() {
        if (!listenersPaused) return
        listenersPaused = false
        currentUid?.let(::startListeners)
    }

    /** Room sudah berubah; minta drain tanpa mem-blok pemanggil. */
    fun requestDrain() {
        scope.launch {
            val uid = currentUid ?: return@launch
            runCatching {
                drainMutex.withLock { drainOutbox(uid) }
            }.onFailure { Log.w(TAG, "Gagal mengirim operasi koleksi: ${it.message}") }
        }
    }

    /** Tunda drain saat repository menulis ribuan operasi sekaligus. */
    fun beginBatch() {
        synchronized(this) { batchDepth++ }
    }

    /** Drain tepat sekali setelah batch terakhir selesai. */
    fun endBatch() {
        val shouldDrain = synchronized(this) {
            if (batchDepth > 0) batchDepth -= 1
            batchDepth == 0
        }
        if (shouldDrain) requestDrain()
    }

    private fun shouldDrainNow(): Boolean = synchronized(this) { batchDepth == 0 }

    private suspend fun handleAuth(user: FirebaseUser?) {
        stopListeners()
        val uid = user?.uid?.let { runCatching { FirestoreIds.user(it) }.getOrNull() }
        currentUid = uid
        if (uid == null) return

        drainMutex.withLock {
            // Prioritas saat startup (termasuk installDebug): selesaikan penghapusan
            // yang tertunda sebelum upload/library maupun listener apa pun berjalan.
            flushPendingPlaylistDeletes(uid)
        }
        uploadMutex.withLock {
            if (!preferences.hasUploaded(uid)) {
                uploadLocalLibrary(uid)
                preferences.markUploaded(uid)
            }
        }
        drainMutex.withLock { drainOutbox(uid) }
        startListeners(uid)
    }

    // ---- Enqueue (dipanggil LibraryRepository setelah Room berhasil ditulis) ------------

    suspend fun enqueuePlaylistUpsert(playlist: LocalPlaylistEntity) {
        if (!isCloudPlaylist(playlist.id, playlist.source)) return
        enqueue(
            SyncOperationEntity(
                kind = KIND_PLAYLIST_UPSERT,
                playlistId = playlist.id,
                name = playlist.name,
                description = playlist.description,
                coverUrl = playlist.coverUrl,
                isPinned = playlist.isPinned,
                trackCount = playlist.trackCount,
                totalDurationMs = playlist.totalDurationMs,
                addedAt = playlist.createdAt,
                createdAt = playlist.updatedAt,
            ),
        )
    }

    suspend fun enqueueTrackUpsert(track: PlaylistTrackEntity) {
        if (!isCloudPlaylist(track.playlistId, null)) return
        enqueue(
            SyncOperationEntity(
                kind = KIND_TRACK_UPSERT,
                playlistId = track.playlistId,
                entryId = track.entryId,
                trackId = track.spotifyId.ifBlank { track.videoUrl.ifBlank { track.entryId } },
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                coverUrl = track.coverUrl,
                videoUrl = track.videoUrl,
                spotifyId = track.spotifyId,
                durationMs = track.durationMs,
                addedAt = track.addedAt,
                orderIndex = track.position,
                createdAt = track.addedAt,
            ),
        )
    }

    suspend fun enqueueTrackDelete(playlistId: String, entryId: String) {
        if (!isCloudPlaylist(playlistId, null)) return
        enqueue(
            SyncOperationEntity(
                kind = KIND_TRACK_DELETE,
                playlistId = playlistId,
                entryId = entryId,
            ),
        )
    }

    suspend fun enqueueTracksClear(playlistId: String) {
        if (!isCloudPlaylist(playlistId, null)) return
        enqueue(SyncOperationEntity(kind = KIND_TRACKS_CLEAR, playlistId = playlistId))
    }

    suspend fun enqueueLikedUpsert(liked: LikedSongEntity) {
        enqueue(
            SyncOperationEntity(
                kind = KIND_LIKED_UPSERT,
                trackId = liked.id,
                title = liked.title,
                artist = liked.artist,
                thumbnailUrl = liked.thumbnailUrl,
                coverUrl = liked.coverUrl,
                videoUrl = liked.videoUrl,
                spotifyId = liked.spotifyId,
                durationMs = liked.durationMs,
                addedAt = liked.addedAt,
                createdAt = liked.addedAt,
            ),
        )
    }

    suspend fun enqueueLikedDelete(trackId: String) {
        enqueue(SyncOperationEntity(kind = KIND_LIKED_DELETE, trackId = trackId))
    }

    /** Antrean massal: satu INSERT Room untuk seluruh lagu playlist, satu drain di akhir. */
    suspend fun enqueueTracksUpsertBulk(tracks: List<PlaylistTrackEntity>) {
        val cloudTracks = tracks.filter { isCloudPlaylist(it.playlistId, null) }
        if (cloudTracks.isEmpty()) return
        enqueueAll(cloudTracks.map { track ->
            SyncOperationEntity(
                kind = KIND_TRACK_UPSERT,
                playlistId = track.playlistId,
                entryId = track.entryId,
                trackId = track.spotifyId.ifBlank { track.videoUrl.ifBlank { track.entryId } },
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                coverUrl = track.coverUrl,
                videoUrl = track.videoUrl,
                spotifyId = track.spotifyId,
                durationMs = track.durationMs,
                addedAt = track.addedAt,
                orderIndex = track.position,
                createdAt = track.addedAt,
            )
        })
    }

    /** Antrean massal: satu INSERT Room untuk seluruh lagu suka, satu drain di akhir. */
    suspend fun enqueueLikedUpsertBulk(liked: List<LikedSongEntity>) {
        if (liked.isEmpty()) return
        enqueueAll(liked.map { song ->
            SyncOperationEntity(
                kind = KIND_LIKED_UPSERT,
                trackId = song.id,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                coverUrl = song.coverUrl,
                videoUrl = song.videoUrl,
                spotifyId = song.spotifyId,
                durationMs = song.durationMs,
                addedAt = song.addedAt,
                createdAt = song.addedAt,
            )
        })
    }

    private suspend fun enqueue(operation: SyncOperationEntity) {
        runCatching { dao.enqueueSync(operation.copy(uid = currentUid.orEmpty())) }
            .onFailure { Log.w(TAG, "Gagal menulis outbox: ${it.message}") }
        if (shouldDrainNow()) requestDrain()
    }

    private suspend fun enqueueAll(operations: List<SyncOperationEntity>) {
        if (operations.isEmpty()) return
        runCatching {
            dao.enqueueSyncAll(operations.map { it.copy(uid = currentUid.orEmpty()) })
        }.onFailure { Log.w(TAG, "Gagal menulis outbox massal: ${it.message}") }
        if (shouldDrainNow()) requestDrain()
    }

    // ---- Upload cepat & hapus permanen (khusus playlist Spotify) ----------------------

    /**
     * Tulis keadaan Room terkini langsung ke Firestore setelah impor/update. Maksimal
     * [FIRESTORE_BATCH_LIMIT] operasi per WriteBatch; entri outbox yang sudah terwakili
     * dibuang agar drain berikutnya tidak mengirim ulang data yang sama.
     */
    suspend fun uploadCloudPlaylistsNow(
        playlists: Collection<LocalPlaylistEntity>,
        tracksByPlaylist: Map<String, List<PlaylistTrackEntity>>,
    ) {
        val uid = currentUid ?: return
        val targets = playlists.filter { playlist ->
            isCloudPlaylist(playlist.id, playlist.source) &&
                !dao.hasPendingOperation(KIND_PLAYLIST_DELETE, playlist.id)
        }
        if (targets.isEmpty()) return

        runCatching {
            var batch = firestore.batch()
            var operationsInBatch = 0
            val consumed = mutableListOf<Long>()

            targets.forEach { playlist ->
                val playlistId = safePlaylist(playlist.id) ?: return@forEach
                consumed += dao.syncOperationsForPlaylist(playlist.id)
                    .filterNot { it.kind == KIND_PLAYLIST_DELETE }
                    .map { it.id }
                batch.set(
                    playlists(uid).document(playlistId),
                    playlistDocument(playlist),
                    SetOptions.merge(),
                )
                operationsInBatch++
                if (operationsInBatch >= FIRESTORE_BATCH_LIMIT) {
                    if (!commitWriteBatch(batch)) return@runCatching
                    batch = firestore.batch()
                    operationsInBatch = 0
                }

                tracksByPlaylist[playlist.id].orEmpty().forEach { track ->
                    val entryId = safeEntry(track.entryId) ?: return@forEach
                    batch.set(
                        tracks(uid, playlistId).document(entryId),
                        trackDocument(track),
                        SetOptions.merge(),
                    )
                    operationsInBatch++
                    if (operationsInBatch >= FIRESTORE_BATCH_LIMIT) {
                        if (!commitWriteBatch(batch)) return@runCatching
                        batch = firestore.batch()
                        operationsInBatch = 0
                    }
                }
            }
            if (operationsInBatch > 0 && !commitWriteBatch(batch)) return@runCatching
            dao.removeSyncOperations(consumed)
        }.onFailure { Log.w(TAG, "Gagal upload playlist Spotify: ${it.message}") }
    }

    /**
     * Batalkan semua antrean lama untuk playlist ini, pasang penanda DELETE, lalu
     * eksekusi penghapusan segera. Jika offline, penanda tetap tinggal dan di-flush
     * lebih dulu saat aplikasi menyala.
     */
    suspend fun deletePlaylistNow(playlistId: String) {
        if (!isCloudPlaylist(playlistId, null)) return
        val stale = runCatching { dao.syncOperationsForPlaylist(playlistId) }
            .getOrDefault(emptyList())
            .map { it.id }
        if (stale.isNotEmpty()) dao.removeSyncOperations(stale)

        val operation = SyncOperationEntity(
            kind = KIND_PLAYLIST_DELETE,
            playlistId = playlistId,
            uid = currentUid.orEmpty(),
        )
        val enqueued = runCatching { dao.enqueueSync(operation) }
        if (enqueued.isFailure) {
            Log.w(TAG, "Gagal menandai hapus playlist: ${enqueued.exceptionOrNull()?.message}")
            return
        }
        trackListeners.remove(playlistId)?.remove()

        val uid = currentUid ?: return
        // Eager: langsung dijalankan, bukan menunggu jadwal drain background.
        scope.launch {
            runCatching { drainMutex.withLock { flushPlaylistDeletes(uid, listOf(operation)) } }
                .onFailure { Log.w(TAG, "Gagal menghapus playlist $playlistId sekarang: ${it.message}") }
        }
    }

    /**
     * Impor manual memilih ulang playlist yang dulu dihapus: penanda DELETE yang mungkin
     * masih tertunda dibatalkan supaya upload berikutnya tidak ditiadakan drain.
     */
    suspend fun clearPendingPlaylistDelete(playlistId: String) {
        if (!isCloudPlaylist(playlistId, null)) return
        drainMutex.withLock {
            val pending = runCatching {
                dao.syncOperationsByKind(KIND_PLAYLIST_DELETE, OUTBOX_BATCH)
            }.getOrDefault(emptyList())
                .filter { it.playlistId == playlistId }
                .map { it.id }
            if (pending.isNotEmpty()) {
                runCatching { dao.removeSyncOperations(pending) }
                    .onFailure { Log.w(TAG, "Gagal membatalkan hapus $playlistId: ${it.message}") }
            }
        }
    }

    /** Dipanggil lebih dulu saat startup, sebelum listener dipasang. */
    private suspend fun flushPendingPlaylistDeletes(uid: String) {
        val operations = runCatching { dao.syncOperationsByKind(KIND_PLAYLIST_DELETE, OUTBOX_BATCH) }
            .getOrDefault(emptyList())
        if (operations.isEmpty()) return

        // Penanda warisan sesi uji untuk playlist lokal dibuang tanpa menyentuh cloud.
        val ignoredIds = operations.filter { !isCloudPlaylist(it.playlistId, null) }.map { it.id }
        if (ignoredIds.isNotEmpty()) dao.removeSyncOperations(ignoredIds)

        operations.filter { isCloudPlaylist(it.playlistId, null) }.forEach { operation ->
            runCatching { flushPlaylistDeletes(uid, listOf(operation)) }
                .onFailure { Log.w(TAG, "Gagal flush hapus ${operation.playlistId}: ${it.message}") }
        }
    }

    /** Penanda outbox baru dihapus hanya setelah server mengonfirmasi penghapusan. */
    private suspend fun flushPlaylistDeletes(uid: String, operations: List<SyncOperationEntity>) {
        operations.forEach { operation ->
            val playlistId = safePlaylist(operation.playlistId)
                ?: run {
                    dao.removeSyncOperations(listOf(operation.id))
                    return@forEach
                }
            if (!deleteTrackDocuments(uid, playlistId)) return@forEach
            val deleted = runCatching { playlists(uid).document(playlistId).delete().await() }
            if (deleted.isFailure) {
                Log.w(TAG, "Gagal menghapus dokumen playlist: ${deleted.exceptionOrNull()?.message}")
                return@forEach
            }
            // commit()/.delete() hanya mengonfirmasi tulisan lokal. Tunggu acknowledgment
            // server sebelum membuang penanda; bila offline, penanda tetap untuk startup.
            val acknowledged = withTimeoutOrNull(SERVER_ACK_TIMEOUT_MS) {
                runCatching { firestore.waitForPendingWrites().await() }.isSuccess
            }
            if (acknowledged != true) {
                Log.w(TAG, "Penghapusan $playlistId belum dikonfirmasi server; penanda disimpan")
                return@forEach
            }
            dao.removeSyncOperations(listOf(operation.id))
        }
    }

    // ---- Outbox drain ------------------------------------------------------------------

    private suspend fun drainOutbox(uid: String) {
        val operations = runCatching { dao.syncOperations(OUTBOX_BATCH) }
            .getOrDefault(emptyList())
            // DELETE_PLAYLIST selalu diproses lebih dulu agar UPSERT lama tidak menghidupkan
            // kembali playlist yang baru saja dihapus.
            .sortedByDescending { it.kind == KIND_PLAYLIST_DELETE }
        var batch = firestore.batch()
        var batchIds = mutableListOf<Long>()

        for (operation in operations) {
            if (operation.kind != KIND_PLAYLIST_DELETE &&
                operation.playlistId.isNotBlank() &&
                dao.hasPendingOperation(KIND_PLAYLIST_DELETE, operation.playlistId)
            ) {
                // Antrean lama untuk playlist yang sedang dihapus dibuang, bukan ditulis.
                dao.removeSyncOperations(listOf(operation.id))
                continue
            }

            // Operasi yang butuh query subkoleksi tidak bisa masuk WriteBatch. Flush dulu
            // supaya urutan TRACKS_CLEAR → TRACK_UPSERT tetap terjaga.
            if (!addToBatch(batch, uid, operation)) {
                if (!commitOutboxBatch(batch, batchIds)) return
                batch = firestore.batch()
                batchIds = mutableListOf()

                val applied = runCatching { applyOperation(uid, operation) }
                if (applied.isFailure) {
                    Log.w(TAG, "Operasi ${operation.id} gagal: ${applied.exceptionOrNull()?.message}")
                    return
                }
                runCatching { dao.removeSyncOperations(listOf(operation.id)) }
                continue
            }

            batchIds += operation.id
            if (batchIds.size >= FIRESTORE_BATCH_LIMIT) {
                if (!commitOutboxBatch(batch, batchIds)) return
                batch = firestore.batch()
                batchIds = mutableListOf()
            }
        }
        if (batchIds.isNotEmpty()) commitOutboxBatch(batch, batchIds)
    }

    /** Masukkan operasi yang bisa di-batch. False = harus diproses terpisah. */
    private fun addToBatch(batch: WriteBatch, uid: String, op: SyncOperationEntity): Boolean {
        when (op.kind) {
            KIND_PLAYLIST_UPSERT -> {
                if (!isCloudPlaylist(op.playlistId, null)) return true
                val playlistId = safePlaylist(op.playlistId) ?: return true
                batch.set(
                    playlists(uid).document(playlistId),
                    playlistDocument(op),
                    SetOptions.merge(),
                )
            }

            KIND_TRACK_UPSERT -> {
                if (!isCloudPlaylist(op.playlistId, null)) return true
                val playlistId = safePlaylist(op.playlistId) ?: return true
                val entryId = safeEntry(op.entryId) ?: return true
                batch.set(
                    tracks(uid, playlistId).document(entryId),
                    trackDocument(op),
                    SetOptions.merge(),
                )
            }

            KIND_TRACK_DELETE -> {
                if (!isCloudPlaylist(op.playlistId, null)) return true
                val playlistId = safePlaylist(op.playlistId) ?: return true
                val entryId = safeEntry(op.entryId) ?: return true
                batch.delete(tracks(uid, playlistId).document(entryId))
            }

            KIND_LIKED_UPSERT -> {
                val trackId = safeTrack(op.trackId) ?: return true
                batch.set(
                    likedTracks(uid).document(trackId),
                    mapOf(
                        KEY_TRACK_ID to op.trackId,
                        KEY_VIDEO_URL to op.videoUrl,
                        KEY_SPOTIFY_ID to op.spotifyId,
                        KEY_TITLE to op.title,
                        KEY_ARTIST to op.artist,
                        KEY_THUMBNAIL to op.thumbnailUrl,
                        KEY_COVER_URL to op.coverUrl,
                        KEY_DURATION to op.durationMs,
                        KEY_ADDED_AT to op.addedAt,
                        KEY_UPDATED_AT to op.addedAt,
                    ),
                    SetOptions.merge(),
                )
            }

            KIND_LIKED_DELETE -> {
                val trackId = safeTrack(op.trackId) ?: return true
                batch.delete(likedTracks(uid).document(trackId))
            }

            KIND_TRACKS_CLEAR, KIND_PLAYLIST_DELETE -> return false

            else -> return false
        }
        return true
    }

    private suspend fun commitWriteBatch(batch: WriteBatch): Boolean {
        val committed = runCatching { batch.commit().await() }
        if (committed.isFailure) {
            Log.w(TAG, "Batch Firestore gagal: ${committed.exceptionOrNull()?.message}")
        }
        return committed.isSuccess
    }

    private suspend fun commitOutboxBatch(
        batch: WriteBatch,
        ids: List<Long>,
    ): Boolean {
        if (ids.isEmpty()) return true
        if (!commitWriteBatch(batch)) return false
        runCatching { dao.removeSyncOperations(ids) }
        return true
    }

    private suspend fun applyOperation(uid: String, op: SyncOperationEntity) {
        when (op.kind) {
            KIND_PLAYLIST_UPSERT -> {
                if (!isCloudPlaylist(op.playlistId, null)) return
                val playlistId = safePlaylist(op.playlistId) ?: return
                playlists(uid).document(playlistId)
                    .set(playlistDocument(op), SetOptions.merge())
                    .await()
            }

            KIND_PLAYLIST_DELETE -> {
                if (!isCloudPlaylist(op.playlistId, null)) return
                flushPlaylistDeletes(uid, listOf(op))
            }

            KIND_TRACK_UPSERT -> {
                if (!isCloudPlaylist(op.playlistId, null)) return
                val playlistId = safePlaylist(op.playlistId) ?: return
                val entryId = safeEntry(op.entryId) ?: return
                tracks(uid, playlistId).document(entryId)
                    .set(trackDocument(op), SetOptions.merge())
                    .await()
            }

            KIND_TRACK_DELETE -> {
                if (!isCloudPlaylist(op.playlistId, null)) return
                val playlistId = safePlaylist(op.playlistId) ?: return
                val entryId = safeEntry(op.entryId) ?: return
                tracks(uid, playlistId).document(entryId).delete().await()
            }

            KIND_TRACKS_CLEAR -> {
                if (!isCloudPlaylist(op.playlistId, null)) return
                val playlistId = safePlaylist(op.playlistId) ?: return
                deleteTrackDocuments(uid, playlistId)
            }

            KIND_LIKED_UPSERT -> {
                val trackId = safeTrack(op.trackId) ?: return
                likedTracks(uid).document(trackId)
                    .set(
                        mapOf(
                            KEY_TRACK_ID to op.trackId,
                            KEY_VIDEO_URL to op.videoUrl,
                            KEY_SPOTIFY_ID to op.spotifyId,
                            KEY_TITLE to op.title,
                            KEY_ARTIST to op.artist,
                            KEY_THUMBNAIL to op.thumbnailUrl,
                            KEY_COVER_URL to op.coverUrl,
                            KEY_DURATION to op.durationMs,
                            KEY_ADDED_AT to op.addedAt,
                            KEY_UPDATED_AT to op.addedAt,
                        ),
                        SetOptions.merge(),
                    ).await()
            }

            KIND_LIKED_DELETE -> {
                val trackId = safeTrack(op.trackId) ?: return
                likedTracks(uid).document(trackId).delete().await()
            }
        }
    }

    // ---- Upload koleksi tamu saat login ------------------------------------------------

    private suspend fun uploadLocalLibrary(uid: String) {
        // Hanya playlist hasil Spotify yang pernah naik ke cloud. Playlist lokal tidak
        // pernah dikirim, dan penanda DELETE yang tertunda tidak boleh ditimpa.
        val playlists = runCatching { dao.observePlaylists().first() }
            .getOrDefault(emptyList())
            .filter { isCloudPlaylist(it.id, it.source) }
            .filterNot { dao.hasPendingOperation(KIND_PLAYLIST_DELETE, it.id) }
        val liked = runCatching { dao.observeLiked().first() }.getOrDefault(emptyList())

        playlists.forEach { playlist ->
            val playlistId = safePlaylist(playlist.id) ?: return@forEach
            runCatching { writePlaylistIfNewer(uid, playlist) }
                .onFailure { Log.w(TAG, "Gagal upload playlist ${playlist.id}: ${it.message}") }

            val tracks = runCatching { dao.trackEntries(playlist.id) }.getOrDefault(emptyList())
            val remoteEntryIds = mutableSetOf<String>()
            tracks.forEach { track ->
                val entryId = safeEntry(track.entryId) ?: return@forEach
                remoteEntryIds += entryId
                runCatching { writeTrackIfNewer(uid, playlistId, track) }
                    .onFailure { Log.w(TAG, "Gagal upload entri ${track.entryId}: ${it.message}") }
            }
            runCatching { deleteMissing(tracks(uid, playlistId), remoteEntryIds) }
        }
        // Tidak ada deleteMissing pada level playlist: cloud bukan sumber kebenaran untuk
        // playlist perangkat lain, dan penghapusan sengaja ditangani penanda DELETE.

        val remoteLikedIds = mutableSetOf<String>()
        liked.forEach { song ->
            val trackId = safeTrack(song.id) ?: return@forEach
            remoteLikedIds += trackId
            runCatching { writeLikedIfNewer(uid, trackId, song) }
                .onFailure { Log.w(TAG, "Gagal upload lagu suka ${song.id}: ${it.message}") }
        }
        runCatching { deleteMissing(likedTracks(uid), remoteLikedIds) }
    }

    private suspend fun writePlaylistIfNewer(uid: String, playlist: LocalPlaylistEntity) {
        val ref = playlists(uid).document(safePlaylist(playlist.id) ?: return)
        val remote = runCatching { ref.get().await() }.getOrNull()
        if (remote?.exists() == true && remote.getLong(KEY_UPDATED_AT) ?: 0L > playlist.updatedAt) return
        ref.set(
            mapOf(
                KEY_ID to playlist.id,
                KEY_NAME to playlist.name,
                KEY_DESCRIPTION to playlist.description,
                KEY_COVER_URL to playlist.coverUrl,
                KEY_TRACK_COUNT to playlist.trackCount,
                KEY_TOTAL_DURATION to playlist.totalDurationMs,
                KEY_CREATED_AT to playlist.createdAt,
                KEY_UPDATED_AT to playlist.updatedAt,
                KEY_IS_PINNED to playlist.isPinned,
                KEY_SOURCE to playlist.source,
            ),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun writeTrackIfNewer(uid: String, playlistId: String, track: PlaylistTrackEntity) {
        val entryId = safeEntry(track.entryId) ?: return
        val ref = tracks(uid, playlistId).document(entryId)
        val remote = runCatching { ref.get().await() }.getOrNull()
        if (remote?.exists() == true && remote.getLong(KEY_UPDATED_AT) ?: 0L > track.addedAt) return
        ref.set(
            mapOf(
                KEY_ENTRY_ID to track.entryId,
                KEY_TRACK_ID to track.spotifyId.ifBlank { track.videoUrl.ifBlank { track.entryId } },
                KEY_VIDEO_URL to track.videoUrl,
                KEY_SPOTIFY_ID to track.spotifyId,
                KEY_TITLE to track.title,
                KEY_ARTIST to track.artist,
                KEY_THUMBNAIL to track.thumbnailUrl,
                KEY_COVER_URL to track.coverUrl,
                KEY_DURATION to track.durationMs,
                KEY_ADDED_AT to track.addedAt,
                KEY_UPDATED_AT to track.addedAt,
                KEY_ORDER_INDEX to track.position,
            ),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun writeLikedIfNewer(uid: String, trackId: String, song: LikedSongEntity) {
        val ref = likedTracks(uid).document(trackId)
        val remote = runCatching { ref.get().await() }.getOrNull()
        if (remote?.exists() == true && remote.getLong(KEY_UPDATED_AT) ?: 0L > song.addedAt) return
        ref.set(
            mapOf(
                KEY_TRACK_ID to song.id,
                KEY_VIDEO_URL to song.videoUrl,
                KEY_SPOTIFY_ID to song.spotifyId,
                KEY_TITLE to song.title,
                KEY_ARTIST to song.artist,
                KEY_THUMBNAIL to song.thumbnailUrl,
                KEY_COVER_URL to song.coverUrl,
                KEY_DURATION to song.durationMs,
                KEY_ADDED_AT to song.addedAt,
                KEY_UPDATED_AT to song.addedAt,
            ),
            SetOptions.merge(),
        ).await()
    }

    // ---- Snapshot listener (two-way) ---------------------------------------------------

    private fun startListeners(uid: String) {
        if (listenersPaused) return
        stopListeners()
        // Seluruh pendaftaran dibungkus runCatching: Firebase belum siap / aturan ditolak
        // tidak boleh membuat aplikasi force close.
        runCatching {
            playlistsListener = playlists(uid).addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Listener playlist gagal: ${error?.message}")
                    return@addSnapshotListener
                }
                scope.launch {
                    runCatching {
                        applyRemotePlaylists(snapshot.documents, snapshot.documentChanges)
                    }.onFailure { Log.w(TAG, "Gagal memakai snapshot playlist: ${it.message}") }
                }
            }
        }.onFailure { Log.w(TAG, "Gagal memasang listener playlist: ${it.message}") }

        runCatching {
            likedListener = likedTracks(uid).addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Listener lagu suka gagal: ${error?.message}")
                    return@addSnapshotListener
                }
                scope.launch {
                    runCatching { applyRemoteLiked(snapshot.documents) }
                        .onFailure { Log.w(TAG, "Gagal memakai snapshot lagu suka: ${it.message}") }
                }
            }
        }.onFailure { Log.w(TAG, "Gagal memasang listener lagu suka: ${it.message}") }

        // Dokumen users/{uid} hanya boleh memulihkan sesi Spotify bila logout manual
        // tidak pernah terjadi (atau sesi lokal lebih baru dari penanda logout cloud).
        runCatching {
            userDocListener = users(uid).addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Listener dokumen pengguna gagal: ${error?.message}")
                    return@addSnapshotListener
                }
                scope.launch {
                    runCatching { applyRemoteSpotifySession(snapshot) }
                        .onFailure { Log.w(TAG, "Gagal memakai sesi Spotify cloud: ${it.message}") }
                }
            }
        }.onFailure { Log.w(TAG, "Gagal memasang listener dokumen pengguna: ${it.message}") }
    }

    /**
     * Jangan pernah menimpa sesi lokal setelah pengguna menekan "Keluar dari Spotify".
     * Penanda `spotifyLoggedOutAt` juga melindungi sesi pada perangkat yang sedang
     * offline saat logout dilakukan di perangkat lain.
     */
    private suspend fun applyRemoteSpotifySession(snapshot: DocumentSnapshot) {
        if (spotify.isExplicitlyLoggedOut) return

        val remoteSpDc = snapshot.getString(KEY_SPOTIFY_SP_DC)?.takeIf { it.isNotBlank() } ?: return
        val localSpDc = runCatching { spotify.cookie.firstOrNull() }
            .getOrNull()?.takeIf { it.isNotBlank() }
        val localConnectedAt = runCatching { spotify.sessionConnectedAt.firstOrNull() }.getOrNull() ?: 0L
        val localLoggedOutAt = runCatching { spotify.loggedOutAt.firstOrNull() }.getOrNull() ?: 0L
        val remoteLoggedOutAt = snapshot.getTimestamp(KEY_SPOTIFY_LOGGED_OUT_AT)?.toDate()?.time ?: 0L

        // Logout manual lokal selalu menang sampai sesi baru ditulis (penanda ini dibuang
        // oleh saveCookie). Ini juga menutup kasus Firestore offline saat logout.
        if (localLoggedOutAt > 0L) return
        if (remoteLoggedOutAt > localConnectedAt) return
        if (localSpDc != null) return

        // Restore diizinkan: sesi lokal memang kosong dan logout cloud bukan yang terbaru.
        spotify.markConnected()
        val connectedAt = snapshot.getLong(KEY_SPOTIFY_CONNECTED_AT) ?: System.currentTimeMillis()
        spotify.saveCookie(remoteSpDc, connectedAt)
        val name = snapshot.getString(KEY_SPOTIFY_DISPLAY_NAME)
        if (!name.isNullOrBlank()) {
            spotify.saveProfile(
                displayName = name,
                avatarUrl = snapshot.getString(KEY_SPOTIFY_AVATAR_URL).orEmpty(),
                userId = snapshot.getString(KEY_SPOTIFY_USER_ID).orEmpty(),
            )
        }
    }

    private fun attachTrackListeners(uid: String, playlistIds: Set<String>) {
        val activeIds = playlistIds.filter { isCloudPlaylist(it, null) }.toSet()
        val stale = trackListeners.keys - activeIds
        stale.forEach { id -> trackListeners.remove(id)?.remove() }

        activeIds.forEach { playlistId ->
            if (trackListeners.containsKey(playlistId)) return@forEach
            val safePlaylistId = safePlaylist(playlistId) ?: return@forEach
            runCatching {
                trackListeners[playlistId] = tracks(uid, safePlaylistId)
                    .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                        if (error != null || snapshot == null) {
                            Log.w(TAG, "Listener entri gagal: ${error?.message}")
                            return@addSnapshotListener
                        }
                        scope.launch {
                            runCatching { applyRemoteTracks(playlistId, snapshot.documents) }
                                .onFailure { Log.w(TAG, "Gagal memakai snapshot entri: ${it.message}") }
                        }
                    }
            }.onFailure { Log.w(TAG, "Gagal memasang listener entri: ${it.message}") }
        }
    }

    private suspend fun applyRemotePlaylists(
        documents: List<DocumentSnapshot>,
        changes: List<DocumentChange>,
    ) {
        val removedIds = mutableSetOf<String>()
        for (change in changes) {
            if (change.type != DocumentChange.Type.REMOVED) continue
            val document = change.document
            val id = document.getString(KEY_ID)?.takeIf { it.isNotBlank() } ?: document.id
            if (!isCloudPlaylist(id, document.getString(KEY_SOURCE))) continue
            if (dao.hasPendingOperation(KIND_PLAYLIST_DELETE, id)) {
                trackListeners.remove(id)?.remove()
                continue
            }
            removedIds += id
        }

        // REMOVED dari perangkat lain: hapus dari Room dan lepas listener tracks-nya.
        removedIds.forEach { removePlaylistFromRoom(it) }

        // Gunakan seluruh dokumen (bukan hanya yang berubah) agar snapshot inkremental
        // tidak mengira playlist yang tidak berubah sebagai playlist yang dihapus.
        val remote = documents.mapNotNull { it.toPlaylistEntity() }
            .filter { isCloudPlaylist(it.id, it.source) }
            .filterNot { it.id in removedIds }
            .filterNot { dao.hasPendingOperation(KIND_PLAYLIST_DELETE, it.id) }
        val remoteIds = remote.map { it.id }.toSet()
        val local = runCatching { dao.observePlaylists().first() }.getOrDefault(emptyList())

        // Sisa dokumen lokal dari sesi uji tidak pernah ditarik kembali. Sebaliknya,
        // playlist lokal yang memang hanya ada di Room juga tidak boleh dihapus cloud.
        local.filter { playlist ->
            isCloudPlaylist(playlist.id, playlist.source) &&
                playlist.id !in remoteIds &&
                playlist.id !in removedIds &&
                !dao.hasPendingOperation(KIND_PLAYLIST_DELETE, playlist.id) &&
                dao.syncOperationsForPlaylist(playlist.id).isEmpty()
        }.forEach { removePlaylistFromRoom(it.id) }

        remote.forEach { playlist ->
            val existing = local.firstOrNull { it.id == playlist.id }
            if (existing == null || playlist.updatedAt >= existing.updatedAt) {
                runCatching { dao.upsertPlaylist(playlist) }
            }
        }

        val listenerIds = remoteIds.filterNot { dao.hasPendingOperation(KIND_PLAYLIST_DELETE, it) }.toSet()
        currentUid?.let { attachTrackListeners(it, listenerIds) }
    }

    private suspend fun removePlaylistFromRoom(playlistId: String) {
        runCatching { dao.deletePlaylistCascade(playlistId) }
            .onFailure { Log.w(TAG, "Gagal menghapus playlist $playlistId dari Room: ${it.message}") }
        trackListeners.remove(playlistId)?.remove()
    }

    private suspend fun applyRemoteTracks(
        playlistId: String,
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
    ) {
        if (!isCloudPlaylist(playlistId, null)) return
        if (dao.hasPendingOperation(KIND_PLAYLIST_DELETE, playlistId)) {
            trackListeners.remove(playlistId)?.remove()
            return
        }
        val local = dao.playlist(playlistId) ?: return
        val remote = documents.mapNotNull { it.toTrackEntity(playlistId) }.sortedBy { it.position }
        val remoteVersion = remote.maxOfOrNull { it.addedAt } ?: 0L
        // Snapshot kosong adalah penghapusan sengaja dari perangkat lain.
        if (documents.isNotEmpty() && remoteVersion < local.updatedAt) return

        dao.replaceTracks(playlistId, remote)
        dao.updatePlaylistAggregates(
            playlistId = playlistId,
            trackCount = remote.size,
            totalDurationMs = remote.sumOf { it.durationMs },
            updatedAt = maxOf(local.updatedAt, remoteVersion),
        )
    }

    private suspend fun applyRemoteLiked(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val remote = documents.mapNotNull { it.toLikedEntity() }
        val remoteIds = remote.map { it.id }.toSet()
        val local = runCatching { dao.observeLiked().first() }.getOrDefault(emptyList())
        val localById = local.associateBy { it.id }

        local.filter { it.id !in remoteIds }.forEach { stale ->
            runCatching { dao.removeLiked(stale.id) }
        }
        remote.forEach { incoming ->
            val existing = localById[incoming.id]
            if (existing == null || incoming.addedAt >= existing.addedAt) {
                runCatching { dao.upsertLiked(incoming) }
            }
        }
    }

    private fun stopListeners() {
        playlistsListener?.remove()
        likedListener?.remove()
        userDocListener?.remove()
        trackListeners.values.forEach { it.remove() }
        playlistsListener = null
        likedListener = null
        userDocListener = null
        trackListeners.clear()
    }

    // ---- Mapping Firestore -------------------------------------------------------------

    private fun com.google.firebase.firestore.DocumentSnapshot.toPlaylistEntity(): LocalPlaylistEntity? {
        val id = getString(KEY_ID)?.takeIf { it.isNotBlank() } ?: this.id
        if (safePlaylist(id) == null) return null
        return LocalPlaylistEntity(
            id = id,
            name = getString(KEY_NAME)?.takeIf { it.isNotBlank() } ?: "Playlist",
            description = getString(KEY_DESCRIPTION).orEmpty(),
            coverUrl = getString(KEY_COVER_URL).orEmpty(),
            source = getString(KEY_SOURCE)?.takeIf { it.isNotBlank() } ?: "LOCAL",
            isPinned = getBoolean(KEY_IS_PINNED) ?: false,
            trackCount = getLong(KEY_TRACK_COUNT)?.toInt() ?: 0,
            totalDurationMs = getLong(KEY_TOTAL_DURATION) ?: 0L,
            createdAt = getLong(KEY_CREATED_AT) ?: 0L,
            updatedAt = getLong(KEY_UPDATED_AT) ?: getLong(KEY_CREATED_AT) ?: 0L,
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTrackEntity(
        playlistId: String,
    ): PlaylistTrackEntity? {
        val entryId = getString(KEY_ENTRY_ID)?.takeIf { it.isNotBlank() } ?: this.id
        if (safeEntry(entryId) == null) return null
        return PlaylistTrackEntity(
            entryId = entryId,
            playlistId = playlistId,
            position = getLong(KEY_ORDER_INDEX)?.toInt() ?: 0,
            videoUrl = getString(KEY_VIDEO_URL).orEmpty(),
            spotifyId = getString(KEY_SPOTIFY_ID).orEmpty(),
            title = getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Lagu",
            artist = getString(KEY_ARTIST).orEmpty(),
            thumbnailUrl = getString(KEY_THUMBNAIL).orEmpty(),
            coverUrl = getString(KEY_COVER_URL).orEmpty(),
            durationMs = getLong(KEY_DURATION) ?: 0L,
            addedAt = getLong(KEY_UPDATED_AT) ?: getLong(KEY_ADDED_AT) ?: 0L,
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toLikedEntity(): LikedSongEntity? {
        val trackId = getString(KEY_TRACK_ID)?.takeIf { it.isNotBlank() } ?: this.id
        if (safeTrack(trackId) == null) return null
        return LikedSongEntity(
            id = trackId,
            videoUrl = getString(KEY_VIDEO_URL).orEmpty(),
            spotifyId = getString(KEY_SPOTIFY_ID).orEmpty(),
            title = getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Lagu",
            artist = getString(KEY_ARTIST).orEmpty(),
            thumbnailUrl = getString(KEY_THUMBNAIL).orEmpty(),
            coverUrl = getString(KEY_COVER_URL).orEmpty(),
            durationMs = getLong(KEY_DURATION) ?: 0L,
            addedAt = getLong(KEY_UPDATED_AT) ?: getLong(KEY_ADDED_AT) ?: 0L,
        )
    }

    private fun playlistDocument(playlist: LocalPlaylistEntity): Map<String, Any> = mapOf(
        KEY_ID to playlist.id,
        KEY_NAME to playlist.name,
        KEY_DESCRIPTION to playlist.description,
        KEY_COVER_URL to playlist.coverUrl,
        KEY_TRACK_COUNT to playlist.trackCount,
        KEY_TOTAL_DURATION to playlist.totalDurationMs,
        KEY_CREATED_AT to playlist.createdAt,
        KEY_UPDATED_AT to playlist.updatedAt,
        KEY_IS_PINNED to playlist.isPinned,
        KEY_SOURCE to playlist.source,
    )

    private fun playlistDocument(op: SyncOperationEntity): Map<String, Any> = mapOf(
        KEY_ID to op.playlistId,
        KEY_NAME to op.name,
        KEY_DESCRIPTION to op.description,
        KEY_COVER_URL to op.coverUrl,
        KEY_TRACK_COUNT to op.trackCount,
        KEY_TOTAL_DURATION to op.totalDurationMs,
        KEY_CREATED_AT to op.addedAt,
        KEY_UPDATED_AT to op.createdAt,
        KEY_IS_PINNED to op.isPinned,
        KEY_SOURCE to if (isCloudPlaylist(op.playlistId, null)) SOURCE_SPOTIFY_SYNC else "LOCAL",
    )

    private fun trackDocument(track: PlaylistTrackEntity): Map<String, Any> = mapOf(
        KEY_ENTRY_ID to track.entryId,
        KEY_TRACK_ID to track.spotifyId.ifBlank { track.videoUrl.ifBlank { track.entryId } },
        KEY_VIDEO_URL to track.videoUrl,
        KEY_SPOTIFY_ID to track.spotifyId,
        KEY_TITLE to track.title,
        KEY_ARTIST to track.artist,
        KEY_THUMBNAIL to track.thumbnailUrl,
        KEY_COVER_URL to track.coverUrl,
        KEY_DURATION to track.durationMs,
        KEY_ADDED_AT to track.addedAt,
        KEY_UPDATED_AT to track.addedAt,
        KEY_ORDER_INDEX to track.position,
    )

    private fun trackDocument(op: SyncOperationEntity): Map<String, Any> = mapOf(
        KEY_ENTRY_ID to op.entryId,
        KEY_TRACK_ID to op.trackId,
        KEY_VIDEO_URL to op.videoUrl,
        KEY_SPOTIFY_ID to op.spotifyId,
        KEY_TITLE to op.title,
        KEY_ARTIST to op.artist,
        KEY_THUMBNAIL to op.thumbnailUrl,
        KEY_COVER_URL to op.coverUrl,
        KEY_DURATION to op.durationMs,
        KEY_ADDED_AT to op.addedAt,
        KEY_UPDATED_AT to op.createdAt,
        KEY_ORDER_INDEX to op.orderIndex,
    )

    // ---- Helpers ------------------------------------------------------------------------

    private fun users(uid: String) = firestore.collection(COLLECTION_USERS).document(uid)

    private fun playlists(uid: String) = users(uid).collection(COLLECTION_PLAYLISTS)

    private fun tracks(uid: String, playlistId: String) =
        playlists(uid).document(playlistId).collection(COLLECTION_TRACKS)

    private fun likedTracks(uid: String) = users(uid).collection(COLLECTION_LIKED)

    private fun safeUser(raw: String?) = runCatching { FirestoreIds.user(raw) }.getOrNull()
    private fun safePlaylist(raw: String?) = runCatching { FirestoreIds.playlist(raw) }.getOrNull()
    private fun safeTrack(raw: String?) = runCatching { FirestoreIds.track(raw) }.getOrNull()
    private fun safeEntry(raw: String?) = runCatching { FirestoreIds.entry(raw) }.getOrNull()

    private suspend fun deleteMissing(
        collection: com.google.firebase.firestore.CollectionReference,
        remoteIds: Set<String>,
    ) {
        val snapshot = runCatching { collection.get().await() }.getOrDefault(null) ?: return
        snapshot.documents.forEach { doc ->
            if (doc.id !in remoteIds) runCatching { doc.reference.delete().await() }
        }
    }

    /** Hapus subcollection tracks per 400 dokumen hingga benar-benar kosong. */
    private suspend fun deleteTrackDocuments(uid: String, playlistId: String): Boolean {
        while (true) {
            val snapshot = runCatching {
                tracks(uid, playlistId).limit(DELETE_BATCH.toLong()).get().await()
            }.getOrNull() ?: return false
            if (snapshot.isEmpty()) return true
            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            if (!commitWriteBatch(batch)) return false
        }
    }

    companion object {
        private const val TAG = "DengerinCloudSync"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_PLAYLISTS = "playlists"
        private const val COLLECTION_TRACKS = "tracks"
        private const val COLLECTION_LIKED = "liked_tracks"

        private const val SOURCE_SPOTIFY_SYNC = "SPOTIFY_SYNC"

        private const val OUTBOX_BATCH = 400
        private const val FIRESTORE_BATCH_LIMIT = 400
        private const val DELETE_BATCH = 400
        private const val SERVER_ACK_TIMEOUT_MS = 10_000L

        const val KIND_PLAYLIST_UPSERT = "playlist_upsert"
        const val KIND_PLAYLIST_DELETE = "playlist_delete"
        const val KIND_TRACK_UPSERT = "track_upsert"
        const val KIND_TRACK_DELETE = "track_delete"
        const val KIND_TRACKS_CLEAR = "tracks_clear"
        const val KIND_LIKED_UPSERT = "liked_upsert"
        const val KIND_LIKED_DELETE = "liked_delete"

        private const val KEY_ID = "id"
        private const val KEY_ENTRY_ID = "entryId"
        private const val KEY_TRACK_ID = "trackId"
        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_COVER_URL = "coverUrl"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_THUMBNAIL = "thumbnailUrl"
        private const val KEY_VIDEO_URL = "videoUrl"
        private const val KEY_SPOTIFY_ID = "spotifyId"
        private const val KEY_DURATION = "durationMs"
        private const val KEY_ADDED_AT = "addedAt"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_ORDER_INDEX = "orderIndex"
        private const val KEY_TRACK_COUNT = "trackCount"
        private const val KEY_TOTAL_DURATION = "totalDurationMs"
        private const val KEY_IS_PINNED = "isPinned"
        private const val KEY_SOURCE = "source"

        private const val KEY_SPOTIFY_SP_DC = "spotifySpDc"
        private const val KEY_SPOTIFY_DISPLAY_NAME = "spotifyDisplayName"
        private const val KEY_SPOTIFY_AVATAR_URL = "spotifyAvatarUrl"
        private const val KEY_SPOTIFY_USER_ID = "spotifyUserId"
        private const val KEY_SPOTIFY_CONNECTED_AT = "spotifyConnectedAt"
        private const val KEY_SPOTIFY_LOGGED_OUT_AT = "spotifyLoggedOutAt"
    }
}
