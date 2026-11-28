package com.dengerin.app

import com.dengerin.app.data.genre.GenreCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreCatalogTest {

    @Test
    fun `katalog berisi enam belas genre unik`() {
        assertEquals(16, GenreCatalog.all.size)
        assertEquals(16, GenreCatalog.all.map { it.id }.distinct().size)
    }

    @Test
    fun `alias memetakan ke genre yang tepat`() {
        assertEquals("galau_sedih", GenreCatalog.matches("galau").first().id)
        assertEquals("galau_sedih", GenreCatalog.matches("baper").first().id)
        assertEquals("jpop_anime", GenreCatalog.matches("wibu").first().id)
        assertEquals("jpop_anime", GenreCatalog.matches("anime").first().id)
        assertEquals("dangdut_koplo", GenreCatalog.matches("koplo").first().id)
        assertEquals("lofi_belajar", GenreCatalog.matches("lofi").first().id)
        assertEquals("pop_indonesia", GenreCatalog.matches("pop indo").first().id)
        assertEquals("kpop", GenreCatalog.matches("kpop").first().id)
    }

    @Test
    fun `input kosong tidak mengembalikan genre`() {
        assertTrue(GenreCatalog.matches(" ").isEmpty())
    }
}
