package com.dengerin.app.data.update

import android.content.Context
import android.content.pm.PackageManager

/**
 * Facade kompatibilitas untuk pemanggil lama & unit test.
 * Logika sebenarnya ada di [GitHubUpdateManager] (OkHttp + Flow).
 */
object UpdateChecker {

    const val GITHUB_OWNER = GitHubUpdateManager.GITHUB_OWNER
    const val GITHUB_REPO = GitHubUpdateManager.GITHUB_REPO
    const val FALLBACK_RELEASE_URL = GitHubUpdateManager.FALLBACK_RELEASE_URL

    suspend fun fetchLatestRelease(): GitHubUpdateManager.ReleaseInfo? =
        GitHubUpdateManager.fetchLatest()

    /** versionName aplikasi saat ini, fallback "1.0.0". */
    fun currentVersion(context: Context): String {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    fun isNewerVersion(remote: String, current: String): Boolean =
        GitHubUpdateManager.isNewerVersion(remote, current)
}
