package app.ipfsmedia.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DestinationContext
import app.ipfsmedia.core.library.DestinationOption
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.DropVerdict
import app.ipfsmedia.core.library.destinationsFor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Hosts the drag layer for everything inside it: provides the [DragController], and draws the
 * tray and the carried card above [content].
 *
 * The tray rises just above the player bar ([trayBottomInset] is how much room the bar takes)
 * and holds the places the held item can go as large slots. Carrying an item onto a slot and
 * tapping a slot after letting go are the same act.
 */
@Composable
fun DragHost(
    controller: DragController,
    trayBottomInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actions = LocalItemActions.current
    var origin by remember { mutableStateOf(Offset.Zero) }

    // Something collapsed opens when the held item rests on it.
    val resting = controller.hoveredRegistration
    LaunchedEffect(resting?.id) {
        val onRest = resting?.onRest ?: return@LaunchedEffect
        delay(Gestures.HOVER_TO_OPEN_MS)
        onRest()
    }

    CompositionLocalProvider(LocalDragController provides controller) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .onPreviewKeyEvent { event ->
                    // Escape cancels a move. It is never a general Back.
                    if (controller.isActive && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        controller.cancel()
                        true
                    } else {
                        false
                    }
                },
        ) {
            content()

            // With the tray open as a menu, a tap anywhere else closes it.
            if (controller.isTrayOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Close",
                            onClick = controller::cancel,
                        ),
                )
            }

            val held = controller.held
            AnimatedVisibility(
                visible = held != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = trayBottomInset + Space.x3),
            ) {
                // Keep drawing the last payload while the tray slides away.
                var last by remember { mutableStateOf(held) }
                if (held != null) last = held
                last?.let { shown ->
                    Tray(
                        held = shown,
                        controller = controller,
                        onChoose = { destination ->
                            controller.choose(destination)?.let { actions.perform(it.destination, it.payload, it.origin) }
                        },
                    )
                }
            }

            if (held != null && controller.phase != DragPhase.TrayOpen) {
                CarriedCard(controller = controller, held = held, hostOrigin = origin)
            }
        }
    }
}

/** Carries out a completed drop. Called by the item that was being dragged when its pointer lifts. */
internal fun ItemActions.performDrop(drop: Drop?) {
    if (drop != null) perform(drop.destination, drop.payload, drop.origin)
}

