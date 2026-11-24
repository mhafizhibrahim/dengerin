package com.dengerin.app.core.util

/** Format durasi santai: 3:42 atau 1:02:15. */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "$minutes:${seconds.pad()}"
    }
}

/** Ringkasan total durasi playlist: `1 j 49 mnt` atau `49 mnt`. */
fun formatTotalDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0 mnt"
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours j $minutes mnt" else "$minutes mnt"
}

private fun Long.pad(): String = toString().padStart(2, '0')
