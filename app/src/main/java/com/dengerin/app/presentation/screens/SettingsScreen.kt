@file:OptIn(coil.annotation.ExperimentalCoilApi::class)

package com.dengerin.app.presentation.screens

import android.content.Context
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.BuildConfig
import com.dengerin.app.R
import com.dengerin.app.core.AppContainer
import com.dengerin.app.core.songListImageRequest
import com.dengerin.app.core.theme.AppThemeMode
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.util.formatTotalDuration
import com.dengerin.app.data.listening.ListeningRank
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.AudioQuality
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.preferences.CacheLimit
import com.dengerin.app.data.preferences.isDataSaverActive
import com.dengerin.app.data.update.GitHubUpdateManager
import com.dengerin.app.data.usage.DataUsageSnapshot
import com.dengerin.app.presentation.components.darkSlatePlaceholder
import com.dengerin.app.presentation.LibraryViewModel
import com.dengerin.app.presentation.SpotifyAccountViewModel
import com.dengerin.app.presentation.profile.FramedAvatar
import com.dengerin.app.presentation.profile.avatarInitial
import com.dengerin.app.presentation.profile.frameById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max

/** Halaman tujuan drawer; diekspor supaya drawer bisa membuka langsung. */
enum class SettingsPage {
    HUB, STATISTICS, HISTORY, DATA_SAVER, AUDIO_QUALITY, STORAGE, ACCOUNT, APPEARANCE, ABOUT,
    LICENSES,
}

private fun SettingsPage.title() = when (this) {
    SettingsPage.HUB -> "Pengaturan"
    SettingsPage.STATISTICS -> "Statistik Mendengarkan"
    SettingsPage.HISTORY -> "Baru Diputar"
    SettingsPage.DATA_SAVER -> "Penghemat Data & Kuota"
    SettingsPage.AUDIO_QUALITY -> "Kualitas Audio & Pemutaran"
    SettingsPage.STORAGE -> "Penyimpanan & Cache"
    SettingsPage.ACCOUNT -> "Akun & Koneksi Spotify"
    SettingsPage.APPEARANCE -> "Tampilan & Tema"
    SettingsPage.ABOUT -> "Tentang Dengerin"
    SettingsPage.LICENSES -> "Lisensi Pihak Ketiga"
}

