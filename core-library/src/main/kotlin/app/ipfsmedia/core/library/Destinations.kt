package app.ipfsmedia.core.library

/**
 * Somewhere a payload can go, or something that can be done with it.
 *
 * "Destination" covers both on purpose. Long-pressing an item raises a tray of slots; letting go
 * and tapping a slot, or carrying the item onto it, are the same act (docs/PLAN.md §7).
 */
sealed interface Destination {
    data object PlayNow : Destination
    data object PlayNext : Destination
    data object AddToQueue : Destination

    /** A position in the queue, reached by carrying an item into the open queue. */
    data class QueueAt(val index: Int) : Destination

    data object Favourite : Destination
    data object Unfavourite : Destination
    data object Download : Destination

    data class Playlist(val id: Long, val name: String) : Destination

    /** A position in an open playlist page. */
    data class PlaylistAt(val id: Long, val index: Int) : Destination

    data object NewPlaylist : Destination

    data object Follow : Destination
    data object Unfollow : Destination
    data object Pin : Destination
    data object Unpin : Destination

    data object RemoveFromQueue : Destination
    data object RemoveFromPlaylist : Destination
    data object RemoveFromHistory : Destination
    data object RenamePlaylist : Destination
    data object DeletePlaylist : Destination

    data object GoToAlbum : Destination
    data object GoToUploader : Destination
    data object GoToDataDao : Destination
}

/** How options are grouped, in display order. The tray shows the first three groups as slots. */
enum class OptionGroup { PLAY, SAVE, PLAYLIST, GO, REMOVE }

/** One row of the list: where the payload can go, what to call it, and whether it can go there now. */
data class DestinationOption(
    val destination: Destination,
    val label: String,
    val group: OptionGroup,
    /**
     * Null when the option can be taken. Otherwise why not; the option is still listed, struck
     * through, with this as its explanation, so a locked item explains itself instead of hiding
     * what it cannot do.
     */
    val refusal: String? = null,
) {
    val enabled: Boolean get() = refusal == null

    /** Whether the tray shows this as a slot. Navigation and removal stay in the menu form. */
    val inTray: Boolean get() = group == OptionGroup.PLAY || group == OptionGroup.SAVE || group == OptionGroup.PLAYLIST
}

/** A playlist as the destination list needs it: id, name and when it was last added to. */
data class PlaylistChoice(val id: Long, val name: String, val updatedAtMs: Long)

/** What [destinationsFor] needs to know beyond the payload itself. */
data class DestinationContext(
    val playlists: List<PlaylistChoice> = emptyList(),
    val origin: Origin = Origin.Library,
) {
    /** The three playlists most recently added to: the ones the tray offers. */
    val recentPlaylists: List<PlaylistChoice>
        get() = playlists.sortedByDescending { it.updatedAtMs }.take(RECENT_PLAYLISTS)

    companion object {
        const val RECENT_PLAYLISTS = 3
    }
}

/**
 * Everything that can be done with [payload], in display order.
 *
 * The single definition behind the tray, the right-click menu, the keyboard commands and
 * TalkBack's custom actions.
 */
fun destinationsFor(payload: DragPayload, context: DestinationContext = DestinationContext()): List<DestinationOption> =
    when (payload) {
        is DragPayload.Media -> mediaOptions(payload, context)
        is DragPayload.Playlist -> playlistOptions(payload, context)
        is DragPayload.Uploader -> uploaderOptions(payload)
        is DragPayload.DataDao -> daoOptions(payload)
    }

private fun mediaOptions(payload: DragPayload.Media, context: DestinationContext): List<DestinationOption> {
    val locked = payload.lockedReason
    val options = mutableListOf(
        DestinationOption(Destination.PlayNow, "Play now", OptionGroup.PLAY, locked),
        DestinationOption(Destination.PlayNext, "Play next", OptionGroup.PLAY, locked),
        DestinationOption(Destination.AddToQueue, "Add to queue", OptionGroup.PLAY, locked),
    )
    options += if (payload.isFavourite) {
        DestinationOption(Destination.Unfavourite, "Remove from favourites", OptionGroup.SAVE)
    } else {
        DestinationOption(Destination.Favourite, "Favourite", OptionGroup.SAVE)
    }
    options += DestinationOption(
        destination = Destination.Download,
        label = "Download",
        group = OptionGroup.SAVE,
        refusal = locked ?: "Already on this device".takeIf { payload.isOnDevice },
    )

    val inPlaylist = (context.origin as? Origin.Playlist)?.playlistId
    context.recentPlaylists
        .filter { it.id != inPlaylist }
        .forEach { options += DestinationOption(Destination.Playlist(it.id, it.name), it.name, OptionGroup.PLAYLIST) }
    options += DestinationOption(Destination.NewPlaylist, "New playlist", OptionGroup.PLAYLIST)

    val single = payload.whole ?: payload.entries.singleOrNull()
    if (single != null) {
        if (single.kind == EntryKind.SONG) {
            options += DestinationOption(Destination.GoToAlbum, "Go to album", OptionGroup.GO)
        }
        options += DestinationOption(Destination.GoToUploader, "Go to uploader", OptionGroup.GO)
        if (single.daoKey != null) {
            options += DestinationOption(Destination.GoToDataDao, "Go to DataDAO", OptionGroup.GO)
        }
    }

    when (context.origin) {
        is Origin.Queue -> options += DestinationOption(Destination.RemoveFromQueue, "Remove from queue", OptionGroup.REMOVE)
        is Origin.Playlist -> options += DestinationOption(Destination.RemoveFromPlaylist, "Remove from playlist", OptionGroup.REMOVE)
        is Origin.History -> options += DestinationOption(Destination.RemoveFromHistory, "Remove from history", OptionGroup.REMOVE)
        Origin.Library -> Unit
    }
    return options
}

