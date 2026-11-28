package com.dengerin.app.data.repository

import androidx.room.withTransaction
import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.DengerinDatabase
import com.dengerin.app.data.db.LikedSongEntity
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.db.PlayHistoryEntity
import com.dengerin.app.data.db.PlaylistTrackEntity
import com.dengerin.app.data.db.toSong
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.resolver.SongResolver
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyCollection
import com.dengerin.app.data.spotify.SpotifyCollectionKind
import com.dengerin.app.data.spotify.SpotifyFileImport
import com.dengerin.app.data.spotify.SpotifyFileTarget
import com.dengerin.app.data.spotify.SpotifyPublicImportRepository
import com.dengerin.app.data.sync.LibraryCloudSync
import com.dengerin.app.domain.model.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Progres impor koleksi Spotify yang dikirim ke UI secara real-time. */
data class SpotifyImportProgress(
    val completedCollections: Int,
    val totalCollections: Int,
    val currentCollectionName: String,
    val totalTracksImported: Int,
)

/** Ringkasan pasti dari satu kali impor ulang inkremental. */
data class SpotifyImportSummary(
    val addedTracksCount: Int = 0,
    val updatedCollectionsCount: Int = 0,
    val newCollectionsCount: Int = 0,
    val skippedCollectionsCount: Int = 0,
)

/** Keadaan lokal satu koleksi Spotify, dipakai sheet untuk penanda "Sudah diimpor". */
data class SpotifyCollectionImportStatus(
    val isImported: Boolean,
    val localTrackCount: Int,
)

private data class SpotifyCollectionImportOutcome(
    val addedTracks: Int = 0,
    val isNew: Boolean = false,
    val updated: Boolean = false,
    val skipped: Boolean = false,
)

private val SPOTIFY_ID_REGEX = Regex("[A-Za-z0-9]{22}")

/** Kunci stabil untuk "lagu yang sama": URI/ID Spotify dulu, baru judul|artis. */
internal fun trackKey(uri: String, id: String, title: String, artist: String): String {
    val spotifyId = canonicalSpotifyId(uri).ifBlank { canonicalSpotifyId(id) }
    if (spotifyId.isNotBlank()) return "spotify:track:$spotifyId"
    return "${title.trim().lowercase(Locale.ROOT)}|${artist.trim().lowercase(Locale.ROOT)}"
}

private fun canonicalSpotifyId(raw: String): String {
    val value = raw.trim().removePrefix("spotify:track:")
    return value.takeIf { SPOTIFY_ID_REGEX.matches(it) }.orEmpty()
}

/**
 * Satu-satunya penentu playlist yang boleh disentuh cloud. Playlist lokal (LOCAL /
 * SPOTIFY_IMPORT tanpa awalan `spotify_`) sepenuhnya offline di Room.
 */
internal fun isCloudPlaylist(playlistId: String, source: String?): Boolean =
    source == "SPOTIFY_SYNC" || playlistId.startsWith("spotify_")

