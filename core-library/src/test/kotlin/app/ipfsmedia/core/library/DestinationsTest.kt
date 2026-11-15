package app.ipfsmedia.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The destination list and the placement table of docs/PLAN.md §7, pinned as data. */
class DestinationsTest {

    private val playlists = listOf(
        PlaylistChoice(1, "Oldest", updatedAtMs = 10),
        PlaylistChoice(2, "Newest", updatedAtMs = 40),
        PlaylistChoice(3, "Middle", updatedAtMs = 30),
        PlaylistChoice(4, "Recent", updatedAtMs = 35),
    )
    private val context = DestinationContext(playlists = playlists)

    private val album = library(media("a")).item("a")!!
    private val locked = library(media("a"), daoStates = listOf(notMember())).item("a")!!

    private fun labels(options: List<DestinationOption>) = options.map { it.label }

    @Test
    fun `the tray for a song is the list in the plan`() {
        val tray = destinationsFor(album.asPayload(), context).filter { it.inTray }
        assertEquals(
            listOf("Play now", "Play next", "Add to queue", "Favourite", "Download", "Newest", "Recent", "Middle", "New playlist"),
            labels(tray),
        )
        assertTrue(tray.all { it.enabled })
    }

    @Test
    fun `the menu holds everything the tray does`() {
        val all = destinationsFor(album.asPayload(), context)
        assertTrue(all.filter { it.inTray }.all { it in all })
        assertTrue(all.any { it.destination == Destination.GoToUploader })
        assertTrue(all.any { it.destination == Destination.GoToDataDao })
    }

    @Test
    fun `a locked item strikes through play, queue and download and says why`() {
        val options = destinationsFor(locked.asPayload(), context).associateBy { it.destination }
        val reason = locked.lockedReason()
        assertEquals(reason, options.getValue(Destination.PlayNow).refusal)
        assertEquals(reason, options.getValue(Destination.PlayNext).refusal)
        assertEquals(reason, options.getValue(Destination.AddToQueue).refusal)
        assertEquals(reason, options.getValue(Destination.Download).refusal)
    }

    @Test
    fun `a locked item can still go into a playlist and be favourited`() {
        val options = destinationsFor(locked.asPayload(), context)
        assertTrue(options.first { it.destination == Destination.NewPlaylist }.enabled)
        assertTrue(options.first { it.destination is Destination.Playlist }.enabled)
        assertTrue(options.first { it.destination == Destination.Favourite }.enabled)
    }

    @Test
    fun `a favourite offers removal, and something on the device does not offer download`() {
        val payload = album.asPayload().copy(isFavourite = true, isOnDevice = true)
        val options = destinationsFor(payload, context).associateBy { it.destination }
        assertTrue(Destination.Unfavourite in options)
        assertFalse(Destination.Favourite in options)
        assertEquals("Already on this device", options.getValue(Destination.Download).refusal)
    }

    @Test
    fun `where it came from decides which removal is offered`() {
        fun removal(origin: Origin) =
            destinationsFor(album.asPayload(), context.copy(origin = origin)).lastOrNull { it.group == OptionGroup.REMOVE }?.destination
        assertNull(removal(Origin.Library))
        assertEquals(Destination.RemoveFromQueue, removal(Origin.Queue(0)))
        assertEquals(Destination.RemoveFromPlaylist, removal(Origin.Playlist(2, listOf(9))))
        assertEquals(Destination.RemoveFromHistory, removal(Origin.History(5)))
    }

    @Test
    fun `a row in a playlist is not offered its own playlist`() {
        val options = destinationsFor(album.asPayload(), context.copy(origin = Origin.Playlist(2, listOf(9))))
        assertFalse(options.any { (it.destination as? Destination.Playlist)?.id == 2L })
    }

    @Test
    fun `an uploader offers follow, a followed one unfollow, and you cannot follow yourself`() {
        val someone = DragPayload.Uploader(OTHER, "moth.eth", followed = false)
        assertEquals(Destination.Follow, destinationsFor(someone).first().destination)
        assertEquals(Destination.Unfollow, destinationsFor(someone.copy(followed = true)).first().destination)
        assertEquals("This is you", destinationsFor(someone.copy(isSelf = true)).first().refusal)
    }

    @Test
    fun `a datadao offers pin, a pinned one unpin`() {
        val dao = DragPayload.DataDao(DAO_A, "Night Shift", pinned = false)
        assertEquals(Destination.Pin, destinationsFor(dao).first().destination)
        assertEquals(Destination.Unpin, destinationsFor(dao.copy(pinned = true)).first().destination)
    }

    @Test
    fun `an empty playlist cannot be played or merged`() {
        val empty = DragPayload.Playlist(1, "Empty", entryCount = 0)
        val options = destinationsFor(empty, context)
        assertTrue(options.filter { it.group == OptionGroup.PLAY }.none { it.enabled })
        assertEquals(DropVerdict.Refuse("This playlist is empty"), dropVerdict(empty, DropTarget.QueueButton))
    }

    // ── The placement table, row by row ─────────────────────────────────────────────────────

