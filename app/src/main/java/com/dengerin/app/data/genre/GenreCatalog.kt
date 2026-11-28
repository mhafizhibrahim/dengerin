package com.dengerin.app.data.genre

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Kategori "Jelajahi Semua" di halaman Cari.
 *
 * [colorHex] adalah warna konten sampul genre (bukan token chrome UI): dipakai
 * untuk latar kartu dan gradien header Detail Genre, dengan teks putih kontras.
 * [aliases] membuat pencarian menerima kata-kata yang dipakai pengguna sehari-hari
 * (mis. "wibu" untuk J-Pop, "baper" untuk Galau).
 */
@Immutable
data class GenreCategory(
    val id: String,
    val title: String,
    val aliases: List<String>,
    val searchQuery: String,
    val colorHex: String,
)

object GenreCatalog {

    val all: List<GenreCategory> = listOf(
        GenreCategory(
            id = "pop_indonesia",
            title = "Pop Indonesia",
            aliases = listOf("pop", "pop indo", "pop indonesia", "indo pop", "idol"),
            searchQuery = "Pop Indonesia",
            colorHex = "#D81B60",
        ),
        GenreCategory(
            id = "galau_sedih",
            title = "Galau & Sedih",
            aliases = listOf("galau", "sedih", "baper", "galau malem", "melancholy"),
            searchQuery = "Lagu Galau Sedih",
            colorHex = "#3949AB",
        ),
        GenreCategory(
            id = "indie_folk",
            title = "Indie & Folk",
            aliases = listOf("indie", "folk", "senja", "indie folk", "indie senja"),
            searchQuery = "Indie Folk",
            colorHex = "#2E7D32",
        ),
        GenreCategory(
            id = "dangdut_koplo",
            title = "Dangdut & Koplo",
            aliases = listOf("koplo", "dangdut", "ambyar", "dangdut koplo"),
            searchQuery = "Dangdut Koplo",
            colorHex = "#E65100",
        ),
        GenreCategory(
            id = "jpop_anime",
            title = "J-Pop & Anime",
            aliases = listOf("wibu", "anime", "jpop", "j pop", "jepang", "ost anime"),
            searchQuery = "J-Pop Anime",
            colorHex = "#7B1FA2",
        ),
        GenreCategory(
            id = "kpop",
            title = "K-Pop",
            aliases = listOf("kpop", "k pop", "korea", "idol korea"),
            searchQuery = "K-Pop Hits",
            colorHex = "#C2185B",
        ),
        GenreCategory(
            id = "lofi_belajar",
            title = "Lo-Fi & Belajar",
            aliases = listOf("lofi", "lo fi", "belajar", "fokus", "study", "lofi fokus"),
            searchQuery = "Lo-Fi Belajar Fokus",
            colorHex = "#00695C",
        ),
        GenreCategory(
            id = "hiphop_rap",
            title = "Hip-Hop & Rap",
            aliases = listOf("hiphop", "hip hop", "rap", "rapper"),
            searchQuery = "Hip Hop Rap",
            colorHex = "#37474F",
        ),
        GenreCategory(
            id = "rock_metal",
            title = "Rock & Metal",
            aliases = listOf("rock", "metal", "alternatif", "hard rock"),
            searchQuery = "Rock Metal",
            colorHex = "#B71C1C",
        ),
        GenreCategory(
            id = "phonk_gaming",
            title = "Phonk & Gaming",
            aliases = listOf("phonk", "gaming", "game", "drift"),
            searchQuery = "Phonk Gaming",
            colorHex = "#4527A0",
        ),
        GenreCategory(
            id = "rnb_soul",
            title = "R&B & Soul",
            aliases = listOf("rnb", "r b", "soul", "r b soul"),
            searchQuery = "R&B Soul",
            colorHex = "#6A1B9A",
        ),
        GenreCategory(
            id = "akustik_santai",
            title = "Akustik & Santai",
            aliases = listOf("akustik", "acoustic", "santai", "chill"),
            searchQuery = "Akustik Santai",
            colorHex = "#558B2F",
        ),
        GenreCategory(
            id = "edm_party",
            title = "EDM & Party",
            aliases = listOf("edm", "party", "dance", "elektro"),
            searchQuery = "EDM Party Dance",
            colorHex = "#00838F",
        ),
        GenreCategory(
            id = "nostalgia_2000",
            title = "Nostalgia 2000-an",
            aliases = listOf("nostalgia", "2000", "2000an", "2000 an", "tahun 2000"),
            searchQuery = "Nostalgia 2000-an",
            colorHex = "#AD1457",
        ),
        GenreCategory(
            id = "musik_tidur",
            title = "Musik Tidur",
            aliases = listOf("tidur", "sleep", "lullaby", "relaksasi"),
            searchQuery = "Musik Tidur Relaksasi",
            colorHex = "#283593",
        ),
        GenreCategory(
            id = "olahraga_workout",
            title = "Olahraga & Workout",
            aliases = listOf("workout", "gym", "olahraga", "fitness", "motivasi"),
            searchQuery = "Workout Gym Motivation",
            colorHex = "#D84315",
        ),
    )

    fun find(id: String?): GenreCategory? = all.firstOrNull { it.id == id }

    /**
     * Cocokkan ketikan pengguna ke judul dan alias. Pencocokan persis didahulukan
     * supaya "pop" tidak memilih K-Pop, dan "galau" langsung memilih Galau & Sedih.
     */
    fun matches(input: String): List<GenreCategory> {
        val key = normalize(input)
        if (key.isBlank()) return emptyList()
        return all.mapNotNull { category ->
            val title = normalize(category.title)
            val aliases = category.aliases.map(::normalize)
            val score = when {
                title == key || aliases.any { it == key } -> 0
                title.contains(key) || key.contains(title) -> 1
                key.length >= 4 && aliases.any { it.contains(key) || key.contains(it) } -> 2
                else -> null
            }
            score?.let { it to category }
        }.sortedBy { it.first }.map { it.second }
    }

    private fun normalize(value: String): String = value.trim().lowercase()
        .replace('&', ' ')
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Warna konten kartu genre, diparse sekali di luar composable. */
fun GenreCategory.backgroundColor(): Color {
    val hex = colorHex.removePrefix("#")
    val argb = if (hex.length == 8) hex.toLong(16) else (0xFF000000L or hex.toLong(16))
    return Color(argb)
}