/** Hub Pengaturan: satu rute, sub-halaman dialihkan dengan transisi geser halus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    libraryViewModel: LibraryViewModel,
    spotifyViewModel: SpotifyAccountViewModel,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onConnectSpotify: () -> Unit = {},
    onOpenSpotifyImport: () -> Unit = {},
    onSpotifyDisconnected: () -> Unit = {},
    onImportSpotify: () -> Unit = {},
    onPlaySong: (Song, List<Song>) -> Unit = { song, _ -> Unit },
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    initialPage: SettingsPage = SettingsPage.HUB,
    onUpdateAvailable: (GitHubUpdateManager.ReleaseInfo) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val spotifyUi by spotifyViewModel.ui.collectAsState()
    val spotifyConnected = spotifyUi.isConnected
    var pageIndex by rememberSaveable(initialPage) { mutableIntStateOf(initialPage.ordinal) }
    // ordinal lama dari build sebelumnya tidak boleh melempar IndexOutOfBoundsException.
    val page = SettingsPage.entries.getOrNull(pageIndex) ?: SettingsPage.HUB
    var query by rememberSaveable { mutableStateOf("") }

    fun navigate(target: SettingsPage) {
        pageIndex = target.ordinal
    }

    BackHandler(enabled = page != SettingsPage.HUB) {
        navigate(SettingsPage.HUB)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            // Halaman lisensi menyediakan top bar sendiri (← + judul + filter).
            if (page != SettingsPage.LICENSES) {
                TopAppBar(
                    title = {
                        Text(
                            page.title(),
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (page == SettingsPage.HUB) onClose() else navigate(SettingsPage.HUB)
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (page == SettingsPage.HUB) "Tutup pengaturan" else "Kembali",
                                tint = colors.textPrimary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.textPrimary,
                        navigationIconContentColor = colors.textPrimary,
                    ),
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            label = "settings-page",
            transitionSpec = {
                (slideInHorizontally { width -> width / 8 } + fadeIn()) togetherWith
                    (slideOutHorizontally { width -> -width / 8 } + fadeOut())
            },
        ) { target ->
            when (target) {
                SettingsPage.HUB -> SettingsHub(
                    container = container,
                    libraryViewModel = libraryViewModel,
                    contentPadding = padding,
                    query = query,
                    onQueryChange = { query = it },
                    onNavigate = ::navigate,
                    onOpenProfile = onOpenProfile,
                    spotifyConnected = spotifyConnected,
                )

                SettingsPage.STATISTICS -> StatisticsScreen(
                    container = container,
                    contentPadding = padding,
                )

                SettingsPage.HISTORY -> RecentlyPlayedScreen(
                    container = container,
                    contentPadding = padding,
                    onPlaySong = onPlaySong,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                )

                SettingsPage.DATA_SAVER -> DataSaverScreen(
                    container = container,
                    contentPadding = padding,
                )

                SettingsPage.AUDIO_QUALITY -> AudioQualityScreen(
                    container = container,
                    contentPadding = padding,
                )

                SettingsPage.STORAGE -> StorageCacheScreen(
                    container = container,
                    contentPadding = padding,
                )

                SettingsPage.ACCOUNT -> AccountSpotifyScreen(
                    container = container,
                    libraryViewModel = libraryViewModel,
                    spotifyViewModel = spotifyViewModel,
                    contentPadding = padding,
                    onOpenProfile = onOpenProfile,
                    onConnectSpotify = onConnectSpotify,
                    onOpenSpotifyImport = onOpenSpotifyImport,
                    onSpotifyDisconnected = onSpotifyDisconnected,
                    onImportSpotify = onImportSpotify,
                )

                SettingsPage.APPEARANCE -> AppearanceScreen(
                    container = container,
                    contentPadding = padding,
                )

                SettingsPage.ABOUT -> AboutScreen(
                    contentPadding = padding,
                    onUpdateAvailable = onUpdateAvailable,
                    onOpenLicenses = { navigate(SettingsPage.LICENSES) },
                )

                SettingsPage.LICENSES -> ThirdPartyLicensesScreen(
                    onBack = { navigate(SettingsPage.HUB) },
                )
            }
        }
    }
}

@Composable
private fun SettingsHub(
    container: AppContainer,
    libraryViewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onOpenProfile: () -> Unit,
    spotifyConnected: Boolean = false,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val settings by container.playbackPreferences.settings.collectAsState()
    val network by container.networkMonitor.state.collectAsState()
    val themeMode by container.themePreferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
    val usage by container.dataUsageTracker.snapshot.collectAsState()
    val stats by container.listeningHistoryRepository.stats.collectAsState()
    val storage by rememberStorage(context, container)
    val cacheBytes = storage.audioBytes
    val keyword = query.trim()

    LaunchedEffect(Unit) {
        container.dataUsageTracker.refresh()
    }

    val activityItems = listOf(
        HubItem(
            title = "Statistik Mendengarkan",
            status = "${formatHours(stats.totalMsWeek / 1000L)} minggu ini",
            keywords = "statistik jam dengar lagu streak",
            icon = Icons.Filled.BarChart,
            page = SettingsPage.STATISTICS,
        ),
        HubItem(
            title = "Baru Diputar",
            status = "${stats.recent.size} lagu di riwayat",
            keywords = "riwayat history lagu terakhir",
            icon = Icons.Filled.History,
            page = SettingsPage.HISTORY,
        ),
    ).filter { it.matches(keyword) }

    val mainItems = listOf(
        HubItem(
            title = "Penghemat Data & Kuota",
            status = "${if (settings.isDataSaverActive(network)) "Aktif" else "Nonaktif"} • ${formatBytes(usage.mobileBytesMonth)}",
            keywords = "data saver kuota hemat seluler wifi cache",
            icon = Icons.Filled.DataUsage,
            page = SettingsPage.DATA_SAVER,
        ),
        HubItem(
            title = "Kualitas Audio & Pemutaran",
            status = "Seluler: ${settings.cellularQuality.label}",
            keywords = "kualitas audio wifi seluler bitrate pemutaran",
            icon = Icons.Filled.Audiotrack,
            page = SettingsPage.AUDIO_QUALITY,
        ),
        HubItem(
            title = "Penyimpanan & Cache",
            status = "${formatBytes(cacheBytes)} / ${settings.cacheLimit.label}",
            keywords = "penyimpanan cache storage hapus bersihkan",
            icon = Icons.Filled.Storage,
            page = SettingsPage.STORAGE,
        ),
        HubItem(
            title = "Akun & Koneksi Spotify",
            status = if (spotifyConnected) "Terhubung" else "Belum",
            keywords = "akun spotify koneksi import masuk keluar",
            icon = Icons.Filled.Link,
            page = SettingsPage.ACCOUNT,
        ),
        HubItem(
            title = "Tampilan & Tema",
            status = themeStatus(themeMode),
            keywords = "tampilan tema light dark cool slate gelap terang",
            icon = Icons.Filled.Palette,
            page = SettingsPage.APPEARANCE,
        ),
        HubItem(
            title = "Tentang Dengerin",
            status = "v${BuildConfig.VERSION_NAME}",
            keywords = "tentang versi aplikasi update",
            icon = Icons.Filled.Info,
            page = SettingsPage.ABOUT,
        ),
    ).filter { it.matches(keyword) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsSearchField(query = query, onQueryChange = onQueryChange)
        }

        item {
            AccountCard(container = container, onOpenProfile = onOpenProfile)
        }

        if (activityItems.isNotEmpty()) {
            item {
                SectionTitle("Aktivitas Kamu")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    activityItems.forEach { item ->
                        ActivityCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(item.page) },
                        )
                    }
                    if (activityItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (mainItems.isNotEmpty()) {
            item {
                SectionTitle("Pengaturan Utama")
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    mainItems.forEachIndexed { index, item ->
                        SettingsRow(
                            item = item,
                            onClick = { onNavigate(item.page) },
                            showDivider = index != mainItems.lastIndex,
                        )
                    }
                }
                if (settings.isDataSaverActive(network)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Data Saver sistem Android juga ikut membatasi kuota.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (activityItems.isEmpty() && mainItems.isEmpty()) {
            item {
                SettingsCard {
                    Text(
                        text = "Tidak ada pengaturan yang cocok dengan \"$query\".",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(colors.surfaceElevated),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            shape = shape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            placeholder = {
                Text(
                    "Cari kuota, cache, tema...",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Cari pengaturan",
                    tint = colors.textSecondary,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
                focusedLeadingIconColor = colors.textSecondary,
                unfocusedLeadingIconColor = colors.textSecondary,
            ),
        )
    }
}

@Composable
private fun AccountCard(container: AppContainer, onOpenProfile: () -> Unit) {
    val colors = LocalDengerinColors.current
    val authUser by container.profileRepository.authState.collectAsState()
    val profile by container.profileRepository.profile.collectAsState()
    val name = if (authUser != null) {
        profile?.displayName?.takeIf { it.isNotBlank() } ?: "Pendengar Dengerin"
    } else {
        "Pendengar Dengerin"
    }

    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profile != null && authUser != null) {
                FramedAvatar(
                    photoUrl = profile?.photoUrl?.takeIf { it.isNotBlank() },
                    photoBase64 = profile?.photoBase64?.takeIf { it.isNotBlank() },
                    frame = frameById(profile?.equippedFrameId ?: UserProfile.DEFAULT_FRAME_ID),
                    size = 44.dp,
                    defaultInitial = avatarInitial(name),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Akun tamu",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(if (authUser != null) "Profil Tersinkron" else "Akun Tamu")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenProfile) {
                Text("Ganti Akun", color = colors.accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = text,
        color = colors.accentSoftContent,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.accentSoftContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/* ------------------------------ Sub-halaman ------------------------------ */

