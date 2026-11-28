package com.dengerin.app.data.listening

import androidx.room.withTransaction
import com.dengerin.app.data.db.DengerinDatabase
import com.dengerin.app.data.db.ListeningEventEntity
import com.dengerin.app.data.db.RecentPlayEntity
import com.dengerin.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** Satu baris di daftar Top 5. `value` = jumlah putar, `durationMs` = total durasi dengar. */
data class ListeningRank(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val playCount: Int,
    val durationMs: Long,
)

/** Lagu di "Baru Diputar", lengkap dengan objek [Song] supaya bisa langsung diputar. */
data class RecentPlay(
    val song: Song,
    val playedAtMillis: Long,
    val listenedDurationMs: Long,
)

/** Semua angka statistik dihitung dari sesi dengar asli yang tersimpan di Room. */
data class ListeningStats(
    val totalMsWeek: Long = 0L,
    val totalMsAll: Long = 0L,
    val songsWeek: Int = 0,
    val songsAll: Int = 0,
    val streakDays: Int = 0,
    val topSongs: List<ListeningRank> = emptyList(),
    val topArtists: List<ListeningRank> = emptyList(),
    val recent: List<RecentPlay> = emptyList(),
)

private data class Session(
    val sessionId: Long,
    val generation: Int,
    val song: Song,
    var listenedMs: Long = 0L,
    var committed: Boolean = false,
    var lastFlushedMs: Long = 0L,
)

private data class SessionWrite(
    val sessionId: Long,
    val generation: Int,
    val song: Song,
    val playedAtMillis: Long,
    val listenedMs: Long,
    val final: Boolean,
)

private data class SessionState(
    var eventId: Long? = null,
    var recentBaseMs: Long = 0L,
    var lastWrittenMs: Long = 0L,
)

private fun RecentPlayEntity.toRecentPlay(): RecentPlay = RecentPlay(
    song = Song(
        id = trackId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        url = "",
        thumbnailUrl = thumbnailUrl,
        coverUrl = coverUrl,
    ),
    playedAtMillis = playedAtMillis,
    listenedDurationMs = listenedDurationMs,
)

/**
 * Riwayat dengar persisten. Sesi baru dicatat setelah 10 detik (atau saat lagu selesai),
 * lalu durasinya terus diperbarui sampai lagu berpindah. Penulisan Room lewat satu channel
 * agar urutan create → update → finish selalu terjaga meski dipanggil dari thread pemutar.
 */
class ListeningHistoryRepository(private val database: DengerinDatabase) {

    private val dao = database.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val sessionIds = AtomicLong(0L)
    private val generation = AtomicInteger(0)
    private val writes = Channel<SessionWrite>(Channel.UNLIMITED)
    private val sessionStates = mutableMapOf<Long, SessionState>()

    private var session: Session? = null

    val stats: StateFlow<ListeningStats> = combine(
        dao.observeListeningEvents(),
        dao.observeRecentPlays(),
        ::computeStats,
    ).stateIn(scope, SharingStarted.Eagerly, ListeningStats())

    init {
        scope.launch {
            for (write in writes) {
                runCatching { persist(write) }
            }
        }
    }

    /** Dipanggil ketika sebuah lagu benar-benar mulai diputar. */
    fun onSongStarted(song: Song) {
        finishSong()
        synchronized(lock) {
            session = Session(
                sessionId = sessionIds.incrementAndGet(),
                generation = generation.get(),
                song = song,
            )
        }
    }

    /**
     * Dipanggil oleh ticker pemutar (≈1 detik) hanya saat audio benar-benar berjalan.
     * `deltaMs` dihitung dari posisi/elapsed time nyata, bukan asumsi durasi lagu.
     */
    fun onPlaybackTick(deltaMs: Long) {
        if (deltaMs <= 0L) return
        val write: SessionWrite?
        synchronized(lock) {
            val current = session ?: return
            current.listenedMs += deltaMs
            val shouldCommit = !current.committed && current.listenedMs >= MIN_LISTENED_MS
            val shouldUpdate = current.committed &&
                current.listenedMs - current.lastFlushedMs >= PERSIST_INTERVAL_MS
            if (!shouldCommit && !shouldUpdate) return
            if (shouldCommit) current.committed = true
            current.lastFlushedMs = current.listenedMs
            write = SessionWrite(
                sessionId = current.sessionId,
                generation = current.generation,
                song = current.song,
                playedAtMillis = System.currentTimeMillis(),
                listenedMs = current.listenedMs,
                final = false,
            )
        }
        write?.let(writes::trySend)
    }

    /** Tutup sesi saat lagu berganti, selesai, atau pemutar dihentikan. */
    fun finishSong(completed: Boolean = false) {
        val write: SessionWrite?
        synchronized(lock) {
            val current = session ?: return
            session = null
            if (!current.committed && current.listenedMs < MIN_LISTENED_MS && !completed) return
            write = SessionWrite(
                sessionId = current.sessionId,
                generation = current.generation,
                song = current.song,
                playedAtMillis = System.currentTimeMillis(),
                listenedMs = current.listenedMs,
                final = true,
            )
        }
        write?.let(writes::trySend)
    }

