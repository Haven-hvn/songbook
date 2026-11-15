package app.ipfsmedia.core.design

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.DropVerdict
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.dropVerdict

/** What is lifted, and where it came from. */
data class Held(
    val payload: DragPayload,
    val origin: Origin,
)

/** Where a lifted item is in its life. */
enum class DragPhase {
    /** Nothing is lifted. */
    Idle,

    /** Lifted by a long-press and not yet moved. Letting go now leaves the tray open. */
    Lifted,

    /** Being carried. Letting go drops it on what is under it, or cancels. */
    Carrying,

    /** The tray is open with nothing being carried: the menu form of the same list. */
    TrayOpen,
}

/** A place registered with the drag layer. */
internal class TargetRegistration(
    val id: Any,
    /** The target for a pointer at this position; lets one row answer "before me" or "after me". */
    val targetAt: (Offset) -> DropTarget,
    val bounds: Rect,
    /** Called when the held item has rested here a while: open what is collapsed. */
    val onRest: (() -> Unit)?,
)

/** What a drop target should look like right now. */
data class DropVisual(
    /** Something is being carried, so targets show whether they take it. */
    val active: Boolean = false,
    val verdict: DropVerdict = DropVerdict.Ignore,
    /** The carried item is over this target. */
    val hovered: Boolean = false,
    /** For row targets: the pointer is in the lower half, so the item would land after this row. */
    val after: Boolean = false,
) {
    val accepts: Boolean get() = active && verdict is DropVerdict.Accept
    val refuses: Boolean get() = active && verdict is DropVerdict.Refuse

    /** Has nothing to do with what is held: drawn dimmed while something is carried. */
    val dimmed: Boolean get() = active && verdict is DropVerdict.Ignore
}

/**
 * The state of the in-window drag layer (docs/PLAN.md §7).
 *
 * One instance for the window, provided by [DragHost]. Items report being picked up and moved;
 * targets register their bounds; the host draws the carried card and the tray. Nothing here
 * decides what a drop *means*: that is `dropVerdict` and `destinationsFor` in `core-library`,
 * and carrying it out is [ItemActions.perform]. So the same rules serve touch, mouse, keyboard
 * and TalkBack, and are tested as plain data.
 */
@Stable
class DragController {
    var held: Held? by mutableStateOf(null)
        private set

    var phase: DragPhase by mutableStateOf(DragPhase.Idle)
        private set

    /** Pointer position in window coordinates while lifted or carrying. */
    var pointer: Offset by mutableStateOf(Offset.Zero)
        private set

    /** The item was lifted with a mouse: the carried card hangs below the pointer, not above a finger. */
    var byMouse: Boolean by mutableStateOf(false)
        private set

    private var liftedAt: Offset = Offset.Zero
    private val targets = mutableStateMapOf<Any, TargetRegistration>()

    internal val hoveredRegistration: TargetRegistration? by derivedStateOf {
        if (phase != DragPhase.Carrying) {
            null
        } else {
            val at = pointer
            // Targets nest (a row inside the queue pane); the smallest one under the pointer wins.
            targets.values
                .filter { it.bounds.contains(at) }
                .minByOrNull { it.bounds.width * it.bounds.height }
        }
    }

    /** The target under the carried item, if any. */
    val hoveredTarget: DropTarget? by derivedStateOf {
        hoveredRegistration?.targetAt?.invoke(pointer)
    }

    /** What letting go right now would do. */
    val hoveredVerdict: DropVerdict? by derivedStateOf {
        val payload = held?.payload
        val target = hoveredTarget
        if (payload == null || target == null) null else dropVerdict(payload, target)
    }

    val isActive: Boolean get() = phase != DragPhase.Idle
    val isCarrying: Boolean get() = phase == DragPhase.Carrying
    val isTrayOpen: Boolean get() = phase == DragPhase.TrayOpen

    /**
     * An item was picked up.
     *
     * @param carrying true when it is already moving (a mouse press-and-move, or a grip), false
     *   for a long-press that has not moved yet.
     */
    fun lift(payload: DragPayload, origin: Origin, at: Offset, byMouse: Boolean, carrying: Boolean) {
        held = Held(payload, origin)
        pointer = at
        liftedAt = at
        this.byMouse = byMouse
        phase = if (carrying) DragPhase.Carrying else DragPhase.Lifted
    }

