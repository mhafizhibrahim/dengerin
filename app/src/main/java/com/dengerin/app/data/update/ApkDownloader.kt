package com.dengerin.app.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Facade kompatibilitas. Unduhan & pemasangan kini ditangani [GitHubUpdateManager].
 */
object ApkDownloader {

    fun apkFileName(version: String): String = "Dengerin-v$version.apk"

    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        var result: File? = null
        GitHubUpdateManager.downloadApkWithProgress(url).collect { state ->
            when (state) {
                is GitHubUpdateManager.DownloadState.Downloading ->
                    withContext(Dispatchers.Main) {
                        onProgress(state.downloadedBytes, state.totalBytes)
                    }

                is GitHubUpdateManager.DownloadState.ReadyToInstall -> result = state.file

                is GitHubUpdateManager.DownloadState.Failed -> throw IOException(state.message)
            }
        }
        result ?: throw IOException("APK tidak tersedia")
    }

    fun install(context: Context, file: File) = GitHubUpdateManager.installApk(context, file)

    fun isApkUrl(url: String): Boolean = url.endsWith(".apk", ignoreCase = true)
}
