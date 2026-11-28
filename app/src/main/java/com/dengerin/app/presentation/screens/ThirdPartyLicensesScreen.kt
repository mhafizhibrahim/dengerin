package com.dengerin.app.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dengerin.app.core.theme.LocalBottomClearance
import com.dengerin.app.core.theme.LocalDengerinColors
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.util.withContext
import java.util.Locale

private data class LicenseText(
    val title: String,
    val content: String,
    val url: String?,
)

private data class ThirdPartyLicenseEntry(
    val id: String,
    val name: String,
    val organization: String,
    val version: String,
    val websiteUrl: String?,
    val repositoryUrl: String?,
    val licenseBadges: List<String>,
    val licenseTexts: List<LicenseText>,
)

/**
 * Daftar lisensi pihak ketiga. Data dependency dibaca dari `res/raw/aboutlibraries.json`
 * yang dihasilkan plugin AboutLibraries saat kompilasi, lalu digabung dengan dua layanan
 * eksternal yang dipanggil aplikasi (LRCLIB dan GitHub Releases API).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartyLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<ThirdPartyLicenseEntry?>(null) }
    val entries by produceState(initialValue = emptyList<ThirdPartyLicenseEntry>()) {
        value = loadThirdPartyLicenses(context.applicationContext)
    }
    val detail = selected

    BackHandler(enabled = detail != null) { selected = null }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.name ?: "Lisensi Pihak Ketiga",
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
                            if (detail != null) selected = null else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (detail != null) "Kembali ke daftar lisensi" else "Kembali",
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
        },
    ) { scaffoldPadding ->
        val listPadding = licenseListPadding(scaffoldPadding)
        if (detail != null) {
            LicenseDetail(
                entry = detail,
                contentPadding = PaddingValues(
                    start = 16.dp + scaffoldPadding.calculateStartPadding(LocalLayoutDirection.current),
                    top = 8.dp + scaffoldPadding.calculateTopPadding(),
                    end = 16.dp + scaffoldPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = LocalBottomClearance.current,
                ),
                onOpenUrl = { url -> openUrl(context, url) },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = scaffoldPadding.calculateTopPadding())
                    .imePadding(),
            ) {
                LicenseFilterField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                val keyword = query.trim()
                val visible = if (keyword.isEmpty()) {
                    entries
                } else {
                    entries.filter { it.name.contains(keyword, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                text = if (keyword.isEmpty()) {
                                    "Data lisensi belum tersedia."
                                } else {
                                    "Tidak ada pustaka yang cocok dengan \"$keyword\"."
                                },
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(items = visible, key = { it.id }) { entry ->
                        LicenseRow(entry = entry, onClick = { selected = entry })
                        ThinDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun licenseListPadding(scaffoldPadding: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
        top = 8.dp,
        end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
        bottom = LocalBottomClearance.current,
    )
}

@Composable
private fun LicenseFilterField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDengerinColors.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(52.dp),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        placeholder = {
            Text(
                "Cari nama pustaka...",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = "Cari lisensi",
                tint = colors.textSecondary,
            )
        },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Hapus filter",
                        tint = colors.textSecondary,
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            disabledContainerColor = colors.surfaceElevated,
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

@Composable
private fun LicenseRow(entry: ThirdPartyLicenseEntry, onClick: () -> Unit) {
    val colors = LocalDengerinColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            entry.name,
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.organization,
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            entry.licenseBadges.take(2).forEach { badge ->
                LicenseBadge(badge)
                Spacer(Modifier.width(4.dp))
            }
            if (entry.licenseBadges.size > 2) {
                Text(
                    "+${entry.licenseBadges.size - 2}",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                entry.version,
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LicenseBadge(text: String) {
    val colors = LocalDengerinColors.current
    Text(
        text = text,
        color = colors.accentSoftContent,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.accentSoftContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun LicenseDetail(
    entry: ThirdPartyLicenseEntry,
    contentPadding: PaddingValues,
    onOpenUrl: (String) -> Unit,
) {
    val colors = LocalDengerinColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                entry.name,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.organization} • ${entry.version}",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            SectionLabel("Tautan")
            val website = entry.websiteUrl
            val repository = entry.repositoryUrl
            if (website == null && repository == null) {
                Text(
                    "Tautan tidak tersedia dalam metadata.",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                website?.let {
                    LinkRow(label = "Situs web", url = it, onOpenUrl = onOpenUrl)
                    ThinDivider()
                }
                repository?.let { LinkRow(label = "Repositori", url = it, onOpenUrl = onOpenUrl) }
            }
        }

        item {
            SectionLabel("Lisensi")
        }

        items(items = entry.licenseTexts, key = { "${entry.id}-${it.title}" }) { license ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    license.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                license.url?.let { url ->
                    Spacer(Modifier.height(4.dp))
                    LinkRow(label = "Teks lisensi", url = url, onOpenUrl = onOpenUrl)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surface)
                        .padding(12.dp),
                ) {
                    Text(
                        license.content,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = LocalDengerinColors.current.textSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun LinkRow(label: String, url: String, onOpenUrl: (String) -> Unit) {
    val colors = LocalDengerinColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
            .padding(vertical = 6.dp),
    ) {
        Text(label, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            url,
            color = colors.accent,
            style = MaterialTheme.typography.bodySmall,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThinDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalDengerinColors.current.border),
    )
}

private fun loadThirdPartyLicenses(context: Context): List<ThirdPartyLicenseEntry> {
    // runCatching: resource aboutlibraries.json bisa belum ter-generate / gagal dibaca.
    // Fallback memakai daftar pustaka inti agar halaman Pengaturan tetap aman.
    val scanned = runCatching {
        Libs.Builder()
            .withContext(context.applicationContext)
            .build()
            .libraries
            .map { it.toEntry() }
    }.getOrElse { coreFallbackLicenses() }
    return (scanned + externalLicenseEntries())
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
}

/** Daftar minimal saat metadata AboutLibraries tidak tersedia. */
private fun coreFallbackLicenses(): List<ThirdPartyLicenseEntry> = listOf(
    fallbackLicense(
        id = "androidx-media3",
        name = "AndroidX Media3",
        organization = "Google LLC",
        version = "1.6.1",
        url = "https://developer.android.com/media/media3",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    fallbackLicense(
        id = "androidx-room",
        name = "AndroidX Room",
        organization = "Google LLC",
        version = "2.8.5",
        url = "https://developer.android.com/jetpack/androidx/releases/room",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    fallbackLicense(
        id = "coil",
        name = "Coil",
        organization = "Coil Contributors",
        version = "2.7.0",
        url = "https://coil-kt.github.io/coil/",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    fallbackLicense(
        id = "firebase",
        name = "Firebase Android SDK",
        organization = "Google LLC",
        version = "BoM 35.0.0",
        url = "https://firebase.google.com/docs/android/setup",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    fallbackLicense(
        id = "okhttp",
        name = "OkHttp",
        organization = "Square, Inc.",
        version = "4.12.0",
        url = "https://square.github.io/okhttp/",
        license = "Apache License 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    fallbackLicense(
        id = "newpipe-extractor",
        name = "NewPipeExtractor",
        organization = "Team NewPipe",
        version = "v0.27.1",
        url = "https://github.com/TeamNewPipe/NewPipeExtractor",
        license = "GNU GPL v3",
        licenseUrl = "https://www.gnu.org/licenses/gpl-3.0.html",
    ),
)

private fun fallbackLicense(
    id: String,
    name: String,
    organization: String,
    version: String,
    url: String,
    license: String,
    licenseUrl: String,
) = ThirdPartyLicenseEntry(
    id = id,
    name = name,
    organization = organization,
    version = version,
    websiteUrl = url,
    repositoryUrl = url,
    licenseBadges = listOf(license),
    licenseTexts = listOf(
        LicenseText(
            title = license,
            content = "Teks lisensi lengkap tersedia di $licenseUrl",
            url = licenseUrl,
        ),
    ),
)

private fun Library.toEntry(): ThirdPartyLicenseEntry {
    val licenses = this.licenses.toList().sortedBy { it.name.lowercase(Locale.getDefault()) }
    return ThirdPartyLicenseEntry(
        id = uniqueId,
        name = name.takeIf { it.isNotBlank() } ?: uniqueId,
        organization = organization?.name?.takeIf { it.isNotBlank() }
            ?: "Pengembang pihak ketiga",
        version = artifactVersion?.takeIf { it.isNotBlank() } ?: "-",
        websiteUrl = website?.takeIf { it.isNotBlank() },
        repositoryUrl = scm?.url?.takeIf { it.isNotBlank() },
        licenseBadges = licenses.map { license ->
            license.spdxId?.takeIf { it.isNotBlank() } ?: license.name
        }.ifEmpty { listOf("Lisensi tidak tercantum") },
        licenseTexts = licenses.map { license -> license.toLicenseText() }
            .ifEmpty {
                listOf(
                    LicenseText(
                        title = "Lisensi tidak tercantum",
                        content = "Metadata dependensi tidak menyertakan informasi lisensi.",
                        url = null,
                    ),
                )
            },
    )
}

private fun License.toLicenseText(): LicenseText = LicenseText(
    title = name,
    content = licenseContent?.takeIf { it.isNotBlank() }?.let(::plainLicenseText)
        ?: "Teks lengkap tidak disertakan dalam metadata AboutLibraries. "
            + "Gunakan tautan lisensi bila tersedia.",
    url = url?.takeIf { it.isNotBlank() },
)

private fun plainLicenseText(content: String): String =
    if (content.contains('<')) {
        Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT).toString()
    } else {
        content
    }

private fun externalLicenseEntries(): List<ThirdPartyLicenseEntry> = listOf(
    ThirdPartyLicenseEntry(
        id = "external-lrclib-api",
        name = "LRCLIB Open Lyrics API",
        organization = "LRCLIB Contributors",
        version = "API publik",
        websiteUrl = "https://lrclib.net",
        repositoryUrl = "https://github.com/tranxuanthang/lrclib",
        licenseBadges = listOf("MIT", "API Terms"),
        licenseTexts = listOf(
            LicenseText(
                title = "MIT (kode server) + Ketentuan layanan API",
                content = LRCLIB_LICENSE_TEXT,
                url = "https://opensource.org/license/mit",
            ),
        ),
    ),
    ThirdPartyLicenseEntry(
        id = "external-github-releases-api",
        name = "GitHub Releases API",
        organization = "GitHub, Inc.",
        version = "REST API v3",
        websiteUrl = "https://docs.github.com/en/rest/releases",
        repositoryUrl = null,
        licenseBadges = listOf("GitHub API Terms"),
        licenseTexts = listOf(
            LicenseText(
                title = "GitHub REST API Terms",
                content = GITHUB_API_LICENSE_TEXT,
                url = "https://docs.github.com/en/site-policy/github-terms/github-api-terms",
            ),
        ),
    ),
)

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka tautan.", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Tautan tidak dapat dibuka.", Toast.LENGTH_SHORT).show()
    }
}

private val LRCLIB_LICENSE_TEXT = """
MIT License (kode sumber server LRCLIB)

Copyright (c) LRCLIB contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

Ketentuan penggunaan layanan: Dengerin mengambil lirik dari API publik LRCLIB
melalui permintaan HTTP. Data lirik dan layanan API tunduk pada kebijakan LRCLIB
(https://lrclib.net): gunakan secara wajar, sertakan atribusi sumber lirik, dan
jangan menyalahgunakan atau menjual data API secara langsung.
""".trimIndent()

private val GITHUB_API_LICENSE_TEXT = """
Dengerin memanggil GitHub Releases API hanya untuk memeriksa ketersediaan versi
aplikasi terbaru di GitHub.

Layanan ini bukan perangkat lunak sumber terbuka dan tidak menggunakan lisensi
MIT/Apache. Penggunaannya tunduk pada GitHub REST API Terms
(https://docs.github.com/en/site-policy/github-terms/github-api-terms) serta
GitHub Acceptable Use Policies: jangan melewati batas laju, jangan mengumpulkan
data secara massal, dan sertakan identifikasi aplikasi yang wajar. Seluruh rilis
dan kode sumber tetap menjadi hak pemilik repositori masing-masing.
""".trimIndent()
