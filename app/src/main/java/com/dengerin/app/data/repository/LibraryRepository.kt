package com.dengerin.app.data.repository

import android.util.Log
import com.dengerin.app.data.db.DengerinDao
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.db.PlayHistoryEntity
import com.dengerin.app.data.db.PlaylistTrackEntity
import com.dengerin.app.data.db.toSong
import com.dengerin.app.data.db.LikedSongEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.resolver.SongResolver
import com.dengerin.app.data.spotify.SpotifyPublicImportRepository
import com.dengerin.app.domain.model.SpotifyTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

/** Satu fasad untuk Room + impor publik + resolver, dipakai UI dan player. */
class LibraryRepository(
    private val dao: DengerinDao,
    private val publicImport: SpotifyPublicImportRepository,
    private val resolver: SongResolver,
) {
    /** Worker sisa lagu hidup di Repository, bukan di ViewModel/Compose. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backgroundPermits = Semaphore(BACKGROUND_CONCURRENCY)

    val history: Flow<List<Song>> = dao.observeHistory().map { items -> items.map { it.toSong() } }
    val frequent: Flow<List<Song>> = dao.observeFrequent().map { items -> items.map { it.toSong() } }
    val liked: Flow<List<Song>> = dao.observeLiked().map { items -> items.map { it.toSong() } }
    val playlists: Flow<List<LocalPlaylistEntity>> = dao.observePlaylists()

    /** Isi semua playlist di Room, dipakai untuk mengenali artis dari koleksi pengguna. */
    val allPlaylistSongs: Flow<List<Song>> = dao.observeAllPlaylistTracks()
        .map { items -> items.map { it.toSong() } }

    fun playlistTracks(playlistId: String): Flow<List<Song>> = when (playlistId) {
        PLAYLIST_LIKED -> liked
        PLAYLIST_HISTORY -> history
        else -> dao.observeTracks(playlistId).map { items -> items.map { it.toSong() } }
    }

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
            false
        } else {
            dao.upsertLiked(
                LikedSongEntity(
                    id = id,
                    videoUrl = song.url,
                    spotifyId = song.spotifyId,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    durationMs = song.durationMs,
                ),
            )
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
        val spotifyId = song.spotifyId
        val videoUrl = song.url
        if (dao.playlistContainsSong(playlistId, spotifyId, videoUrl)) {
            dao.removePlaylistSong(playlistId, spotifyId, videoUrl)
            dao.touchPlaylist(playlistId, System.currentTimeMillis())
            false
        } else {
            dao.insertTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    position = dao.playlistTrackCount(playlistId),
                    videoUrl = videoUrl,
                    spotifyId = spotifyId,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    durationMs = song.durationMs,
                ),
            )
            dao.touchPlaylist(playlistId, System.currentTimeMillis())
            true
        }
    }

    /** Buat playlist baru dan langsung isi dengan lagu yang dipilih (tanpa duplikasi). */
    suspend fun createPlaylistWithSong(name: String, song: Song): String = withContext(Dispatchers.IO) {
        val id = "local_${UUID.randomUUID()}"
        dao.upsertPlaylist(
            LocalPlaylistEntity(
                id = id,
                name = name.ifBlank { "Playlist Baru" },
                source = "LOCAL",
            ),
        )
        dao.insertTrack(
            PlaylistTrackEntity(
                playlistId = id,
                position = 0,
                videoUrl = song.url,
                spotifyId = song.spotifyId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationMs = song.durationMs,
            ),
        )
        id
    }

    suspend fun importPlaylist(
        link: String,
        onProgress: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val imported = publicImport.import(link).getOrThrow()
            dao.upsertPlaylist(
                LocalPlaylistEntity(
                    id = imported.id,
                    name = imported.name,
                    coverUrl = imported.coverUrl,
                    source = "SPOTIFY_IMPORT",
                ),
            )
            dao.clearTracks(imported.id)
            dao.insertTracks(imported.tracks.toEntities(imported.id))

            val unresolved = imported.tracks.map { track ->
                Song(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs,
                    url = "",
                    thumbnailUrl = track.thumbnailUrl,
                    spotifyId = track.id,
                    source = "SPOTIFY",
                )
            }.filter { it.url.isBlank() || it.thumbnailUrl.isBlank() }

            if (unresolved.isNotEmpty()) {
                // Tahap 1: 10 lagu pertama diblokir sebentar supaya sampul HD sudah
                // tampil begitu PlaylistDetailScreen dibuka. SongResolver memakai
                // Semaphore(4) di Dispatchers.IO dan langsung UPDATE Room per lagu.
                val firstBatch = unresolved.take(FIRST_BATCH_SIZE)
                onProgress("Menyiapkan sampul lagu (0/${firstBatch.size})…")
                resolver.resolveAll(firstBatch) { done, total ->
                    onProgress("Menyiapkan sampul lagu ($done/$total)…")
                }

                // Tahap 2: sisanya menyusul di worker Repository dengan konkurensi
                // lebih rendah agar aman dari rate-limit YouTube.
                val rest = unresolved.drop(FIRST_BATCH_SIZE)
                if (rest.isNotEmpty()) {
                    onProgress("Sisa sampul lagu sedang disiapkan di latar belakang…")
                    backgroundScope.launch {
                        rest.forEach { song ->
                            backgroundPermits.withPermit {
                                try {
                                    resolver.resolve(song).getOrThrow()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    Log.w(
                                        TAG,
                                        "Gagal mencocokkan thumbnail ${song.title}: ${error.message}",
                                    )
                                }
                            }
                        }
                    }
                }
            }
            imported.id
        }
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val id = "local_${UUID.randomUUID()}"
        dao.upsertPlaylist(LocalPlaylistEntity(id = id, name = name.ifBlank { "Playlist Baru" }))
        id
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        dao.clearTracks(id)
        dao.deletePlaylist(id)
    }

    suspend fun resolve(song: Song): Result<Song> = resolver.resolve(song)

    suspend fun resolve(songs: List<Song>): Result<List<Song>> = resolver.resolveAll(songs)

    private fun List<SpotifyTrack>.toEntities(playlistId: String) = mapIndexed { index, track ->
        PlaylistTrackEntity(
            playlistId = playlistId,
            position = index,
            spotifyId = track.id,
            title = track.title,
            artist = track.artist,
            // Tanpa gambar trek asli, jangan pinjam cover playlist.
            thumbnailUrl = track.thumbnailUrl.takeIf { it.isNotBlank() }.orEmpty(),
            durationMs = track.durationMs,
        )
    }

    companion object {
        const val TAG = "DengerinLibrary"
        const val FIRST_BATCH_SIZE = 10
        const val BACKGROUND_CONCURRENCY = 3
        const val PLAYLIST_LIKED = "__liked__"
        const val PLAYLIST_HISTORY = "__history__"
    }
}
