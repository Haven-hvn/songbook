package app.ipfsmedia.core.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every timing and distance the touch and pointer behaviour depends on, in one place.
 *
 * Nothing is built or run before CI, so these are first guesses to be tuned once a build is on
 * a Googlebook (docs/PLAN.md §14, finding 2). Tuning them is not a redesign: the grip, the swipe
 * and the tray all work without the carry gesture.
 *
 * The long-press duration is not here on purpose. It is the system's
 * (`ViewConfiguration.longPressTimeoutMillis`), so the accessibility setting for it applies.
 */
object Gestures {
    /** How far a held item must move before it counts as being carried rather than held still. */
    val carrySlop: Dp = 12.dp

    /** The carried card sits this far up and to the side of the finger, so the finger does not cover it. */
    val carriedOffsetX: Dp = 16.dp
    val carriedOffsetY: Dp = (-72).dp

    /** With a mouse the card hangs just below the pointer instead. */
    val carriedOffsetPointerX: Dp = 12.dp
    val carriedOffsetPointerY: Dp = 12.dp

    /** A held item this close to the top or bottom of a list scrolls it. */
    val autoScrollEdge: Dp = 64.dp

    /** Fastest edge scroll, in dp per second, reached at the very edge. */
    const val AUTO_SCROLL_MAX_DP_PER_SECOND: Float = 900f

    /** How long a held item rests on something collapsed (a sidebar group, the queue button) before it opens. */
    const val HOVER_TO_OPEN_MS: Long = 650L

    /** How far a picked-up card tilts, in degrees. */
    const val CARRIED_TILT_DEGREES: Float = -3f

    /** How much a picked-up card grows. */
    const val CARRIED_SCALE: Float = 1.04f

    /** A second click this soon after the first is a double-click (mouse): play. */
    const val DOUBLE_CLICK_MS: Long = 300L

    /** A notice with Undo stays this long. */
    const val UNDO_NOTICE_MS: Long = 6_000L

    /** How far a row must be swiped, as a fraction of its width, to act. */
    const val SWIPE_FRACTION: Float = 0.35f

    /** The wheel over the seek bar moves this far per notch. */
    const val WHEEL_SEEK_MS: Long = 5_000L

    /** The wheel over the volume control moves this far per notch. */
    const val WHEEL_VOLUME_STEP: Float = 0.05f

    /** Arrow keys on the volume control, and the volume shortcuts. */
    const val KEY_VOLUME_STEP: Float = 0.05f

    /** Left and right with nothing focused, and double-tap on either side of a video. */
    const val KEY_SEEK_MS: Long = 10_000L

    /** Video controls hide this long after the last touch or pointer movement while playing. */
    const val VIDEO_CONTROLS_HIDE_MS: Long = 3_000L

    /** How far a Home shelf's grip is dragged before the shelf changes places with its neighbour. */
    val shelfMoveDistance: Dp = 96.dp
}