@Composable
private fun StatisticsScreen(container: AppContainer, contentPadding: PaddingValues) {
    val colors = LocalDengerinColors.current
    val stats by container.listeningHistoryRepository.stats.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "Jam Dengar • minggu ini",
                    value = formatHours(stats.totalMsWeek / 1000L),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Lagu Diputar • minggu ini",
                    value = stats.songsWeek.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Streak Harian",
                    value = "${stats.streakDays} hari",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Keseluruhan: ${formatHours(stats.totalMsAll / 1000L)} • ${stats.songsAll} lagu diputar",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            SectionTitle("Top 5 Lagu")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                if (stats.topSongs.isEmpty()) {
                    EmptyRankMessage("Belum ada lagu yang diputar cukup lama.")
                } else {
                    stats.topSongs.forEachIndexed { index, item ->
                        RankedRow(
                            rank = index + 1,
                            title = item.title,
                            subtitle = item.subtitle,
                            trailing = "${item.playCount}x",
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Top 5 Artis")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                if (stats.topArtists.isEmpty()) {
                    EmptyRankMessage("Dengarkan minimal 10 detik per lagu untuk melihat artis favorit.")
                } else {
                    stats.topArtists.forEachIndexed { index, item ->
                        RankedRow(
                            rank = index + 1,
                            title = item.title,
                            subtitle = "Total ${formatTotalDuration(item.durationMs)}",
                            trailing = "${item.playCount} putar",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRankMessage(message: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = message,
        color = colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@Composable
private fun RecentlyPlayedScreen(
    container: AppContainer,
    contentPadding: PaddingValues,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val stats by container.listeningHistoryRepository.stats.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val weekStart = today.with(DayOfWeek.MONDAY)
        .atStartOfDay(zone).toInstant().toEpochMilli()

    fun groupOf(playedAt: Long): String {
        val date = Instant.ofEpochMilli(playedAt).atZone(zone).toLocalDate()
        return when {
            date == today -> "Hari Ini"
            date == yesterday -> "Kemarin"
            playedAt >= weekStart -> "Minggu Ini"
            else -> "Lebih Lama"
        }
    }

    val labels = listOf("Hari Ini", "Kemarin", "Minggu Ini", "Lebih Lama")
    val groups = labels.associateWith { label -> stats.recent.filter { groupOf(it.playedAtMillis) == label } }
    val queue = stats.recent.map { it.song }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (stats.recent.isEmpty()) {
            item {
                SettingsCard {
                    Text(
                        text = "Belum ada lagu di riwayat. Putar satu lagu minimal 10 detik agar muncul di sini.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        groups.forEach { (label, entries) ->
            if (entries.isEmpty()) return@forEach
            item {
                SectionTitle(label)
                Spacer(Modifier.height(10.dp))
                SettingsCard {
                    entries.forEachIndexed { index, entry ->
                        val time = Instant.ofEpochMilli(entry.playedAtMillis)
                            .atZone(zone).toLocalTime().toString().take(5)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPlaySong(entry.song, queue) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = songListImageRequest(context, entry.song),
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.song.title,
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${entry.song.artist} · $time · ${formatTotalDuration(entry.listenedDurationMs)} dengar",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                IconButton(onClick = { expandedId = entry.song.id }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Opsi lagu ${entry.song.title}",
                                        tint = colors.textSecondary,
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedId == entry.song.id,
                                    onDismissRequest = { expandedId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Putar Berikutnya / Add to Queue") },
                                        onClick = {
                                            onAddToQueue(entry.song)
                                            expandedId = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tambah ke Playlist") },
                                        onClick = {
                                            onAddToPlaylist(entry.song)
                                            expandedId = null
                                        },
                                    )
                                }
                            }
                        }
                        if (index != entries.lastIndex) Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSaverScreen(container: AppContainer, contentPadding: PaddingValues) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.playbackPreferences.settings.collectAsState()
    val network by container.networkMonitor.state.collectAsState()
    val usage by container.dataUsageTracker.snapshot.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.dataUsageTracker.refresh() }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset statistik?", color = colors.textPrimary) },
            text = {
                Text(
                    "Seluruh penghitung kuota (seluler, Wi-Fi, rata-rata per lagu, dan byte yang dihemat cache) akan dikembalikan ke 0 MB.",
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    container.dataUsageTracker.reset()
                    showResetDialog = false
                    Toast.makeText(context, "Statistik kuota direset.", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Reset", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle("Penghemat Data")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                SwitchRow(
                    title = "Penghemat Data",
                    subtitle = "Seluler memaksa kualitas Rendah (~1 MB/lagu) dan mematikan prefetch. Data Saver sistem juga diikuti.",
                    checked = settings.dataSaver,
                ) { enabled -> scope.launch { container.playbackPreferences.setDataSaver(enabled) } }
                if (network.isSystemDataSaver) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Data Saver sistem Android sedang aktif.",
                        color = colors.accentSoftContent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            SectionTitle("Statistik Kuota")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                QuotaRow(
                    label = "Seluler",
                    usage = usage,
                    todayBytes = usage.mobileBytesToday,
                    monthBytes = usage.mobileBytesMonth,
                )
                Spacer(Modifier.height(16.dp))
                QuotaRow(
                    label = "Wi-Fi",
                    usage = usage,
                    todayBytes = usage.wifiBytesToday,
                    monthBytes = usage.wifiBytesMonth,
                )
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
                Text("Rata-rata per Lagu", color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "~${formatMegabytes(usage.bytesPerSong)} / lagu",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.accentSoftContainer)
                    .padding(16.dp),
            ) {
                Text(
                    "Kuota Dihemat oleh Cache",
                    color = colors.accentSoftContent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatBytes(usage.savedByCacheBytes),
                    color = colors.accentSoftContent,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Audio yang dibaca langsung dari cache, tanpa mengunduh ulang.",
                    color = colors.accentSoftContent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            TextButton(onClick = { showResetDialog = true }) {
                Text("Reset statistik", color = colors.accent)
            }
        }
    }
}

@Composable
private fun QuotaRow(
    label: String,
    usage: DataUsageSnapshot,
    todayBytes: Long,
    monthBytes: Long,
) {
    val colors = LocalDengerinColors.current
    val widest = max(usage.mobileBytesMonth, usage.wifiBytesMonth)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("Bulan Ini", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Hari Ini: ${formatBytes(todayBytes)}   •   Bulan Ini: ${formatBytes(monthBytes)}",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (widest > 0L) (monthBytes.toFloat() / widest).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = colors.accent,
            trackColor = colors.surfaceElevated,
        )
    }
}

@Composable
private fun AudioQualityScreen(container: AppContainer, contentPadding: PaddingValues) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.playbackPreferences.settings.collectAsState()
    val network by container.networkMonitor.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = if (network.isCellular) "Sedang pakai data seluler" else "Sedang pakai Wi-Fi / jaringan lain",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            SettingsCard {
                QualityGroup(
                    label = "Wi-Fi",
                    selected = settings.wifiQuality,
                ) { quality -> scope.launch { container.playbackPreferences.setWifiQuality(quality) } }
                Spacer(Modifier.height(18.dp))
                QualityGroup(
                    label = "Data Seluler",
                    selected = settings.cellularQuality,
                ) { quality -> scope.launch { container.playbackPreferences.setCellularQuality(quality) } }
                Spacer(Modifier.height(18.dp))
                SwitchRow(
                    title = "Auto Adjust Quality",
                    subtitle = "Turunkan satu tingkat saat koneksi lambat supaya tidak buffering.",
                    checked = settings.autoAdjustQuality,
                ) { enabled -> scope.launch { container.playbackPreferences.setAutoAdjustQuality(enabled) } }
            }
        }
        item {
            Text(
                "Perubahan langsung dipakai pemutar tanpa perlu restart.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StorageCacheScreen(container: AppContainer, contentPadding: PaddingValues) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.playbackPreferences.settings.collectAsState()
    val storage by rememberStorage(context, container)
    var showClearDialog by remember { mutableStateOf(false) }
    val appBytes = max(0L, storage.used - storage.dengerinBytes)
    val freeBytes = storage.free

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsCard {
                Text(
                    text = "Rincian ruang penyimpanan",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                ) {
                    Box(
                        Modifier
                            .weight(appBytes.coerceAtLeast(1L).toFloat())
                            .fillMaxSize()
                            .background(colors.border),
                    )
                    Box(
                        Modifier
                            .weight(storage.dengerinBytes.coerceAtLeast(1L).toFloat())
                            .fillMaxSize()
                            .background(colors.accent),
                    )
                    Box(
                        Modifier
                            .weight(freeBytes.coerceAtLeast(1L).toFloat())
                            .fillMaxSize()
                            .background(colors.surfaceElevated),
                    )
                }
                Spacer(Modifier.height(10.dp))
                StorageLegend("Aplikasi lain", appBytes, colors.border)
                StorageLegend("Cache Dengerin", storage.dengerinBytes, colors.accent)
                StorageLegend("Ruang Kosong", freeBytes, colors.surfaceElevated)

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Batas cache audio",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CacheLimit.entries.forEach { limit ->
                        FilterChip(
                            selected = settings.cacheLimit == limit,
                            onClick = {
                                container.audioCacheManager.applyMaxBytes(limit.bytes)
                                scope.launch { container.playbackPreferences.setCacheLimit(limit) }
                            },
                            label = { Text(limit.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentSoftContainer,
                                selectedLabelColor = colors.accentSoftContent,
                                labelColor = colors.textSecondary,
                                containerColor = colors.surfaceElevated,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentSoftContainer,
                        contentColor = colors.accentSoftContent,
                    ),
                ) {
                    Text("Hapus Cache", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surface,
            title = { Text("Hapus cache?", color = colors.textPrimary) },
            text = {
                Text(
                    "Cache audio dan gambar akan dihapus. Lagu yang pernah diputar perlu diunduh ulang saat dibuka kembali.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.audioCacheManager.clear()
                            container.imageLoader.diskCache?.clear()
                            container.musicExtractorRepository.clearCache()
                        }
                        Toast.makeText(context, "Cache Dengerin dibersihkan.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Hapus", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun AccountSpotifyScreen(
    container: AppContainer,
    libraryViewModel: LibraryViewModel,
    spotifyViewModel: SpotifyAccountViewModel,
    contentPadding: PaddingValues,
    onOpenProfile: () -> Unit,
    onConnectSpotify: () -> Unit,
    onOpenSpotifyImport: () -> Unit,
    onSpotifyDisconnected: () -> Unit,
    onImportSpotify: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val authUser by container.profileRepository.authState.collectAsState()
    val spotify by spotifyViewModel.ui.collectAsState()
    val connected = spotify.isConnected

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "dengerin_account") {
            SectionTitle("Akun Dengerin")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            authUser?.email ?: "Belum masuk",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (authUser != null) "Profil tersinkron" else "Masuk opsional",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onOpenProfile) {
                    Text("Kelola akun / ganti akun", color = colors.accent)
                }
            }
        }

        item(key = "spotify_connection") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_dengerin_mark),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle("Akun & Integrasi Spotify")
            }
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = spotify.profile?.avatarUrl.orEmpty(),
                            contentDescription = null,
                            placeholder = darkSlatePlaceholder(),
                            error = darkSlatePlaceholder(),
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                spotify.profile?.displayName ?: "Akun Spotify",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Terhubung",
                                    color = colors.accentSoftContent,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onOpenSpotifyImport,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    ) { Text("Impor Koleksi Spotify") }
                    TextButton(
                        onClick = {
                            spotifyViewModel.logout()
                            onSpotifyDisconnected()
                        },
                    ) {
                        Text("Keluar dari Spotify", color = colors.textSecondary)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Hubungkan Akun Spotify",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Impor playlist pribadi & Lagu Disukai gratis tanpa perlu akun berbayar",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onConnectSpotify,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                        ),
                    ) { Text("Masuk dengan Akun Spotify") }
                }
                spotify.connectionError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Atau impor lewat link playlist publik:",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onImportSpotify) {
                    Text("Impor dari Spotify", color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun AppearanceScreen(container: AppContainer, contentPadding: PaddingValues) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    val themeMode by container.themePreferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionTitle("Mode Tampilan")
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                listOf(
                    AppThemeMode.SYSTEM to "Sistem",
                    AppThemeMode.LIGHT to "Cool Slate Light",
                    AppThemeMode.DARK to "Dark Mode",
                ).forEach { (mode, label) ->
                    ThemeOption(
                        label = label,
                        selected = themeMode == mode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        scope.launch { container.themePreferences.setThemeMode(mode) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Perubahan tema langsung diterapkan, tanpa animasi.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private enum class AboutUpdateStatus { IDLE, CHECKING, UP_TO_DATE, OFFLINE }

@Composable
private fun AboutScreen(
    contentPadding: PaddingValues,
    onUpdateAvailable: (GitHubUpdateManager.ReleaseInfo) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(AboutUpdateStatus.IDLE) }
    val versionName = BuildConfig.VERSION_NAME

    fun checkUpdate() {
        if (status == AboutUpdateStatus.CHECKING) return
        status = AboutUpdateStatus.CHECKING
        scope.launch {
            val release = GitHubUpdateManager.fetchLatest()
            status = when {
                release == null -> AboutUpdateStatus.OFFLINE
                GitHubUpdateManager.isNewerVersion(release.version, versionName) -> {
                    onUpdateAvailable(release)
                    AboutUpdateStatus.IDLE
                }

                else -> AboutUpdateStatus.UP_TO_DATE
            }
        }
    }

    // Cek otomatis begitu halaman Tentang Dengerin dibuka.
    LaunchedEffect(Unit) { checkUpdate() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = settingsListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colorResource(R.color.dengerin_brand_green)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dengerin_mark),
                            contentDescription = "Logo Dengerin",
                            tint = colorResource(R.color.dengerin_symbol),
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Dengerin",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Dengerin lagu, tanpa drama iklan.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Pembaruan")
            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = "Pembaruan",
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Versi Terbaru",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Rilis terbaru dicek langsung dari GitHub.",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    when (status) {
                        AboutUpdateStatus.CHECKING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Memeriksa versi terbaru...",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        AboutUpdateStatus.UP_TO_DATE -> {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accentSoftContainer)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Versi terbaru",
                                    tint = colors.accentSoftContent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Aplikasi sudah versi terbaru (v$versionName)",
                                    color = colors.accentSoftContent,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        AboutUpdateStatus.OFFLINE -> {
                            Text(
                                "Tidak dapat memeriksa pembaruan (periksa koneksi internet)",
                                color = colors.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        AboutUpdateStatus.IDLE -> Unit
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { checkUpdate() },
                        enabled = status != AboutUpdateStatus.CHECKING,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.onAccent,
                            disabledContainerColor = colors.surfaceElevated,
                            disabledContentColor = colors.textSecondary,
                        ),
                    ) {
                        Text(
                            "Periksa Pembaruan",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

        item {
            Divider()
            SettingsCard {
                NavigationRow(
                    icon = Icons.Filled.Info,
                    title = "Lisensi pihak ketiga",
                    subtitle = "Perangkat lunak sumber terbuka yang digunakan di Dengerin",
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

/* --------------------------- Komponen bersama --------------------------- */

private data class HubItem(
    val title: String,
    val status: String,
    val keywords: String,
    val icon: ImageVector,
    val page: SettingsPage,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        return listOf(title, status, keywords).any { it.contains(query, ignoreCase = true) }
    }
}

/** Padding datar untuk semua daftar Pengaturan: ruang bawah sudah dari root. */
@Composable
private fun settingsListPadding(scaffoldPadding: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
        top = 12.dp + scaffoldPadding.calculateTopPadding(),
        end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
        bottom = LocalBottomClearance.current,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = LocalDengerinColors.current.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Grup datar tanpa kartu/border: pemisah cukup divider tipis. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun ActivityCard(item: HubItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(item.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                item.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.status,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Buka $title",
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsRow(item: HubItem, onClick: () -> Unit, showDivider: Boolean) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.status,
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = "Buka ${item.title}",
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
    if (showDivider) Divider()
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalDengerinColors.current.border),
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                value,
                color = colors.accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun RankedRow(rank: Int, title: String, subtitle: String, trailing: String) {
    val colors = LocalDengerinColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            color = colors.accent,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Text(trailing, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
    if (rank < 5) Divider()
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalDengerinColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.border,
            ),
        )
    }
}

@Composable
private fun QualityGroup(
    label: String,
    selected: AudioQuality,
    onSelect: (AudioQuality) -> Unit,
) {
    val colors = LocalDengerinColors.current
    Text(
        label,
        color = colors.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    AudioQuality.entries.forEach { quality ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(quality) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected == quality,
                onClick = { onSelect(quality) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.accent,
                    unselectedColor = colors.textSecondary,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(quality.label, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(quality.quotaLabel, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) colors.accentSoftContent else colors.textSecondary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accentSoftContainer else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
    )
}

@Composable
private fun StorageLegend(label: String, bytes: Long, swatch: Color) {
    val colors = LocalDengerinColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(swatch, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label · ${formatBytes(bytes)}",
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun rememberStorage(context: Context, container: AppContainer): State<StorageSnapshot> =
    // Placeholder ringan: StatFs/Coil disk cache tidak boleh dihitung di composition
    // (main thread). Nilai nyata dihitung di IO dan langsung mengganti placeholder.
    produceState(initialValue = StorageSnapshot(0L, 0L, 0L, 0L)) {
        while (isActive) {
            value = withContext(Dispatchers.IO) { readStorage(context, container) }
            delay(STORAGE_REFRESH_MS)
        }
    }

private data class StorageSnapshot(
    val used: Long,
    val free: Long,
    val dengerinBytes: Long,
    val audioBytes: Long,
)

private fun readStorage(context: Context, container: AppContainer): StorageSnapshot {
    val stat = StatFs(context.cacheDir.path)
    val total = stat.totalBytes
    val free = stat.availableBytes
    val audioBytes = container.audioCacheManager.cacheSizeBytes()
    val coilBytes = container.imageLoader.diskCache?.size ?: 0L
    return StorageSnapshot(
        used = max(0L, total - free),
        free = free,
        dengerinBytes = audioBytes + coilBytes,
        audioBytes = audioBytes,
    )
}

private const val STORAGE_REFRESH_MS = 1_000L

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))

private fun formatHours(seconds: Long): String =
    String.format(Locale.getDefault(), "%.1f jam", seconds / 3600f)

private fun themeStatus(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.SYSTEM -> "Sistem"
    AppThemeMode.LIGHT -> "Cool Slate Light"
    AppThemeMode.DARK -> "Dark Mode"
}