private fun playlistOptions(payload: DragPayload.Playlist, context: DestinationContext): List<DestinationOption> {
    val empty = "This playlist is empty".takeIf { payload.entryCount == 0 }
    val options = mutableListOf(
        DestinationOption(Destination.PlayNow, "Play now", OptionGroup.PLAY, empty),
        DestinationOption(Destination.PlayNext, "Play next", OptionGroup.PLAY, empty),
        DestinationOption(Destination.AddToQueue, "Add to queue", OptionGroup.PLAY, empty),
    )
    context.recentPlaylists
        .filter { it.id != payload.id }
        .forEach {
            options += DestinationOption(Destination.Playlist(it.id, it.name), "Merge into ${it.name}", OptionGroup.PLAYLIST, empty)
        }
    options += DestinationOption(Destination.NewPlaylist, "Duplicate", OptionGroup.PLAYLIST)
    options += DestinationOption(Destination.RenamePlaylist, "Rename", OptionGroup.GO)
    options += DestinationOption(Destination.DeletePlaylist, "Delete playlist", OptionGroup.REMOVE)
    return options
}

private fun uploaderOptions(payload: DragPayload.Uploader): List<DestinationOption> = buildList {
    if (payload.followed) {
        add(DestinationOption(Destination.Unfollow, "Unfollow", OptionGroup.SAVE))
    } else {
        add(
            DestinationOption(
                destination = Destination.Follow,
                label = "Follow",
                group = OptionGroup.SAVE,
                refusal = "This is you".takeIf { payload.isSelf },
            ),
        )
    }
    add(DestinationOption(Destination.GoToUploader, "Go to uploader", OptionGroup.GO))
}

private fun daoOptions(payload: DragPayload.DataDao): List<DestinationOption> = buildList {
    if (payload.pinned) {
        add(DestinationOption(Destination.Unpin, "Unpin from sidebar", OptionGroup.SAVE))
    } else {
        add(DestinationOption(Destination.Pin, "Pin to sidebar", OptionGroup.SAVE))
    }
    add(DestinationOption(Destination.GoToDataDao, "Go to DataDAO", OptionGroup.GO))
}

/**
 * A place in the interface an item can be carried to: the "precise placement" half of §7, for
 * when order matters or the target is already on screen.
 */
sealed interface DropTarget {
    /** A slot of the tray. */
    data class TraySlot(val destination: Destination) : DropTarget

    data class SidebarPlaylist(val id: Long, val name: String) : DropTarget
    data object SidebarNewPlaylist : DropTarget

    /** Row position [index] of an open playlist page. */
    data class PlaylistPage(val id: Long, val index: Int) : DropTarget

    /** The queue button in the player bar; holding over it opens the queue. */
    data object QueueButton : DropTarget

    /** Row position [index] of the open queue. */
    data class QueueDrawer(val index: Int) : DropTarget

    data object PlayerBar : DropTarget
    data object SidebarCollection : DropTarget
    data object SidebarFollowing : DropTarget
    data object SidebarDataDaos : DropTarget
}

/** What happens if the held payload is let go on a target. */
sealed interface DropVerdict {
    /** The target takes it: light up, and show [label] ("Add to Night Drive"). */
    data class Accept(val destination: Destination, val label: String) : DropVerdict

    /** The target would take this kind of thing, but not this one, and says why. */
    data class Refuse(val reason: String) : DropVerdict

    /** The target has nothing to do with this kind of payload: dim, and ignore the drop. */
    data object Ignore : DropVerdict
}

