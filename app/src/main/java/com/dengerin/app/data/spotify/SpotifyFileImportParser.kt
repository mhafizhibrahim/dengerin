package com.dengerin.app.data.spotify

import com.dengerin.app.domain.model.SpotifyTrack
import org.json.JSONArray
import org.json.JSONObject

enum class SpotifyFileTarget { LIKED, PLAYLIST }

/** Hasil baca file ekspor Spotify: sekumpulan lagu + tujuan penyimpanan. */
data class SpotifyFileImport(
    val name: String,
    val target: SpotifyFileTarget,
    val tracks: List<SpotifyTrack>,
)

/**
 * Parser lokal untuk CSV Exportify dan JSON unduhan data privasi Spotify
 * (`Playlist1.json`, `YourLibrary*.json`). Murni String -> model, tanpa jaringan,
 * tanpa login, dan tanpa pemanggilan resolver YouTube.
 */
object SpotifyFileImportParser {

    fun parse(text: String, fileName: String): Result<SpotifyFileImport> = runCatching {
        val body = text.trimStart('\uFEFF').trim()
        if (body.isBlank()) error("File kosong atau tidak dapat dibaca.")
        val result = if (body.startsWith("{") || body.startsWith("[")) {
            parseJson(body, fileName)
        } else {
            parseCsv(body, fileName)
        }
        if (result.tracks.isEmpty()) error("Tidak ada lagu yang bisa dibaca dari file ini.")
        result
    }

    // ------------------------------------------------------------------ CSV

    private fun parseCsv(body: String, fileName: String): SpotifyFileImport {
        val rows = splitCsv(body)
        if (rows.isEmpty()) error("CSV tidak berisi baris lagu.")
        val header = rows.first().map { it.trim().lowercase() }

        fun column(vararg aliases: String): Int = aliases.firstNotNullOfOrNull { alias ->
            header.indexOfFirst { it == alias.lowercase() }.takeIf { it >= 0 }
        } ?: -1

        val titleIndex = column("track name", "name", "title", "judul")
        if (titleIndex < 0) error("Kolom 'Track Name' tidak ditemukan pada CSV.")

        val artistIndex = column("artist name(s)", "artist name", "artists", "artist", "artis")
        val durationIndex = column("duration (ms)", "duration_ms", "durationms", "duration")
        val imageIndex = column("album image url", "album image", "image url", "image", "cover url")
        val uriIndex = column("spotify uri", "uri", "spotify id", "track id", "id")

        val tracks = rows.drop(1).mapIndexedNotNull { index, row ->
            val title = row.getOrNull(titleIndex)?.trim().orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            val artist = row.getOrNull(artistIndex)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Artis tidak diketahui"
            val duration = parseDuration(row.getOrNull(durationIndex).orEmpty())
            val image = row.getOrNull(imageIndex)?.trim().orEmpty()
            val uri = row.getOrNull(uriIndex)?.trim().orEmpty()
            SpotifyTrack(
                id = stableTrackId(uri, "$title|$artist|$duration"),
                title = title,
                artist = artist,
                durationMs = duration,
                thumbnailUrl = image,
                coverUrl = image,
                uri = uri,
            )
        }

        return SpotifyFileImport(
            name = nameWithoutExtension(fileName),
            target = SpotifyFileTarget.PLAYLIST,
            tracks = tracks,
        )
    }

