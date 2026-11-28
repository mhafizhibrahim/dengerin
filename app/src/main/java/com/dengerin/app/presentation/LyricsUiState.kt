package com.dengerin.app.presentation

import androidx.compose.runtime.Immutable
import com.dengerin.app.data.lyrics.LyricLine
import com.dengerin.app.data.model.Song

/**
 * State lirik "intinya" saja: berubah saat lagu/isi lirik/sync offset berubah.
 * Posisi, durasi dan indeks baris aktif sengaja tidak disimpan di sini — ketiganya
 * dihitung dari timeline pemutaran memakai `derivedStateOf` di sisi UI supaya daftar
 * lirik tidak recompose tiap tick ~160 ms.
 */
@Immutable
data class LyricsUiState(
    val song: Song? = null,
    val lines: List<LyricLine> = emptyList(),
    val plainLines: List<String> = emptyList(),
    val isSynced: Boolean = false,
    /** Badge sumber kecil, mis. "Lirik tersinkronisasi · YouTube CC". */
    val sourceLabel: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val syncOffsetMs: Long = 0L,
) {
    val hasLyrics: Boolean get() = if (isSynced) lines.isNotEmpty() else plainLines.isNotEmpty()
}
