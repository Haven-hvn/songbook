package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.PlayableEntry
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One row of the queue. The same entry can be queued twice, so a row has its own [uid]: it is
 * what a list keys on and what a drag refers to, and it does not survive a restart.
 */
data class QueueRow(
    val uid: Long,
    val entry: PlayableEntry,
)

/** Hands out row ids. One source for the window and the service, so they can never collide. */
@Singleton
class QueueIds @Inject constructor() {
    private val next = AtomicLong(1)

    fun next(): Long = next.getAndIncrement()
}

/** The queue as it is kept between runs: entries by id, never file paths (docs/PLAN.md §9). */
data class SavedQueue(
    val entries: List<PlayableEntry>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    /** Media3's values: 0 off, 1 one, 2 all. */
    val repeatMode: Int,
    val volume: Float,
)

/**
 * Piece CIDs the spool trimmer must leave alone: the file being played and the ones about to be.
 *
 * [order] is row indices in the order they will play, starting with the current one. The first
 * [records] distinct uploads are kept, however many rows each one fills: twelve songs of one
 * album are one file.
 */
internal fun piecesToPin(entries: List<PlayableEntry>, order: List<Int>, records: Int = PIN_RECORDS): List<String> {
    val seen = LinkedHashMap<String, String?>()
    for (index in order) {
        val entry = entries.getOrNull(index) ?: continue
        if (entry.recordKey !in seen) {
            if (seen.size >= records) break
            seen[entry.recordKey] = entry.pieceCid
        }
    }
    return seen.values.filterNotNull().distinct()
}

/**
 * Which row a failure belongs to. Media3 reports a failure to load the *next* item while the
 * player is still on the previous one, so the current index cannot be trusted; the record that
 * failed can. [order] is play order from the current row.
 */
internal fun failedRow(entries: List<PlayableEntry>, order: List<Int>, failedRecordKey: String?): Int? {
    if (failedRecordKey == null) return order.firstOrNull()
    return order.firstOrNull { entries.getOrNull(it)?.recordKey == failedRecordKey } ?: order.firstOrNull()
}

/**
 * Where playback goes after a row that cannot be played: the next row, in play order, that
 * belongs to a different upload. Every song of one album is the same file, so they all fail
 * together and are stepped over together. [orderFromFailed] starts at the failed row.
 */
internal fun skipTarget(entries: List<PlayableEntry>, orderFromFailed: List<Int>): Int? {
    val failed = orderFromFailed.firstOrNull()?.let(entries::getOrNull) ?: return null
    return orderFromFailed.drop(1).firstOrNull { index ->
        entries.getOrNull(index)?.let { it.recordKey != failed.recordKey } == true
    }
}

/**
 * How long something must play before it counts as played: half of it, at most
 * [HISTORY_AFTER_MS], and never less than [HISTORY_AT_LEAST_MS] so skipping through a queue
 * leaves no trail.
 */
internal fun historyThresholdMs(durationMs: Long?): Long {
    val half = durationMs?.takeIf { it > 0 }?.div(2) ?: HISTORY_AFTER_MS
    return half.coerceIn(HISTORY_AT_LEAST_MS, HISTORY_AFTER_MS)
}

/** Whether where playback stopped is worth remembering: long things that are played as one piece. */
internal fun remembersPosition(entry: PlayableEntry, durationMs: Long?): Boolean =
    entry.isWholeFile && durationMs != null && durationMs >= RESUME_MIN_DURATION_MS

internal const val PIN_RECORDS = 4
internal const val HISTORY_AFTER_MS = 30_000L
internal const val HISTORY_AT_LEAST_MS = 5_000L
internal const val RESUME_MIN_DURATION_MS = 10 * 60_000L
