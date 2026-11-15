package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.ChapterRow
import app.ipfsmedia.core.library.LibraryState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdmissionTest {

    @Test
    fun `everything is admitted before the catalogue has loaded`() {
        val entries = listOf(song("gone", 0), album("b"))
        val admission = admitEntries(entries, 1, LibraryState.EMPTY)
        assertEquals(entries, admission.entries)
        assertEquals(1, admission.startIndex)
        assertEquals(0, admission.skipped)
    }

    @Test
    fun `an entry whose record has expired is left out and counted`() {
        val admission = admitEntries(listOf(album("gone"), album("b")), 0, catalogue(record("b")))
        assertEquals(listOf("b"), admission.entries.map { it.recordKey })
        assertEquals(1, admission.skipped)
    }

    @Test
    fun `an entry the wallet can no longer open is left out`() {
        val admission = admitEntries(listOf(album("a")), 0, catalogue(record("a"), member = false))
        assertEquals(emptyList<String>(), admission.entries.map { it.recordKey })
        assertEquals(1, admission.skipped)
    }

    @Test
    fun `an upload with no gate plays whatever the wallet's memberships`() {
        val admission = admitEntries(listOf(album("a")), 0, catalogue(record("a", gated = false), member = false))
        assertEquals(1, admission.entries.size)
    }

    @Test
    fun `playing from a row that was left out starts at the next one that was kept`() {
        val entries = listOf(album("a"), album("gone"), album("c"), album("d"))
        val admission = admitEntries(entries, 1, catalogue(record("a"), record("c"), record("d")))
        assertEquals(listOf("a", "c", "d"), admission.entries.map { it.recordKey })
        assertEquals(1, admission.startIndex)
        assertEquals("c", admission.entries[admission.startIndex].recordKey)
    }

    @Test
    fun `the start index follows its entry when earlier rows are left out`() {
        val entries = listOf(album("gone"), album("a"), album("b"))
        val admission = admitEntries(entries, 2, catalogue(record("a"), record("b")))
        assertEquals("b", admission.entries[admission.startIndex].recordKey)
    }

    @Test
    fun `when nothing at or after the chosen row is kept, play starts from the top`() {
        val entries = listOf(album("a"), album("gone"))
        val admission = admitEntries(entries, 1, catalogue(record("a")))
        assertEquals(0, admission.startIndex)
        assertEquals(1, admission.entries.size)
    }

    @Test
    fun `a song from an album that was uploaded again is re-pointed at the new file`() {
        val saved = song("a", 1, title = "Two", pieceCid = "old-piece")
        val state = catalogue(
            record("a", pieceCid = "new-piece"),
            chapters = listOf(
                ChapterRow("a", 0, 0L, "One", "new-piece"),
                ChapterRow("a", 1, 90_000L, "Two", "new-piece"),
                ChapterRow("a", 2, 200_000L, "Three", "new-piece"),
            ),
        )
        val admitted = admitEntries(listOf(saved), 0, state).entries.single()
        assertEquals("new-piece", admitted.pieceCid)
        assertEquals(90_000L, admitted.startMs)
        assertEquals(200_000L, admitted.endMs)
    }

    @Test
    fun `a song that cannot be found in the new file is left out rather than played from the wrong place`() {
        val saved = song("a", 1, title = "Two", pieceCid = "old-piece")
        val state = catalogue(record("a", pieceCid = "new-piece"))
        val admission = admitEntries(listOf(saved), 0, state)
        assertEquals(0, admission.entries.size)
        assertEquals(1, admission.skipped)
    }
}
