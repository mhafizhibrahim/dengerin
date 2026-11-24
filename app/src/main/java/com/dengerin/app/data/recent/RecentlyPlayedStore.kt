package com.dengerin.app.data.recent

import com.dengerin.app.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Daftar "Baru Diputar" hanya untuk sesi aplikasi ini (tidak disimpan ke disk). */
class RecentlyPlayedStore {
    private val mutableSongs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = mutableSongs.asStateFlow()

    fun add(song: Song) {
        mutableSongs.update { current ->
            (listOf(song) + current.filterNot { it.id == song.id }).take(20)
        }
    }
}
