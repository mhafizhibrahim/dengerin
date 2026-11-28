package com.dengerin.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sumber tunggal untuk pengecekan rilis GitHub, unduhan APK, dan pemasangan.
 *
 * Semua kegagalan jaringan (offline, timeout, rate-limit, JSON rusak) mengembalikan null /
 * [DownloadState.Failed] sehingga pemanggil bisa tetap fail-silent saat pemutaran offline.
 */
object GitHubUpdateManager {

    // Ubah di sini jika repo GitHub berpindah (git remote origin).
    const val GITHUB_OWNER = "mhafizhibrahim"
    const val GITHUB_REPO = "dengerin"

    const val FALLBACK_RELEASE_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val APK_DIRECTORY = "updates"
    private const val APK_FILE_NAME = "dengerin-update.apk"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val FALLBACK_CHANGELOG = "Perbaikan bug dan peningkatan performa."
    private const val BUFFER_SIZE = 16 * 1024
    private const val EMIT_EVERY_BYTES = 64L * 1024L

    data class ReleaseInfo(
        val version: String,
        val title: String,
        val changelog: String,
        val htmlUrl: String,
        /** URL asset .apk pertama, null bila rilis tidak menyertakan APK. */
        val apkUrl: String?,
        /** Ukuran asset APK dalam byte, null bila tidak tersedia. */
        val apkSizeBytes: Long?,
    ) {
        val releaseUrl: String
            get() = htmlUrl.ifBlank { FALLBACK_RELEASE_URL }
    }

    sealed interface DownloadState {
        data class Downloading(
            val percent: Int,
            val downloadedBytes: Long,
            val totalBytes: Long,
        ) : DownloadState

        data class ReadyToInstall(val file: File) : DownloadState

        data class Failed(val message: String) : DownloadState
    }

    @Volatile
    private var appContext: Context? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Dipanggil sekali dari [com.dengerin.app.DengerinApplication]. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: error("GitHubUpdateManager belum diinisialisasi")

    /** Ambil rilis terbaru. Null berarti gagal / belum ada release. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        var result: ReleaseInfo? = null
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Dengerin-Android")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                result = parseRelease(JSONObject(body))
            }
        } catch (e: Exception) {
            result = null
        }
        result
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        val version = stripVersionPrefix(json.optString("tag_name"))
        if (version.isEmpty()) return null

        var apkUrl: String? = null
        var apkSize: Long? = null
        json.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url").ifBlank { null } ?: continue
                apkUrl = url
                val size = asset.optLong("size", -1L)
                apkSize = if (size >= 0) size else null
                break
            }
        }

        return ReleaseInfo(
            version = version,
            title = json.optString("name").ifBlank { "Versi $version" },
            changelog = json.optString("body").ifBlank { FALLBACK_CHANGELOG },
            htmlUrl = json.optString("html_url").ifBlank { FALLBACK_RELEASE_URL },
            apkUrl = apkUrl,
            apkSizeBytes = apkSize,
        )
    }

    /**
     * Perbandingan semantic versioning per segmen angka (major.minor.patch).
     * Awalan v/V diabaikan, segmen kurang dianggap 0, suffix pre-release setelah '-' diabaikan.
     * Contoh: "1.0.10" > "1.0.2" (bukan perbandingan string).
     * Segmen ke-4 atau lebih ikut dibandingkan (mis. "1.3.0.1" > "1.3.0").
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = versionSegments(remote)
        val currentParts = versionSegments(current)
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun stripVersionPrefix(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V").trim()

    private fun versionSegments(version: String): List<Int> =
        stripVersionPrefix(version)
            .substringBefore('-')
            .split('.')
            .map { it.trim().toIntOrNull() ?: 0 }

    /**
     * Unduh APK ke cache aplikasi sambil memancarkan progres.
     * File disimpan di cacheDir/updates/dengerin-update.apk (sesuai FileProvider).
     */
    fun downloadApkWithProgress(apkUrl: String): Flow<DownloadState> = flow {
        val context = requireContext()
        val directory = File(context.cacheDir, APK_DIRECTORY).apply { mkdirs() }
        val target = File(directory, APK_FILE_NAME)

        try {
            if (target.exists() && !target.delete()) throw IOException("Gagal menimpa APK lama")

            val request = Request.Builder()
                .url(apkUrl)
                .header("Accept", APK_MIME_TYPE)
                .header("User-Agent", "Dengerin-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Respons unduhan kosong")
                val total = body.contentLength()
                var downloaded = 0L
                var lastEmit = 0L

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastEmit >= EMIT_EVERY_BYTES) {
                                lastEmit = downloaded
                                emit(progressState(downloaded, total))
                            }
                        }
                        output.flush()
                    }
                }

                if (target.length() == 0L) throw IOException("APK kosong")
                val finalTotal = if (total > 0) total else target.length()
                emit(DownloadState.Downloading(100, target.length(), finalTotal))
                emit(DownloadState.ReadyToInstall(target))
            }
        } catch (e: Exception) {
            target.delete()
            emit(
                DownloadState.Failed(
                    e.message?.takeIf { it.isNotBlank() } ?: "Unduh gagal. Cek koneksi internet, ya.",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun progressState(downloaded: Long, total: Long): DownloadState.Downloading {
        val percent = if (total > 0) {
            ((downloaded * 100L) / total).coerceIn(0L, 100L).toInt()
        } else {
            0
        }
        return DownloadState.Downloading(percent, downloaded, total)
    }

    fun canInstallApk(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installApk(context: Context, file: File) {
        // FileProvider/pengelola instal bisa menolak (path, authority, atau tidak ada
        // installer). Kegagalan harus berhenti di sini, bukan di coroutine UI.
        runCatching {
            if (!file.exists()) error("Berkas pembaruan tidak ditemukan.")
            val uri = FileProvider.getUriForFile(
                context.applicationContext,
                "${context.packageName}$AUTHORITY_SUFFIX",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                Toast.makeText(context, "Pembaruan tidak dapat dibuka.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Intent pengaturan "Instal aplikasi yang tidak dikenal" (Android O+). */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Arahkan ke pengaturan "Instal aplikasi yang tidak dikenal" (Android O+). */
    fun openInstallPermissionSettings(context: Context) {
        runCatching { context.startActivity(installPermissionIntent(context)) }
    }

    fun openReleasePage(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // Tidak ada aplikasi yang bisa membuka URL — abaikan.
        }
    }

    fun hasApkAsset(release: ReleaseInfo): Boolean = release.apkUrl?.isNotBlank() == true
}
