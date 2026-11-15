package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.EntryAvailability
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.LibraryState
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.resolveEntry

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the player bar, the queue and the Now Playing page draw from. Position is separate. */
data class PlayerUiState(
    /** False until the window has reached the playback service. */
    val connected: Boolean = false,
    /** The queue in list order. */
    val queue: List<QueueRow> = emptyList(),
    /** Index into [queue] of the row the player is on; -1 when the queue is empty. */
    val currentIndex: Int = -1,
    /**
     * Row indices in the order they will play, starting with the current one. List order, or the
     * shuffled order when shuffle is on, so "Up next" always shows what is really next.
     */
    val playOrder: List<Int> = emptyList(),
    val isPlaying: Boolean = false,
    /** The user wants playback; it may still be waiting on a fetch. */
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    /** Length of the current row (the song, not its album's file). Null until known. */
    val durationMs: Long? = null,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val volume: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Width over height of the video being decoded; null when there is none. */
    val videoAspectRatio: Float? = null,
    /** What the fetch-and-unlock pipeline is doing for the current row's upload, if anything. */
    val stage: StageState? = null,
    /** Set when the queue has stopped and needs the user. */
    val blocked: Blocked? = null,
) {
    val current: QueueRow? get() = queue.getOrNull(currentIndex)

    val isEmpty: Boolean get() = queue.isEmpty()

    /** The rows after the current one, in the order they will play. */
    val upNext: List<QueueRow> get() = playOrder.drop(1).mapNotNull(queue::getOrNull)

    val isVideo: Boolean get() = current?.entry?.kind == EntryKind.VIDEO

    /** Whether the main button offers Pause: playing, or on its way to playing. */
    val showsPause: Boolean get() = blocked == null && playWhenReady && (isPlaying || isBuffering)

    /** The stage to show beside the current row: only while it is the reason nothing is heard. */
    val waitingOn: StageState.Working? get() = (stage as? StageState.Working)?.takeIf { !isPlaying }
}

/** What happened to a request to queue something. */
data class QueueOutcome(
    val added: Int,
    /** Entries left out because they are gone, locked, or no longer match their album's file. */
    val skipped: Int,
) {
    val nothingAdded: Boolean get() = added == 0
}

/** The queue as it was, so "replace" and "clear" can be undone. */
data class QueueSnapshot(
    val entries: List<PlayableEntry>,
    val index: Int,
    val positionMs: Long,
    val wasPlaying: Boolean,
)

internal data class Admission(
    val entries: List<PlayableEntry>,
    /** [entries] index to start from. */
    val startIndex: Int,
    val skipped: Int,
)

/**
 * Decides what may enter the queue. Saved entries (a playlist made last month, a history row)
 * are matched against the catalogue as it is now: one whose record has expired, whose DataDAO
 * the wallet has left, or whose album was re-uploaded without that song is left out and counted,
 * and one that was re-matched plays from its new position.
 *
 * Before the catalogue has loaded there is nothing to match against, so everything is admitted
 * and the player finds out when it tries.
 *
 * [startIndex] refers to the list as given; the result's refers to the admitted list, moved to
 * the next admitted entry when the chosen one was left out.
 */
internal fun admitEntries(entries: List<PlayableEntry>, startIndex: Int, library: LibraryState): Admission {
    if (!library.ready) {
        return Admission(entries, startIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)), 0)
    }
    val admitted = ArrayList<PlayableEntry>(entries.size)
    var start = -1
    entries.forEachIndexed { index, entry ->
        val resolved = resolveEntry(entry, library)
        if (resolved.availability == EntryAvailability.AVAILABLE) {
            if (start < 0 && index >= startIndex) start = admitted.size
            admitted += resolved.entry
        }
    }
    return Admission(
        entries = admitted,
        startIndex = if (start >= 0) start else 0,
        skipped = entries.size - admitted.size,
    )
}