/**
 * The placement table of docs/PLAN.md §7 as a function.
 *
 * | Put it on                      | Media               | Playlist     | Uploader | DataDAO |
 * |--------------------------------|---------------------|--------------|----------|---------|
 * | a playlist in the sidebar      | add to it           | merge into it| —        | —       |
 * | "New playlist" in the sidebar  | create one from it  | duplicate    | —        | —       |
 * | an open playlist page          | insert at position  | —            | —        | —       |
 * | queue button                   | add to the end      | add all      | —        | —       |
 * | queue drawer                   | insert at position  | insert all   | —        | —       |
 * | player bar                     | play now            | play now     | —        | —       |
 * | Collection in the sidebar      | favourite           | —            | —        | —       |
 * | Following in the sidebar       | —                   | —            | follow   | —       |
 * | Your DataDAOs in the sidebar   | —                   | —            | —        | pin     |
 */
fun dropVerdict(payload: DragPayload, target: DropTarget): DropVerdict = when (target) {
    is DropTarget.TraySlot -> traySlotVerdict(payload, target.destination)

    is DropTarget.SidebarPlaylist -> when (payload) {
        is DragPayload.Media ->
            DropVerdict.Accept(Destination.Playlist(target.id, target.name), "Add to ${target.name}")
        is DragPayload.Playlist -> when {
            payload.id == target.id -> DropVerdict.Ignore
            payload.entryCount == 0 -> DropVerdict.Refuse("This playlist is empty")
            else -> DropVerdict.Accept(Destination.Playlist(target.id, target.name), "Merge into ${target.name}")
        }
        else -> DropVerdict.Ignore
    }

    DropTarget.SidebarNewPlaylist -> when (payload) {
        is DragPayload.Media -> DropVerdict.Accept(Destination.NewPlaylist, "New playlist from this")
        is DragPayload.Playlist -> DropVerdict.Accept(Destination.NewPlaylist, "Duplicate ${payload.name}")
        else -> DropVerdict.Ignore
    }

    is DropTarget.PlaylistPage -> when (payload) {
        is DragPayload.Media -> DropVerdict.Accept(Destination.PlaylistAt(target.id, target.index), "Insert here")
        else -> DropVerdict.Ignore
    }

    DropTarget.QueueButton -> playable(payload, Destination.AddToQueue, "Add to queue")

    is DropTarget.QueueDrawer -> playable(payload, Destination.QueueAt(target.index), "Insert here")

    DropTarget.PlayerBar -> playable(payload, Destination.PlayNow, "Play now")

    DropTarget.SidebarCollection -> when (payload) {
        is DragPayload.Media ->
            if (payload.isFavourite) DropVerdict.Refuse("Already a favourite")
            else DropVerdict.Accept(Destination.Favourite, "Favourite")
        else -> DropVerdict.Ignore
    }

    DropTarget.SidebarFollowing -> when (payload) {
        is DragPayload.Uploader -> when {
            payload.isSelf -> DropVerdict.Refuse("This is you")
            payload.followed -> DropVerdict.Refuse("Already following")
            else -> DropVerdict.Accept(Destination.Follow, "Follow ${payload.name}")
        }
        else -> DropVerdict.Ignore
    }

    DropTarget.SidebarDataDaos -> when (payload) {
        is DragPayload.DataDao ->
            if (payload.pinned) DropVerdict.Refuse("Already pinned")
            else DropVerdict.Accept(Destination.Pin, "Pin ${payload.name}")
        else -> DropVerdict.Ignore
    }
}

/** Playing and queueing: media and playlists only, and never a locked or empty one. */
private fun playable(payload: DragPayload, destination: Destination, label: String): DropVerdict = when (payload) {
    is DragPayload.Media -> payload.lockedReason?.let { DropVerdict.Refuse(it) } ?: DropVerdict.Accept(destination, label)
    is DragPayload.Playlist ->
        if (payload.entryCount == 0) DropVerdict.Refuse("This playlist is empty") else DropVerdict.Accept(destination, label)
    else -> DropVerdict.Ignore
}

/** A tray slot answers exactly as its row in [destinationsFor] does. */
private fun traySlotVerdict(payload: DragPayload, destination: Destination): DropVerdict {
    // The tray is built from the same list, without context: recent playlists are matched by id.
    val option = destinationsFor(payload).firstOrNull { it.destination == destination }
        ?: if (destination is Destination.Playlist && payload !is DragPayload.Uploader && payload !is DragPayload.DataDao) {
            DestinationOption(destination, destination.name, OptionGroup.PLAYLIST)
        } else {
            return DropVerdict.Ignore
        }
    return option.refusal?.let { DropVerdict.Refuse(it) } ?: DropVerdict.Accept(option.destination, option.label)
}
