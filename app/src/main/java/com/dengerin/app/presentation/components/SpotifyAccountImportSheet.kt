package com.dengerin.app.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dengerin.app.R
import com.dengerin.app.core.listImageRequest
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.repository.SpotifyCollectionImportStatus
import com.dengerin.app.data.spotify.SpotifyAccountImportRepository
import com.dengerin.app.data.spotify.SpotifyCollection
import com.dengerin.app.data.spotify.SpotifyCollectionKind
import com.dengerin.app.presentation.SpotifyAccountViewModel

/**
 * Pemilih koleksi akun Spotify: Lagu Disukai di paling atas, lalu playlist (termasuk
 * yang private) dan album tersimpan. Pencarian + multi-pilih + progres impor real-time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyAccountImportSheet(
    viewModel: SpotifyAccountViewModel,
    onDismiss: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val colors = LocalDengerinColors.current
    val state by viewModel.ui.collectAsState()
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "spotify_import"
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        viewModel.importFile(content.orEmpty(), fileName)
    }

    val allCollections = state.collections
    val visible = remember(allCollections, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) allCollections
        else allCollections.filter { it.name.contains(keyword, ignoreCase = true) }
    }
    val liked = visible.filter { it.kind == SpotifyCollectionKind.LIKED }
    val playlists = visible.filter { it.kind == SpotifyCollectionKind.PLAYLIST }
    val albums = visible.filter { it.kind == SpotifyCollectionKind.ALBUM }
    val selectedCollections = allCollections.filter { it.id in selected.value }
    val allSelected = allCollections.isNotEmpty() && selected.value.size == allCollections.size

    ModalBottomSheet(
        onDismissRequest = { if (!state.importState.isImporting) onDismiss() },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = colors.background,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.32f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Impor Koleksi Spotify",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            state.profile?.displayName?.let { name ->
                Text(
                    name,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onLogout,
                enabled = !state.importState.isImporting,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Keluar dari Spotify", color = colors.textSecondary)
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari playlist atau lagu…", color = colors.textSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        selected.value = if (allSelected) emptySet()
                        else allCollections.map { it.id }.toSet()
                    },
                    enabled = !state.importState.isImporting,
                ) {
                    Text(
                        if (allSelected) "Batalkan pilihan" else "Pilih Semua",
                        color = colors.accent,
                    )
                }
                Text(
                    "${selected.value.size} dipilih",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.refresh() }, enabled = !state.importState.isImporting) {
                    Text("Segarkan", color = colors.textSecondary)
                }
            }
            TextButton(
                onClick = { fileImportLauncher.launch(arrayOf("text/*", "application/json")) },
                enabled = !state.importState.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Impor File CSV / JSON Spotify",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Tanpa login",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = LocalBottomClearance.current,
                ),
            ) {
                if (state.isLoadingCollections) {
                    item(key = "loading") {
                        Text(
                            "Mengambil koleksi dari Spotify…",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                state.connectionError?.let { error ->
                    item(key = "error") {
                        Text(
                            "$error\nTekan Segarkan untuk mencoba lagi.",
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                if (liked.isNotEmpty()) {
                    item(key = "header_liked") { Section("Lagu Disukai Spotify") }
                    items(liked, key = { "liked_${it.id}" }) { collection ->
                        CollectionChoice(
                            collection = collection,
                            localStatus = state.importStatuses[SpotifyAccountImportRepository.COLLECTION_LIKED],
                            checked = collection.id in selected.value,
                            enabled = !state.importState.isImporting,
                        ) { toggle(selected, collection.id) }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item(key = "header_playlists") { Section("Playlist") }
                    items(playlists, key = { "playlist_${it.id}" }) { collection ->
                        CollectionChoice(
                            collection = collection,
                            localStatus = state.importStatuses[collection.id],
                            checked = collection.id in selected.value,
                            enabled = !state.importState.isImporting,
                        ) { toggle(selected, collection.id) }
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "header_albums") { Section("Album Tersimpan") }
                    items(albums, key = { "album_${it.id}" }) { collection ->
                        CollectionChoice(
                            collection = collection,
                            localStatus = state.importStatuses[collection.id],
                            checked = collection.id in selected.value,
                            enabled = !state.importState.isImporting,
                        ) { toggle(selected, collection.id) }
                    }
                }
                if (!state.isLoadingCollections && state.connectionError == null && visible.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Tidak ada koleksi yang cocok.",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }

            val importState = state.importState
            if (importState.isImporting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        if (importState.totalCollections > 0) {
                            (importState.completedCollections.toFloat() / importState.totalCollections)
                                .coerceIn(0f, 1f)
                        } else if (importState.total > 0) {
                            (importState.done.toFloat() / importState.total).coerceIn(0f, 1f)
                        } else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = colors.accent,
                    trackColor = colors.surfaceElevated,
                )
                Spacer(Modifier.height(6.dp))
                val status = buildString {
                    append(
                        if (importState.totalCollections > 0) {
                            "Mengimpor ${importState.completedCollections}/${importState.totalCollections} koleksi"
                        } else {
                            "Mengimpor ${importState.done}/${importState.total} koleksi"
                        },
                    )
                    if (importState.totalTracksImported > 0) {
                        append(" · ${importState.totalTracksImported} lagu")
                    }
                    if (importState.currentCollectionName.isNotBlank() &&
                        importState.currentCollectionName != importState.label
                    ) {
                        append("\n${importState.currentCollectionName}")
                    }
                }
                Text(
                    status,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        "Sembunyikan (Jalan di Latar Belakang)",
                        color = colors.accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            importState.message?.let { message ->
                Text(message, color = colors.accentSoftContent, style = MaterialTheme.typography.bodySmall)
            }
            importState.error?.let { error ->
                Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { viewModel.importSelected(selectedCollections) },
                enabled = selectedCollections.isNotEmpty() && !importState.isImporting,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(
                    if (importState.isImporting) "Mengimpor…"
                    else "Impor ${selectedCollections.size} koleksi",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun toggle(store: androidx.compose.runtime.MutableState<Set<String>>, id: String) {
    val current = store.value
    store.value = if (id in current) current - id else current + id
}

@Composable
private fun Section(title: String) {
    val colors = LocalDengerinColors.current
    Text(
        title,
        color = colors.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}

@Composable
private fun CollectionChoice(
    collection: SpotifyCollection,
    localStatus: SpotifyCollectionImportStatus?,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.textSecondary,
            ),
        )
        Spacer(Modifier.width(4.dp))
        if (collection.kind == SpotifyCollectionKind.LIKED) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.accentSoftContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            AsyncImage(
                model = listImageRequest(context, collection.coverUrl),
                contentDescription = null,
                placeholder = painterResource(R.drawable.ic_music_placeholder),
                error = painterResource(R.drawable.ic_music_placeholder),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                collection.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val hasNewTracks = localStatus?.isImported == true &&
                collection.trackCount > localStatus.localTrackCount
            val countLabel = when {
                localStatus?.isImported != true ->
                    if (collection.trackCount > 0) " · ${collection.trackCount} lagu" else ""
                hasNewTracks ->
                    " · Sudah diimpor · +${collection.trackCount - localStatus.localTrackCount} lagu baru"
                else -> " · Sudah diimpor · ${collection.trackCount} lagu"
            }
            Text(
                when (collection.kind) {
                    SpotifyCollectionKind.LIKED -> "Lagu Disukai"
                    SpotifyCollectionKind.PLAYLIST -> if (collection.isPrivate) "Playlist · Pribadi" else "Playlist"
                    SpotifyCollectionKind.ALBUM -> "Album"
                } + countLabel,
                color = if (hasNewTracks) colors.accentSoftContent else colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
