package com.dengerin.app.data.spotify

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Hash persisted query yang dipakai repository Pathfinder. */
data class SpotifyPathfinderHashes(
    val libraryV3: String = "",
    val fetchLibraryTracks: String = "",
    val fetchPlaylist: String = "",
    val searchDesktop: String = "",
    val queryArtistOverview: String = "",
) {
    val isComplete: Boolean
        get() = SHA256_REGEX.matches(libraryV3) &&
            SHA256_REGEX.matches(fetchLibraryTracks) &&
            SHA256_REGEX.matches(fetchPlaylist)

    fun forKey(operation: String): String = when (operation) {
        OP_LIBRARY -> libraryV3
        OP_LIKED -> fetchLibraryTracks
        OP_PLAYLIST -> fetchPlaylist
        OP_SEARCH_DESKTOP -> searchDesktop
        OP_ARTIST_OVERVIEW -> queryArtistOverview
        // alias yang diminta spesifikasi; dipetakan ke operasi Pathfinder yang tersedia.
        OP_SEARCH_PLAYLISTS, OP_SEARCH_ARTISTS -> searchDesktop
        else -> ""
    }

    fun merge(other: SpotifyPathfinderHashes) = SpotifyPathfinderHashes(
        libraryV3 = libraryV3.ifBlank { other.libraryV3 },
        fetchLibraryTracks = fetchLibraryTracks.ifBlank { other.fetchLibraryTracks },
        fetchPlaylist = fetchPlaylist.ifBlank { other.fetchPlaylist },
        searchDesktop = searchDesktop.ifBlank { other.searchDesktop },
        queryArtistOverview = queryArtistOverview.ifBlank { other.queryArtistOverview },
    )
}

/**
 * Auto-ekstraksi `sha256Hash` GraphQL dari bundle JS Web Player, persis seperti Spotube.
 * Hash di-cache di Preferences DataStore; bila server membalas `PersistedQueryNotFound`,
 * cache dibuang dan diambil ulang dari HTML `open.spotify.com` + script CDN-nya.
 */
class SpotifyPathfinderHashResolver(context: Context) {

    private val dataStore: DataStore<Preferences> =
        context.applicationContext.spotifySessionDataStore
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val storedPreferences = dataStore.data.catch { emit(emptyPreferences()) }

    /** Satu discovery bersama: retry paralel tidak boleh fetch bundle Web Player berkali-kali. */
    private val discoveryMutex = Mutex()

    @Volatile
    private var lastForceRefreshAtMs = 0L