    /** Parser CSV RFC 4180 sederhana: koma di dalam tanda kutip tidak memecah kolom. */
    private fun splitCsv(body: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < body.length) {
            val char = body[index]
            when {
                quoted && char == '"' -> {
                    if (index + 1 < body.length && body[index + 1] == '"') {
                        cell.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                }
                quoted -> cell.append(char)
                char == '"' -> quoted = true
                char == ',' -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                char == '\n' -> {
                    row += cell.toString()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                    cell.setLength(0)
                }
                char == '\r' -> Unit
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }

    // ----------------------------------------------------------------- JSON

    private fun parseJson(body: String, fileName: String): SpotifyFileImport {
        val root: Any = if (body.startsWith("[")) JSONArray(body) else JSONObject(body)
        val baseName = nameWithoutExtension(fileName)
        val rootName = (root as? JSONObject)?.optString("name")?.takeIf { it.isNotBlank() }

        val tracks = mutableListOf<SpotifyTrack>()
        val seen = HashSet<String>()
        collectTracks(root, 0, tracks, seen)

        val likedName = listOfNotNull(baseName, rootName).any {
            it.contains("liked", ignoreCase = true) || it.contains("disukai", ignoreCase = true)
        }
        return SpotifyFileImport(
            name = rootName ?: baseName,
            target = if (likedName) SpotifyFileTarget.LIKED else SpotifyFileTarget.PLAYLIST,
            tracks = tracks,
        )
    }

    private fun collectTracks(
        node: Any?,
        depth: Int,
        out: MutableList<SpotifyTrack>,
        seen: MutableSet<String>,
    ) {
        if (node == null || depth > 12) return
        when (node) {
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectTracks(runCatching { node.opt(index) }.getOrNull(), depth + 1, out, seen)
                }
            }
            is JSONObject -> {
                if (looksLikeTrack(node)) {
                    parseTrack(node)?.let { track ->
                        if (seen.add(track.id)) out += track
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectTracks(runCatching { node.opt(key) }.getOrNull(), depth + 1, out, seen)
                }
            }
        }
    }

    private fun looksLikeTrack(node: JSONObject): Boolean {
        if (node.optString("name").isBlank() && node.optString("trackName").isBlank()) return false
        val hasDuration = node.has("duration_ms") || node.has("durationMs") || node.has("duration")
        val hasArtist = node.has("artists") || node.has("artist")
        val uri = node.optString("uri").orEmpty()
        return (hasDuration && hasArtist) ||
            uri.contains("track", ignoreCase = true) ||
            (hasArtist && node.has("album"))
    }

    private fun parseTrack(node: JSONObject): SpotifyTrack? {
        val title = node.optString("name").takeIf { it.isNotBlank() }
            ?: node.optString("trackName").takeIf { it.isNotBlank() }
            ?: return null
        val artist = parseArtists(node)
        val duration = parseDuration(
            listOf("duration_ms", "durationMs", "duration")
                .firstNotNullOfOrNull { key -> node.opt(key)?.toString() }.orEmpty(),
        )
        val image = firstImage(node)
        val uri = node.optString("uri").takeIf { it.isNotBlank() }.orEmpty()
        return SpotifyTrack(
            id = stableTrackId(uri, "$title|$artist|$duration"),
            title = title,
            artist = artist,
            durationMs = duration,
            thumbnailUrl = image,
            coverUrl = image,
            uri = uri,
        )
    }

    private fun parseArtists(node: JSONObject): String {
        val names = when (val value = node.opt("artists")) {
            is JSONArray -> (0 until value.length()).mapNotNull { index ->
                when (val item = value.opt(index)) {
                    is JSONObject -> item.optString("name").takeIf { it.isNotBlank() }
                    is String -> item.takeIf { it.isNotBlank() }
                    else -> null
                }
            }
            is JSONObject -> listOfNotNull(value.optString("name").takeIf { it.isNotBlank() })
            is String -> listOf(value).filter { it.isNotBlank() }
            null -> emptyList()
            else -> emptyList()
        }
        return names.joinToString(", ").ifBlank {
            node.optString("artist").takeIf { it.isNotBlank() } ?: "Artis tidak diketahui"
        }
    }

    private fun firstImage(node: JSONObject): String {
        val direct = listOf("imageUrl", "coverUrl", "albumImageUrl")
            .firstNotNullOfOrNull { key -> node.optString(key).takeIf { it.isNotBlank() } }
        if (!direct.isNullOrBlank()) return direct

        val album = node.optJSONObject("album")
        val arrays = listOfNotNull(node.optJSONArray("images"), album?.optJSONArray("images"))
        for (array in arrays) {
            for (index in 0 until array.length()) {
                val url = when (val item = array.opt(index)) {
                    is JSONObject -> item.optString("url").takeIf { it.isNotBlank() }
                    is String -> item.takeIf { it.isNotBlank() }
                    else -> null
                }
                if (!url.isNullOrBlank()) return url
            }
        }
        return ""
    }

    // ------------------------------------------------------------- helpers

    private fun parseDuration(raw: String): Long {
        val value = raw.trim()
        if (value.isBlank()) return 0L
        value.toLongOrNull()?.let { return it }
        return runCatching {
            val parts = value.split(":")
            when (parts.size) {
                2 -> (parts[0].toLong() * 60 + parts[1].toDouble().toLong()) * 1000L
                3 -> (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble().toLong()) * 1000L
                else -> 0L
            }
        }.getOrDefault(0L)
    }

    private fun stableTrackId(uri: String, fallbackKey: String): String {
        val fromUri = uri.substringAfterLast(":", "")
            .takeIf { it.isNotBlank() && uri.contains("track", ignoreCase = true) }
        if (fromUri != null) return fromUri
        return "file_" + Integer.toUnsignedString(fallbackKey.hashCode(), 36)
    }

    private fun nameWithoutExtension(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }
}
