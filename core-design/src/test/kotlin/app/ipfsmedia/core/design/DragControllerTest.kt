package app.ipfsmedia.core.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.DropVerdict
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.PlayableEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DragControllerTest {

    private fun entry(record: String, chapter: Int = 0) = PlayableEntry(
        recordKey = record,
        chapterIndex = chapter,
        kind = EntryKind.SONG,
        pieceCid = "piece-$record",
        startMs = 0,
        endMs = 60_000,
        title = "Song $record/$chapter",
        albumTitle = "Album $record",
        uploaderName = "uploader",
        uploaderAddress = "0x2222222222222222222222222222222222222222",
        daoKey = null,
        durationMs = 60_000,
    )

    private fun song(record: String = "a", chapter: Int = 0, locked: String? = null) =
        DragPayload.Media(entries = listOf(entry(record, chapter)), lockedReason = locked)

    private fun DragController.target(id: Any, target: DropTarget, bounds: Rect, onRest: (() -> Unit)? = null) =
        register(TargetRegistration(id, { target }, bounds, onRest))

    private val playlist = DropTarget.SidebarPlaylist(7, "Night Drive")
    private val slop = 10f

    @Test
    fun `a long-press that does not move leaves the tray open`() {
        val controller = DragController()
        controller.lift(song(), Origin.Library, Offset(50f, 50f), byMouse = false, carrying = false)
        assertEquals(DragPhase.Lifted, controller.phase)

        assertNull(controller.release())
        assertEquals(DragPhase.TrayOpen, controller.phase)
        assertTrue(controller.held != null)
    }

    @Test
    fun `small movement within the slop is still a long-press, not a carry`() {
        val controller = DragController()
        controller.lift(song(), Origin.Library, Offset(50f, 50f), byMouse = false, carrying = false)
        controller.moveTo(Offset(54f, 53f), slop)
        assertEquals(DragPhase.Lifted, controller.phase)
        controller.moveTo(Offset(70f, 50f), slop)
        assertEquals(DragPhase.Carrying, controller.phase)
    }

    @Test
    fun `carrying onto a target that takes the item drops it there`() {
        val controller = DragController()
        controller.target("playlist", playlist, Rect(0f, 100f, 200f, 140f))
        controller.lift(song(), Origin.Library, Offset(300f, 300f), byMouse = true, carrying = true)
        controller.moveTo(Offset(100f, 120f), slop)

        assertEquals(playlist, controller.hoveredTarget)
        val drop = controller.release()
        assertEquals(Destination.Playlist(7, "Night Drive"), drop?.destination)
        assertEquals(DragPhase.Idle, controller.phase)
        assertNull(controller.held)
    }

    @Test
    fun `letting go over nothing cancels`() {
        val controller = DragController()
        controller.target("playlist", playlist, Rect(0f, 100f, 200f, 140f))
        controller.lift(song(), Origin.Library, Offset(300f, 300f), byMouse = true, carrying = true)
        controller.moveTo(Offset(500f, 500f), slop)

        assertNull(controller.release())
        assertEquals(DragPhase.Idle, controller.phase)
    }

    @Test
    fun `a target that refuses says why and takes nothing`() {
        val controller = DragController()
        controller.target("bar", DropTarget.PlayerBar, Rect(0f, 500f, 800f, 580f))
        controller.lift(song(locked = "Members of Night Shift only"), Origin.Library, Offset(10f, 10f), true, true)
        controller.moveTo(Offset(400f, 540f), slop)

        assertEquals(DropVerdict.Refuse("Members of Night Shift only"), controller.hoveredVerdict)
        assertNull(controller.release())
    }

    @Test
    fun `nested targets resolve to the smallest one under the pointer`() {
        val controller = DragController()
        controller.target("pane", DropTarget.QueueButton, Rect(600f, 0f, 900f, 600f))
        controller.target("row", DropTarget.QueueDrawer(3), Rect(600f, 200f, 900f, 256f))
        controller.lift(song(), Origin.Library, Offset(10f, 10f), true, true)

        controller.moveTo(Offset(700f, 220f), slop)
        assertEquals(DropTarget.QueueDrawer(3), controller.hoveredTarget)
        controller.moveTo(Offset(700f, 400f), slop)
        assertEquals(DropTarget.QueueButton, controller.hoveredTarget)
    }

    @Test
    fun `a row answers before or after by which half the pointer is in`() {
        val controller = DragController()
        val bounds = Rect(0f, 100f, 300f, 160f)
        controller.register(
            TargetRegistration(
                id = "row",
                targetAt = { at -> DropTarget.QueueDrawer(if (at.y > bounds.center.y) 5 else 4) },
                bounds = bounds,
                onRest = null,
            ),
        )
        controller.lift(song(), Origin.Library, Offset(10f, 10f), true, true)

        controller.moveTo(Offset(50f, 110f), slop)
        assertEquals(DropTarget.QueueDrawer(4), controller.hoveredTarget)
        assertFalse(controller.visualFor("row", DropTarget.QueueDrawer(4)).after)

        controller.moveTo(Offset(50f, 150f), slop)
        assertEquals(DropTarget.QueueDrawer(5), controller.hoveredTarget)
        assertTrue(controller.visualFor("row", DropTarget.QueueDrawer(4)).after)
    }

    @Test
    fun `targets show nothing until something is being carried`() {
        val controller = DragController()
        controller.target("playlist", playlist, Rect(0f, 100f, 200f, 140f))
        assertFalse(controller.visualFor("playlist", playlist).active)

        controller.lift(song(), Origin.Library, Offset(300f, 300f), byMouse = false, carrying = false)
        assertFalse(controller.visualFor("playlist", playlist).active)

        controller.moveTo(Offset(400f, 400f), slop)
        val visual = controller.visualFor("playlist", playlist)
        assertTrue(visual.accepts)
        assertFalse(visual.hovered)
    }

    @Test
    fun `a target that has nothing to do with what is held is dimmed`() {
        val controller = DragController()
        controller.lift(song(), Origin.Library, Offset(0f, 0f), true, true)
        assertTrue(controller.visualFor("following", DropTarget.SidebarFollowing).dimmed)
        assertTrue(controller.visualFor("collection", DropTarget.SidebarCollection).accepts)
    }

    @Test
    fun `choosing a tray slot carries out that destination`() {
        val controller = DragController()
        controller.openTray(song(), Origin.Queue(2))
        val drop = controller.choose(Destination.PlayNext)
        assertEquals(Destination.PlayNext, drop?.destination)
        assertEquals(Origin.Queue(2), drop?.origin)
        assertEquals(DragPhase.Idle, controller.phase)
    }

    @Test
    fun `with the tray open, tapping other items adds them to what is held`() {
        val controller = DragController()
        controller.openTray(song("a", 0), Origin.Library)
        assertTrue(controller.addToStack(song("a", 1)))
        assertTrue(controller.addToStack(song("b", 0)))
        // Adding the same one again changes nothing.
        assertTrue(controller.addToStack(song("a", 1)))

        assertEquals(3, controller.held?.payload?.count)
    }

    @Test
    fun `a stack with a locked item in it is locked as a whole`() {
        val controller = DragController()
        controller.openTray(song("a"), Origin.Library)
        controller.addToStack(song("b", locked = "Not open yet"))
        assertEquals("Not open yet", (controller.held?.payload as DragPayload.Media).lockedReason)
    }

    @Test
    fun `nothing joins a stack while an item is being carried, or when nothing is held`() {
        val controller = DragController()
        assertFalse(controller.addToStack(song("a")))
        controller.lift(song("a"), Origin.Library, Offset(0f, 0f), true, true)
        assertFalse(controller.addToStack(song("b")))
    }

    @Test
    fun `the keyboard puts down on a focused target that takes the item`() {
        val controller = DragController()
        controller.openTray(song(), Origin.Library)
        assertNull(controller.putDownOn(DropTarget.SidebarFollowing))
        assertTrue(controller.isTrayOpen)

        val drop = controller.putDownOn(playlist)
        assertEquals(Destination.Playlist(7, "Night Drive"), drop?.destination)
        assertFalse(controller.isActive)
    }

    @Test
    fun `cancel puts everything back`() {
        val controller = DragController()
        controller.lift(song(), Origin.Library, Offset(0f, 0f), true, true)
        controller.cancel()
        assertEquals(DragPhase.Idle, controller.phase)
        assertNull(controller.held)
        assertNull(controller.release())
    }

    @Test
    fun `a target that goes away stops being hit`() {
        val controller = DragController()
        controller.target("playlist", playlist, Rect(0f, 100f, 200f, 140f))
        controller.lift(song(), Origin.Library, Offset(0f, 0f), true, true)
        controller.moveTo(Offset(100f, 120f), slop)
        assertEquals(playlist, controller.hoveredTarget)

        controller.unregister("playlist")
        assertNull(controller.hoveredTarget)
    }
}
