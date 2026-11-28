package com.dengerin.app.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.repository.SpotifyCollectionImportStatus
import com.dengerin.app.data.repository.SpotifyImportProgress
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyCollection
import com.dengerin.app.data.spotify.SpotifyFileImportParser
import com.dengerin.app.data.spotify.SpotifyProfile
import com.dengerin.app.data.spotify.SpotifySessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SpotifyImportState(
    val isImporting: Boolean = false,
    val label: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val completedCollections: Int = 0,
    val totalCollections: Int = 0,
    val currentCollectionName: String = "",
    val totalTracksImported: Int = 0,
    val addedTracksCount: Int = 0,
    val skippedCollectionsCount: Int = 0,
)

@Immutable
data class SpotifyAccountUiState(
    val isConnected: Boolean = false,
    val profile: SpotifyProfile? = null,
    val collections: List<SpotifyCollection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val connectionError: String? = null,
    val importStatuses: Map<String, SpotifyCollectionImportStatus> = emptyMap(),
    val importState: SpotifyImportState = SpotifyImportState(),
)

/** Satu tempat untuk sesi web Spotify, daftar koleksi, dan progres impor akun. */
class SpotifyAccountViewModel(
    private val sessionManager: SpotifySessionManager,
    private val accountImport: SpotifyAccountImportRepository,
    private val library: LibraryRepository,
    private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _ui = MutableStateFlow(SpotifyAccountUiState())
    val ui: StateFlow<SpotifyAccountUiState> = _ui.asStateFlow()

    // collectors DataStore bisa memicu refresh berurutan; yang terakhir yang menang.
    private var refreshJob: Job? = null
    private var importJob: Job? = null

    init {
        // Status terhubung berasal dari DataStore, bukan dari hasil fetch API.
        viewModelScope.launch {
            sessionManager.isConnected.collect { connected ->
                _ui.update { current ->
                    current.copy(
                        isConnected = connected,
                        connectionError = if (connected) null else current.connectionError,
                        profile = if (connected) current.profile else null,
                        collections = if (connected) current.collections else emptyList(),
                        isLoadingCollections = if (connected) current.isLoadingCollections else false,
                    )
                }
                if (connected) refresh()
            }
        }
        viewModelScope.launch {
            sessionManager.profile.collect { saved ->
                if (saved != null) _ui.update { current -> current.copy(profile = saved) }
            }
        }
        // Status "Sudah diimpor" berasal dari Room, bukan dari jaringan. Tidak ada
        // auto-sync: koleksi hanya di-refresh saat sheet dibuka atau tombol ditekan.
        viewModelScope.launch {
            library.spotifyImportStatuses.collect { statuses ->
                _ui.update { current -> current.copy(importStatuses = statuses) }
            }
        }
    }

    /**
     * Menyelesaikan seluruh penulisan sesi (sp_dc, accessToken, expiry, profil) sebelum
     * pemanggil menutup dialog login. Kegagalan fetch profil tidak membatalkan koneksi.
     */
    suspend fun connectWithSession(
        rawCookie: String,
        token: String?,
        expiresAtMs: Long,
        clientId: String = "",
    ) {
        runCatching {
            // Sesi baru selalu mengalahkan penanda logout manual sebelum data ditulis.
            sessionManager.markConnected()
            refreshJob?.cancel()
            sessionManager.saveCookie(rawCookie)
            if (!token.isNullOrBlank()) {
                sessionManager.saveSessionToken(token, expiresAtMs, clientId)
            }
            val profile = accountImport.fetchProfile().getOrNull()
            if (profile != null) {
                sessionManager.saveProfile(profile.displayName, profile.avatarUrl, profile.id)
                sessionManager.saveProfileToCloud(profile)
            } else {
                sessionManager.saveProfile("Akun Spotify", "")
            }
            _ui.update { current -> current.copy(connectionError = null) }
            // Token sudah tersimpan: muat ulang koleksi memakai token WebView tersebut.
            refresh()
        }
            .onFailure { error ->
                _ui.update { current ->
                    current.copy(
                        connectionError = error.message?.takeIf { it.isNotBlank() }
                            ?: "Gagal menyambungkan akun Spotify.",
                    )
                }
            }
    }

    suspend fun connectWithCookie(rawCookie: String) =
        connectWithSession(rawCookie, token = null, expiresAtMs = 0L)

    fun refresh() {
        if (sessionManager.isExplicitlyLoggedOut) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _ui.update { current ->
                current.copy(isLoadingCollections = true, connectionError = null)
            }
            // Profil gagal diambil TIDAK boleh menghentikan koleksi: tetap lanjut ke
            // Pathfinder supaya Lagu Disukai dan Playlist tetap tampil.
            val profile = accountImport.fetchProfile().getOrNull()
            val collections = accountImport.fetchCollections().getOrElse { error ->
                _ui.update { current ->
                    current.copy(
                        isLoadingCollections = false,
                        profile = profile ?: current.profile,
                        connectionError = error.message?.takeIf { it.isNotBlank() }
                            ?: "Koleksi Spotify belum bisa diambil.",
                    )
                }
                return@launch
            }
            // Profil asli ikut bertahan di DataStore/cloud bila aplikasi dibuka ulang.
            if (profile != null) {
                runCatching {
                    sessionManager.saveProfile(profile.displayName, profile.avatarUrl, profile.id)
                    sessionManager.saveProfileToCloud(profile)
                }
            }
            _ui.update { current ->
                current.copy(
                    profile = profile ?: current.profile,
                    collections = collections,
                    isLoadingCollections = false,
                    connectionError = null,
                )
            }
        }
    }

    /**
     * Satu-satunya aksi putus akun: batalkan refresh, update UI lebih dulu, lalu panggil
     * [SpotifySessionManager.logoutSpotify]. Tidak membuka dialog login dan tidak menyentuh
     * HeadlessSpotifyTokenRefresher.
     */
    fun logout() {
        refreshJob?.cancel()
        refreshJob = null
        importJob?.cancel()
        importJob = null
        _ui.update { current ->
            current.copy(
                isConnected = false,
                profile = null,
                collections = emptyList(),
                isLoadingCollections = false,
                connectionError = null,
                importState = SpotifyImportState(),
            )
        }
        viewModelScope.launch {
            runCatching { sessionManager.logoutSpotify() }
                .onFailure { error ->
                    _ui.update { current ->
                        current.copy(
                            connectionError = error.message?.takeIf { it.isNotBlank() }
                                ?: "Gagal memutus akun Spotify. Coba lagi ya.",
                        )
                    }
                }
        }
    }

    fun importSelected(selected: List<SpotifyCollection>) {
        if (selected.isEmpty() || _ui.value.importState.isImporting) return
        importJob?.cancel()
        importJob = applicationScope.launch {
            _ui.update { current ->
                current.copy(
                    importState = SpotifyImportState(
                        isImporting = true,
                        label = "Menyiapkan koleksi…",
                        total = selected.size,
                        totalCollections = selected.size,
                    ),
                )
            }
            val result = library.importSpotifyCollections(selected) { progress ->
                _ui.update { current ->
                    current.copy(
                        importState = current.importState.copy(
                            label = progress.currentCollectionName,
                            done = progress.completedCollections,
                            total = progress.totalCollections,
                            completedCollections = progress.completedCollections,
                            totalCollections = progress.totalCollections,
                            currentCollectionName = progress.currentCollectionName,
                            totalTracksImported = progress.totalTracksImported,
                        ),
                    )
                }
            }
            if (!_ui.value.isConnected) return@launch
            result.fold(
                onSuccess = { summary ->
                    val message = if (summary.addedTracksCount > 0) {
                        buildString {
                            append("+${summary.addedTracksCount} lagu baru berhasil ditambahkan")
                            if (summary.skippedCollectionsCount > 0) {
                                append(" · ${summary.skippedCollectionsCount} koleksi dilewati")
                            }
                        }
                    } else {
                        "Semua lagu dari koleksi terpilih sudah ada"
                    }
                    _ui.update { current ->
                        current.copy(
                            importState = current.importState.copy(
                                isImporting = false,
                                label = "Selesai",
                                done = selected.size,
                                total = selected.size,
                                completedCollections = selected.size,
                                totalTracksImported = summary.addedTracksCount,
                                addedTracksCount = summary.addedTracksCount,
                                skippedCollectionsCount = summary.skippedCollectionsCount,
                                message = message,
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@fold
                    _ui.update { current ->
                        current.copy(
                            importState = current.importState.copy(
                                isImporting = false,
                                error = error.message?.takeIf { it.isNotBlank() }
                                    ?: "Gagal mengimpor koleksi Spotify.",
                            ),
                        )
                    }
                },
            )
        }
    }

    /** Impor cadangan dari CSV/JSON: tidak butuh sesi Spotify atau pencarian YouTube. */
    fun importFile(text: String, fileName: String) {
        if (_ui.value.importState.isImporting) return
        viewModelScope.launch {
            _ui.update { current ->
                current.copy(
                    importState = SpotifyImportState(
                        isImporting = true,
                        label = "Membaca file…",
                    ),
                )
            }
            val parsed = SpotifyFileImportParser.parse(text, fileName)
            val result = parsed.fold(
                onSuccess = { import ->
                    library.importSpotifyFile(import) { label, done, total ->
                        _ui.update { current ->
                            current.copy(
                                importState = current.importState.copy(
                                    label = label,
                                    done = done,
                                    total = if (total > 0) total else current.importState.total,
                                ),
                            )
                        }
                    }
                },
                onFailure = { Result.failure(it) },
            )
            result.fold(
                onSuccess = { count ->
                    _ui.update { current ->
                        current.copy(
                            importState = current.importState.copy(
                                isImporting = false,
                                label = "Selesai",
                                done = count,
                                total = count,
                                message = "$count lagu dari file berhasil masuk ke Dengerin.",
                            ),
                        )
                    }
                },
                onFailure = { error ->
                    _ui.update { current ->
                        current.copy(
                            importState = current.importState.copy(
                                isImporting = false,
                                error = error.message?.takeIf { it.isNotBlank() }
                                    ?: "Gagal mengimpor file.",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun consumeImportResult() {
        _ui.update { current ->
            current.copy(
                importState = current.importState.copy(message = null, error = null),
            )
        }
    }
}