/** Satu fasad untuk Room + impor publik + resolver, dipakai UI dan player. */
class LibraryRepository(
    private val dao: DengerinDao,
    private val database: DengerinDatabase,
    private val publicImport: SpotifyPublicImportRepository,
    private val accountImport: SpotifyAccountImportRepository,
    private val resolver: SongResolver,
    private val cloud: LibraryCloudSync,
) {
    val history: Flow<List<Song>> = dao.observeHistory().map { items -> items.map { it.toSong() } }
    val frequent: Flow<List<Song>> = dao.observeFrequent().map { items -> items.map { it.toSong() } }
    val liked: Flow<List<Song>> = dao.observeLiked().map { items -> items.map { it.toSong() } }
    val playlists: Flow<List<LocalPlaylistEntity>> = dao.observePlaylists()

    fun isSaved(playlistId: String): Flow<Boolean> = playlists.map { list -> list.any { it.id == playlistId } }

    /**
     * Keadaan koleksi Spotify di Room, dipantau langsung dari DAO. Tidak ada jaringan
     * di sini: sheet hanya membaca bayangan lokal, impor tetap manual lewat tombol.
     * Kuncinya adalah id koleksi Spotify (tanpa awalan `spotify_`) atau id Lagu Disukai.
     */
    val spotifyImportStatuses: Flow<Map<String, SpotifyCollectionImportStatus>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistTracks(),
        dao.observeLiked(),
    ) { localPlaylists, allTracks, likedSongs ->
        val countsByPlaylist = allTracks.groupingBy { it.playlistId }.eachCount()
        val statuses = mutableMapOf<String, SpotifyCollectionImportStatus>()
        localPlaylists
            .filter { it.id.startsWith("spotify_") && isCloudPlaylist(it.id, it.source) }
            .forEach { playlist ->
                statuses[playlist.id.removePrefix("spotify_")] = SpotifyCollectionImportStatus(
                    isImported = true,
                    localTrackCount = countsByPlaylist[playlist.id] ?: 0,
                )
            }
        if (likedSongs.isNotEmpty()) {
            statuses[SpotifyAccountImportRepository.COLLECTION_LIKED] =
                SpotifyCollectionImportStatus(isImported = true, localTrackCount = likedSongs.size)
        }
        statuses
    }

    /** Isi semua playlist di Room, dipakai untuk mengenali artis dari koleksi pengguna. */
    val allPlaylistSongs: Flow<List<Song>> = dao.observeAllPlaylistTracks()
        .map { items -> items.map { it.toSong() } }

    fun playlistTracks(playlistId: String): Flow<List<Song>> = when (playlistId) {
        PLAYLIST_LIKED -> liked
        PLAYLIST_HISTORY -> history
        else -> playlistTrackEntries(playlistId).map { items -> items.map { it.toSong() } }
    }

    /** Entri asli (dengan entryId) dipakai menu per lagu pada playlist nyata. */
    fun playlistTrackEntries(playlistId: String): Flow<List<PlaylistTrackEntity>> =
        dao.observeTracks(playlistId)

    suspend fun playlistInfo(playlistId: String): LocalPlaylistEntity? = withContext(Dispatchers.IO) {
        when (playlistId) {
            PLAYLIST_LIKED -> LocalPlaylistEntity(PLAYLIST_LIKED, "Lagu Disukai", source = "LOCAL")
            PLAYLIST_HISTORY -> LocalPlaylistEntity(PLAYLIST_HISTORY, "Riwayat Putar", source = "LOCAL")
            else -> dao.playlist(playlistId)
        }
    }

    suspend fun recordPlay(song: Song) = withContext(Dispatchers.IO) {
        runCatching {
            val updated = dao.bumpHistory(
                videoUrl = song.url,
                spotifyId = song.spotifyId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationSeconds = song.durationMs / 1000L,
                playedAt = System.currentTimeMillis(),
            )
            if (updated == 0) {
                dao.upsertHistory(
                    PlayHistoryEntity(
                        videoUrl = song.url,
                        spotifyId = song.spotifyId,
                        title = song.title,
                        artist = song.artist,
                        thumbnailUrl = song.thumbnailUrl,
                        durationSeconds = song.durationMs / 1000L,
                    ),
                )
            }
        }
    }

    suspend fun isLiked(songId: String): Boolean = withContext(Dispatchers.IO) { dao.isLiked(songId) }

    suspend fun toggleLike(song: Song): Boolean = withContext(Dispatchers.IO) {
        val id = song.spotifyId.ifBlank { song.id }
        if (dao.isLiked(id)) {
            dao.removeLiked(id)
            cloud.enqueueLikedDelete(id)
            false
        } else {
            val entity = LikedSongEntity(
                id = id,
                videoUrl = song.url,
                spotifyId = song.spotifyId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                coverUrl = song.coverUrl,
                durationMs = song.durationMs,
            )
            dao.upsertLiked(entity)
            cloud.enqueueLikedUpsert(entity)
            true
        }
    }

    suspend fun playlistSongCount(playlistId: String): Int = withContext(Dispatchers.IO) {
        dao.playlistTrackCount(playlistId)
    }

    suspend fun isSongInPlaylist(playlistId: String, song: Song): Boolean = withContext(Dispatchers.IO) {
        dao.playlistContainsSong(playlistId, song.spotifyId, song.url)
    }

    /**
     * Tambah atau hapus lagu dari playlist nyata. Mengembalikan true bila lagu
     * sekarang ada di playlist (ditambahkan), false bila baru dihapus.
     */
    suspend fun toggleSongInPlaylist(playlistId: String, song: Song): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.trackEntries(playlistId).firstOrNull { it.sameContent(song) }
        if (existing != null) {
            dao.deleteTrackEntry(playlistId, existing.entryId)
            if (isCloudPlaylistId(playlistId)) {
                cloud.enqueueTrackDelete(playlistId, existing.entryId)
            }
            refreshPlaylistAggregates(playlistId)
            flushCloudPlaylist(playlistId)
            false
        } else {
            appendTrack(playlistId, song)
            true
        }
    }

    /**
     * Tambah lagu (boleh duplikat) ke playlist. Setiap entri memakai entryId unik supaya
     * dua lagu identik tidak saling menimpa di Room maupun di Firestore.
     */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): String = withContext(Dispatchers.IO) {
        appendTrack(playlistId, song)
    }

    /** Hapus tepat satu entri; aman untuk playlist yang berisi lagu duplikat. */
    suspend fun removeTrackEntry(playlistId: String, entryId: String) = withContext(Dispatchers.IO) {
        if (dao.trackEntry(playlistId, entryId) == null) return@withContext
        dao.deleteTrackEntry(playlistId, entryId)
        if (isCloudPlaylistId(playlistId)) {
            cloud.enqueueTrackDelete(playlistId, entryId)
        }
        refreshPlaylistAggregates(playlistId)
        flushCloudPlaylist(playlistId)
    }

    private suspend fun appendTrack(playlistId: String, song: Song): String {
        val entry = PlaylistTrackEntity(
            playlistId = playlistId,
            position = dao.playlistTrackCount(playlistId),
            videoUrl = song.url,
            spotifyId = song.spotifyId,
            title = song.title,
            artist = song.artist,
            thumbnailUrl = song.thumbnailUrl,
            coverUrl = song.coverUrl,
            durationMs = song.durationMs,
        )
        dao.insertTrack(entry)
        if (isCloudPlaylistId(playlistId)) {
            cloud.enqueueTrackUpsert(entry)
        }
        refreshPlaylistAggregates(playlistId)
        flushCloudPlaylist(playlistId)
        return entry.entryId
    }

    /** Playlist lokal tidak pernah masuk outbox, apalagi Firestore. */
    private suspend fun isCloudPlaylistId(playlistId: String): Boolean =
        isCloudPlaylist(playlistId, dao.playlist(playlistId)?.source)

    /** Kirim keadaan Room terkini ke Firestore segera setelah playlist Spotify berubah. */
    private suspend fun flushCloudPlaylist(playlistId: String) {
        if (!isCloudPlaylistId(playlistId)) return
        val playlist = dao.playlist(playlistId) ?: return
        cloud.uploadCloudPlaylistsNow(listOf(playlist), mapOf(playlistId to dao.trackEntries(playlistId)))
    }

    private suspend fun refreshPlaylistAggregates(playlistId: String) {
        val tracks = dao.trackEntries(playlistId)
        dao.updatePlaylistAggregates(
            playlistId = playlistId,
            trackCount = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs },
            updatedAt = System.currentTimeMillis(),
        )
        val playlist = dao.playlist(playlistId) ?: return
        if (isCloudPlaylist(playlist.id, playlist.source)) {
            cloud.enqueuePlaylistUpsert(playlist)
        }
    }

    private fun PlaylistTrackEntity.sameContent(song: Song): Boolean {
        val songId = song.spotifyId.ifBlank { song.url }
        val entryIdValue = spotifyId.ifBlank { videoUrl }
        return songId.isNotBlank() && songId == entryIdValue
    }

    /** Buat playlist baru dan langsung isi dengan lagu yang dipilih (tanpa duplikasi). */
    suspend fun createPlaylistWithSong(name: String, song: Song): String = withContext(Dispatchers.IO) {
        val id = createPlaylist(name)
        appendTrack(id, song)
        id
    }

    suspend fun importPlaylist(
        link: String,
        onProgress: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val imported = publicImport.import(link, onProgress).getOrThrow()
            val playlist = LocalPlaylistEntity(
                id = imported.id,
                name = imported.name,
                coverUrl = imported.coverUrl,
                source = "SPOTIFY_IMPORT",
            )
            dao.upsertPlaylist(playlist)
            val cloudPlaylist = isCloudPlaylist(playlist.id, playlist.source)
            if (cloudPlaylist) {
                cloud.enqueuePlaylistUpsert(playlist)
                cloud.enqueueTracksClear(imported.id)
            }
            dao.clearTracks(imported.id)
            val entries = imported.tracks.toEntities(imported.id)
            dao.insertTracks(entries)
            if (cloudPlaylist) cloud.enqueueTracksUpsertBulk(entries)
            refreshPlaylistAggregates(imported.id)
            flushCloudPlaylist(imported.id)
            // Impor murni metadata. Tidak ada SongResolver/NewPipe di sini: audio
            // ECONOMY_OPUS baru dicari saat lagu diputar (lazy audio resolve).
            imported.id
        }
    }

    /**
     * Impor ulang inkremental koleksi akun Spotify. Hanya lagu yang belum ada di Room
     * yang ditulis; lagu lama (termasuk cache youtubeVideoId-nya) tidak pernah disentuh.
     * Resolver YouTube/NewPipe tidak dipanggil sama sekali di jalur ini.
     */
    suspend fun importSpotifyCollections(
        collections: List<SpotifyCollection>,
        onProgress: (SpotifyImportProgress) -> Unit = {},
    ): Result<SpotifyImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            if (collections.isEmpty()) return@runCatching SpotifyImportSummary()

            val totalCollections = collections.size
            val completed = AtomicInteger(0)
            val addedTracks = AtomicInteger(0)
            val newCollections = AtomicInteger(0)
            val updatedCollections = AtomicInteger(0)
            val skippedCollections = AtomicInteger(0)
            val pageProgress = mutableMapOf<Int, Int>()

            fun report(index: Int, name: String) {
                val inFlight = synchronized(pageProgress) { pageProgress.values.sum() }
                onProgress(
                    SpotifyImportProgress(
                        completedCollections = completed.get(),
                        totalCollections = totalCollections,
                        currentCollectionName = name,
                        totalTracksImported = addedTracks.get() + inFlight,
                    ),
                )
            }

            report(-1, "Menyiapkan koleksi")
            cloud.beginBatch()
            try {
                coroutineScope {
                    val gate = Semaphore(COLLECTION_CONCURRENCY)
                    collections.mapIndexed { index, collection ->
                        async {
                            gate.withPermit {
                                val label = if (collection.kind == SpotifyCollectionKind.LIKED) {
                                    "Lagu Disukai"
                                } else collection.name
                                try {
                                    report(index, label)
                                    val tracks = accountImport.fetchTracks(collection) { done, _ ->
                                        synchronized(pageProgress) { pageProgress[index] = done }
                                        report(index, label)
                                    }.getOrThrow()

                                    val outcome = when (collection.kind) {
                                        SpotifyCollectionKind.LIKED -> importMissingLikedTracks(tracks)
                                        SpotifyCollectionKind.PLAYLIST,
                                        SpotifyCollectionKind.ALBUM,
                                        -> importMissingPlaylistTracks(
                                            playlistId = "spotify_${collection.id}",
                                            collection = collection,
                                            tracks = tracks,
                                        )
                                    }
                                    addedTracks.addAndGet(outcome.addedTracks)
                                    if (outcome.isNew) newCollections.incrementAndGet()
                                    if (outcome.updated) updatedCollections.incrementAndGet()
                                    if (outcome.skipped) skippedCollections.incrementAndGet()
                                    report(index, label)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    // Satu koleksi gagal tidak membatalkan koleksi lain.
                                    val reason = error.message?.takeIf { it.isNotBlank() }
                                        ?: "tidak diketahui"
                                    report(index, "$label · gagal: $reason")
                                } finally {
                                    synchronized(pageProgress) { pageProgress.remove(index) }
                                    completed.incrementAndGet()
                                    report(index, label)
                                }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                cloud.endBatch()
            }
            SpotifyImportSummary(
                addedTracksCount = addedTracks.get(),
                updatedCollectionsCount = updatedCollections.get(),
                newCollectionsCount = newCollections.get(),
                skippedCollectionsCount = skippedCollections.get(),
            )
        }
    }

    /** Playlist baru: header + seluruh lagu ditulis dalam satu transaksi Room. */
    private suspend fun importMissingPlaylistTracks(
        playlistId: String,
        collection: SpotifyCollection,
        tracks: List<SpotifyTrack>,
    ): SpotifyCollectionImportOutcome {
        val desiredName = if (collection.kind == SpotifyCollectionKind.ALBUM) {
            "Album · ${collection.name}"
        } else collection.name
        val existing = dao.playlist(playlistId)

        if (existing == null) {
            // Impor manual ini menghidupkan kembali playlist yang pernah dihapus permanen.
            cloud.clearPendingPlaylistDelete(playlistId)
            val entries = tracks.toEntities(playlistId)
            val now = System.currentTimeMillis()
            val header = LocalPlaylistEntity(
                id = playlistId,
                name = desiredName,
                coverUrl = collection.coverUrl,
                source = "SPOTIFY_SYNC",
                trackCount = entries.size,
                totalDurationMs = entries.sumOf { it.durationMs },
                createdAt = now,
                updatedAt = now,
            )
            database.withTransaction {
                dao.upsertPlaylist(header)
                if (entries.isNotEmpty()) dao.insertTracks(entries)
            }
            // Header + lagu langsung ke Firestore via WriteBatch (maks. 400 per batch).
            cloud.uploadCloudPlaylistsNow(listOf(header), mapOf(playlistId to entries))
            return SpotifyCollectionImportOutcome(addedTracks = entries.size, isNew = true)
        }

        val existingKeys = dao.playlistTrackKeys(playlistId)
            .asSequence()
            .map { trackKey("", it.spotifyId, it.title, it.artist) }
            .toSet()
        val seen = mutableSetOf<String>()
        val missing = tracks.filter { track ->
            val key = trackKey(track.uri, track.id, track.title, track.artist)
            key !in existingKeys && seen.add(key)
        }
        val headerChanged = existing.name != desiredName || existing.coverUrl != collection.coverUrl

        if (missing.isEmpty()) {
            if (headerChanged) {
                val updated = existing.copy(
                    name = desiredName,
                    coverUrl = collection.coverUrl,
                    updatedAt = System.currentTimeMillis(),
                )
                dao.upsertPlaylist(updated)
                cloud.enqueuePlaylistUpsert(updated)
            }
            // Tidak ada lagu baru: tidak ada replaceTracks, tidak ada aggregate write,
            // dan tidak ada outbox bila nama/cover juga sama (0 write).
            return SpotifyCollectionImportOutcome(updated = headerChanged, skipped = true)
        }

        val currentTracks = dao.trackEntries(playlistId)
        val nextPosition = (currentTracks.maxOfOrNull { it.position } ?: -1) + 1
        val entries = missing.toEntities(playlistId, startPosition = nextPosition)
        val updated = existing.copy(
            name = desiredName,
            coverUrl = collection.coverUrl,
            trackCount = currentTracks.size + entries.size,
            totalDurationMs = currentTracks.sumOf { it.durationMs } + entries.sumOf { it.durationMs },
            updatedAt = System.currentTimeMillis(),
        )
        // Hanya sisipan. Entri lama tidak dihapus/ditimpa, jadi videoUrl hasil resolver
        // dan metadata lokal tetap utuh.
        database.withTransaction {
            dao.insertTracks(entries)
            dao.upsertPlaylist(updated)
        }
        cloud.enqueuePlaylistUpsert(updated)
        cloud.enqueueTracksUpsertBulk(entries)
        return SpotifyCollectionImportOutcome(addedTracks = entries.size, updated = true)
    }

    private suspend fun importMissingLikedTracks(
        tracks: List<SpotifyTrack>,
    ): SpotifyCollectionImportOutcome {
        val existingKeys = dao.likedTrackKeys()
            .asSequence()
            .map { trackKey("", it.spotifyId, it.title, it.artist) }
            .toSet()
        val seen = mutableSetOf<String>()
        val missing = tracks.filter { track ->
            val key = trackKey(track.uri, track.id, track.title, track.artist)
            key !in existingKeys && seen.add(key)
        }
        if (missing.isEmpty()) return SpotifyCollectionImportOutcome(skipped = true)

        val entries = missing.map { track ->
            LikedSongEntity(
                id = track.id,
                spotifyId = track.id,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                coverUrl = track.coverUrl,
                durationMs = track.durationMs,
                syncedFromSpotify = true,
            )
        }
        database.withTransaction { dao.upsertLikedAll(entries) }
        cloud.enqueueLikedUpsertBulk(entries)
        return SpotifyCollectionImportOutcome(addedTracks = entries.size, updated = true)
    }

    /**
     * Impor dari file CSV/JSON ekspor Spotify. Metadata Room v5 + outbox langsung,
     * tanpa login, tanpa jaringan, dan tanpa resolver YouTube/NewPipe.
     */
    suspend fun importSpotifyFile(
        import: SpotifyFileImport,
        onProgress: (label: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tracks = import.tracks
            if (tracks.isEmpty()) error("Tidak ada lagu yang bisa dibaca dari file ini.")
            val total = tracks.size
            when (import.target) {
                SpotifyFileTarget.LIKED -> {
                    val liked = tracks.mapIndexed { index, track ->
                        onProgress(import.name, index + 1, total)
                        LikedSongEntity(
                            id = track.id,
                            spotifyId = track.id,
                            title = track.title,
                            artist = track.artist,
                            thumbnailUrl = track.thumbnailUrl,
                            coverUrl = track.coverUrl,
                            durationMs = track.durationMs,
                            syncedFromSpotify = true,
                        )
                    }
                    dao.upsertLikedAll(liked)
                    liked.forEach { cloud.enqueueLikedUpsert(it) }
                }
                SpotifyFileTarget.PLAYLIST -> {
                    val playlistId = "local_${UUID.randomUUID()}"
                    val coverUrl = tracks.firstNotNullOfOrNull { track ->
                        track.coverUrl.takeIf { it.isNotBlank() }
                    }.orEmpty()
                    val playlist = LocalPlaylistEntity(
                        id = playlistId,
                        name = import.name.ifBlank { "Impor Spotify" },
                        coverUrl = coverUrl,
                        source = "SPOTIFY_IMPORT",
                    )
                    dao.upsertPlaylist(playlist)
                    val cloudPlaylist = isCloudPlaylist(playlist.id, playlist.source)
                    if (cloudPlaylist) {
                        cloud.enqueuePlaylistUpsert(playlist)
                        cloud.enqueueTracksClear(playlistId)
                    }
                    dao.clearTracks(playlistId)
                    val entries = tracks.toEntities(playlistId)
                    dao.insertTracks(entries)
                    if (cloudPlaylist) cloud.enqueueTracksUpsertBulk(entries)
                    entries.forEachIndexed { index, _ -> onProgress(import.name, index + 1, total) }
                    refreshPlaylistAggregates(playlistId)
                    flushCloudPlaylist(playlistId)
                }
            }
            total
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): String = withContext(Dispatchers.IO) {
        val id = "local_${UUID.randomUUID()}"
        val playlist = LocalPlaylistEntity(
            id = id,
            name = name.ifBlank { "Playlist Baru" },
            description = description.trim(),
            source = "LOCAL",
        )
        dao.upsertPlaylist(playlist)
        id
    }

    suspend fun saveRemoteSpotifyPlaylist(
        playlistId: String,
        name: String,
        coverUrl: String,
        tracks: List<com.dengerin.app.data.spotify.SpotifyCatalogTrack>,
    ): String = withContext(Dispatchers.IO) {
        val id = if (playlistId.startsWith("spotify_")) playlistId else "spotify_$playlistId"
        val existing = dao.playlist(id)
        val playlist = LocalPlaylistEntity(
            id = id,
            name = name.ifBlank { existing?.name ?: "Playlist Spotify" },
            coverUrl = coverUrl.ifBlank { existing?.coverUrl.orEmpty() },
            source = "SPOTIFY_SYNC",
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsertPlaylist(playlist)
        cloud.enqueuePlaylistUpsert(playlist)
        cloud.enqueueTracksClear(id)
        dao.clearTracks(id)
        val entries = tracks.mapIndexed { index, track ->
            PlaylistTrackEntity(
                entryId = "trk_${UUID.randomUUID()}",
                playlistId = id,
                position = index,
                videoUrl = track.uri.takeIf { it.startsWith("http") }.orEmpty(),
                spotifyId = track.id,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                coverUrl = track.coverUrl,
                durationMs = track.durationMs,
            )
        }
        if (entries.isNotEmpty()) {
            dao.insertTracks(entries)
            cloud.enqueueTracksUpsertBulk(entries)
        }
        refreshPlaylistAggregates(id)
        flushCloudPlaylist(id)
        id
    }

    suspend fun renamePlaylist(id: String, name: String, description: String) = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext
        dao.renamePlaylist(id, name.trim(), description.trim(), System.currentTimeMillis())
        val cloudPlaylist = isCloudPlaylistId(id)
        if (cloudPlaylist) dao.playlist(id)?.let { cloud.enqueuePlaylistUpsert(it) }
        if (cloudPlaylist) flushCloudPlaylist(id)
    }

    suspend fun setPlaylistPinned(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        dao.setPlaylistPinned(id, isPinned, System.currentTimeMillis())
        val cloudPlaylist = isCloudPlaylistId(id)
        if (cloudPlaylist) dao.playlist(id)?.let { cloud.enqueuePlaylistUpsert(it) }
        if (cloudPlaylist) flushCloudPlaylist(id)
    }

    /**
     * Hapus permanen. Playlist lokal cukup hilang dari Room; playlist Spotify juga
     * membatalkan antrean lama dan langsung menghapus dokumen Firestore beserta tracks.
     */
    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        val cloudPlaylist = isCloudPlaylistId(id)
        database.withTransaction {
            dao.clearTracks(id)
            dao.deletePlaylist(id)
        }
        if (cloudPlaylist) cloud.deletePlaylistNow(id)
    }

    suspend fun resolve(song: Song): Result<Song> = resolver.resolve(song)

    suspend fun resolve(songs: List<Song>): Result<List<Song>> = resolver.resolveAll(songs)

    private fun List<SpotifyTrack>.toEntities(
        playlistId: String,
        startPosition: Int = 0,
    ) = mapIndexed { index, track ->
        PlaylistTrackEntity(
            playlistId = playlistId,
            position = startPosition + index,
            spotifyId = track.id,
            title = track.title,
            artist = track.artist,
            // Tanpa gambar trek asli, jangan pinjam cover playlist.
            thumbnailUrl = track.thumbnailUrl.takeIf { it.isNotBlank() }.orEmpty(),
            coverUrl = track.coverUrl.takeIf { it.isNotBlank() }.orEmpty(),
            durationMs = track.durationMs,
        )
    }

    companion object {
        const val PLAYLIST_LIKED = "__liked__"
        const val PLAYLIST_HISTORY = "__history__"
        private const val COLLECTION_CONCURRENCY = 3
    }
}
