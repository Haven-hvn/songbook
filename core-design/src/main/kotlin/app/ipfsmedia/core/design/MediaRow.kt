package app.ipfsmedia.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.DropVerdict
import app.ipfsmedia.core.library.Origin
import kotlinx.coroutines.launch

/** What sits at the start of a row. */
sealed interface RowLeading {
    /** The upload's cover art. */
    data class Art(val recordKey: String, val title: String) : RowLeading

    /** A track number, as on an album page. */
    data class Number(val number: Int) : RowLeading

    data object None : RowLeading
}

/** Makes a row a place items can be inserted before or after: a queue row, a playlist row. */
data class RowDrop(
    val index: Int,
    val targetFor: (Int) -> DropTarget,
)

/**
 * A song, a queued entry, a playlist or history entry, a search hit: the one row every list in
 * the app is made of.
 *
 * Like [MediaCard] it carries its behaviour. What differs by where it is shown is passed in:
 * [origin] decides which "remove" it offers, [drop] makes it a place to insert at, [grip] gives
 * it the handle that moves it without a long-press, and [onActivate] says what a tap means
 * there.
 *
 * Swiping right adds the row to the queue; swiping left removes it from a queue, playlist or
 * history. Both are only the quick path: the same actions are in the tray and the menu.
 *
 * @param unavailable why the row cannot be played ("Unavailable", "Members of Night Shift
 *   only"); the row is dimmed and says so instead of a duration.
 */
@Composable
fun MediaRow(
    title: String,
    subtitle: String?,
    payload: () -> DragPayload,
    onActivate: (ActivatedBy) -> Unit,
    modifier: Modifier = Modifier,
    origin: Origin = Origin.Library,
    leading: RowLeading = RowLeading.None,
    recordKey: String? = null,
    chapterIndex: Int? = null,
    trailing: String? = null,
    badge: String? = null,
    unavailable: String? = null,
    selected: Boolean = false,
    grip: Boolean = false,
    drop: RowDrop? = null,
    onMoveRow: ((delta: Int) -> Unit)? = null,
    swipeToQueue: Boolean = true,
) {
    val actions = LocalItemActions.current
    val input = LocalInputKind.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    val removal = removalFor(origin)
    val canQueue = swipeToQueue && unavailable == null && origin !is Origin.Queue

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = canQueue,
        enableDismissFromEndToStart = removal != null,
        // Swiping is a touch gesture. A mouse carries the row instead.
        gesturesEnabled = input.hasTouch && (canQueue || removal != null),
        backgroundContent = {
            SwipeBackground(
                direction = swipe.dismissDirection,
                removeLabel = "Remove",
            )
        },
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.perform(Destination.AddToQueue, payload(), origin)
                SwipeToDismissBoxValue.EndToStart -> removal?.let { actions.perform(it, payload(), origin) }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // The row comes back: queueing leaves it where it was, and a removed row is taken
            // out of the list by the list, not by the swipe.
            scope.launch { swipe.reset() }
        },
    ) {
        RowBody(
            title = title,
            subtitle = subtitle,
            payload = payload,
            onActivate = onActivate,
            origin = origin,
            leading = leading,
            recordKey = recordKey,
            chapterIndex = chapterIndex,
            trailing = trailing,
            badge = badge,
            unavailable = unavailable,
            selected = selected,
            grip = grip,
            drop = drop,
            onMoveRow = onMoveRow,
        )
    }
}

