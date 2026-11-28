package com.dengerin.app.presentation.profile

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dengerin.app.data.profile.DeveloperUser
import com.dengerin.app.data.profile.ProfileRepository
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.profile.isDeveloper
import com.dengerin.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoggedIn: Boolean = false,
    val email: String = "",
    val playlistCount: Int = 0,
    val isBusy: Boolean = false,
    val message: String? = null,
    val developerUsers: List<DeveloperUser> = emptyList(),
    val isLoadingUsers: Boolean = false,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    library: LibraryRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val feedback = MutableStateFlow<String?>(null)
    private val devUsers = MutableStateFlow<List<DeveloperUser>>(emptyList())
    private val loadingUsers = MutableStateFlow(false)
    private var latestUserQuery = ""

    private val coreState = combine(
        repository.authState,
        repository.profile,
        library.playlists,
    ) { user, profile, playlists -> Triple(user, profile, playlists.size) }

    private val devState = combine(devUsers, loadingUsers) { users, loading -> users to loading }

    val uiState: StateFlow<ProfileUiState> = combine(
        coreState,
        devState,
        busy,
        feedback,
    ) { core, dev, isBusy, message ->
        ProfileUiState(
            profile = core.second,
            isLoggedIn = core.first != null,
            email = core.first?.email.orEmpty(),
            playlistCount = core.third,
            isBusy = isBusy,
            message = message,
            developerUsers = dev.first,
            isLoadingUsers = dev.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        busy.value = true
        repository.signInWithGoogle(idToken)
            .onSuccess { report("Profil Google berhasil disambungkan.") }
            .onFailure { report(it.message ?: "Masuk Google gagal. Coba lagi ya.") }
        busy.value = false
    }

    fun refreshFromCloud() = viewModelScope.launch {
        busy.value = true
        repository.refreshFromCloud()
            .onSuccess { report("Profil dari cloud berhasil dimuat.") }
            .onFailure { report(it.message ?: "Gagal menarik profil cloud.") }
        busy.value = false
    }

    fun signOut() = viewModelScope.launch {
        busy.value = true
        repository.signOut()
            .onSuccess {
                devUsers.value = emptyList()
                report("Keluar berhasil. Progres tersimpan di cloud.")
            }
            .onFailure { report(it.message ?: "Gagal menyimpan progres sebelum keluar.") }
        busy.value = false
    }

    fun saveIdentity(displayName: String, bio: String, photoUrl: String) = viewModelScope.launch {
        repository.updateProfile { current ->
            val nextUrl = photoUrl.trim()
            current.copy(
                displayName = displayName.trim().ifBlank { "Pendengar Baru" },
                bio = bio.trim(),
                photoUrl = nextUrl,
                // URL baru sengaja menggantikan foto galeri; URL kosong tidak menghapusnya.
                photoBase64 = if (nextUrl.isNotBlank() && nextUrl != current.photoUrl) "" else current.photoBase64,
            )
        }
        report("Profil diperbarui.")
    }

    /** Pangkas/resize foto galeri lalu simpan ke cache + Firestore `photoBase64`. */
    fun saveProfilePhoto(uri: Uri) = viewModelScope.launch {
        busy.value = true
        repository.saveProfilePhoto(uri)
            .onSuccess { report("Foto profil tersimpan dan sudah tersinkron ke cloud.") }
            .onFailure { report(it.message ?: "Gagal menyimpan foto profil. Coba lagi ya.") }
        busy.value = false
    }

    fun clearProfilePhoto() = viewModelScope.launch {
        busy.value = true
        repository.clearProfilePhoto()
            .onSuccess { report("Foto profil dihapus.") }
            .onFailure { report(it.message ?: "Gagal menghapus foto profil.") }
        busy.value = false
    }

    fun equipFrame(frameId: String) = viewModelScope.launch {
        val level = levelOf(uiState.value.profile)
        val frame = frameById(frameId)
        if (!isFrameUnlocked(frame, level)) {
            report("Bingkai ini terbuka di ${frame.requirementLabel}.")
            return@launch
        }
        repository.updateProfile { current -> current.copy(equippedFrameId = frame.id) }
        report("Bingkai ${frame.name} dipakai.")
    }

    fun toggleTitle(titleId: String) = viewModelScope.launch {
        val title = titleById(titleId) ?: return@launch
        val state = uiState.value
        if (title.developerOnly && !isDeveloper(state.email)) {
            report("Title Creator hanya untuk akun developer.")
            return@launch
        }
        if (!isTitleUnlocked(title, state.profile, state.playlistCount, state.email)) {
            report("${title.label} terbuka saat ${title.requirementLabel.lowercase()}.")
            return@launch
        }

        repository.updateProfile { current ->
            when {
                current.equippedTitle1Id == title.id -> current.copy(
                    equippedTitle1Id = UserProfile.DEFAULT_TITLE_ID,
                    equippedTitle2Id = current.equippedTitle2Id.takeIf { it != title.id }.orEmpty(),
                )

                current.equippedTitle2Id == title.id -> current.copy(equippedTitle2Id = "")

                current.equippedTitle2Id.isBlank() -> current.copy(equippedTitle2Id = title.id)

                else -> current.copy(
                    equippedTitle1Id = current.equippedTitle2Id,
                    equippedTitle2Id = title.id,
                )
            }
        }
        report("Title ${title.label} diperbarui.")
    }

    fun searchDeveloperUsers(query: String) = viewModelScope.launch {
        latestUserQuery = query
        if (!isDeveloper(uiState.value.email)) {
            devUsers.value = emptyList()
            return@launch
        }
        loadingUsers.value = true
        repository.loadDeveloperUsers(query)
            .onSuccess { users -> if (latestUserQuery == query) devUsers.value = users }
            .onFailure { error ->
                if (latestUserQuery == query) devUsers.value = emptyList()
                report(error.message ?: "Gagal memuat daftar pengguna.")
            }
        loadingUsers.value = false
    }

    fun setOwnLevel(level: Int) = viewModelScope.launch {
        if (!isDeveloper(uiState.value.email)) {
            report("Akses Developer Panel ditolak.")
            return@launch
        }
        busy.value = true
        repository.setOwnLevel(level)
            .onSuccess { value -> report("Level kamu diset ke Lv. $value.") }
            .onFailure { report(it.message ?: "Gagal menyetel level.") }
        busy.value = false
    }

    fun setUserLevel(uid: String, level: Int) = viewModelScope.launch {
        if (!isDeveloper(uiState.value.email)) {
            report("Akses Developer Panel ditolak.")
            return@launch
        }
        busy.value = true
        repository.setUserLevel(uid, level)
            .onSuccess { value ->
                report("Level pengguna diset ke Lv. $value.")
                searchDeveloperUsers(latestUserQuery)
            }
            .onFailure { report(it.message ?: "Gagal menyetel level pengguna.") }
        busy.value = false
    }

    fun dismissMessage() = feedback.update { null }

    private fun report(message: String) {
        feedback.value = message
    }
}