    suspend fun getHashes(forceRefresh: Boolean = false): Result<SpotifyPathfinderHashes> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!forceRefresh) {
                    readCached().takeIf { it.isComplete }?.let { return@runCatching it }
                }
                return@runCatching discoveryMutex.withLock {
                    val now = System.currentTimeMillis()
                    if (forceRefresh && now - lastForceRefreshAtMs < FORCE_REFRESH_COOLDOWN_MS) {
                        readCached().takeIf { it.hasRequiredHashes() }?.let { return@withLock it }
                    }
                    if (forceRefresh) {
                        invalidate()
                        lastForceRefreshAtMs = System.currentTimeMillis()
                    }
                    val cached = readCached()
                    if (!forceRefresh && cached.hasRequiredHashes()) return@withLock cached

                    val discovered = discoverFromWebPlayer()
                    val merged = cached.merge(discovered)
                    if (!merged.hasRequiredHashes()) {
                        throw IOException(
                            "Hash GraphQL Spotify belum bisa diekstrak dari Web Player. " +
                                "Periksa koneksi lalu tekan Segarkan.",
                        )
                    }
                    writeCached(merged)
                    merged
                }
            }
        }

    suspend fun getHash(operation: String, forceRefresh: Boolean = false): Result<String> =
        getHashes(forceRefresh).mapCatching { hashes ->
            hashes.forKey(operation).takeIf { SHA256_REGEX.matches(it) }
                ?: discoverOperationHash(operation).getOrElse {
                    throw IOException("Hash Pathfinder '$operation' belum tersedia.")
                }
        }

    private fun SpotifyPathfinderHashes.hasRequiredHashes(): Boolean = isComplete

    private suspend fun discoverOperationHash(operation: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val discovered = discoverFromWebPlayer()
            val merged = readCached().merge(discovered)
            writeCached(merged)
            merged.forKey(operation).takeIf { SHA256_REGEX.matches(it) }
                ?: throw IOException("Hash Pathfinder '$operation' belum tersedia.")
        }
    }

    /** Dipanggil repository saat Pathfinder menjawab `PersistedQueryNotFound`. */
    suspend fun invalidate(operation: String? = null) {
        dataStore.edit { preferences ->
            when (operation) {
                OP_LIBRARY -> preferences.remove(HASH_LIBRARY_KEY)
                OP_LIKED -> preferences.remove(HASH_LIKED_KEY)
                OP_PLAYLIST -> preferences.remove(HASH_PLAYLIST_KEY)
                OP_SEARCH_DESKTOP, OP_SEARCH_PLAYLISTS, OP_SEARCH_ARTISTS -> preferences.remove(HASH_SEARCH_DESKTOP_KEY)
                OP_ARTIST_OVERVIEW -> preferences.remove(HASH_ARTIST_OVERVIEW_KEY)
                else -> {
                    preferences.remove(HASH_LIBRARY_KEY)
                    preferences.remove(HASH_LIKED_KEY)
                    preferences.remove(HASH_PLAYLIST_KEY)
                    preferences.remove(HASH_SEARCH_DESKTOP_KEY)
                    preferences.remove(HASH_ARTIST_OVERVIEW_KEY)
                }
            }
        }
    }

    private suspend fun readCached(): SpotifyPathfinderHashes {
        val prefs = storedPreferences.firstOrNull() ?: emptyPreferences()
        return SpotifyPathfinderHashes(
            libraryV3 = prefs[HASH_LIBRARY_KEY].orEmpty(),
            fetchLibraryTracks = prefs[HASH_LIKED_KEY].orEmpty(),
            fetchPlaylist = prefs[HASH_PLAYLIST_KEY].orEmpty(),
            searchDesktop = prefs[HASH_SEARCH_DESKTOP_KEY].orEmpty(),
            queryArtistOverview = prefs[HASH_ARTIST_OVERVIEW_KEY].orEmpty(),
        )
    }

    private suspend fun writeCached(hashes: SpotifyPathfinderHashes) {
        dataStore.edit { preferences ->
            preferences[HASH_LIBRARY_KEY] = hashes.libraryV3
            preferences[HASH_LIKED_KEY] = hashes.fetchLibraryTracks
            preferences[HASH_PLAYLIST_KEY] = hashes.fetchPlaylist
            preferences[HASH_SEARCH_DESKTOP_KEY] = hashes.searchDesktop
            preferences[HASH_ARTIST_OVERVIEW_KEY] = hashes.queryArtistOverview
        }
    }

    private suspend fun discoverFromWebPlayer(): SpotifyPathfinderHashes {
        val html = fetchText(WEB_PLAYER_URL)
        var found = extractHashes(html)
        if (found.isComplete) return found

        val scriptUrls = SCRIPT_SRC_REGEX.findAll(html)
            .map { it.groupValues[1] }
            .map(::absoluteUrl)
            .filter { it.isNotBlank() }
            .filter { it.contains("spotifycdn.com") || it.contains("open.spotify.com") }
            .distinct()
            .take(MAX_SCRIPTS)
            .toList()

        for (url in scriptUrls) {
            val js = runCatching { fetchText(url) }.getOrNull() ?: continue
            found = found.merge(extractHashes(js))
            if (found.isComplete) break
        }
        return found
    }

    private fun extractHashes(source: String) = SpotifyPathfinderHashes(
        libraryV3 = HASH_LIBRARY_REGEX.find(source)?.groupValues?.get(1).orEmpty(),
        fetchLibraryTracks = HASH_LIKED_REGEX.find(source)?.groupValues?.get(1).orEmpty(),
        fetchPlaylist = HASH_PLAYLIST_REGEX.find(source)?.groupValues?.get(1).orEmpty(),
        searchDesktop = HASH_SEARCH_DESKTOP_REGEX.find(source)?.groupValues?.get(1).orEmpty(),
        queryArtistOverview = HASH_ARTIST_OVERVIEW_REGEX.find(source)?.groupValues?.get(1).orEmpty(),
    )

    private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", SpotifySessionDom.CHROME_DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        val response = client.newCall(request).execute()
        try {
            if (response.code !in 200..299) {
                throw IOException("Gagal memuat Web Player Spotify (HTTP ${response.code}).")
            }
            response.body?.string().orEmpty()
        } finally {
            response.close()
        }
    }

    private fun absoluteUrl(raw: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "https://open.spotify.com$raw"
        else -> ""
    }

    private companion object {
        const val WEB_PLAYER_URL = "https://open.spotify.com/"
        const val MAX_SCRIPTS = 12
        const val FORCE_REFRESH_COOLDOWN_MS = 2_000L
        val HASH_LIBRARY_KEY = stringPreferencesKey("hashLibraryV3")
        val HASH_LIKED_KEY = stringPreferencesKey("hashFetchLibraryTracks")
        val HASH_PLAYLIST_KEY = stringPreferencesKey("hashFetchPlaylist")
        val HASH_SEARCH_DESKTOP_KEY = stringPreferencesKey("hashSearchDesktop")
        val HASH_ARTIST_OVERVIEW_KEY = stringPreferencesKey("hashQueryArtistOverview")
    }
}

private const val OP_LIBRARY = "libraryV3"
private const val OP_LIKED = "fetchLibraryTracks"
private const val OP_PLAYLIST = "fetchPlaylist"
private const val OP_SEARCH_DESKTOP = "searchDesktop"
private const val OP_SEARCH_PLAYLISTS = "searchPlaylists"
private const val OP_SEARCH_ARTISTS = "searchArtists"
private const val OP_ARTIST_OVERVIEW = "queryArtistOverview"
private val SHA256_REGEX = Regex("""^[a-f0-9]{64}$""")
private val HASH_LIBRARY_REGEX =
    Regex("""["']libraryV3["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
private val HASH_LIKED_REGEX =
    Regex("""["']fetchLibraryTracks["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
private val HASH_PLAYLIST_REGEX =
    Regex("""["']fetchPlaylist["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
private val HASH_SEARCH_DESKTOP_REGEX =
    Regex("""["']searchDesktop["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
private val HASH_ARTIST_OVERVIEW_REGEX =
    Regex("""["'](?:queryArtistOverview|artistOverviewV2)["']\s*,\s*["']query["']\s*,\s*["']([a-f0-9]{64})["']""")
private val SCRIPT_SRC_REGEX = Regex("""<script[^>]+src=["']([^"']+\.js[^"']*)["']""")
