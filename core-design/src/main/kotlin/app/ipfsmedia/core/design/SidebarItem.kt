package app.ipfsmedia.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.DropVerdict
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.ShortcutScope
import app.ipfsmedia.core.library.shortcutFor
import app.ipfsmedia.core.library.withShortcutHint

/**
 * One entry of the sidebar: a section (Home, Collection…), a pinned DataDAO, a playlist, a
 * followed uploader.
 *
 * It is also where many drops land. While something is carried, an entry that can take it is
 * outlined, the one under it fills, one that would refuse it says why on the carried card, and
 * the rest dim. With the keyboard, Ctrl+V on a focused entry puts down what Ctrl+X picked up.
 *
 * @param rail the sidebar is collapsed to a rail: icon only, the label as a tooltip.
 * @param payload set when the entry itself can be carried (a pin, a playlist, an uploader);
 *   it then has the full item behaviour, including its own actions menu.
 * @param onRest called when a carried item has rested on this entry: open what it stands for.
 * @param onMoveRow Alt+Up and Alt+Down on a focused entry that can be reordered (a pin, a
 *   playlist): -1 moves it up, +1 down.
 */
@Composable
fun SidebarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    badgeCount: Int? = null,
    rail: Boolean = false,
    shortcut: ShortcutAction? = null,
    dropTarget: DropTarget? = null,
    onRest: (() -> Unit)? = null,
    payload: DragPayload? = null,
    onMoveRow: ((delta: Int) -> Unit)? = null,
) {
    val colors = appColors
    val controller = LocalDragController.current
    val actions = LocalItemActions.current
    val input = LocalInputKind.current
    val interaction = rememberItemInteraction()
    val dropId = remember { Any() }
    val visual = if (dropTarget != null) controller.visualFor(dropId, dropTarget) else DropVisual()
    val lit = visual.accepts && visual.hovered

    val ground = when {
        lit -> colors.accentFill
        selected -> colors.accentSurface
        else -> Color.Transparent
    }
    val content = when {
        lit -> colors.onAccentFill
        selected -> colors.accent
        else -> colors.textSecondary
    }
    val border = when {
        visual.refuses && visual.hovered -> BorderStroke(Sizes.focusRing, colors.error)
        visual.accepts -> BorderStroke(Sizes.focusRing, colors.accent)
        else -> null
    }
    val description = buildString {
        append(label)
        if (supporting != null) append(", ").append(supporting)
        if (badgeCount != null && badgeCount > 0) append(", ").append(badgeCount).append(" new")
    }

    val behaviour = if (payload != null) {
        Modifier.itemBehaviour(
            interaction = interaction,
            payload = { payload },
            onActivate = { onClick() },
            onMoveRow = onMoveRow,
        )
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            role = Role.Tab,
            onClick = onClick,
        )
    }

    val body: @Composable () -> Unit = {
        Surface(
            shape = Radius.md,
            color = ground,
            contentColor = content,
            border = border,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = if (input.touchOnly) Sizes.touchTarget else Sizes.sidebarItemHeight)
                .alpha(if (visual.dimmed) DIMMED_ALPHA else 1f)
                .then(if (dropTarget != null) Modifier.dropTarget(dropTarget, dropId, onRest) else Modifier)
                .onPreviewKeyEvent { event ->
                    // Ctrl+V on a focused entry puts down what was picked up with Ctrl+X.
                    val chord = if (event.type == KeyEventType.KeyDown) event.toChord() else null
                    if (dropTarget != null && chord != null &&
                        shortcutFor(chord, ShortcutScope.SHELL) == ShortcutAction.PASTE_ITEM
                    ) {
                        val drop = controller.putDownOn(dropTarget)
                        actions.performDrop(drop)
                        drop != null
                    } else {
                        false
                    }
                }
                .then(behaviour)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    contentDescription = description
                    val verdict = visual.verdict
                    if (visual.hovered && verdict is DropVerdict.Refuse) stateDescription = verdict.reason
                },
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                if (payload != null) {
                    ItemMenu(
                        interaction = interaction,
                        payload = { payload },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (rail) Space.none else Space.x3, vertical = Space.x1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(Space.x3),
                ) {
                    when {
                        leading != null -> leading()
                        icon != null -> Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.icon),
                        )
                    }
                    if (!rail) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (supporting != null) {
                                Text(
                                    text = supporting,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (lit) colors.onAccentFill else colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (badgeCount != null && badgeCount > 0) CountBadge(count = badgeCount)
                    }
                }
            }
        }
    }

    if (rail) {
        // Collapsed to icons, the label moves into a tooltip, with the shortcut when there is one.
        val tip = if (shortcut != null) withShortcutHint(label, shortcut, input.hasKeyboard) else label
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
            tooltip = { PlainTooltip { Text(tip) } },
            state = rememberTooltipState(),
        ) { body() }
    } else {
        body()
    }
}

private const val DIMMED_ALPHA = 0.4f
