package com.dengerin.app.data.extractor

import android.os.SystemClock
import android.util.Log
import com.dengerin.app.core.StreamHeaders
import com.dengerin.app.core.hdCoverUrl
import com.dengerin.app.core.listThumbnailUrl
import com.dengerin.app.core.youtubeListThumbnailUrl
import com.dengerin.app.core.youtubeThumbnailUrl
import com.dengerin.app.core.youtubeVideoId
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.quality.AudioQualityEngine
import com.dengerin.app.domain.model.AudioStreamInfo
import com.dengerin.app.domain.model.StreamPick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicExtractorRepository(
    private val qualityEngine: AudioQualityEngine,
) {

    /**
     * Cache URL audio sesi ini. Kuncinya menyertakan profil kualitas supaya
     * perpindahan Wi-Fi ↔ seluler langsung memakai stream dengan bitrate tepat.
     * Cache byte sesungguhnya ada di [com.dengerin.app.player.AudioCacheManager].
     */
    private val audioCache = ConcurrentHashMap<String, CachedAudioStream>()

    /** Pencarian lagu dan auto-fetch sampul genre maksimal 2 request bersamaan. */
    private val searchSemaphore = Semaphore(SEARCH_CONCURRENCY)

    private data class CachedAudioStream(
        val stream: AudioStreamInfo,
        val fetchedAtMs: Long,
    )

    private val innerTubeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
            searchSemaphore.withPermit {
                val service = ServiceList.YouTube
                var items = search(service, query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
                if (items.isEmpty()) {
                    // Fallback ke pencarian YouTube biasa kalau filter lagu kosong.
                    items = search(service, query, emptyList())
                }
                items
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.url }
                    .map { it.toSong() }
            }
        }
    }

    /** Autocomplete YouTube: ±1–2 KB per ketikan, maksimal 5 saran. */
    suspend fun suggestions(query: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            searchSemaphore.withPermit {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                service.suggestionExtractor.suggestionList(query)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .take(MAX_SUGGESTIONS)
            }
        }
    }

    /**
     * Satu thumbnail medium (mqdefault 320x180, ±12–15 KB) dari hasil teratas
     * untuk kartu genre. [default.jpg] 120x90 sengaja tidak dipakai karena pecah.
     */
    suspend fun searchGenreCover(query: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            searchSemaphore.withPermit {
                val service = ServiceList.YouTube
                var items = search(service, query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
                if (items.isEmpty()) items = search(service, query, emptyList())
                val first = items.firstOrNull { it.url.isNotBlank() }
                    ?: throw IllegalStateException("Sampul genre belum ditemukan.")
                listThumbnailUrl(bestThumbnail(first.thumbnails))
                    ?: youtubeListThumbnailUrl(first.url)
                    ?: throw IllegalStateException("Sampul genre tidak tersedia.")
            }
        }
    }

    /**
     * Ambil URL audio dengan strategi [pick] yang sudah mempertimbangkan kuota.
     * Cache jangka pendek di sini hanya pengaman; cache RAM 4 jam ada di StreamResolver.
     */
    suspend fun getAudioStreamUrl(
        videoUrl: String,
        pick: StreamPick = StreamPick.STANDARD_OPUS,
    ): Result<AudioStreamInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val decision = qualityEngine.decide()
            val cacheKey = "$videoUrl|${decision.profile.name}|${pick.name}"
            val cached = audioCache[cacheKey]
            if (cached != null &&
                SystemClock.elapsedRealtime() - cached.fetchedAtMs < STREAM_TTL_MS
            ) {
                return@runCatching cached.stream
            }

            // Rantai fallback: audio-only (Opus/M4A) → InnerTube → video muxed
            // bitrate terendah. Video muxed HANYA dipakai bila tidak ada audio sama sekali.
            val candidates = runCatching { extractWithNewPipe(videoUrl) }
                .getOrElse { primaryError ->
                    Log.w(
                        TAG,
                        "NewPipe gagal mengekstrak audio: ${primaryError.message}. Mencoba InnerTube.",
                        primaryError,
                    )
                    runCatching { fetchInnerTubeAudio(videoUrl) }
                        .onFailure { fallbackError ->
                            Log.e(TAG, "Fallback InnerTube juga gagal.", fallbackError)
                        }
                        .getOrElse { fallbackError ->
                            throw IllegalStateException(
                                "Gagal mengambil audio: " +
                                    (primaryError.message?.takeIf { it.isNotBlank() }
                                        ?: "tidak diketahui"),
                                fallbackError,
                            )
                        }
                }

            val stream = selectStream(candidates, pick)
            audioCache[cacheKey] = CachedAudioStream(stream, SystemClock.elapsedRealtime())
            if (audioCache.size >= MAX_CACHE_ENTRIES) audioCache.clear()
            stream
        }
    }

    /**
     * Urutan pemilihan wajib hemat kuota:
     * 1. WEBMA_OPUS/M4A audio-only 48–64 kbps, bitrate > 0 (seluler/Data Saver),
     *    atau maks ~128 kbps (Wi-Fi).
     * 2. Audio-only dengan bitrate positif terkecil bila rentang itu tidak ada.
     */
    private fun selectStream(
        candidates: List<AudioStreamInfo>,
        pick: StreamPick,
    ): AudioStreamInfo {
        require(candidates.isNotEmpty()) { "Tidak ada kandidat stream audio." }
        val audioOnly = candidates.filterNot(::isVideoStream)
        // Video muxed hanya masuk kumpulan kandidat bila audio-only benar-benar tidak ada.
        val pool = audioOnly.ifEmpty { candidates }
        val economyAudio = pool.filter {
            (isOpusAudioInfo(it) || isM4AAudioInfo(it)) && it.bitrateBps > 0
        }

        val chosen = when (pick) {
            StreamPick.ECONOMY_OPUS -> firstNotNull(
                { economyAudio.filter { it.bitrateBps in 48_000..64_000 }.minByOrNull { it.bitrateBps } },
                { economyAudio.minByOrNull { it.bitrateBps } },
                { pool.filter { it.bitrateBps > 0 }.minByOrNull { it.bitrateBps } },
                { pool.minByOrNull { it.bitrateBps } },
            )

            StreamPick.STANDARD_OPUS -> firstNotNull(
                { pool.filter(::isOpusAudioInfo).filter { it.bitrateBps <= 128_000 }.maxByOrNull { it.bitrateBps } },
                { pool.filter(::isOpusAudioInfo).minByOrNull { it.bitrateBps } },
                { pool.filter { it.bitrateBps <= 128_000 }.maxByOrNull { it.bitrateBps } },
                { pool.minByOrNull { it.bitrateBps } },
            )

            StreamPick.COMPATIBILITY -> firstNotNull(
                { pool.filter(::isM4AAudioInfo).minByOrNull { it.bitrateBps } },
                { pool.filterNot(::isOpusAudioInfo).minByOrNull { it.bitrateBps } },
                { pool.minByOrNull { it.bitrateBps } },
            )
        }
        return requireNotNull(chosen) { "Tidak ada stream audio yang bisa diputar." }
    }

    private fun firstNotNull(vararg attempts: () -> AudioStreamInfo?): AudioStreamInfo? {
        for (attempt in attempts) {
            val result = attempt()
            if (result != null) return result
        }
        return null
    }

    private fun isOpusAudioInfo(stream: AudioStreamInfo): Boolean =
        stream.formatName.contains("opus", ignoreCase = true) ||
            stream.mimeType.startsWith("audio/webm", ignoreCase = true)

    private fun isM4AAudioInfo(stream: AudioStreamInfo): Boolean =
        stream.mimeType.startsWith("audio/mp4", ignoreCase = true) ||
            stream.formatName.contains("m4a", ignoreCase = true) ||
            stream.formatName.contains("aac", ignoreCase = true)

    private fun isVideoStream(stream: AudioStreamInfo): Boolean =
        stream.mimeType.startsWith("video/", ignoreCase = true)

    /** Hapus URL stream yang sudah kedaluwarsa supaya ekstraksi ulang terjadi. */
    fun invalidate(videoUrl: String) {
        audioCache.entries.removeAll { it.key.startsWith("$videoUrl|") }
    }

    fun clearCache() = audioCache.clear()

    /**
     * Kumpulkan SELURUH kandidat audio-only. Pemilihan akhir ada di [selectStream]
     * supaya urutan fallback (Opus hemat → kompatibilitas bitrate terkecil) ketat.
     * Video muxed hanya dipakai bila audioStreams benar-benar kosong.
     */
    private fun extractWithNewPipe(videoUrl: String): List<AudioStreamInfo> {
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val audioStreams = info.audioStreams
            .filter { it.isUrl && it.content.startsWith("http", ignoreCase = true) }
            .filter(::isSupportedAudio)

        val candidates = audioStreams.map(::toAudioStreamInfo)
        if (candidates.isNotEmpty()) return candidates

        val muxed = info.videoStreams
            .filter { it.isUrl && it.content.startsWith("http", ignoreCase = true) }
            .filter { stream ->
                stream.format?.mimeType.orEmpty().let { mime ->
                    mime.startsWith("video/mp4", ignoreCase = true) ||
                        mime.startsWith("video/webm", ignoreCase = true)
                }
            }
            .minByOrNull { it.bitrate }
        if (muxed != null) {
            Log.w(TAG, "Tidak ada audio-only; fallback ke video muxed bitrate terendah.")
            return listOf(
                AudioStreamInfo(
                    audioUrl = muxed.content,
                    formatName = muxed.format?.name ?: "muxed",
                    mimeType = muxed.format?.mimeType ?: "video/*",
                    bitrateBps = muxed.bitrate.coerceAtLeast(0),
                    userAgent = StreamHeaders.NEW_PIPE_USER_AGENT,
                    headers = StreamHeaders.YOUTUBE_HEADERS,
                ),
            )
        }
        // Menandakan primary error supaya caller masih punya fallback InnerTube.
        throw IllegalStateException("Tidak ada stream audio maupun video yang bisa diputar.")
    }

    private fun toAudioStreamInfo(stream: AudioStream): AudioStreamInfo {
        // Beberapa format mengisi `bitrate` tetapi meninggalkan `averageBitrate` = 0.
        val bitrate = if (stream.averageBitrate > 0) stream.averageBitrate else stream.bitrate
        return AudioStreamInfo(
            audioUrl = stream.content,
            formatName = stream.format?.name ?: "audio",
            mimeType = stream.format?.mimeType ?: "audio/*",
            bitrateBps = bitrate.coerceAtLeast(0),
            userAgent = StreamHeaders.NEW_PIPE_USER_AGENT,
            headers = StreamHeaders.YOUTUBE_HEADERS,
        )
    }

    /** Kumpulkan kandidat audio dari InnerTube (hanya adaptiveFormats, audio-only). */
    private fun fetchInnerTubeAudio(videoUrl: String): List<AudioStreamInfo> {
        val videoId = youtubeVideoId(videoUrl)
            ?: throw IllegalArgumentException("Video ID tidak ditemukan dari URL lagu.")

        val candidates = mutableListOf<AudioStreamInfo>()
        var lastError: Exception? = null
        for (client in INNER_TUBE_CLIENTS) {
            try {
                candidates += requestInnerTubeAudio(client, videoId)
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "Klien InnerTube ${client.name} gagal: ${error.message}", error)
            }
        }
        if (candidates.isEmpty()) {
            throw lastError ?: IllegalStateException("Semua klien InnerTube gagal.")
        }
        return candidates
    }

    private fun requestInnerTubeAudio(
        client: InnerTubeClient,
        videoId: String,
    ): List<AudioStreamInfo> {
        val clientJson = JSONObject()
            .put("clientName", client.name)
            .put("clientVersion", client.version)
            .put("hl", "id")
            .put("gl", "ID")
        client.androidSdkVersion?.let { clientJson.put("androidSdkVersion", it) }
        client.osName?.let { clientJson.put("osName", it) }
        client.osVersion?.let { clientJson.put("osVersion", it) }
        client.deviceModel?.let { clientJson.put("deviceModel", it) }

        val body = JSONObject()
            .put("videoId", videoId)
            .put("context", JSONObject().put("client", clientJson))
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = okhttp3.Request.Builder()
            .url("$PLAYER_ENDPOINT&key=$INNERTUBE_API_KEY")
            .header("User-Agent", client.userAgent)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Referer", "https://www.youtube.com/")
            .header("Origin", "https://www.youtube.com")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.version)
            .header("Cookie", "SOCS=CAI")
            .post(body)
            .build()

        innerTubeClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("InnerTube HTTP ${response.code}: ${response.message}")
            }

            val root = JSONObject(responseBody)
            val playability = root.optJSONObject("playabilityStatus")
            if (playability?.optString("status") != "OK") {
                val reason = playability?.optString("reason")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Video tidak tersedia."
                throw IllegalStateException(reason)
            }

            val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                ?: throw IllegalStateException("InnerTube tidak mengembalikan adaptiveFormats.")

            val candidates = mutableListOf<AudioStreamInfo>()
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                val mimeType = format.optString("mimeType")
                if (!mimeType.startsWith("audio/mp4") && !mimeType.startsWith("audio/webm")) {
                    continue
                }
                val url = format.optString("url").takeIf { it.startsWith("http") } ?: continue
                val bitrate = format.optInt(
                    "averageBitrate",
                    format.optInt("bitrate", 0),
                )

                candidates += AudioStreamInfo(
                    audioUrl = url,
                    formatName = if (mimeType.startsWith("audio/mp4")) "M4A" else "WebM Opus",
                    mimeType = mimeType.substringBefore(";"),
                    bitrateBps = bitrate,
                    userAgent = client.userAgent,
                    headers = mapOf(
                        "Referer" to "https://www.youtube.com/",
                        "Origin" to "https://www.youtube.com",
                        "X-YouTube-Client-Name" to client.clientId,
                        "X-YouTube-Client-Version" to client.version,
                    ),
                )
            }
            return candidates
        }
    }

    private fun search(
        service: StreamingService,
        query: String,
        contentFilters: List<String>,
    ): List<StreamInfoItem> {
        val extractor = service.getSearchExtractor(query, contentFilters, "")
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
    }

    private fun StreamInfoItem.toSong(): Song {
        val small = listThumbnailUrl(bestThumbnail(thumbnails))
            ?: youtubeListThumbnailUrl(url).orEmpty()
        val hd = hdCoverUrl(bestThumbnail(thumbnails))
            ?: youtubeThumbnailUrl(url).orEmpty()
        val channelId = uploaderUrl?.substringAfterLast('/')?.takeIf { it.isNotBlank() }.orEmpty()
        return Song(
            id = url,
            title = name,
            artist = uploaderName?.takeIf { it.isNotBlank() } ?: "Artis tidak diketahui",
            durationMs = if (duration > 0) duration * 1000L else 0L,
            url = url,
            thumbnailUrl = small,
            coverUrl = hd,
            artistId = channelId,
            artistIds = listOfNotNull(channelId.takeIf { it.isNotBlank() }),
        )
    }

    /**
     * YouTube menyediakan `default.jpg` 120x90 yang pecah. Ambil yang lebar minimal
     * 300px (mqdefault 320 / hqdefault 480); [listThumbnailUrl] lalu menormalkan
     * ke mqdefault agar list tetap ringan ~12–15 KB.
     */
    private fun bestThumbnail(images: List<Image>): String? =
        images.filter { it.width >= 300 }
            .maxByOrNull { it.width * it.height }
            ?.url
            ?: images.maxByOrNull { it.width * it.height }?.url

    /** Hanya format audio yang dikenal; format video/muxed tidak pernah masuk kandidat. */
    private fun isSupportedAudio(stream: AudioStream): Boolean {
        val mime = stream.format?.mimeType.orEmpty()
        return stream.format?.name?.contains("opus", ignoreCase = true) == true ||
            mime.startsWith("audio/webm", ignoreCase = true) ||
            mime.startsWith("audio/mp4", ignoreCase = true) ||
            mime.startsWith("audio/mpeg", ignoreCase = true) ||
            mime.startsWith("audio/ogg", ignoreCase = true) ||
            mime.startsWith("audio/flac", ignoreCase = true)
    }

    private data class InnerTubeClient(
        val name: String,
        val version: String,
        val clientId: String,
        val userAgent: String,
        val androidSdkVersion: Int? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceModel: String? = null,
    )

    private companion object {
        const val TAG = "DengerinExtractor"
        const val MAX_CACHE_ENTRIES = 40
        const val SEARCH_CONCURRENCY = 2
        const val MAX_SUGGESTIONS = 5
        /** URL YouTube bertanda tangan kedaluwarsa; jangan simpan terlalu lama. */
        const val STREAM_TTL_MS = 45L * 60 * 1000
        const val PLAYER_ENDPOINT =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

        val INNER_TUBE_CLIENTS = listOf(
            InnerTubeClient(
                name = "ANDROID_VR",
                version = "1.65.10",
                clientId = "28",
                userAgent = StreamHeaders.ANDROID_VR_USER_AGENT,
                androidSdkVersion = 32,
                osName = "Android",
                osVersion = "12L",
                deviceModel = "Quest 3",
            ),
            InnerTubeClient(
                name = "IOS",
                version = "20.10.4",
                clientId = "5",
                userAgent = StreamHeaders.IOS_USER_AGENT,
                osName = "iOS",
                osVersion = "18.3",
                deviceModel = "iPhone16,2",
            ),
        )
    }
}
