package haven.mobile.core.cache

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Staged files the trimmer must leave alone.
 *
 * The spool drops the oldest-touched file first when it goes over budget. That is right for
 * something opened last week and wrong for the album the queue is about to reach: evicting it
 * would turn "next track" into a fresh fetch. The player publishes the pieces it is playing and
 * about to play here, and [PlaintextSpoolImpl] skips them.
 *
 * Not in haven-mobile: its viewer plays one item at a time, so "just written" was the only file
 * that needed protecting.
 */
@Singleton
class SpoolPins @Inject constructor() {
    @Volatile
    private var pinned: Set<String> = emptySet()

    /** Replaces the pinned set with [pieceCids]. */
    fun set(pieceCids: Collection<String>) {
        pinned = pieceCids.toSet()
    }

    fun snapshot(): Set<String> = pinned
}
