package app.ipfsmedia.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.LibraryState
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.PlaylistChoice
import java.io.File

/**
 * Where every tap, drop, menu choice, shortcut and TalkBack action on an item ends up.
 *
 * The shared components call this; `:app` implements it. A screen built from the shared
 * components therefore gets play, queue, favourite, download, playlists, drag and drop and the
 * context menu without writing any of it.
 */
@Stable
interface ItemActions {
    /** Carries out [destination] for [payload]. */
    fun perform(destination: Destination, payload: DragPayload, origin: Origin = Origin.Library)

    /**
     * Plays [entries] starting from the one at [startIndex], with the rest queued around it: a
     * tap on the fourth song of an album plays that song and leaves the album as the queue.
     * [label] is what the notice says is playing.
     */
    fun playFrom(entries: List<PlayableEntry>, startIndex: Int, label: String)

    /** Empties this identity's play history. It can be undone like any other action. */
    fun clearHistory()

    /**
     * Takes these uploads' content off the device. The one item action that is not a
     * destination: nothing is carried anywhere, and downloading again is how it is undone.
     */
    fun removeDownloads(recordKeys: List<String>, label: String)

    /** The user's playlists, for the tray and the menus. Read in composition, so it updates. */
    val playlists: List<PlaylistChoice>
}

private object NoItemActions : ItemActions {
    override fun perform(destination: Destination, payload: DragPayload, origin: Origin) = Unit
    override fun playFrom(entries: List<PlayableEntry>, startIndex: Int, label: String) = Unit
    override fun clearHistory() = Unit
    override fun removeDownloads(recordKeys: List<String>, label: String) = Unit
    override val playlists: List<PlaylistChoice> = emptyList()
}

val LocalItemActions = staticCompositionLocalOf<ItemActions> { NoItemActions }

/** Where cover art comes from. `:app` backs this with the per-identity artwork store. */
fun interface ArtworkSource {
    /** The extracted picture for [recordKey], or null when there is none (yet). */
    fun fileFor(recordKey: String): File?
}

val LocalArtworkSource = compositionLocalOf { ArtworkSource { null } }

/** What the fetch-and-unlock pipeline is doing for one upload, as an item shows it. */
@Immutable
data class StageInfo(
    /** "Fetching…", "Unlocking…". */
    val label: String,
    /** 0..1 when real byte counts exist; null while fetching, when there is no honest number. */
    val fraction: Float? = null,
)

/**
 * Per record key: uploads being fetched, unlocked or downloaded right now. An item shows its
 * stage on the item itself, wherever that item appears (docs/PLAN.md §7).
 */
val LocalStages = compositionLocalOf<Map<String, StageInfo>> { emptyMap() }

/** What is playing, so the row or card for it can say so. */
@Immutable
data class NowPlaying(
    val recordKey: String? = null,
    val chapterIndex: Int = Int.MIN_VALUE,
    val isPlaying: Boolean = false,
) {
    fun isRecord(recordKey: String): Boolean = this.recordKey == recordKey

    fun isEntry(recordKey: String, chapterIndex: Int): Boolean =
        this.recordKey == recordKey && this.chapterIndex == chapterIndex
}

val LocalNowPlaying = compositionLocalOf { NowPlaying() }

/** Window width as the layout cares about it (docs/PLAN.md §6, "Window sizes"). */
enum class WidthClass {
    /** Under 600dp: bottom bar, one column, the queue as a full-height sheet. */
    Compact,

    /** 600 to 839dp: navigation rail, the queue as an overlay. */
    Medium,

    /** 840 to 1199dp: full sidebar, the queue as an overlay. */
    Expanded,

    /** 1200dp and over: full sidebar, the queue docked beside the content. */
    Large,
    ;

    val isCompact: Boolean get() = this == Compact

    companion object {
        fun of(width: Dp): WidthClass = when {
            width.value < 600f -> Compact
            width.value < 840f -> Medium
            width.value < 1200f -> Expanded
            else -> Large
        }
    }
}

val LocalWidthClass = staticCompositionLocalOf { WidthClass.Expanded }

/** What input the user has; see [InputKind]. Provided once at the root. */
val LocalInputKind = staticCompositionLocalOf { InputKind(hasKeyboard = false, hasTouch = true) }

/**
 * The catalogue for the active identity. Provided once by the shell from the one shared
 * `LibraryRepository.state`, so a screen reads the catalogue without a view model of its own
 * and every screen sees the same snapshot.
 */
val LocalLibraryState = compositionLocalOf { LibraryState.EMPTY }

/**
 * Counts requests to put the cursor in the search field (Ctrl+F, the sidebar's search entry).
 * The search page focuses its field whenever this changes.
 */
val LocalSearchFocusRequests = compositionLocalOf { 0 }
