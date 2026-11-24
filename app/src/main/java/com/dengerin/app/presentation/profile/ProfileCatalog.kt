package com.dengerin.app.presentation.profile

import com.dengerin.app.core.theme.CosmeticGradient
import com.dengerin.app.data.profile.CREATOR_TITLE_ID
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.profile.isDeveloper

/** Katalog bingkai avatar. Level dihitung dari [UserProfile.levelFromXp]. */
data class AvatarFrame(
    val id: String,
    val name: String,
    val requiredLevel: Int,
    val gradient: CosmeticGradient?,
    val glow: Boolean = false,
    /** Tier tinggi: glow bertingkat + partikel titik cahaya di Canvas. */
    val sparkles: Boolean = false,
) {
    val requirementLabel: String get() = "Lv. $requiredLevel"
}

/** Katalog pita title. Syarat bebas digabung; semua dicek terhadap profil lokal. */
data class ProfileTitle(
    val id: String,
    val label: String,
    val gradient: CosmeticGradient,
    val requiredLevel: Int = 1,
    val minListeningMinutes: Long = 0L,
    val minNightMinutes: Long = 0L,
    val minPlaylists: Int = 0,
    val minSongsPlayed: Long = 0L,
    val requirementLabel: String,
    val developerOnly: Boolean = false,
)

val avatarFrames: List<AvatarFrame> = listOf(
    AvatarFrame("default", "Standar", 1, null),
    AvatarFrame("emerald", "Emerald", 3, CosmeticGradient.EMERALD, glow = true),
    AvatarFrame("cyber_blue", "Cyber Blue", 7, CosmeticGradient.CYBER, glow = true),
    AvatarFrame("amethyst", "Amethyst", 12, CosmeticGradient.AMETHYST, glow = true),
    AvatarFrame("solar_gold", "Solar Gold", 20, CosmeticGradient.SOLAR, glow = true, sparkles = true),
    AvatarFrame("cosmic_aurora", "Cosmic Aurora", 35, CosmeticGradient.AURORA, glow = true, sparkles = true),
)

val profileTitles: List<ProfileTitle> = listOf(
    ProfileTitle(
        id = "new_listener",
        label = "New Listener",
        gradient = CosmeticGradient.SLATE,
        requirementLabel = "Otomatis di Lv. 1",
    ),
    ProfileTitle(
        id = "music_lover",
        label = "Music Lover",
        gradient = CosmeticGradient.EMERALD,
        requiredLevel = 5,
        requirementLabel = "Capai Lv. 5",
    ),
    ProfileTitle(
        id = "audiophile",
        label = "Audiophile",
        gradient = CosmeticGradient.CYBER,
        requiredLevel = 10,
        requirementLabel = "Capai Lv. 10",
    ),
    ProfileTitle(
        id = "music_legend",
        label = "Music Legend",
        gradient = CosmeticGradient.SOLAR,
        requiredLevel = 25,
        requirementLabel = "Capai Lv. 25",
    ),
    ProfileTitle(
        id = "maestro",
        label = "Maestro",
        gradient = CosmeticGradient.AURORA,
        requiredLevel = 50,
        requirementLabel = "Capai Lv. 50",
    ),
    ProfileTitle(
        id = "chill_vibes",
        label = "Chill Vibes",
        gradient = CosmeticGradient.SUNSET,
        minListeningMinutes = 60L,
        requirementLabel = "Dengar 60 menit",
    ),
    ProfileTitle(
        id = "night_owl",
        label = "Night Owl",
        gradient = CosmeticGradient.NIGHT,
        minNightMinutes = 30L,
        requirementLabel = "30 menit jam 23.00–04.00",
    ),
    ProfileTitle(
        id = "playlist_maker",
        label = "Playlist Maker",
        gradient = CosmeticGradient.EMERALD,
        minPlaylists = 3,
        requirementLabel = "Punya 3 playlist",
    ),
    ProfileTitle(
        id = "heavy_rotation",
        label = "Heavy Rotation",
        gradient = CosmeticGradient.AMETHYST,
        minSongsPlayed = 50L,
        requirementLabel = "Putar 50 lagu",
    ),
    ProfileTitle(
        id = CREATOR_TITLE_ID,
        label = "Creator",
        gradient = CosmeticGradient.CREATOR,
        developerOnly = true,
        requirementLabel = "Khusus Developer",
    ),
)

fun levelOf(profile: UserProfile?): Int = UserProfile.levelFromXp(profile?.totalXp ?: 0L)

fun isFrameUnlocked(frame: AvatarFrame, level: Int): Boolean = level >= frame.requiredLevel

fun isTitleUnlocked(
    title: ProfileTitle,
    profile: UserProfile?,
    playlistCount: Int,
    email: String? = null,
): Boolean {
    if (profile == null) return false
    if (title.developerOnly && !isDeveloper(email)) return false
    val level = levelOf(profile)
    return level >= title.requiredLevel &&
        profile.totalListeningSeconds / 60L >= title.minListeningMinutes &&
        profile.nightListeningMinutes >= title.minNightMinutes &&
        playlistCount >= title.minPlaylists &&
        profile.totalSongsPlayed >= title.minSongsPlayed
}

/** Title developer sengaja tidak ikut katalog akun lain. */
fun visibleProfileTitles(email: String?): List<ProfileTitle> =
    profileTitles.filter { !it.developerOnly || isDeveloper(email) }

fun unlockedTitleCount(profile: UserProfile?, playlistCount: Int, email: String? = null): Int =
    visibleProfileTitles(email).count { isTitleUnlocked(it, profile, playlistCount, email) }

fun frameById(id: String): AvatarFrame =
    avatarFrames.firstOrNull { it.id == id } ?: avatarFrames.first()

fun titleById(id: String): ProfileTitle? = profileTitles.firstOrNull { it.id == id }

fun equippedTitles(profile: UserProfile?, email: String? = null): List<ProfileTitle> = buildList {
    profile ?: return@buildList
    titleById(profile.equippedTitle1Id)
        ?.takeIf { !it.developerOnly || isDeveloper(email) }
        ?.let(::add)
    titleById(profile.equippedTitle2Id)
        ?.takeIf { !it.developerOnly || isDeveloper(email) }
        ?.let { title ->
            if (none { it.id == title.id }) add(title)
        }
}
