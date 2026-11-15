package app.ipfsmedia.core.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DestinationContext
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.ShortcutScope
import app.ipfsmedia.core.library.destinationsFor
import app.ipfsmedia.core.library.shortcutFor

/** What an item needs to remember between its gestures, its menu and its drawing. */
@Stable
class ItemInteraction internal constructor() {
    val interactionSource: MutableInteractionSource = MutableInteractionSource()
    val pickUp: PickUpState = PickUpState()

    var menuOpen: Boolean by mutableStateOf(false)
        private set

    /** Where the menu was asked for, within the item; null puts it at the item's start. */
    var menuAt: Offset? by mutableStateOf(null)
        private set

    fun openMenu(at: Offset? = null) {
        menuAt = at
        menuOpen = true
    }

    fun closeMenu() {
        menuOpen = false
    }
}

@Composable
fun rememberItemInteraction(): ItemInteraction = remember { ItemInteraction() }

/**
 * Everything an item that can be acted on does, in one modifier (docs/PLAN.md §6 and §7):
 *
 *  - tap, click or Enter activates it ([onActivate] is told how, because a row plays on a tap
 *    but only selects on a single mouse click);
 *  - Space plays it, Q queues it, Delete removes it from a queue, playlist or history;
 *  - long-press lifts it and raises the tray; a mouse press-and-move carries it;
 *  - right-click, the Menu key or Shift+F10 opens its actions as a menu ([ItemMenu]);
 *  - Ctrl+X picks it up from the keyboard; Alt+Up and Alt+Down move a row;
 *  - with the tray already open, tapping it adds it to what is held;
 *  - every action is also a TalkBack custom action, so dragging is never required.
 *
 * All of them act through [ItemActions] on the same [payload], so they cannot offer different
 * things.
 */
@Composable
fun Modifier.itemBehaviour(
    interaction: ItemInteraction,
    payload: () -> DragPayload,
    onActivate: (ActivatedBy) -> Unit,
    origin: Origin = Origin.Library,
    onPlay: (() -> Unit)? = null,
    onMoveRow: ((delta: Int) -> Unit)? = null,
    canPickUp: Boolean = true,
    activateLabel: String = "Open",
): Modifier {
    val controller = LocalDragController.current
    val actions = LocalItemActions.current
    val currentPayload by rememberUpdatedState(payload)
    val currentOrigin by rememberUpdatedState(origin)
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnMoveRow by rememberUpdatedState(onMoveRow)
    val playlists = actions.playlists

    return this
        .pickUpSource(
            state = interaction.pickUp,
            payload = payload,
            origin = origin,
            enabled = canPickUp,
            onContextMenu = { at -> interaction.openMenu(at) },
        )
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val chord = event.toChord() ?: return@onPreviewKeyEvent false
            when (shortcutFor(chord, ShortcutScope.ITEM)) {
                ShortcutAction.OPEN_ITEM -> {
                    currentOnActivate(ActivatedBy.Keyboard)
                    true
                }
                ShortcutAction.PLAY_ITEM -> {
                    val play = currentOnPlay
                    if (play != null) play() else actions.perform(Destination.PlayNow, currentPayload(), currentOrigin)
                    true
                }
                ShortcutAction.QUEUE_ITEM -> {
                    actions.perform(Destination.AddToQueue, currentPayload(), currentOrigin)
                    true
                }
                ShortcutAction.ITEM_ACTIONS -> {
                    interaction.openMenu()
                    true
                }
                ShortcutAction.CUT_ITEM -> {
                    controller.openTray(currentPayload(), currentOrigin)
                    true
                }
                ShortcutAction.REMOVE_ITEM -> {
                    val remove = removalFor(currentOrigin)
                    if (remove != null) actions.perform(remove, currentPayload(), currentOrigin)
                    remove != null
                }
                ShortcutAction.MOVE_ROW_UP -> currentOnMoveRow?.let { it(-1); true } ?: false
                ShortcutAction.MOVE_ROW_DOWN -> currentOnMoveRow?.let { it(1); true } ?: false
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = interaction.interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClickLabel = activateLabel,
            onLongClickLabel = "Actions",
            onLongClick = { if (canPickUp) interaction.pickUp.liftFromLongPress() },
            onClick = {
                // With the tray open, a tap on another item adds it to what is held.
                val joined = controller.isTrayOpen && controller.addToStack(currentPayload())
                if (!joined) currentOnActivate(interaction.pickUp.activatedBy())
            },
        )
        .semantics {
            customActions = destinationsFor(currentPayload(), DestinationContext(playlists, currentOrigin))
                .filter { it.enabled }
                .map { option ->
                    CustomAccessibilityAction(option.label) {
                        actions.perform(option.destination, currentPayload(), currentOrigin)
                        true
                    }
                }
        }
}

/** The "remove" that applies to where an item is shown, or null on a library surface. */
internal fun removalFor(origin: Origin): Destination? = when (origin) {
    is Origin.Queue -> Destination.RemoveFromQueue
    is Origin.Playlist -> Destination.RemoveFromPlaylist
    is Origin.History -> Destination.RemoveFromHistory
    Origin.Library -> null
}

/**
 * An item's actions as an ordinary menu: what right-click, the Menu key and the "more" button
 * show. It is the same list the tray shows as slots, plus navigation and removal, which the tray
 * leaves out.
 *
 * Place it first inside the item's outermost box (top-start); it opens at the pointer when
 * there was one, and at the item's corner otherwise.
 */
@Composable
fun ItemMenu(
    interaction: ItemInteraction,
    payload: () -> DragPayload,
    origin: Origin = Origin.Library,
    modifier: Modifier = Modifier,
) {
    if (!interaction.menuOpen) return
    val actions = LocalItemActions.current
    val shown = remember(interaction.menuAt) { payload() }
    val options = remember(shown, actions.playlists, origin) {
        destinationsFor(shown, DestinationContext(actions.playlists, origin))
    }
    val at = interaction.menuAt
    val offset = with(LocalDensity.current) {
        if (at == null) DpOffset.Zero else DpOffset(at.x.toDp(), at.y.toDp())
    }
    // A zero-size anchor at the item's top-left corner, so the offset is measured from there and
    // the menu opens at the pointer rather than under the item.
    Box(modifier = modifier.size(Space.none)) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = interaction::closeMenu,
            offset = offset,
        ) {
            options.forEachIndexed { index, option ->
                if (index > 0 && options[index - 1].group != option.group) HorizontalDivider()
                DropdownMenuItem(
                    text = { MenuLabel(label = option.label, refusal = option.refusal) },
                    onClick = {
                        interaction.closeMenu()
                        actions.perform(option.destination, shown, origin)
                    },
                    enabled = option.enabled,
                    leadingIcon = {
                        Icon(
                            imageVector = iconFor(option.destination),
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.icon),
                        )
                    },
                )
            }
        }
    }
}