    /** The pointer holding the item moved. [carrySlopPx] is how far counts as carrying. */
    fun moveTo(at: Offset, carrySlopPx: Float) {
        if (phase != DragPhase.Lifted && phase != DragPhase.Carrying) return
        pointer = at
        if (phase == DragPhase.Lifted && (at - liftedAt).getDistance() > carrySlopPx) {
            phase = DragPhase.Carrying
        }
    }

    /**
     * The pointer was lifted. Returns what to carry out, if the item was dropped on something
     * that takes it. An item that was never moved stays up with the tray open.
     */
    fun release(): Drop? {
        val current = held ?: return null
        return when (phase) {
            DragPhase.Lifted -> {
                phase = DragPhase.TrayOpen
                null
            }
            DragPhase.Carrying -> {
                val verdict = hoveredVerdict
                clear()
                (verdict as? DropVerdict.Accept)?.let { Drop(it.destination, current.payload, current.origin) }
            }
            DragPhase.TrayOpen, DragPhase.Idle -> null
        }
    }

    /** Opens the tray for [payload] without a gesture: the keyboard's "pick up" (Ctrl+X). */
    fun openTray(payload: DragPayload, origin: Origin) {
        held = Held(payload, origin)
        byMouse = false
        phase = DragPhase.TrayOpen
    }

    /** A tray slot was chosen while the tray was open as a menu. */
    fun choose(destination: Destination): Drop? {
        val current = held ?: return null
        clear()
        return Drop(destination, current.payload, current.origin)
    }

    /** The keyboard's "put down" (Ctrl+V) on a focused target. */
    fun putDownOn(target: DropTarget): Drop? {
        val current = held ?: return null
        val verdict = dropVerdict(current.payload, target) as? DropVerdict.Accept ?: return null
        clear()
        return Drop(verdict.destination, current.payload, current.origin)
    }

    /**
     * With the tray open, tapping another item adds it to what is held (docs/PLAN.md §7).
     * Returns false when there is nothing held that it can join.
     */
    fun addToStack(more: DragPayload): Boolean {
        val current = held ?: return false
        val base = current.payload as? DragPayload.Media ?: return false
        val extra = more as? DragPayload.Media ?: return false
        if (phase != DragPhase.TrayOpen) return false
        val known = base.entries.map { it.id }.toSet()
        val added = extra.entries.filter { it.id !in known }
        if (added.isEmpty()) return true
        held = current.copy(
            payload = DragPayload.Media(
                entries = base.entries + added,
                whole = null,
                // A stack is locked if any part of it is: it cannot be played as a whole.
                lockedReason = base.lockedReason ?: extra.lockedReason,
                isFavourite = false,
                isOnDevice = base.isOnDevice && extra.isOnDevice,
            ),
        )
        return true
    }

    /** Escape, Back, a tap outside the tray, or letting go over nothing. */
    fun cancel() {
        clear()
    }

    private fun clear() {
        held = null
        phase = DragPhase.Idle
    }

    /** What [target] should look like, given what is held. Reads state, so it recomposes its reader. */
    fun visualFor(id: Any?, target: DropTarget): DropVisual {
        val payload = held?.payload ?: return DropVisual()
        if (phase != DragPhase.Carrying) return DropVisual()
        val registration = hoveredRegistration
        val hovered = id != null && registration?.id == id
        val effective = if (hovered) hoveredTarget ?: target else target
        return DropVisual(
            active = true,
            verdict = dropVerdict(payload, effective),
            hovered = hovered,
            after = hovered && effective != target,
        )
    }

    internal fun register(registration: TargetRegistration) {
        targets[registration.id] = registration
    }

    internal fun unregister(id: Any) {
        targets.remove(id)
    }
}

/** A completed drop or tray choice, ready to be carried out. */
data class Drop(
    val destination: Destination,
    val payload: DragPayload,
    val origin: Origin,
)

val LocalDragController = staticCompositionLocalOf { DragController() }