@Composable
private fun Tray(held: Held, controller: DragController, onChoose: (Destination) -> Unit) {
    val actions = LocalItemActions.current
    val options = remember(held, actions.playlists) {
        destinationsFor(held.payload, DestinationContext(actions.playlists, held.origin)).filter { it.inTray }
    }
    val firstSlot = remember { FocusRequester() }
    // Opened from the keyboard, the tray takes focus so its slots can be reached with Tab and arrows.
    LaunchedEffect(controller.isTrayOpen) {
        if (controller.isTrayOpen) runCatching { firstSlot.requestFocus() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (controller.isTrayOpen) {
            Text(
                text = trayTitle(held.payload),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(bottom = Space.x2)
                    .background(MaterialTheme.colorScheme.inverseSurface, Radius.pill)
                    .padding(horizontal = Space.x3, vertical = Space.x1),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.x3),
            contentAlignment = Alignment.Center,
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                expandedShadowElevation = Elevation.tray,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    options.forEachIndexed { index, option ->
                        TraySlot(
                            option = option,
                            controller = controller,
                            onChoose = onChoose,
                            modifier = if (index == 0) Modifier.focusRequester(firstSlot) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

private fun trayTitle(payload: DragPayload): String =
    if (payload.count > 1) "${payload.count} items" else payload.label

@Composable
private fun TraySlot(
    option: DestinationOption,
    controller: DragController,
    onChoose: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val target = remember(option.destination) { DropTarget.TraySlot(option.destination) }
    val id = remember { Any() }
    val visual = controller.visualFor(id, target)
    val lit = visual.hovered && option.enabled
    val refused = !option.enabled
    Surface(
        onClick = { if (option.enabled) onChoose(option.destination) },
        enabled = controller.isTrayOpen,
        shape = Radius.xl,
        color = when {
            lit -> colors.accentFill
            visual.hovered && refused -> colors.errorSurface
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            lit -> colors.onAccentFill
            refused -> colors.textTertiary
            else -> colors.textPrimary
        },
        modifier = modifier
            .size(width = Sizes.traySlotWidth, height = Sizes.traySlotHeight)
            .dropTarget(id = id, target = target)
            .semantics {
                contentDescription = option.refusal?.let { "${option.label}. $it" } ?: option.label
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Space.x2, vertical = Space.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = iconFor(option.destination),
                contentDescription = null,
                modifier = Modifier.size(Sizes.icon),
            )
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // A slot that cannot take the item is still shown, struck through, so a locked
                // item explains itself instead of hiding what it cannot do.
                textDecoration = if (refused) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
    }
}

/** The held item, following the pointer as a small card, offset so a finger does not cover it. */
@Composable
private fun CarriedCard(controller: DragController, held: Held, hostOrigin: Offset) {
    val colors = appColors
    val density = LocalDensity.current
    val offsetX = if (controller.byMouse) Gestures.carriedOffsetPointerX else Gestures.carriedOffsetX
    val offsetY = if (controller.byMouse) Gestures.carriedOffsetPointerY else Gestures.carriedOffsetY
    val verdict = controller.hoveredVerdict
    Surface(
        shape = Radius.lg,
        color = colors.elevated,
        contentColor = colors.textPrimary,
        shadowElevation = Elevation.carried,
        modifier = Modifier
            // Read at layout time, so moving the pointer does not recompose the card.
            .offset {
                val at = controller.pointer - hostOrigin
                with(density) {
                    IntOffset(
                        (at.x + offsetX.toPx()).roundToInt(),
                        (at.y + offsetY.toPx()).roundToInt(),
                    )
                }
            }
            .width(Sizes.carriedCardWidth)
            .graphicsLayer {
                rotationZ = Gestures.CARRIED_TILT_DEGREES
                scaleX = Gestures.CARRIED_SCALE
                scaleY = Gestures.CARRIED_SCALE
            },
    ) {
        Row(
            modifier = Modifier.padding(Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            GeneratedCover(
                seed = carriedSeed(held.payload),
                title = held.payload.label,
                modifier = Modifier.size(Sizes.rowArt),
                shape = Radius.sm,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = held.payload.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val (line, lineColor) = when (verdict) {
                    is DropVerdict.Accept -> verdict.label to colors.accent
                    is DropVerdict.Refuse -> verdict.reason to colors.error
                    else -> null to colors.textSecondary
                }
                if (line != null) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelMedium,
                        color = lineColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (held.payload.count > 1) CountBadge(count = held.payload.count)
        }
    }
}

private fun carriedSeed(payload: DragPayload): String = when (payload) {
    is DragPayload.Media -> payload.whole?.recordKey ?: payload.entries.firstOrNull()?.recordKey ?: payload.label
    is DragPayload.Playlist -> "playlist-${payload.id}"
    is DragPayload.Uploader -> payload.address
    is DragPayload.DataDao -> payload.key
}

/** The icon for a destination, wherever it is listed: the tray, a menu, a button. */
fun iconFor(destination: Destination) = when (destination) {
    Destination.PlayNow -> AppIcons.Play
    Destination.PlayNext -> AppIcons.PlayNext
    Destination.AddToQueue, is Destination.QueueAt -> AppIcons.AddToQueue
    Destination.Favourite -> AppIcons.FavouriteOutline
    Destination.Unfavourite -> AppIcons.Favourite
    Destination.Download -> AppIcons.Download
    is Destination.Playlist, is Destination.PlaylistAt -> AppIcons.Playlist
    Destination.NewPlaylist -> AppIcons.PlaylistAdd
    Destination.Follow -> AppIcons.Follow
    Destination.Unfollow -> AppIcons.Unfollow
    Destination.Pin -> AppIcons.PinOutline
    Destination.Unpin -> AppIcons.Pin
    Destination.RemoveFromQueue, Destination.RemoveFromPlaylist, Destination.RemoveFromHistory -> AppIcons.Close
    Destination.RenamePlaylist -> AppIcons.Edit
    Destination.DeletePlaylist -> AppIcons.Delete
    Destination.GoToAlbum -> AppIcons.Album
    Destination.GoToUploader -> AppIcons.Person
    Destination.GoToDataDao -> AppIcons.DataDao
}
