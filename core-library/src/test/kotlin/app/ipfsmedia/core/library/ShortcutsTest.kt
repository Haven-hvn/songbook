package app.ipfsmedia.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShortcutsTest {

    private fun shell(key: ShortcutKey, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, typing: Boolean = false) =
        shortcutFor(KeyChord(key, ctrl, shift, alt), ShortcutScope.SHELL, typing)

    private fun item(key: ShortcutKey, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false) =
        shortcutFor(KeyChord(key, ctrl, shift, alt), ShortcutScope.ITEM)

    @Test
    fun `the plan's shortcuts map to their actions`() {
        assertEquals(ShortcutAction.PLAY_PAUSE, shell(ShortcutKey.SPACE))
        assertEquals(ShortcutAction.SEARCH, shell(ShortcutKey.F, ctrl = true))
        assertEquals(ShortcutAction.NEW_PLAYLIST, shell(ShortcutKey.N, ctrl = true))
        assertEquals(ShortcutAction.PREVIOUS, shell(ShortcutKey.LEFT, ctrl = true))
        assertEquals(ShortcutAction.NEXT, shell(ShortcutKey.RIGHT, ctrl = true))
        assertEquals(ShortcutAction.MUTE, shell(ShortcutKey.M))
        assertEquals(ShortcutAction.SHUFFLE, shell(ShortcutKey.S))
        assertEquals(ShortcutAction.REPEAT, shell(ShortcutKey.R))
        assertEquals(ShortcutAction.REFRESH, shell(ShortcutKey.R, ctrl = true))
        assertEquals(ShortcutAction.UNDO, shell(ShortcutKey.Z, ctrl = true))
        assertEquals(ShortcutAction.REDO, shell(ShortcutKey.Z, ctrl = true, shift = true))
        assertEquals(ShortcutAction.DISMISS, shell(ShortcutKey.ESCAPE))
        assertEquals(ShortcutAction.TOGGLE_QUEUE, shell(ShortcutKey.Q))
        assertEquals(ShortcutAction.FULLSCREEN, shell(ShortcutKey.F))
    }

    @Test
    fun `ctrl and a digit selects a sidebar section by position`() {
        assertEquals(0, shell(ShortcutKey.DIGIT_1, ctrl = true)?.sectionIndex)
        assertEquals(6, shell(ShortcutKey.DIGIT_7, ctrl = true)?.sectionIndex)
        assertNull(shell(ShortcutKey.DIGIT_8, ctrl = true))
        assertNull(ShortcutAction.PLAY_PAUSE.sectionIndex)
    }

    @Test
    fun `on a focused item space plays it and q queues it`() {
        assertEquals(ShortcutAction.PLAY_ITEM, item(ShortcutKey.SPACE))
        assertEquals(ShortcutAction.QUEUE_ITEM, item(ShortcutKey.Q))
        assertEquals(ShortcutAction.OPEN_ITEM, item(ShortcutKey.ENTER))
        assertEquals(ShortcutAction.ITEM_ACTIONS, item(ShortcutKey.MENU))
        assertEquals(ShortcutAction.ITEM_ACTIONS, item(ShortcutKey.F10, shift = true))
        assertEquals(ShortcutAction.REMOVE_ITEM, item(ShortcutKey.DELETE))
        assertEquals(ShortcutAction.MOVE_ROW_UP, item(ShortcutKey.UP, alt = true))
        assertEquals(ShortcutAction.MOVE_ROW_DOWN, item(ShortcutKey.DOWN, alt = true))
        assertEquals(ShortcutAction.CUT_ITEM, item(ShortcutKey.X, ctrl = true))
    }

    @Test
    fun `an item passes on what it does not handle`() {
        assertEquals(ShortcutAction.MUTE, item(ShortcutKey.M))
        assertEquals(ShortcutAction.SEARCH, item(ShortcutKey.F, ctrl = true))
    }

    @Test
    fun `item-only keys do nothing in the shell`() {
        assertNull(shell(ShortcutKey.ENTER))
        assertNull(shell(ShortcutKey.DELETE))
        assertNull(shell(ShortcutKey.MENU))
    }

    @Test
    fun `nothing that could be typing fires while a text field has focus`() {
        assertNull(shell(ShortcutKey.SPACE, typing = true))
        assertNull(shell(ShortcutKey.M, typing = true))
        assertNull(shell(ShortcutKey.LEFT, typing = true))
        assertNull(shell(ShortcutKey.LEFT, ctrl = true, typing = true))
        assertNull(shell(ShortcutKey.Z, ctrl = true, typing = true))
        assertNull(shell(ShortcutKey.V, ctrl = true, typing = true))
        assertNull(shell(ShortcutKey.A, ctrl = true, typing = true))
        // These cannot be mistaken for text editing.
        assertEquals(ShortcutAction.SEARCH, shell(ShortcutKey.F, ctrl = true, typing = true))
        assertEquals(ShortcutAction.NEW_PLAYLIST, shell(ShortcutKey.N, ctrl = true, typing = true))
        assertEquals(ShortcutAction.DISMISS, shell(ShortcutKey.ESCAPE, typing = true))
        assertEquals(ShortcutAction.SECTION_2, shell(ShortcutKey.DIGIT_2, ctrl = true, typing = true))
    }

    @Test
    fun `no chord means two different things in one scope`() {
        ShortcutScope.entries.forEach { scope ->
            val perChord = SHORTCUTS
                .filter { it.action.needsFocusedItem == (scope == ShortcutScope.ITEM) }
                .groupBy { it.chord }
            perChord.forEach { (chord, specs) ->
                assertEquals(1, specs.map { it.action }.distinct().size, "${chord.display} is ambiguous in $scope")
            }
        }
    }

    @Test
    fun `every action has a shortcut, and hints read as the keys`() {
        assertTrue(ShortcutAction.entries.all { action -> SHORTCUTS.any { it.action == action } })
        assertEquals("Q", shortcutHint(ShortcutAction.TOGGLE_QUEUE))
        assertEquals("Ctrl+Shift+Z", shortcutHint(ShortcutAction.REDO))
        assertEquals("Queue (Q)", withShortcutHint("Queue", ShortcutAction.TOGGLE_QUEUE, keyboardAttached = true))
        assertEquals("Queue", withShortcutHint("Queue", ShortcutAction.TOGGLE_QUEUE, keyboardAttached = false))
    }
}
