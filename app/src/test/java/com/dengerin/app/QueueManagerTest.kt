package com.dengerin.app

import com.dengerin.app.data.model.Song
import com.dengerin.app.player.QueueManager
import com.dengerin.app.player.QueueSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Mesin antrean murni JVM: threading disuntik supaya tidak butuh Robolectric. */
class QueueManagerTest {
    private fun queueManager() = QueueManager(
        isMainThread = { true },
        postToMain = { block -> block() },
    )

    private fun song(id: String, title: String = id) = Song(
        id = id,
        title = title,
        artist = "Artis",
        durationMs = 1_000L,
        url = "https://www.youtube.com/watch?v=$id",
    )

    @Test
    fun `lagu duplikat punya queueId unik dan bisa dihapus satu per satu`() {
        val queue = queueManager()
        val a = song("a")
        val b = song("b")
        queue.setQueue(listOf(a, b), shuffle = false, selected = a, title = "Playlist")

        queue.playNext(a)
        queue.addToQueue(a)

        val userItems = queue.upcomingItems.filter { it.sourceType == QueueSourceType.USER_QUEUE }
        assertEquals(2, userItems.size)
        assertNotEquals(userItems[0].queueId, userItems[1].queueId)
        assertEquals(4, queue.items.map { it.queueId }.toSet().size)

        queue.removeUpcomingItem(userItems[0].queueId)

        assertEquals(1, queue.upcomingItems.count { it.sourceType == QueueSourceType.USER_QUEUE })
        assertEquals(userItems[1].queueId, queue.items[1].queueId)
        assertEquals(listOf("a", "a", "b"), queue.activeSongs.map { it.id })
    }

    @Test
    fun `zona user mendahului konteks dan clear hanya menghapus zona user`() {
        val queue = queueManager()
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val u1 = song("u1")
        val u2 = song("u2")
        queue.setQueue(listOf(a, b, c), shuffle = false, selected = a, title = "Playlist")

        queue.playNext(u1)
        queue.addToQueue(u2)

        assertEquals(listOf("a", "u1", "u2", "b", "c"), queue.activeSongs.map { it.id })

        queue.clearUserQueue()

        assertEquals(listOf("a", "b", "c"), queue.activeSongs.map { it.id })
    }

    @Test
    fun `shuffle hanya mengacak sisa konteks dan restore urutan asli`() {
        val queue = queueManager()
        val a = song("a")
        val b = song("b")
        val c = song("c")
        val d = song("d")
        val u = song("u")
        queue.setQueue(listOf(a, b, c, d), shuffle = false, selected = a, title = "Playlist")
        queue.playNext(u)

        queue.toggleShuffle(true)

        assertEquals("a", queue.activeSongs[0].id)
        assertEquals("u", queue.activeSongs[1].id)
        assertEquals(setOf("b", "c", "d"), queue.activeSongs.drop(2).map { it.id }.toSet())

        queue.toggleShuffle(false)

        assertEquals(listOf("a", "u", "b", "c", "d"), queue.activeSongs.map { it.id })
    }

    @Test
    fun `shuffle tidak mengubah lagu yang sudah terlewat`() {
        val queue = queueManager()
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        queue.setQueue(songs, shuffle = false, selected = songs[0], title = "Playlist")
        queue.setCurrentIndex(1)

        queue.toggleShuffle(true)

        assertEquals("a", queue.activeSongs[0].id)
        assertEquals("b", queue.activeSongs[1].id)
        assertEquals(setOf("c", "d"), queue.activeSongs.drop(2).map { it.id }.toSet())

        queue.toggleShuffle(false)

        assertEquals(listOf("a", "b", "c", "d"), queue.activeSongs.map { it.id })
    }

    @Test
    fun `autoplay ditaruh setelah konteks dan tidak ikut terhapus saat clear user queue`() {
        val queue = queueManager()
        val a = song("a")
        val b = song("b")
        val u = song("u")
        val rec = song("rec")
        queue.setQueue(listOf(a, b), shuffle = false, selected = a, title = "Playlist")
        queue.addToQueue(u)
        queue.setAutoplayQueue(listOf(rec), "Putar Otomatis · Lagu Serupa")

        assertEquals(listOf("a", "u", "b", "rec"), queue.activeSongs.map { it.id })
        assertEquals(3, queue.indexOfQueueId(queue.items[3].queueId))

        queue.clearUserQueue()

        assertEquals(listOf("a", "b", "rec"), queue.activeSongs.map { it.id })
        assertEquals(
            "Putar Otomatis · Lagu Serupa",
            queue.items.last().contextTitle,
        )
    }
}
