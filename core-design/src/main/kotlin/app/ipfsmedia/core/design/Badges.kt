package app.ipfsmedia.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.OfflineState

/** The one wording for each access state, everywhere it is shown. */
fun AccessState.label(): String = when (this) {
    AccessState.OPEN -> "Public"
    AccessState.MEMBER -> "Member"
    AccessState.NOT_MEMBER -> "Not a member"
    AccessState.UNKNOWN -> "Can't check right now"
    AccessState.NOT_OPEN_YET -> "Not open yet"
}

fun AccessState.icon(): ImageVector = when (this) {
    AccessState.OPEN -> AppIcons.Public
    AccessState.MEMBER -> AppIcons.LockOpen
    AccessState.NOT_MEMBER -> AppIcons.Lock
    AccessState.UNKNOWN -> AppIcons.Unknown
    AccessState.NOT_OPEN_YET -> AppIcons.NotOpenYet
}

fun OfflineState.label(): String = when (this) {
    OfflineState.ON_DEVICE -> "On this device"
    OfflineState.PARTIAL -> "Partly downloaded"
    OfflineState.NOT_DOWNLOADED -> "Not downloaded"
    OfflineState.EXPIRED -> "Expired"
}

fun OfflineState.icon(): ImageVector = when (this) {
    OfflineState.ON_DEVICE -> AppIcons.OnDevice
    OfflineState.PARTIAL -> AppIcons.Sync
    OfflineState.NOT_DOWNLOADED -> AppIcons.Download
    OfflineState.EXPIRED -> AppIcons.Offline
}

/** Foreground and ground for a state. The meaning of each colour is fixed (see [AppColors]). */
@Composable
private fun AccessState.colors(): Pair<Color, Color> {
    val colors = appColors
    return when (this) {
        AccessState.OPEN -> colors.textSecondary to colors.inset
        AccessState.MEMBER -> colors.success to colors.successSurface
        AccessState.NOT_MEMBER -> colors.error to colors.errorSurface
        AccessState.UNKNOWN -> colors.warning to colors.warningSurface
        AccessState.NOT_OPEN_YET -> colors.accent to colors.accentSurface
    }
}

@Composable
private fun OfflineState.colors(): Pair<Color, Color> {
    val colors = appColors
    return when (this) {
        OfflineState.ON_DEVICE -> colors.success to colors.successSurface
        OfflineState.PARTIAL -> colors.warning to colors.warningSurface
        OfflineState.NOT_DOWNLOADED -> colors.textTertiary to colors.inset
        OfflineState.EXPIRED -> colors.error to colors.errorSurface
    }
}

/** A small labelled pill: an icon and a few words, in a state's colours. */
@Composable
fun StatusPill(
    text: String,
    icon: ImageVector?,
    foreground: Color,
    ground: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(Sizes.badgeHeight)
            .clip(Radius.full)
            .background(ground)
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = foreground, modifier = Modifier.size(Sizes.iconSmall))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The access state of an item. As a pill with its words where there is room; as the icon alone
 * (with the words for screen readers) where there is not.
 */
@Composable
fun AccessBadge(access: AccessState, modifier: Modifier = Modifier, iconOnly: Boolean = false) {
    val (foreground, ground) = access.colors()
    if (iconOnly) {
        StateDot(icon = access.icon(), label = access.label(), foreground = foreground, ground = ground, modifier = modifier)
    } else {
        StatusPill(text = access.label(), icon = access.icon(), foreground = foreground, ground = ground, modifier = modifier)
    }
}

/** Whether an item's content is on the device. Parachord's source badge, repurposed. */
@Composable
fun OfflineBadge(offline: OfflineState, modifier: Modifier = Modifier, iconOnly: Boolean = false) {
    val (foreground, ground) = offline.colors()
    if (iconOnly) {
        StateDot(icon = offline.icon(), label = offline.label(), foreground = foreground, ground = ground, modifier = modifier)
    } else {
        StatusPill(text = offline.label(), icon = offline.icon(), foreground = foreground, ground = ground, modifier = modifier)
    }
}

@Composable
private fun StateDot(icon: ImageVector, label: String, foreground: Color, ground: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.badgeHeight)
            .clip(Radius.full)
            .background(ground)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = foreground, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** Names the DataDAO an item belongs to. Every item carries one (docs/PLAN.md §5). */
@Composable
fun DaoBadge(name: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = appColors
    Row(
        modifier = modifier
            .height(Sizes.badgeHeight)
            .clip(Radius.full)
            .background(colors.accentSurface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Open $name", onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Icon(
            imageVector = AppIcons.DataDao,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(Sizes.iconSmall),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A count in a filled circle: the queue button's number, a stack of held items, unseen uploads. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = appColors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = Sizes.badgeHeight, minHeight = Sizes.badgeHeight)
            .clip(Radius.full)
            .background(colors.accentFill)
            .padding(horizontal = Space.x1),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 999) "999+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onAccentFill,
            maxLines = 1,
        )
    }
}

/** A small "new" marker for uploads that arrived with the last refresh. */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    val colors = appColors
    StatusPill(text = "New", icon = null, foreground = colors.onAccentFill, ground = colors.accentFill, modifier = modifier)
}

/**
 * What the fetch-and-unlock pipeline is doing, on the item it is doing it for.
 *
 * While fetching there is no honest number (`foc-cache` reports nothing until the whole file has
 * arrived), so the bar moves without claiming a fraction. Once bytes are being unlocked the
 * fraction is real and the bar shows it.
 */
@Composable
fun StageIndicator(stage: StageInfo, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val colors = appColors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = stage.label },
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (showLabel) {
            Text(
                text = stage.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val fraction = stage.fraction
        if (fraction != null) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                color = colors.accent,
                trackColor = colors.accentSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearWavyProgressIndicator(
                color = colors.accent,
                trackColor = colors.accentSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The expressive loading indicator, for "working on it" with no bar to show. */
@Composable
fun BusyIndicator(modifier: Modifier = Modifier, label: String = "Loading") {
    LoadingIndicator(
        color = appColors.accent,
        modifier = modifier.semantics { contentDescription = label },
    )
}
