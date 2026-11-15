package app.ipfsmedia.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistResolveTest {

    private fun savedSong(index: Int, title: String, pieceCid: String = "bafkzcib-a") = PlayableEntry(
        recordKey = "a",
        chapterIndex = index,
        kind = EntryKind.SONG,
        pieceCid = pieceCid,
        startMs = index * 60_000L,
        endMs = (index + 1) * 60_000L,
        title = title,
        albumTitle = "Old album title",
        uploaderName = "old name",
        uploaderAddress = OTHER,
        daoKey = DAO_A,
        durationMs = 60_000,
    )

    @Test
    fun `a record that is gone stays in the playlist marked unavailable with its saved titles`() {
        val resolved = resolveEntry(savedSong(1, "Two"), library())
        assertEquals(EntryAvailability.UNAVAILABLE, resolved.availability)
        assertEquals("Two", resolved.entry.title)
        assertEquals("Old album title", resolved.entry.albumTitle)
        assertNull(resolved.item)
    }

    @Test
    fun `a song plays from its saved times even when the chapter cache has been dropped`() {
        val resolved = resolveEntry(savedSong(1, "Two"), library(media("a")))
        assertEquals(EntryAvailability.AVAILABLE, resolved.availability)
        assertEquals(60_000L, resolved.entry.startMs)
        assertEquals(120_000L, resolved.entry.endMs)
        // Titles of the album itself are brought up to date.
        assertEquals("Title a", resolved.entry.albumTitle)
    }

    @Test
    fun `a re-uploaded album re-matches the song at the same index when the title still matches`() {
        val state = library(
            media("a", pieceCid = "bafkzcib-new", durationSeconds = 400),
            chapters = listOf(
                ChapterRow("a", 0, 0, "One", "bafkzcib-new"),
                ChapterRow("a", 1, 90_000, "Two", "bafkzcib-new"),
                ChapterRow("a", 2, 200_000, "Three", "bafkzcib-new"),
            ),
        )
        val resolved = resolveEntry(savedSong(1, "two ", pieceCid = "bafkzcib-old"), state)
        assertEquals(EntryAvailability.AVAILABLE, resolved.availability)
        assertEquals(90_000L, resolved.entry.startMs)
        assertEquals(200_000L, resolved.entry.endMs)
        assertEquals("bafkzcib-new", resolved.entry.pieceCid)
    }

    @Test
    fun `a re-uploaded album re-matches by title when the song moved`() {
        val state = library(
            media("a", pieceCid = "bafkzcib-new"),
            chapters = listOf(
                ChapterRow("a", 0, 0, "Intro", "bafkzcib-new"),
                ChapterRow("a", 1, 30_000, "One", "bafkzcib-new"),
                ChapterRow("a", 2, 90_000, "Two", "bafkzcib-new"),
            ),
        )
        val resolved = resolveEntry(savedSong(1, "Two", pieceCid = "bafkzcib-old"), state)
        assertEquals(EntryAvailability.AVAILABLE, resolved.availability)
        assertEquals(2, resolved.entry.chapterIndex)
        assertEquals(90_000L, resolved.entry.startMs)
    }

    @Test
    fun `a song that cannot be found in the new file is marked changed, not played from the wrong spot`() {
        val state = library(
            media("a", pieceCid = "bafkzcib-new"),
            chapters = listOf(ChapterRow("a", 0, 0, "Something else", "bafkzcib-new")),
        )
        assertEquals(EntryAvailability.CHANGED, resolveEntry(savedSong(1, "Two", pieceCid = "bafkzcib-old"), state).availability)
    }

    @Test
    fun `two songs with the same title are not guessed between`() {
        val state = library(
            media("a", pieceCid = "bafkzcib-new"),
            chapters = listOf(
                ChapterRow("a", 0, 0, "Two", "bafkzcib-new"),
                ChapterRow("a", 1, 30_000, "Other", "bafkzcib-new"),
                ChapterRow("a", 2, 90_000, "Two", "bafkzcib-new"),
            ),
        )
        assertEquals(EntryAvailability.CHANGED, resolveEntry(savedSong(1, "Two", pieceCid = "bafkzcib-old"), state).availability)
    }

    @Test
    fun `a re-uploaded album that has not been read yet is changed until it has`() {
        val state = library(media("a", pieceCid = "bafkzcib-new"))
        assertEquals(EntryAvailability.CHANGED, resolveEntry(savedSong(1, "Two", pieceCid = "bafkzcib-old"), state).availability)
    }

    @Test
    fun `a whole album follows the record to its current file`() {
        val saved = library(media("a")).item("a")!!.wholeEntry().copy(pieceCid = "bafkzcib-old")
        val resolved = resolveEntry(saved, library(media("a", pieceCid = "bafkzcib-new")))
        assertEquals(EntryAvailability.AVAILABLE, resolved.availability)
        assertEquals("bafkzcib-new", resolved.entry.pieceCid)
    }

    @Test
    fun `an entry the wallet cannot open is locked, and still listed`() {
        val resolved = resolveEntry(savedSong(1, "Two"), library(media("a"), daoStates = listOf(notMember())))
        assertEquals(EntryAvailability.LOCKED, resolved.availability)
        assertTrue(resolved.item != null)
    }

    @Test
    fun `entries survive a round trip through storage`() {
        val entry = savedSong(2, "Three")
        assertEquals(entry, entry.toSnapshot().toPlayable())
        assertEquals("a" to 2, PlayableEntry.parseId(entry.id))
    }
}
