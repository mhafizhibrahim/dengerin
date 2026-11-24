package com.dengerin.app.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unduh APK rilis ke cache aplikasi lalu buka installer sistem lewat FileProvider.
 * Tidak butuh izin penyimpanan: file hanya hidup di cache aplikasi sendiri.
 */
object ApkDownloader {

    private const val TIMEOUT_MS = 20_000
    private const val BUFFER_SIZE = 16 * 1024

    fun apkFileName(version: String): String = "Dengerin-v$version.apk"

    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, apkFileName(version))

        try {
            if (target.exists() && !target.delete()) throw IOException("Gagal menimpa APK lama")
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.android.package-archive")
                setRequestProperty("User-Agent", "Dengerin-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }

            val total = connection.contentLength
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (total > 0) {
                            val fraction = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) { onProgress(fraction) }
                        }
                    }
                    output.flush()
                }
            }
            if (!target.exists() || target.length() == 0L) throw IOException("APK kosong")
            withContext(Dispatchers.Main) { onProgress(1f) }
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isApkUrl(url: String): Boolean = url.endsWith(".apk", ignoreCase = true)
}
