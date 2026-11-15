package app.ipfsmedia.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.EntryAvailability
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.OfflineState
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.ResolvedEntry
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.library.lockedReason

/**
 * What a saved row (a playlist entry, a history entry, a favourite song) says in place of its
 * length when it cannot be played, or null when it can. A saved row outlives the record it
 * points at: Arkiv records expire, and the row stays, saying so.
 */
fun ResolvedEntry.unavailableLabel(): String? = when (availability) {
    EntryAvailability.AVAILABLE -> null
    EntryAvailability.CHANGED -> "Album changed"
    EntryAvailability.UNAVAILABLE -> "No longer available"
    EntryAvailability.LOCKED -> "Locked"
}

/** Why a saved row cannot be played, as a sentence for the notice and the struck-through options. */
fun ResolvedEntry.refusal(): String? = when (availability) {
    EntryAvailability.AVAILABLE -> null
    EntryAvailability.CHANGED -> "This song is not in the album's current file"
    EntryAvailability.UNAVAILABLE -> "This is no longer in the index"
    EntryAvailability.LOCKED -> item?.lockedReason() ?: "Locked"
}

/** The payload for a saved row. One that cannot be played can still be put in a playlist. */
fun ResolvedEntry.asPayload(isFavourite: Boolean = false): DragPayload.Media = DragPayload.Media(
    entries = listOf(entry),
    lockedReason = refusal(),
    isFavourite = isFavourite,
    isOnDevice = item?.offline == OfflineState.ON_DEVICE,
)

/** The second line of a row for [this] entry: who uploaded it, and what it is or belongs to. */
fun PlayableEntry.subtitleLine(): String = when (kind) {
    EntryKind.SONG -> "$uploaderName · $albumTitle"
    EntryKind.ALBUM -> "Album · $uploaderName"
    EntryKind.VIDEO -> "Video · $uploaderName"
}

/**
 * A saved entry as a row: in a playlist, in History, among the favourite songs.
 *
 * A tap plays ([onPlay] decides what "play" means there: usually this row, with the rest of the
 * list queued around it). A mouse click only selects; a double-click plays. A row that cannot
 * be played stays in the list, dimmed, and says why when it is tried.
 */
@Composable
fun SavedEntryRow(
    resolved: ResolvedEntry,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    origin: Origin = Origin.Library,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    /** The DataDAO it belongs to, shown as a badge where there is room. */
    badge: String? = resolved.item?.daoName,
    trailing: String? = formatClock(resolved.entry.durationMs),
    isFavourite: Boolean = false,
    grip: Boolean = false,
    drop: RowDrop? = null,
    onMoveRow: ((delta: Int) -> Unit)? = null,
) {
    val entry = resolved.entry
    val actions = LocalItemActions.current
    val payload = remember(resolved, isFavourite) { resolved.asPayload(isFavourite) }
    MediaRow(
        title = entry.title,
        subtitle = entry.subtitleLine(),
        payload = { payload },
        onActivate = { by ->
            when {
                !by.plays -> onSelect()
                resolved.isPlayable -> onPlay()
                // Says why, in the same words the row's menu uses.
                else -> actions.perform(Destination.PlayNow, payload, origin)
            }
        },
        modifier = modifier,
        origin = origin,
        leading = RowLeading.Art(entry.recordKey, entry.albumTitle),
        recordKey = entry.recordKey,
        // A whole album is "playing" whichever of its songs is on.
        chapterIndex = entry.chapterIndex.takeIf { !entry.isWholeFile },
        trailing = trailing,
        badge = badge,
        unavailable = resolved.unavailableLabel(),
        selected = selected,
        grip = grip,
        drop = drop,
        onMoveRow = onMoveRow,
    )
}

/**
 * An album or video as a row, for the list form of a grid: Collection's list view, search
 * results. A tap opens its page, as a tap on its card does; Space plays it.
 */
@Composable
fun ItemRow(
    item: LibraryItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payload = remember(item) { item.asPayload() }
    MediaRow(
        title = item.title,
        subtitle = if (item.kind == ItemKind.VIDEO) "Video · ${item.uploaderName}" else item.uploaderName,
        payload = { payload },
        onActivate = { onOpen() },
        modifier = modifier,
        leading = RowLeading.Art(item.id, item.title),
        recordKey = item.id,
        trailing = formatClock(item.durationMs),
        badge = item.daoName,
        unavailable = if (item.access.isLocked) "Locked" else null,
    )
}
