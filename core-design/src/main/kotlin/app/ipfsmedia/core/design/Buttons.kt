package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.withShortcutHint

/**
 * An icon-only button. It always has a label: read by screen readers, and shown as a tooltip on
 * hover, on focus and on long-press, with the shortcut when a keyboard is attached
 * ("Queue (Q)").
 */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shortcut: ShortcutAction? = null,
    tint: Color = Color.Unspecified,
    /** For toggles: the on state is drawn in the accent colour and announced. */
    selected: Boolean = false,
    large: Boolean = false,
) {
    val colors = appColors
    val input = LocalInputKind.current
    val tooltip = if (shortcut != null) withShortcutHint(label, shortcut, input.hasKeyboard) else label
    val content = when {
        tint != Color.Unspecified -> tint
        selected -> colors.accent
        else -> colors.textSecondary
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(contentColor = content),
            modifier = modifier
                .size(Sizes.touchTarget)
                .semantics {
                    contentDescription = label
                    if (selected) stateDescription = "On"
                },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (large) Sizes.iconLarge else Sizes.icon),
            )
        }
    }
}

/**
 * The play and pause button of a transport. Round at rest, and its shape squeezes while it is
 * pressed (Material 3 Expressive's shape morph), which is the one place in the transport where
 * the press itself is worth showing.
 */
@Composable
fun PlayPauseButton(
    showsPause: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val colors = appColors
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = colors.textPrimary,
            contentColor = colors.ground,
        ),
        modifier = modifier
            .size(if (large) Sizes.playButtonLarge else Sizes.playButton)
            .semantics { contentDescription = if (showsPause) "Pause" else "Play" },
    ) {
        Icon(
            imageVector = if (showsPause) AppIcons.Pause else AppIcons.Play,
            contentDescription = null,
            modifier = Modifier.size(if (large) Sizes.iconLarge + Space.x2 else Sizes.iconLarge),
        )
    }
}

/** A filled button in the accent purple. The same purple in both themes, so its white text stays readable. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = appColors
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accentFill, contentColor = colors.onAccentFill),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Space.x2))
        }
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of the extra choices behind a split button's arrow. */
data class SplitChoice(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    /** Why it cannot be chosen now; shown struck through with this as the reason. */
    val refusal: String? = null,
)

/**
 * The main action on an album, video or playlist page: "Play", with Play next, Add to queue and
 * Download behind the arrow. One per page, in the call-to-action colour.
 *
 * @param refusal why the main action cannot be taken (a locked item); the button is disabled
 *   and says so to screen readers. The reason itself is shown beside the button by the page.
 */
@Composable
fun MainActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    choices: List<SplitChoice>,
    modifier: Modifier = Modifier,
    refusal: String? = null,
) {
    val colors = appColors
    var open by rememberSaveable { mutableStateOf(false) }
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = colors.callToAction,
        contentColor = colors.onCallToAction,
    )
    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onClick,
                    enabled = refusal == null,
                    colors = buttonColors,
                    modifier = Modifier.semantics {
                        if (refusal != null) stateDescription = refusal
                    },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                    )
                    Spacer(Modifier.width(Space.x2))
                    Text(text = label, maxLines = 1)
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = open,
                    onCheckedChange = { open = it },
                    colors = buttonColors,
                    modifier = Modifier.semantics {
                        contentDescription = "More ways to play"
                        stateDescription = if (open) "Open" else "Closed"
                    },
                ) {
                    Icon(
                        imageVector = if (open) AppIcons.ExpandLess else AppIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                    )
                }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { MenuLabel(label = choice.label, refusal = choice.refusal) },
                    onClick = {
                        open = false
                        choice.onClick()
                    },
                    enabled = choice.refusal == null,
                    leadingIcon = {
                        Icon(imageVector = choice.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                    },
                )
            }
        }
    }
}

/**
 * A menu row's text. An option that cannot be taken is still listed, struck through, with the
 * reason under it, so a locked item explains itself instead of hiding what it cannot do.
 */
@Composable
internal fun MenuLabel(label: String, refusal: String?) {
    if (refusal == null) {
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        androidx.compose.foundation.layout.Column {
            Text(
                text = label,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = refusal,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A switch between a few views of one page (All / Music / Video; grid / list): Material's
 * connected button group.
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { if (it) onSelect(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    checkedContainerColor = colors.accentFill,
                    checkedContentColor = colors.onAccentFill,
                ),
            ) {
                Text(text = option, maxLines = 1)
            }
        }
    }
}
