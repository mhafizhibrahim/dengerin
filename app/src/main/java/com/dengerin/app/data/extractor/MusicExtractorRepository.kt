package com.dengerin.app.data.extractor

import android.net.Uri
import android.util.Log
import com.dengerin.app.core.StreamHeaders
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.data.model.Song
import com.dengerin.app.domain.model.AudioStreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicExtractorRepository {

    /** Cache URL audio sesi ini supaya putar ulang terasa instan. */
    private val audioCache = ConcurrentHashMap<String, AudioStreamInfo>()

    private val innerTubeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        runCatching {
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

    suspend fun getAudioStreamUrl(videoUrl: String): Result<AudioStreamInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                audioCache[videoUrl]?.let { return@runCatching it }

                val stream = runCatching { extractWithNewPipe(videoUrl) }
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

                audioCache[videoUrl] = stream
                if (audioCache.size >= MAX_CACHE_ENTRIES) audioCache.clear()
                stream
            }
        }

    fun clearCache() = audioCache.clear()

    private fun extractWithNewPipe(videoUrl: String): AudioStreamInfo {
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val candidates = info.audioStreams
            .filter { it.isUrl && it.content.startsWith("http", ignoreCase = true) }
            .filter { formatPriority(it) >= 0 }

        val best = candidates.maxWithOrNull(
            compareBy<AudioStream> { formatPriority(it) }
                .thenBy { it.averageBitrate.coerceAtLeast(0) },
        ) ?: throw IllegalStateException("Tidak ada stream audio yang bisa diputar.")

        return AudioStreamInfo(
            audioUrl = best.content,
            formatName = best.format?.name ?: "audio",
            mimeType = best.format?.mimeType ?: "audio/*",
            bitrateBps = best.averageBitrate,
            userAgent = StreamHeaders.NEW_PIPE_USER_AGENT,
            headers = StreamHeaders.YOUTUBE_HEADERS,
        )
    }

    private fun fetchInnerTubeAudio(videoUrl: String): AudioStreamInfo {
        val videoId = extractVideoId(videoUrl)
            ?: throw IllegalArgumentException("Video ID tidak ditemukan dari URL lagu.")

        var lastError: Exception? = null
        for (client in INNER_TUBE_CLIENTS) {
            try {
                return requestInnerTubeAudio(client, videoId)
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "Klien InnerTube ${client.name} gagal: ${error.message}", error)
            }
        }
        throw lastError ?: IllegalStateException("Semua klien InnerTube gagal.")
    }

    private fun requestInnerTubeAudio(
        client: InnerTubeClient,
        videoId: String,
    ): AudioStreamInfo {
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

            return candidates.maxByOrNull { it.bitrateBps }
                ?: throw IllegalStateException("Tidak ada URL audio di respons InnerTube.")
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

    private fun StreamInfoItem.toSong() = Song(
        id = url,
        title = name,
        artist = uploaderName?.takeIf { it.isNotBlank() } ?: "Artis tidak diketahui",
        durationMs = if (duration > 0) duration * 1000L else 0L,
        url = url,
        thumbnailUrl = bestThumbnail(thumbnails),
    )

    private fun bestThumbnail(images: List<Image>): String =
        upgradeThumbnailUrl(images.maxByOrNull { it.width * it.height }?.url).orEmpty()

    /** M4A lebih diutamakan, lalu WebM/Opus; format lain tetap diterima ExoPlayer. */
    private fun formatPriority(stream: AudioStream): Int = when (stream.format) {
        MediaFormat.M4A -> 3
        MediaFormat.WEBMA_OPUS -> 2
        MediaFormat.WEBMA, MediaFormat.OPUS -> 1
        MediaFormat.MP3, MediaFormat.OGG, MediaFormat.FLAC -> 0
        else -> -1
    }

    private fun extractVideoId(url: String): String? {
        val queryId = Uri.parse(url).getQueryParameter("v")
        if (!queryId.isNullOrBlank()) return queryId.take(11)
        return VIDEO_ID_REGEX.find(url)?.groupValues?.get(1)
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
        const val PLAYER_ENDPOINT =
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        val VIDEO_ID_REGEX =
            Regex("""(?:youtu\.be/|/embed/|/shorts/|/live/|/v/)([A-Za-z0-9_-]{11})""")

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
