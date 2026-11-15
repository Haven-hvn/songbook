package app.ipfsmedia.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueModelTest {

    private val queue = listOf(
        song("a", 0), song("a", 1), song("a", 2),
        album("b"),
        song("c", 0), song("c", 1),
        video("d"),
        album("e"),
    )
    private val inOrder = queue.indices.toList()

    @Test
    fun `the spool keeps the playing file and the next few, counted by upload not by row`() {
        // Three songs of one album are one file: the limit is about files.
        assertEquals(listOf("piece-a", "piece-b"), piecesToPin(queue, inOrder, records = 2))
        assertEquals(
            listOf("piece-a", "piece-b", "piece-c", "piece-d"),
            piecesToPin(queue, inOrder, records = 4),
        )
    }

    @Test
    fun `pins follow the order things will play in, not list order`() {
        val shuffled = listOf(6, 3, 0, 4)
        assertEquals(listOf("piece-d", "piece-b"), piecesToPin(queue, shuffled, records = 2))
    }

    @Test
    fun `an entry with no known file takes a slot but pins nothing`() {
        val entries = listOf(album("x", pieceCid = null), album("y"))
        assertEquals(listOf("piece-y"), piecesToPin(entries, listOf(0, 1), records = 2))
        assertEquals(emptyList<String>(), piecesToPin(entries, listOf(0, 1), records = 1))
    }

    @Test
    fun `a failure is pinned on the row of the upload that failed, not on the row the player is on`() {
        // The player is still on the last song of a while it fails to open b.
        val fromCurrent = listOf(2, 3, 4, 5, 6, 7)
        assertEquals(3, failedRow(queue, fromCurrent, "b"))
        assertEquals(4, failedRow(queue, fromCurrent, "c"))
    }

    @Test
    fun `with nothing to go on, the failed row is the current one`() {
        assertEquals(2, failedRow(queue, listOf(2, 3, 4), null))
        assertEquals(2, failedRow(queue, listOf(2, 3, 4), "not-in-the-queue"))
        assertNull(failedRow(queue, emptyList(), "a"))
    }

    @Test
    fun `stepping over a failed album steps over all of its songs`() {
        assertEquals(3, skipTarget(queue, listOf(0, 1, 2, 3, 4)))
        assertEquals(3, skipTarget(queue, listOf(1, 2, 3, 4)))
        assertEquals(6, skipTarget(queue, listOf(4, 5, 6, 7)))
    }

    @Test
    fun `there is nothing to step to after the last upload`() {
        assertNull(skipTarget(queue, listOf(7)))
        assertNull(skipTarget(queue, listOf(4, 5)))
        assertNull(skipTarget(queue, emptyList()))
    }

    @Test
    fun `stepping over follows shuffle order`() {
        assertEquals(6, skipTarget(queue, listOf(1, 0, 2, 6, 3)))
    }

    @Test
    fun `something counts as played after half of it, at most thirty seconds, at least five`() {
        assertEquals(30_000L, historyThresholdMs(null))
        assertEquals(30_000L, historyThresholdMs(600_000L))
        assertEquals(20_000L, historyThresholdMs(40_000L))
        assertEquals(5_000L, historyThresholdMs(4_000L))
        assertEquals(30_000L, historyThresholdMs(0L))
    }

    @Test
    fun `only long things played as one piece remember where they stopped`() {
        assertTrue(remembersPosition(video("d"), 3_600_000L))
        assertTrue(remembersPosition(album("b"), 45 * 60_000L))
        assertFalse(remembersPosition(album("b"), 4 * 60_000L))
        assertFalse(remembersPosition(song("a", 1), 45 * 60_000L))
        assertFalse(remembersPosition(video("d"), null))
    }
}