    private val media = album.asPayload()
    private val playlist = DragPayload.Playlist(7, "Night Drive", entryCount = 3)
    private val uploader = DragPayload.Uploader(OTHER, "moth.eth", followed = false)
    private val dao = DragPayload.DataDao(DAO_A, "Night Shift", pinned = false)

    private fun accepted(payload: DragPayload, target: DropTarget): Destination? =
        (dropVerdict(payload, target) as? DropVerdict.Accept)?.destination

    private fun ignored(payload: DragPayload, target: DropTarget): Boolean =
        dropVerdict(payload, target) == DropVerdict.Ignore

    @Test
    fun `a playlist in the sidebar takes media and merges playlists`() {
        val target = DropTarget.SidebarPlaylist(2, "Newest")
        assertEquals(Destination.Playlist(2, "Newest"), accepted(media, target))
        assertEquals(Destination.Playlist(2, "Newest"), accepted(playlist, target))
        assertTrue(ignored(uploader, target))
        assertTrue(ignored(dao, target))
        // A playlist dropped on itself does nothing.
        assertTrue(ignored(playlist, DropTarget.SidebarPlaylist(7, "Night Drive")))
    }

    @Test
    fun `new playlist in the sidebar creates from media and duplicates a playlist`() {
        assertEquals(Destination.NewPlaylist, accepted(media, DropTarget.SidebarNewPlaylist))
        assertEquals(Destination.NewPlaylist, accepted(playlist, DropTarget.SidebarNewPlaylist))
        assertTrue(ignored(uploader, DropTarget.SidebarNewPlaylist))
    }

    @Test
    fun `an open playlist page takes media at a position and nothing else`() {
        val target = DropTarget.PlaylistPage(2, index = 4)
        assertEquals(Destination.PlaylistAt(2, 4), accepted(media, target))
        assertTrue(ignored(playlist, target))
    }

    @Test
    fun `the queue button adds to the end`() {
        assertEquals(Destination.AddToQueue, accepted(media, DropTarget.QueueButton))
        assertEquals(Destination.AddToQueue, accepted(playlist, DropTarget.QueueButton))
        assertTrue(ignored(dao, DropTarget.QueueButton))
    }

    @Test
    fun `the queue drawer inserts at a position`() {
        assertEquals(Destination.QueueAt(3), accepted(media, DropTarget.QueueDrawer(3)))
        assertEquals(Destination.QueueAt(3), accepted(playlist, DropTarget.QueueDrawer(3)))
    }

    @Test
    fun `the player bar plays now`() {
        assertEquals(Destination.PlayNow, accepted(media, DropTarget.PlayerBar))
        assertEquals(Destination.PlayNow, accepted(playlist, DropTarget.PlayerBar))
        assertTrue(ignored(uploader, DropTarget.PlayerBar))
    }

    @Test
    fun `collection favourites media only`() {
        assertEquals(Destination.Favourite, accepted(media, DropTarget.SidebarCollection))
        assertTrue(ignored(playlist, DropTarget.SidebarCollection))
        assertEquals(
            DropVerdict.Refuse("Already a favourite"),
            dropVerdict(media.copy(isFavourite = true), DropTarget.SidebarCollection),
        )
    }

    @Test
    fun `following takes uploaders only`() {
        assertEquals(Destination.Follow, accepted(uploader, DropTarget.SidebarFollowing))
        assertTrue(ignored(media, DropTarget.SidebarFollowing))
        assertTrue(ignored(dao, DropTarget.SidebarFollowing))
    }

    @Test
    fun `your datadaos takes datadaos only`() {
        assertEquals(Destination.Pin, accepted(dao, DropTarget.SidebarDataDaos))
        assertTrue(ignored(media, DropTarget.SidebarDataDaos))
        assertTrue(ignored(uploader, DropTarget.SidebarDataDaos))
    }

    @Test
    fun `a locked item is refused by every place that would play it, with the reason`() {
        val payload = locked.asPayload()
        val reason = DropVerdict.Refuse(locked.lockedReason()!!)
        assertEquals(reason, dropVerdict(payload, DropTarget.QueueButton))
        assertEquals(reason, dropVerdict(payload, DropTarget.QueueDrawer(0)))
        assertEquals(reason, dropVerdict(payload, DropTarget.PlayerBar))
        assertEquals(reason, dropVerdict(payload, DropTarget.TraySlot(Destination.PlayNow)))
        // …and still accepted by a playlist.
        assertEquals(Destination.Playlist(2, "Newest"), accepted(payload, DropTarget.SidebarPlaylist(2, "Newest")))
        assertEquals(Destination.NewPlaylist, accepted(payload, DropTarget.TraySlot(Destination.NewPlaylist)))
    }

    @Test
    fun `a tray slot answers exactly as its row in the list`() {
        destinationsFor(media, context).filter { it.inTray }.forEach { option ->
            val verdict = dropVerdict(media, DropTarget.TraySlot(option.destination))
            assertTrue(verdict is DropVerdict.Accept, "${option.label} should accept")
        }
    }

    @Test
    fun `an album never read moves as a whole album`() {
        assertEquals(1, album.asPayload().count)
        assertEquals(EntryKind.ALBUM, album.asPayload().entries.single().kind)
    }
}
