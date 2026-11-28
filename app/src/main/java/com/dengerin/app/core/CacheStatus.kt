package com.dengerin.app.core

import androidx.compose.runtime.compositionLocalOf

/**
 * Status cache audio untuk baris lagu. Nilainya berasal dari
 * `AudioCacheManager.cachedTrackIds` (StateFlow, dihitung di Dispatchers.IO).
 * Dipasang sekali di root aplikasi supaya composable tidak pernah memanggil
 * SimpleCache/File I/O saat fast scroll.
 */
val LocalCachedTrackIds = compositionLocalOf<Set<String>> { emptySet() }
