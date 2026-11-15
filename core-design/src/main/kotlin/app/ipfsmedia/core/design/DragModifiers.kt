package app.ipfsmedia.core.design

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.Origin

/**
 * Makes this a place a carried item can be put ([target] says which).
 *
 * @param id identity of this registration; pass the same value to [DragController.visualFor] to
 *   learn whether the item is over it. A fresh one is made when omitted.
 * @param onRest called when a held item has rested here a while: open what is collapsed.
 */
fun Modifier.dropTarget(
    target: DropTarget,
    id: Any? = null,
    onRest: (() -> Unit)? = null,
): Modifier = composed {
    val controller = LocalDragController.current
    val key = id ?: remember { Any() }
    val currentTarget by rememberUpdatedState(target)
    val currentOnRest by rememberUpdatedState(onRest)
    DisposableEffect(controller, key) {
        onDispose { controller.unregister(key) }
    }
    onGloballyPositioned { coordinates ->
        controller.register(
            TargetRegistration(
                id = key,
                targetAt = { currentTarget },
                bounds = coordinates.boundsInRoot(),
                onRest = currentOnRest?.let { rest -> { rest() } },
            ),
        )
    }
}

/**
 * A row of a list that takes items at a position. The pointer over the upper half means
 * "before this row" ([index]), over the lower half "after it" ([index] + 1); [targetFor] turns
 * the position into the target.
 */
fun Modifier.dropTargetRow(
    index: Int,
    id: Any,
    targetFor: (Int) -> DropTarget,
): Modifier = composed {
    val controller = LocalDragController.current
    val currentIndex by rememberUpdatedState(index)
    val currentTargetFor by rememberUpdatedState(targetFor)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(controller, id) {
        onDispose { controller.unregister(id) }
    }
    onGloballyPositioned { coordinates ->
        bounds = coordinates.boundsInRoot()
        controller.register(
            TargetRegistration(
                id = id,
                targetAt = { pointer ->
                    val after = pointer.y > bounds.center.y
                    currentTargetFor(if (after) currentIndex + 1 else currentIndex)
                },
                bounds = bounds,
                onRest = null,
            ),
        )
    }
}

/** What a drop target registered with [id] should look like right now. */
@Composable
fun rememberDropVisual(id: Any, target: DropTarget): DropVisual = LocalDragController.current.visualFor(id, target)

/**
 * Scrolls [state] while a carried item is held near the top or bottom edge of this list, faster
 * the nearer the edge.
 */
fun Modifier.dragAutoScroll(state: ScrollableState): Modifier = composed {
    val controller = LocalDragController.current
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val edgePx = with(density) { Gestures.autoScrollEdge.toPx() }
    val maxPxPerSecond = with(density) { Gestures.AUTO_SCROLL_MAX_DP_PER_SECOND * this.density }
    LaunchedEffect(controller.isCarrying) {
        if (!controller.isCarrying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val at = controller.pointer
            if (bounds.isEmpty || at.x < bounds.left || at.x > bounds.right) continue
            val fromTop = at.y - bounds.top
            val fromBottom = bounds.bottom - at.y
            val speed = when {
                fromTop in 0f..edgePx -> -(1f - fromTop / edgePx)
                fromBottom in 0f..edgePx -> 1f - fromBottom / edgePx
                else -> 0f
            }
            if (speed != 0f) state.scrollBy(speed * maxPxPerSecond * seconds)
        }
    }
    onGloballyPositioned { bounds = it.boundsInRoot() }
}

/** How an item was activated, for the cases where the answer changes what happens. */
enum class ActivatedBy { Touch, Mouse, MouseDouble, Keyboard }

/**
 * Tracks the pointer on an item that can be picked up, and reports what happens to it.
 *
 * This modifier *watches*; it does not replace the item's ordinary click handling. The item
 * keeps a normal `combinedClickable`, so it has a ripple, focus, keyboard activation and
 * TalkBack actions like any button, and that is also where the long-press is detected, with the
 * system's own duration. This watches the same pointer one pass earlier, which is what lets it:
 *
 *  - carry the item once a long-press has lifted it (the finger is still down, and from then on
 *    its movement belongs to the drag, not to scrolling);
 *  - lift at once on a mouse press-and-move, or on any press when [immediate] (a row's grip);
 *  - open the actions menu on a right-click, before the click handler sees it;
 *  - tell a mouse double-click from two clicks.
 *
 * @param onLift how the item reports "I was picked up here"; the long-press handler calls the
 *   same function through [PickUpState.liftFromLongPress].
 */