    /** Bersihkan seluruh riwayat & statistik mendengarkan lokal. */
    fun clear() {
        // Tandai generasi baru supaya write lama yang masih antre tidak menghidupkan
        // lagi riwayat yang baru saja dihapus.
        generation.incrementAndGet()
        synchronized(lock) { session = null }
        sessionStates.clear()
        scope.launch {
            dao.clearListeningEvents()
            dao.clearRecentPlays()
        }
    }

    private suspend fun persist(write: SessionWrite) {
        if (write.generation != generation.get()) return
        val state = sessionStates.getOrPut(write.sessionId) { SessionState() }
        database.withTransaction {
            val eventId = state.eventId
            if (eventId == null) {
                val latest = dao.latestRecentPlay()
                val base = if (latest?.trackId == write.song.id) latest.listenedDurationMs else 0L
                state.recentBaseMs = base
                val id = dao.insertListeningEvent(
                    ListeningEventEntity(
                        trackId = write.song.id,
                        title = write.song.title,
                        artist = write.song.artist,
                        thumbnailUrl = write.song.thumbnailUrl,
                        coverUrl = write.song.coverUrl,
                        durationMs = write.song.durationMs,
                        playedAtMillis = write.playedAtMillis,
                        listenedDurationMs = write.listenedMs,
                    ),
                )
                state.eventId = id
                state.lastWrittenMs = write.listenedMs
            } else {
                val delta = max(0L, write.listenedMs - state.lastWrittenMs)
                if (delta > 0L) dao.addEventListening(eventId, delta)
                state.lastWrittenMs = write.listenedMs
            }

            dao.upsertRecentPlay(
                RecentPlayEntity(
                    trackId = write.song.id,
                    title = write.song.title,
                    artist = write.song.artist,
                    thumbnailUrl = write.song.thumbnailUrl,
                    coverUrl = write.song.coverUrl,
                    durationMs = write.song.durationMs,
                    playedAtMillis = write.playedAtMillis,
                    listenedDurationMs = state.recentBaseMs + write.listenedMs,
                ),
            )
            val overflow = dao.overflowRecentIds(MAX_RECENT)
            if (overflow.isNotEmpty()) dao.deleteRecentPlays(overflow)
        }
        if (write.final) sessionStates.remove(write.sessionId)
    }

    private fun computeStats(
        events: List<ListeningEventEntity>,
        recent: List<RecentPlayEntity>,
    ): ListeningStats {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(DayOfWeek.MONDAY)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val weekEvents = events.filter { it.playedAtMillis >= weekStart }
        val playedDays = events.asSequence()
            .map { Instant.ofEpochMilli(it.playedAtMillis).atZone(zone).toLocalDate() }
            .toSet()
        var streak = 0
        var cursor = today
        if (cursor !in playedDays) cursor = cursor.minusDays(1)
        while (cursor in playedDays) {
            streak++
            cursor = cursor.minusDays(1)
        }

        fun rankSongs(source: List<ListeningEventEntity>): List<ListeningRank> = source
            .groupBy { it.trackId }
            .values
            .map { group ->
                val first = group.first()
                ListeningRank(
                    key = first.trackId,
                    title = first.title,
                    subtitle = first.artist,
                    coverUrl = first.coverUrl.ifBlank { first.thumbnailUrl },
                    playCount = group.size,
                    durationMs = group.sumOf { it.listenedDurationMs },
                )
            }
            .sortedWith(compareByDescending<ListeningRank> { it.playCount }
                .thenByDescending { it.durationMs })
            .take(TOP_LIMIT)

        fun rankArtists(source: List<ListeningEventEntity>): List<ListeningRank> = source
            .groupBy { it.artist.trim().ifBlank { "Artis tidak diketahui" } }
            .values
            .map { group ->
                ListeningRank(
                    key = group.first().artist,
                    title = group.first().artist.ifBlank { "Artis tidak diketahui" },
                    subtitle = "${group.size} putar",
                    coverUrl = group.first().coverUrl.ifBlank { group.first().thumbnailUrl },
                    playCount = group.size,
                    durationMs = group.sumOf { it.listenedDurationMs },
                )
            }
            .sortedWith(compareByDescending<ListeningRank> { it.playCount }
                .thenByDescending { it.durationMs })
            .take(TOP_LIMIT)

        return ListeningStats(
            totalMsWeek = weekEvents.sumOf { it.listenedDurationMs },
            totalMsAll = events.sumOf { it.listenedDurationMs },
            songsWeek = weekEvents.size,
            songsAll = events.size,
            streakDays = streak,
            topSongs = rankSongs(events),
            topArtists = rankArtists(events),
            recent = recent.map(RecentPlayEntity::toRecentPlay),
        )
    }

    companion object {
        const val MIN_LISTENED_MS = 10_000L
        private const val PERSIST_INTERVAL_MS = 15_000L
        private const val MAX_RECENT = 100
        private const val TOP_LIMIT = 5
    }
}
