package com.dengerin.app.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dengerin.app.core.theme.LocalDengerinColors
import com.dengerin.app.data.profile.DeveloperUser
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.profile.isDeveloper
import com.dengerin.app.presentation.profile.AvatarFrame
import com.dengerin.app.presentation.profile.CosmeticRibbon
import com.dengerin.app.presentation.profile.DevBadge
import com.dengerin.app.presentation.profile.FramedAvatar
import com.dengerin.app.presentation.profile.GoogleSignInHelper
import com.dengerin.app.presentation.profile.avatarInitial
import com.dengerin.app.presentation.profile.HexLevelBadge
import com.dengerin.app.presentation.profile.ProfileTitle
import com.dengerin.app.presentation.profile.ProfileViewModel
import com.dengerin.app.presentation.profile.avatarFrames
import com.dengerin.app.presentation.profile.equippedTitles
import com.dengerin.app.presentation.profile.frameById
import com.dengerin.app.presentation.profile.isFrameUnlocked
import com.dengerin.app.presentation.profile.isTitleUnlocked
import com.dengerin.app.presentation.profile.levelOf
import com.dengerin.app.presentation.profile.unlockedTitleCount
import com.dengerin.app.presentation.profile.visibleProfileTitles
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signInHelper = remember(context) { GoogleSignInHelper(context) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeveloperPanel by remember { mutableStateOf(false) }
    val developer = isDeveloper(state.email)
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.saveProfilePhoto(uri)
            } else {
                Toast.makeText(context, "Pemilihan foto dibatalkan.", Toast.LENGTH_SHORT).show()
            }
        },
    )
    val onPickPhoto = {
        pickPhotoLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text("Profil", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = viewModel::refreshFromCloud, enabled = !state.isBusy) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Tarik profil dari cloud",
                                tint = colors.accent,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        val profile = state.profile
        if (!state.isLoggedIn || profile == null) {
            LoggedOutProfile(
                isBusy = state.isBusy,
                onSignIn = {
                    scope.launch {
                        signInHelper.signIn()
                            .onSuccess { idToken -> viewModel.signInWithGoogle(idToken) }
                            .onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: "Masuk Google gagal. Coba lagi ya.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        } else {
            LoggedInProfile(
                profile = profile,
                email = state.email,
                playlistCount = state.playlistCount,
                isBusy = state.isBusy,
                isDeveloper = developer,
                onEdit = { showEditDialog = true },
                onPickPhoto = onPickPhoto,
                onEquipFrame = viewModel::equipFrame,
                onToggleTitle = viewModel::toggleTitle,
                onOpenDeveloperPanel = { showDeveloperPanel = true },
                onSignOut = viewModel::signOut,
                modifier = Modifier.padding(padding),
            )

            if (showEditDialog) {
                EditProfileDialog(
                    profile = profile,
                    onDismiss = { showEditDialog = false },
                    onPickPhoto = onPickPhoto,
                    onClearPhoto = viewModel::clearProfilePhoto,
                    onSave = { name, bio, photoUrl ->
                        viewModel.saveIdentity(name, bio, photoUrl)
                        showEditDialog = false
                    },
                )
            }

            if (showDeveloperPanel && developer) {
                DeveloperPanelDialog(
                    email = state.email,
                    currentLevel = levelOf(profile),
                    viewModel = viewModel,
                    onDismiss = { showDeveloperPanel = false },
                )
            }
        }
    }
}

@Composable
private fun LoggedOutProfile(
    isBusy: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(colors.surfaceElevated, CircleShape)
                            .border(1.dp, colors.border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Profil belum terhubung",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Profil kamu, level kamu.",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Simpan progres dengar, kumpulkan bingkai, dan pakai title favorit. " +
                            "Masuk Google sepenuhnya opsional.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            ProfileBenefit(
                icon = Icons.Filled.Star,
                title = "Naik level dari mendengar",
                detail = "Tiap 60 detik putar dapat +10 XP. Level naik otomatis.",
            )
        }
        item {
            ProfileBenefit(
                icon = Icons.Filled.AutoAwesome,
                title = "Koleksi bingkai & title",
                detail = "Enam bingkai bercahaya dan sembilan pita bisa dipilih.",
            )
        }
        item {
            ProfileBenefit(
                icon = Icons.Filled.CloudDone,
                title = "Progres ikut ke perangkat lain",
                detail = "Level, XP, bingkai, dan title tersimpan di akun Google kamu.",
            )
        }

        item {
            Button(
                onClick = onSignIn,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.background,
                ),
            ) {
                Text(
                    text = if (isBusy) "Menyambungkan…" else "Masuk dengan Google",
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tanpa login pun, semua fitur musik tetap berjalan normal.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileBenefit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
) {
    val colors = LocalDengerinColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoggedInProfile(
    profile: UserProfile,
    email: String,
    playlistCount: Int,
    isBusy: Boolean,
    isDeveloper: Boolean,
    onEdit: () -> Unit,
    onPickPhoto: () -> Unit,
    onEquipFrame: (String) -> Unit,
    onToggleTitle: (String) -> Unit,
    onOpenDeveloperPanel: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val level = levelOf(profile)
    val frame = frameById(profile.equippedFrameId)
    val titles = equippedTitles(profile, email)
    val visibleTitles = visibleProfileTitles(email)
    val xpStart = UserProfile.xpForLevel(level)
    val xpNext = UserProfile.xpForLevel(level + 1)
    val progress = ((profile.totalXp - xpStart).toFloat() / (xpNext - xpStart).toFloat())
        .coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable(onClick = onPickPhoto)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Ganti foto profil dari galeri"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            FramedAvatar(
                                photoUrl = profile.photoUrl.takeIf { it.isNotBlank() },
                                photoBase64 = profile.photoBase64.takeIf { it.isNotBlank() },
                                frame = frame,
                                size = 76.dp,
                                defaultInitial = avatarInitial(profile.displayName),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = profile.displayName.ifBlank { "Pendengar Baru" },
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (isDeveloper) DevBadge(compact = true)
                                if (level >= 5) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = "Profil terverifikasi level 5",
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // FlowRow mencegah label seperti "New Listener" terpotong.
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                titles.take(2).forEach { title ->
                                    CosmeticRibbon(
                                        text = title.label,
                                        gradient = title.gradient,
                                        compact = true,
                                    )
                                }
                            }
                            if (email.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = email,
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        HexLevelBadge(level = level)
                    }
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(profile.bio, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Level $level",
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${profile.totalXp} XP",
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(colors.surfaceElevated, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .background(colors.accent, RoundedCornerShape(50)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${profile.totalXp - xpStart} / ${xpNext - xpStart} XP menuju Lv. ${level + 1}",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Total Jam", formatHours(profile.totalListeningSeconds), Modifier.weight(1f))
                        StatCard("Lagu Diputar", profile.totalSongsPlayed.toString(), Modifier.weight(1f))
                        StatCard(
                            "Title Terbuka",
                            "${unlockedTitleCount(profile, playlistCount, email)}/${visibleTitles.size}",
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit Profil", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }

        item { ProfileSectionTitle("Bingkai Avatar") }
        avatarFrames.chunked(3).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { frameItem ->
                        FrameChoice(
                            frame = frameItem,
                            level = level,
                            selected = frameItem.id == profile.equippedFrameId,
                            onClick = { onEquipFrame(frameItem.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item { ProfileSectionTitle("Pita Title (maks. 2)") }
        visibleTitles.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { titleItem ->
                        TitleChoice(
                            title = titleItem,
                            profile = profile,
                            playlistCount = playlistCount,
                            email = email,
                            selected = profile.equippedTitle1Id == titleItem.id ||
                                profile.equippedTitle2Id == titleItem.id,
                            onClick = { onToggleTitle(titleItem.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (isDeveloper) {
            item {
                OutlinedButton(
                    onClick = onOpenDeveloperPanel,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.accent),
                ) {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Developer Panel", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hanya tersedia untuk akun developer.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isBusy) "Menyimpan progres…" else "Keluar", color = colors.error, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Progres terakhir disimpan ke cloud sebelum keluar.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        color = LocalDengerinColors.current.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalDengerinColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Text(value, color = colors.accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FrameChoice(
    frame: AvatarFrame,
    level: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val unlocked = isFrameUnlocked(frame, level)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FramedAvatar(photoUrl = null, frame = frame, size = 44.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                frame.name,
                color = if (unlocked) colors.textPrimary else colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!unlocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Bingkai terkunci",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(frame.requirementLabel, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TitleChoice(
    title: ProfileTitle,
    profile: UserProfile,
    playlistCount: Int,
    email: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val unlocked = isTitleUnlocked(title, profile, playlistCount, email)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            CosmeticRibbon(text = title.label, gradient = title.gradient, compact = true)
            Spacer(Modifier.height(8.dp))
            Text(
                title.label,
                color = if (unlocked) colors.textPrimary else colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unlocked) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Title sedang dipakai",
                            tint = colors.accent,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                } else {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Title terkunci",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(if (unlocked && !selected) 0.dp else 3.dp))
                Text(
                    title.requirementLabel,
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun DeveloperPanelDialog(
    email: String,
    currentLevel: Int,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
) {
    if (!isDeveloper(email)) return

    val colors = LocalDengerinColors.current
    val state by viewModel.uiState.collectAsState()
    var levelInput by remember { mutableStateOf(currentLevel.toString()) }
    var query by remember { mutableStateOf("") }
    var targetUser by remember { mutableStateOf<DeveloperUser?>(null) }
    var targetLevelInput by remember { mutableStateOf("") }

    LaunchedEffect(query) { viewModel.searchDeveloperUsers(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = colors.accent)
                Spacer(Modifier.width(8.dp))
                Text("Developer Panel", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Atur Level Diri Sendiri",
                    color = colors.accent,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Level saat ini: Lv. $currentLevel",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1, 5, 10, 25, 50).forEach { preset ->
                        OutlinedButton(
                            onClick = { levelInput = preset.toString() },
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Text("Lv. $preset", color = colors.textPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = levelInput,
                    onValueChange = { levelInput = it.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("Level bebas (1–999)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { levelInput.toIntOrNull()?.let(viewModel::setOwnLevel) },
                    enabled = levelInput.toIntOrNull() != null && !state.isBusy,
                ) {
                    Text("Simpan Level Saya", color = colors.accent, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Atur Level Pengguna Lain",
                    color = colors.accent,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari nama atau email") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.isLoadingUsers) "Mencari pengguna…" else "${state.developerUsers.size} pengguna ditemukan",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.developerUsers, key = { it.uid }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetUser = user
                                    targetLevelInput = user.level.toString()
                                }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (user.photoUrl.isBlank()) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(30.dp),
                                )
                            } else {
                                AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(30.dp).clip(CircleShape),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.displayName.ifBlank { "Tanpa nama" },
                                    color = colors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    user.email.ifBlank { "Email belum tersedia" },
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Lv. ${user.level}",
                                color = colors.accent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = colors.textSecondary) }
        },
    )

    val target = targetUser
    if (target != null) {
        AlertDialog(
            onDismissRequest = { targetUser = null },
            containerColor = colors.surface,
            title = {
                Text(
                    "Set Level ${target.displayName.ifBlank { "Pengguna" }}",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(target.email, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetLevelInput,
                        onValueChange = { targetLevelInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Level Baru (1–999)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                }
            },
            confirmButton = {
                val newLevel = targetLevelInput.toIntOrNull()
                TextButton(
                    onClick = {
                        if (newLevel != null) {
                            viewModel.setUserLevel(target.uid, newLevel)
                            targetUser = null
                        }
                    },
                    enabled = newLevel != null && !state.isBusy,
                ) {
                    Text("Update", color = colors.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { targetUser = null }) {
                    Text("Batal", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val colors = LocalDengerinColors.current
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var photoUrl by remember { mutableStateOf(profile.photoUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Edit Profil", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.accent),
                ) {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Ganti Foto Profil dari Galeri",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Foto dipangkas 1:1, diresize 256 px, lalu disimpan ke cloud (tanpa Storage berbayar).",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (profile.photoBase64.isNotBlank() || profile.photoUrl.isNotBlank()) {
                    TextButton(onClick = onClearPhoto) {
                        Text("Hapus foto saat ini", color = colors.error)
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama tampilan") },
                    singleLine = true,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio singkat") },
                    maxLines = 3,
                    colors = fieldColors(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = photoUrl,
                    onValueChange = { photoUrl = it },
                    label = { Text("URL foto Google (opsional)") },
                    singleLine = true,
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, bio, photoUrl) }) {
                Text("Simpan", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = colors.textSecondary) }
        },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalDengerinColors.current.textPrimary,
    unfocusedTextColor = LocalDengerinColors.current.textPrimary,
    focusedBorderColor = LocalDengerinColors.current.accent,
    unfocusedBorderColor = LocalDengerinColors.current.border,
    focusedLabelColor = LocalDengerinColors.current.accent,
    unfocusedLabelColor = LocalDengerinColors.current.textSecondary,
    cursorColor = LocalDengerinColors.current.accent,
)

private fun formatHours(seconds: Long): String =
    String.format(Locale.getDefault(), "%.1f jam", seconds / 3600f)
