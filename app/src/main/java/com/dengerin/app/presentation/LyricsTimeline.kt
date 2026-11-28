package com.dengerin.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * JANGAN LAKUKAN INI LAGI — daftar regresi performa lirik:
 *
 * 1. Jangan membaca `positionProvider()` atau `timeline.collectAsState().value` langsung
 *    di body Composable. Baca hanya di `derivedStateOf`, `graphicsLayer`, draw lambda,
 *    atau `snapshotFlow`.
 * 2. Jangan meneruskan posisi sebagai `Long`; teruskan provider `() -> Long`.
 * 3. Jangan mengganti provider dengan `{ timeline.value.first }` — itu bukan snapshot
 *    state, jadi `derivedStateOf` tidak akan pernah ter-invalidate.
 * 4. Jangan menyimpan hasil `animate*AsState` dengan `by` lalu dipakai di composition.
 *    Simpan `State<T>`-nya dan baca `.value` di dalam lambda `graphicsLayer` / draw.
 * 5. Jangan menganimasikan `fontSize`, `fontWeight`, atau `Color` teks lirik; itu memicu
 *    pengukuran ulang `StaticLayout` dan jank.
 * 6. Jangan memakai `CompositingStrategy.Offscreen` + `BlendMode.DstIn`, atau
 *    `Modifier.blur()` di belakang `LazyColumn`. Pakai dua overlay gradient biasa.
 * 7. Jangan memakai `fillMaxWidth(progress)` untuk seekbar; itu me-relayout tiap tick.
 *    Gambar progres di fase draw dengan `drawRoundRect`.
 */

typealias PositionProvider = () -> Long

/** Provider posisi/durasi yang snapshot-backed, aman dibaca di dalam `derivedStateOf`. */
data class TimelineProviders(
    val position: PositionProvider,
    val duration: () -> Long,
)

/**
 * Satu titik konversi `StateFlow` timeline menjadi lambda provider.
 *
 * `collectAsState()` dipanggil di sini, tetapi `.value` sengaja tidak dibaca di body
 * composable. Pembaruan timeline hanya meng-invalidate snapshot reader di dalam
 * `derivedStateOf` / `graphicsLayer` / `snapshotFlow`, bukan seluruh layar.
 */
@Composable
fun rememberTimelineProviders(timeline: StateFlow<Pair<Long, Long>>): TimelineProviders {
    val timelineState = timeline.collectAsState()
    return remember(timelineState) {
        TimelineProviders(
            position = { timelineState.value.first },
            duration = { timelineState.value.second },
        )
    }
}

/** Teks waktu hanya berubah khi detiknya berganti (1 Hz), bukan tiap tick ~160 ms. */
@Composable
fun TimelineProviders.rememberCurrentSecond(): State<Long> =
    remember(position) {
        derivedStateOf { position() / 1_000L }
    }