@Composable
fun Modifier.pickUpSource(
    state: PickUpState,
    payload: () -> DragPayload,
    origin: Origin = Origin.Library,
    enabled: Boolean = true,
    immediate: Boolean = false,
    onContextMenu: ((Offset) -> Unit)? = null,
): Modifier {
    val controller = LocalDragController.current
    val actions = LocalItemActions.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val carrySlopPx = with(LocalDensity.current) { Gestures.carrySlop.toPx() }
    val currentPayload by rememberUpdatedState(payload)
    val currentOrigin by rememberUpdatedState(origin)
    val currentOnContextMenu by rememberUpdatedState(onContextMenu)
    val pinnable = LocalPinnableContainer.current
    state.bind(controller, { currentPayload() }, { currentOrigin })

    return this
        .onGloballyPositioned { state.topLeftInRoot = it.positionInRoot() }
        .pointerInput(enabled, immediate, pinnable) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val isMouse = down.type == PointerType.Mouse
                state.lastPointerIsMouse = isMouse
                state.lastPosition = down.position
                state.pressing = true

                if (isMouse && currentEvent.buttons.isSecondaryPressed) {
                    // Right-click: the actions menu at the pointer. Consumed, so it is not also a click.
                    val menu = currentOnContextMenu
                    if (menu != null) {
                        down.consume()
                        menu(down.position)
                    }
                    state.pressing = false
                    return@awaitEachGesture
                }

                if (isMouse) {
                    val now = down.uptimeMillis
                    state.doubleClick = now - state.lastMouseDownAt <= Gestures.DOUBLE_CLICK_MS
                    state.lastMouseDownAt = now
                } else {
                    state.doubleClick = false
                }

                var mine = false
                // A lazy list drops rows that scroll out of view, and with them this gesture.
                // While the item is being carried it is pinned, so edge-scrolling a list does
                // not cancel the move that caused it.
                var pin: PinnableContainer.PinnedHandle? = null
                if (enabled && immediate) {
                    controller.lift(currentPayload(), currentOrigin, state.inRoot(down.position), isMouse, carrying = true)
                    down.consume()
                    mine = true
                }

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        state.lastPosition = change.position

                        // A long-press handled by the item's own click handling lifted it.
                        if (!mine && state.liftedByLongPress) mine = true

                        if (!mine && enabled && isMouse && event.buttons.isPrimaryPressed &&
                            (change.position - down.position).getDistance() > touchSlop
                        ) {
                            // With a mouse, press-and-move lifts at once; there is no wait.
                            controller.lift(currentPayload(), currentOrigin, state.inRoot(change.position), true, carrying = true)
                            mine = true
                        }

                        if (mine) {
                            if (pin == null) pin = pinnable?.pin()
                            // From here the pointer belongs to the drag: nothing below scrolls or clicks.
                            change.consume()
                            if (change.pressed) {
                                controller.moveTo(state.inRoot(change.position), carrySlopPx)
                            } else {
                                actions.performDrop(controller.release())
                                break
                            }
                        } else if (!change.pressed || event.type == PointerEventType.Release) {
                            break
                        }
                    }
                } finally {
                    // Still up after the loop means the gesture was taken away mid-carry (the
                    // item left the composition): put it back rather than leave it hanging.
                    if (mine && (controller.phase == DragPhase.Lifted || controller.phase == DragPhase.Carrying)) {
                        controller.cancel()
                    }
                    pin?.release()
                    state.liftedByLongPress = false
                    state.pressing = false
                }
            }
        }
}

/**
 * State shared between an item's [pickUpSource] and its click handling. One per item:
 * `remember { PickUpState() }`.
 */
class PickUpState {
    internal var topLeftInRoot: Offset = Offset.Zero
    internal var lastPosition: Offset = Offset.Zero
    internal var lastMouseDownAt: Long = Long.MIN_VALUE / 2
    internal var liftedByLongPress: Boolean = false
    internal var pressing: Boolean = false

    /** The last press was a mouse press. Decides between "play" (touch) and "select" (mouse) on rows. */
    var lastPointerIsMouse: Boolean = false
        internal set

    /** The press that produced the current click was the second of a double-click. */
    var doubleClick: Boolean = false
        internal set

    private var controller: DragController? = null
    private var payload: (() -> DragPayload)? = null
    private var origin: (() -> Origin)? = null

    internal fun bind(controller: DragController, payload: () -> DragPayload, origin: () -> Origin) {
        this.controller = controller
        this.payload = payload
        this.origin = origin
    }

    internal fun inRoot(local: Offset): Offset = topLeftInRoot + local

    /** How the last click came about, for [ActivatedBy]. */
    fun activatedBy(): ActivatedBy = when {
        !lastPointerIsMouse -> ActivatedBy.Touch
        doubleClick -> ActivatedBy.MouseDouble
        else -> ActivatedBy.Mouse
    }

    /** Position of the last press within the item, for placing a menu at the pointer. */
    val pressPosition: Offset get() = lastPosition

    /**
     * Call from the item's `onLongClick`. Lifts the item where the finger is; the finger is
     * still down, and [pickUpSource] carries it from here. With a mouse a long press does
     * nothing: the mouse lifts by moving, and its menu is the right button.
     */
    fun liftFromLongPress(): Boolean {
        val controller = controller ?: return false
        val payload = payload ?: return false
        val origin = origin ?: return false
        if (lastPointerIsMouse) return false
        controller.lift(payload(), origin(), inRoot(lastPosition), byMouse = false, carrying = false)
        if (pressing) {
            liftedByLongPress = true
        } else {
            // The long-press came from somewhere with no finger down (an accessibility action):
            // there is nothing to carry with, so this is the tray as a menu.
            controller.release()
        }
        return true
    }
}
