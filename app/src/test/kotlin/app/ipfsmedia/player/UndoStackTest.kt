package app.ipfsmedia.player

import app.ipfsmedia.player.actions.UndoEntry
import app.ipfsmedia.player.actions.UndoStack
import app.ipfsmedia.player.actions.moveTarget
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UndoStackTest {

    private val log = mutableListOf<String>()

    private fun entry(name: String, redoable: Boolean = true) = UndoEntry(
        label = name,
        undo = { log += "undo $name" },
        redo = if (redoable) ({ log += "redo $name" }) else null,
    )

    @Test
    fun `undo takes back the latest action first`() = runTest {
        val stack = UndoStack()
        stack.push(entry("a"))
        stack.push(entry("b"))

        assertEquals("b", stack.undo())
        assertEquals("a", stack.undo())
        assertNull(stack.undo())
        assertEquals(listOf("undo b", "undo a"), log)
    }

    @Test
    fun `redo repeats what was undone, in order`() = runTest {
        val stack = UndoStack()
        stack.push(entry("a"))
        stack.push(entry("b"))
        stack.undo()
        stack.undo()

        assertEquals("a", stack.redo())
        assertEquals("b", stack.redo())
        assertNull(stack.redo())
        assertEquals(listOf("undo b", "undo a", "redo a", "redo b"), log)
    }

    @Test
    fun `doing something new clears what could be redone`() = runTest {
        val stack = UndoStack()
        stack.push(entry("a"))
        stack.undo()
        assertTrue(stack.canRedo)

        stack.push(entry("b"))
        assertFalse(stack.canRedo)
        assertNull(stack.redo())
    }

    @Test
    fun `an action that cannot be repeated is not offered for redo`() = runTest {
        val stack = UndoStack()
        stack.push(entry("a"))
        stack.undo()
        stack.push(entry("once", redoable = false))
        stack.undo()
        assertFalse(stack.canRedo)
    }

    @Test
    fun `the oldest actions are dropped past the limit`() = runTest {
        val stack = UndoStack(limit = 2)
        stack.push(entry("a"))
        stack.push(entry("b"))
        stack.push(entry("c"))

        assertEquals("c", stack.undo())
        assertEquals("b", stack.undo())
        assertNull(stack.undo())
    }

    @Test
    fun `the notice names what undo would take back`() {
        val stack = UndoStack()
        assertNull(stack.nextUndoLabel)
        stack.push(entry("Added to Night Drive"))
        assertEquals("Added to Night Drive", stack.nextUndoLabel)
    }

    @Test
    fun `clearing leaves nothing to undo or redo`() = runTest {
        val stack = UndoStack()
        stack.push(entry("a"))
        stack.push(entry("b"))
        stack.undo()
        stack.clear()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `a row dropped below itself lands one lower than the row it was dropped on`() {
        // Five rows; row 1 dropped before row 4 ends up at index 3.
        assertEquals(3, moveTarget(from = 1, insertBefore = 4, size = 5))
        // Dropped after the last row: the end.
        assertEquals(4, moveTarget(from = 1, insertBefore = 5, size = 5))
    }

    @Test
    fun `a row dropped above itself lands on the row it was dropped on`() {
        assertEquals(0, moveTarget(from = 3, insertBefore = 0, size = 5))
        assertEquals(2, moveTarget(from = 3, insertBefore = 2, size = 5))
    }

    @Test
    fun `dropping a row on either side of itself moves nothing`() {
        assertNull(moveTarget(from = 2, insertBefore = 2, size = 5))
        assertNull(moveTarget(from = 2, insertBefore = 3, size = 5))
    }

    @Test
    fun `positions outside the list are brought inside it or refused`() {
        assertEquals(4, moveTarget(from = 0, insertBefore = 99, size = 5))
        assertEquals(0, moveTarget(from = 4, insertBefore = -3, size = 5))
        assertNull(moveTarget(from = 7, insertBefore = 1, size = 5))
    }
}
