package com.dengerin.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.theme.AppThemeMode
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.core.upgradeThumbnailUrl
import com.dengerin.app.data.db.LocalPlaylistEntity
import com.dengerin.app.data.model.Song
import com.dengerin.app.data.preferences.ThemePreferences
import com.dengerin.app.data.repository.LibraryRepository
import com.dengerin.app.data.update.UpdateChecker
import com.dengerin.app.presentation.LibraryViewModel
import com.dengerin.app.presentation.update.UpdateDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    themePreferences: ThemePreferences,
    onOpenPlaylist: (String) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.uiState.collectAsState()
    val action by viewModel.action.collectAsState()
    val themeMode by themePreferences.themeMode.collectAsState(initial = AppThemeMode.SYSTEM)
    val versionName = remember { UpdateChecker.currentVersion(context) }

    var showImportDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateRelease by remember { mutableStateOf<UpdateChecker.ReleaseInfo?>(null) }
    var linkInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }

    LaunchedEffect(action.importedPlaylistId) {
        action.importedPlaylistId?.let { id ->
            onOpenPlaylist(id)
            viewModel.consumeAction()
        }
    }

    // Dialog tetap terbuka sampai sampul 10 lagu pertama siap, lalu ditutup
    // otomatis bersamaan dengan perpindahan ke detail playlist.
    LaunchedEffect(action.importedPlaylistId, action.error) {
        if (action.importedPlaylistId != null || action.error != null) showImportDialog = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Koleksi",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Pengaturan tampilan",
                            tint = colors.textSecondary,
                        )
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Buat playlist lokal", tint = colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            text = "Impor Playlist Spotify",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tempel link playlist publik—tanpa perlu masuk.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.background,
                            ),
                        ) { Text("Impor Link Playlist") }
                        if (action.isBusy) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                action.progress ?: "Sedang mengambil data…",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        action.message?.let { message ->
                            Spacer(Modifier.height(8.dp))
                            Text(message, color = colors.accentSoftContent, style = MaterialTheme.typography.bodySmall)
                        }
                        action.error?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                CollectionRow(
                    icon = Icons.Filled.Favorite,
                    title = "Lagu Disukai",
                    subtitle = "${state.liked.size} lagu",
                    onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_LIKED) },
                )
            }
            item {
                CollectionRow(
                    icon = Icons.Filled.History,
                    title = "Riwayat Putar",
                    subtitle = "${state.history.size} lagu",
                    onClick = { onOpenPlaylist(LibraryRepository.PLAYLIST_HISTORY) },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Playlist",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Playlist baru", tint = colors.accent)
                    }
                }
            }

            state.playlists.forEach { playlist ->
                item { PlaylistRow(playlist = playlist, onClick = { onOpenPlaylist(playlist.id) }) }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tampilan",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ganti tema, langsung berubah kok.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeOption("Sistem", themeMode == AppThemeMode.SYSTEM, Modifier.weight(1f)) {
                            scope.launch { themePreferences.setThemeMode(AppThemeMode.SYSTEM) }
                        }
                        ThemeOption("Gelap", themeMode == AppThemeMode.DARK, Modifier.weight(1f)) {
                            scope.launch { themePreferences.setThemeMode(AppThemeMode.DARK) }
                        }
                        ThemeOption("Terang", themeMode == AppThemeMode.LIGHT, Modifier.weight(1f)) {
                            scope.launch { themePreferences.setThemeMode(AppThemeMode.LIGHT) }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Pembaruan",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Rilis terbaru dicek langsung dari GitHub.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Card(
                    onClick = {
                        if (checkingUpdate) return@Card
                        checkingUpdate = true
                        updateMessage = null
                        scope.launch {
                            val release = UpdateChecker.fetchLatestRelease()
                            checkingUpdate = false
                            when {
                                release == null ->
                                    updateMessage = "Gagal memeriksa pembaruan, cek koneksi internet"
                                UpdateChecker.isNewerVersion(release.version, versionName) ->
                                    updateRelease = release
                                else ->
                                    updateMessage = "Aplikasi sudah versi terbaru"
                            }
                        }
                    },
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = "Cek pembaruan",
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Cek Pembaruan",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Versi v$versionName",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            updateMessage?.let { message ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    message,
                                    color = if (message.startsWith("Gagal")) colors.error else colors.accentSoftContent,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    updateRelease?.let { release ->
        UpdateDialog(
            release = release,
            currentVersion = versionName,
            onDismiss = { updateRelease = null },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!action.isBusy) showImportDialog = false },
            title = { Text("Impor Link Playlist", color = colors.textPrimary) },
            text = {
                Column {
                    Text(
                        "Tempel link open.spotify.com/playlist/..., spotify:playlist:..., atau spotify.link/....",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                        ),
                    )
                    if (action.isBusy) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = action.progress ?: "Menyiapkan playlist…",
                            color = colors.accentSoftContent,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !action.isBusy,
                    onClick = {
                        clipboard.getText()?.let { linkInput = it.text }
                    },
                ) { Text("Tempel", color = colors.accent) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !action.isBusy,
                        onClick = { showImportDialog = false },
                    ) {
                        Text("Batal", color = colors.textSecondary)
                    }
                    TextButton(
                        enabled = linkInput.isNotBlank() && !action.isBusy,
                        onClick = {
                            viewModel.importLink(linkInput.trim())
                            linkInput = ""
                        },
                    ) { Text("Impor", color = colors.accent) }
                }
            },
            containerColor = colors.surface,
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Playlist Baru", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nama playlist") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.border,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylist(nameInput.trim())
                        nameInput = ""
                        showCreateDialog = false
                    },
                ) { Text("Buat", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tampilan", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption("Sistem", themeMode == AppThemeMode.SYSTEM, Modifier.fillMaxWidth()) {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.SYSTEM) }
                    }
                    ThemeOption("Gelap", themeMode == AppThemeMode.DARK, Modifier.fillMaxWidth()) {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.DARK) }
                    }
                    ThemeOption("Terang", themeMode == AppThemeMode.LIGHT, Modifier.fillMaxWidth()) {
                        scope.launch { themePreferences.setThemeMode(AppThemeMode.LIGHT) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Selesai", color = colors.accent)
                }
            },
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun CollectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: LocalPlaylistEntity, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = upgradeThumbnailUrl(playlist.coverUrl)
                    ?.takeIf { it.isNotBlank() }
                    ?: R.drawable.ic_music_placeholder,
                contentDescription = null,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (playlist.source) {
                        "SPOTIFY_SYNC" -> "Playlist · Spotify"
                        "SPOTIFY_IMPORT" -> "Playlist · Impor"
                        else -> "Playlist · Lokal"
                    },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Badge(playlist.source)
        }
    }
}

@Composable
private fun Badge(source: String) {
    val colors = LocalDengerinColors.current
    val label = when (source) {
        "SPOTIFY_SYNC" -> "Spotify"
        "SPOTIFY_IMPORT" -> "Impor"
        else -> "Lokal"
    }
    Text(
        text = label,
        color = colors.accentSoftContent,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.accentSoftContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = if (selected) colors.accent else colors.textSecondary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .background(if (selected) colors.accentSoftContainer else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
