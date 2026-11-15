package app.ipfsmedia.core.library

import haven.mobile.core.domain.MediaKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTest {

    private val state = library(
        media("a1", title = "Blue Hour", handle = "moth.eth", block = 30),
        media("a2", title = "Hours of Blue", block = 20),
        media("a3", title = "Café Nocturne", block = 10),
        media("v1", title = "Blue Hour (live)", kind = MediaKind.VIDEO, block = 5),
        daoStates = listOf(member(DAO_A, title = "Night Shift")),
        chapters = chapters("a3", titles = arrayOf("Blue Train", "Slow Rain")),
    )

    @Test
    fun `an empty query finds nothing`() {
        assertTrue(search("   ", state).isEmpty)
    }

    @Test
    fun `titles that start with the query come before ones that only contain it`() {
        assertEquals(listOf("a1", "a2"), search("blue", state).albums.map { it.id })
    }

    @Test
    fun `every word must match, in any order and any field`() {
        assertEquals(listOf("a1", "a2"), search("hour blue", state).albums.map { it.id }.sorted())
        assertEquals(listOf("a1", "a2", "a3"), search("moth", state).albums.map { it.id }.sorted())
        assertTrue(search("blue zebra", state).isEmpty)
    }

    @Test
    fun `accents and case do not matter`() {
        assertEquals(listOf("a3"), search("CAFE", state).albums.map { it.id })
    }

    @Test
    fun `songs are found once their album has been read`() {
        val hit = search("train", state).songs.single()
        assertEquals("Blue Train", hit.song.title)
        assertEquals("a3", hit.album.id)
    }

    @Test
    fun `videos uploaders and datadaos have their own groups`() {
        assertEquals(listOf("v1"), search("live", state).videos.map { it.id })
        assertEquals(listOf(OTHER), search("moth", state).uploaders.map { it.address })
        assertEquals(listOf(DAO_A), search("night", state).daos.map { it.key })
    }

    @Test
    fun `a group is capped`() {
        val many = library(*Array(80) { media("m$it", title = "Same name $it") })
        assertEquals(10, search("same", many, limitPerGroup = 10).albums.size)
    }
}