@Composable
private fun RowBody(
    title: String,
    subtitle: String?,
    payload: () -> DragPayload,
    onActivate: (ActivatedBy) -> Unit,
    origin: Origin,
    leading: RowLeading,
    recordKey: String?,
    chapterIndex: Int?,
    trailing: String?,
    badge: String?,
    unavailable: String?,
    selected: Boolean,
    grip: Boolean,
    drop: RowDrop?,
    onMoveRow: ((delta: Int) -> Unit)?,
) {
    val colors = appColors
    val input = LocalInputKind.current
    val interaction = rememberItemInteraction()
    val hovered by interaction.interactionSource.collectIsHoveredAsState()
    val focused by interaction.interactionSource.collectIsFocusedAsState()
    val nowPlaying = LocalNowPlaying.current
    val isCurrent = recordKey != null &&
        (if (chapterIndex != null) nowPlaying.isEntry(recordKey, chapterIndex) else nowPlaying.isRecord(recordKey))
    val stage = recordKey?.let { LocalStages.current[it] }
    val dropId = remember { Any() }
    val visual = if (drop != null) rememberDropVisual(dropId, drop.targetFor(drop.index)) else DropVisual()
    val lineColor = when {
        !visual.hovered -> null
        visual.verdict is DropVerdict.Accept -> colors.accent
        visual.verdict is DropVerdict.Refuse -> colors.error
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (input.touchOnly) Sizes.rowHeightTouch else Sizes.rowHeight)
            .then(if (drop != null) Modifier.dropTargetRow(drop.index, dropId, drop.targetFor) else Modifier)
            .clip(Radius.md)
            .background(
                when {
                    selected -> colors.accentSurface
                    hovered || focused -> colors.inset
                    else -> colors.ground
                },
            )
            // The insertion line: above the row for "before", below it for "after".
            .drawWithContent {
                drawContent()
                if (lineColor != null) {
                    val y = if (visual.after) size.height - Sizes.dropLine.toPx() / 2 else Sizes.dropLine.toPx() / 2
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = Sizes.dropLine.toPx(),
                    )
                }
            }
            .itemBehaviour(
                interaction = interaction,
                payload = payload,
                onActivate = onActivate,
                origin = origin,
                onMoveRow = onMoveRow,
                activateLabel = "Play",
            )
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = listOfNotNull(title, subtitle, badge, unavailable ?: trailing, stage?.label)
                    .joinToString(", ")
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        ItemMenu(
            interaction = interaction,
            payload = payload,
            origin = origin,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.x2, vertical = Space.x1)
                .alpha(if (unavailable != null) UNAVAILABLE_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            if (grip) {
                // The grip moves the row at once: no long-press, and it never fights scrolling.
                val gripState = remember { PickUpState() }
                Box(
                    modifier = Modifier
                        .size(Sizes.touchTarget)
                        .cursor(Cursor.Grab)
                        .pickUpSource(state = gripState, payload = payload, origin = origin, immediate = true)
                        .semantics { contentDescription = "Move" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Grip,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            }
            when (leading) {
                is RowLeading.Art -> Box(modifier = Modifier.size(Sizes.rowArt)) {
                    Artwork(
                        recordKey = leading.recordKey,
                        title = leading.title,
                        modifier = Modifier.matchParentSize(),
                        shape = Radius.sm,
                        showInitials = false,
                    )
                    if (isCurrent) PlayingMarker(modifier = Modifier.align(Alignment.Center))
                }
                is RowLeading.Number -> Box(modifier = Modifier.width(Space.x8), contentAlignment = Alignment.Center) {
                    if (isCurrent) {
                        PlayingMarker()
                    } else {
                        Text(
                            text = leading.number.toString(),
                            style = AppType.clock,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                RowLeading.None -> if (isCurrent) PlayingMarker()
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) colors.accent else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stage != null) {
                    StageIndicator(stage = stage)
                } else if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (badge != null && !LocalWidthClass.current.isCompact) DaoBadge(name = badge)
            val end = unavailable ?: trailing
            if (end != null) {
                Text(
                    text = end,
                    style = if (unavailable != null) MaterialTheme.typography.labelSmall else AppType.clock,
                    color = if (unavailable != null) colors.warning else colors.textSecondary,
                    maxLines = 1,
                )
            }
            IconAction(
                icon = AppIcons.More,
                label = "Actions for $title",
                onClick = { interaction.openMenu() },
            )
        }
    }
}

/** What shows behind a row as it is swiped: where it is going. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, removeLabel: String) {
    val colors = appColors
    val toQueue = direction == SwipeToDismissBoxValue.StartToEnd
    val toRemove = direction == SwipeToDismissBoxValue.EndToStart
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(Radius.md)
            .background(
                when {
                    toQueue -> colors.accentSurface
                    toRemove -> colors.errorSurface
                    else -> colors.ground
                },
            )
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (toRemove) Arrangement.End else Arrangement.Start,
    ) {
        if (toQueue || toRemove) {
            Icon(
                imageVector = if (toQueue) AppIcons.AddToQueue else AppIcons.Delete,
                contentDescription = null,
                tint = if (toQueue) colors.accent else colors.error,
                modifier = Modifier.size(Sizes.icon),
            )
            Text(
                text = if (toQueue) "Add to queue" else removeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (toQueue) colors.accent else colors.error,
                modifier = Modifier.padding(horizontal = Space.x2),
            )
        }
    }
}

private const val UNAVAILABLE_ALPHA = 0.55f
