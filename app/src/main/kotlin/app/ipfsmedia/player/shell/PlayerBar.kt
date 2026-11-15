package app.ipfsmedia.player.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AlwaysDark
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Artwork
import app.ipfsmedia.core.design.CountBadge
import app.ipfsmedia.core.design.DaoBadge
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalDragController
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalWidthClass
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.SeekBar
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.StageIndicator
import app.ipfsmedia.core.design.StageInfo
import app.ipfsmedia.core.design.VolumeControl
import app.ipfsmedia.core.design.WidthClass
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.dropTarget
import app.ipfsmedia.core.design.PlayPauseButton
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.describeError
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.NowPlayingRoute
import app.ipfsmedia.core.player.Blocked
import app.ipfsmedia.core.player.PlayerUiState
import app.ipfsmedia.core.player.RepeatMode
import app.ipfsmedia.core.player.StagingException
import app.ipfsmedia.player.actions.ShellViewModel

/**
 * The player bar: dark in both themes, fixed at the bottom. What is playing with its DataDAO,
 * the transport, the position, the queue button with its count, shuffle, repeat and volume.
 *
 * Carrying something onto the bar plays it; onto the queue button, adds it to the queue, and
 * resting there opens the queue so it can be placed.
 *
 * When the queue has stopped and needs the user (a signature, or a row that cannot be played
 * with nothing to move on to), the bar says so in a strip above itself.
 */
@Composable
fun PlayerBar(
    shell: ShellViewModel,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by shell.playerState.collectAsStateWithLifecycle()
    val position by shell.player.positionMs.collectAsStateWithLifecycle()
    val width = LocalWidthClass.current
    val player = shell.player
    val drag = LocalDragController.current
    val barTargetId = remember { Any() }
    val barVisual = drag.visualFor(barTargetId, DropTarget.PlayerBar)
    var mutedFrom by rememberSaveable { mutableFloatStateOf(1f) }

    AlwaysDark {
        val colors = appColors
        Column(modifier = modifier.fillMaxWidth()) {
            state.blocked?.let { blocked -> BlockedStrip(blocked = blocked, shell = shell) }
            Surface(
                color = if (barVisual.accepts && barVisual.hovered) colors.accentFill else colors.ground,
                contentColor = colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (width.isCompact) Sizes.playerBarHeightCompact else Sizes.playerBarHeight)
                    .dropTarget(DropTarget.PlayerBar, barTargetId),
            ) {
                Column {
                    HorizontalDivider(color = colors.border)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = if (width.isCompact) Space.x2 else Space.x4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x2),
                    ) {
                        NowPlayingSummary(
                            state = state,
                            modifier = Modifier.weight(if (width.isCompact) 1f else SIDE_WEIGHT),
                        )

                        if (width.isCompact) {
                            PlayPause(state = state, onClick = player::togglePlay)
                            IconAction(
                                icon = AppIcons.SkipNext,
                                label = "Next",
                                onClick = player::next,
                                enabled = state.hasNext,
                                shortcut = ShortcutAction.NEXT,
                            )
                            QueueButton(count = state.queue.size, open = queueOpen, onToggle = onToggleQueue, onRest = onOpenQueue)
                        } else {
                            Column(
                                modifier = Modifier.weight(CENTRE_WEIGHT),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconAction(
                                        icon = AppIcons.Shuffle,
                                        label = if (state.shuffle) "Shuffle is on" else "Shuffle",
                                        onClick = player::toggleShuffle,
                                        selected = state.shuffle,
                                        shortcut = ShortcutAction.SHUFFLE,
                                    )
                                    IconAction(
                                        icon = AppIcons.SkipPrevious,
                                        label = "Previous",
                                        onClick = player::previous,
                                        enabled = !state.isEmpty,
                                        shortcut = ShortcutAction.PREVIOUS,
                                    )
                                    PlayPause(state = state, onClick = player::togglePlay)
                                    IconAction(
                                        icon = AppIcons.SkipNext,
                                        label = "Next",
                                        onClick = player::next,
                                        enabled = state.hasNext,
                                        shortcut = ShortcutAction.NEXT,
                                    )
                                    IconAction(
                                        icon = if (state.repeat == RepeatMode.ONE) AppIcons.RepeatOne else AppIcons.Repeat,
                                        label = when (state.repeat) {
                                            RepeatMode.OFF -> "Repeat"
                                            RepeatMode.ALL -> "Repeating the queue"
                                            RepeatMode.ONE -> "Repeating this one"
                                        },
                                        onClick = player::cycleRepeat,
                                        selected = state.repeat != RepeatMode.OFF,
                                        shortcut = ShortcutAction.REPEAT,
                                    )
                                }
                                SeekBar(
                                    positionMs = { position },
                                    durationMs = state.durationMs,
                                    onSeek = player::seekTo,
                                    enabled = !state.isEmpty,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Row(
                                modifier = Modifier.weight(SIDE_WEIGHT),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (state.isVideo) {
                                    IconAction(
                                        icon = AppIcons.Fullscreen,
                                        label = "Full screen",
                                        onClick = onFullscreen,
                                        shortcut = ShortcutAction.FULLSCREEN,
                                    )
                                }
                                QueueButton(count = state.queue.size, open = queueOpen, onToggle = onToggleQueue, onRest = onOpenQueue)
                                if (width != WidthClass.Medium) {
                                    VolumeControl(
                                        volume = state.volume,
                                        onVolume = player::setVolume,
                                        onToggleMute = {
                                            toggleMute(shell, state.volume, mutedFrom) { mutedFrom = it }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (width.isCompact) {
                        // No room for the seek bar: a thin line shows how far along it is.
                        ProgressLine(positionMs = position, durationMs = state.durationMs)
                    }
                }
            }
        }
    }
}

/** Art, title, uploader and DataDAO of what is playing; opens the full view. */
@Composable
private fun NowPlayingSummary(state: PlayerUiState, modifier: Modifier = Modifier) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val library = LocalLibraryState.current
    val entry = state.current?.entry
    if (entry == null) {
        Text(
            text = "Nothing playing",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
            modifier = modifier.padding(horizontal = Space.x2),
        )
        return
    }
    val daoName = library.dao(entry.daoKey)?.name
    val waiting = state.waitingOn
    Row(
        modifier = modifier
            .fillMaxHeight()
            .clip(Radius.md)
            .clickable(role = Role.Button, onClickLabel = "Show what is playing") { navigator.open(NowPlayingRoute) }
            .padding(Space.x1)
            .semantics(mergeDescendants = true) {
                contentDescription = "Playing ${entry.title} by ${entry.uploaderName}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Artwork(
            recordKey = entry.recordKey,
            title = entry.albumTitle,
            modifier = Modifier.size(if (LocalWidthClass.current.isCompact) Sizes.rowArt else Sizes.playerArt),
            shape = Radius.sm,
            showInitials = false,
        )
        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (waiting != null) {
                // What is really happening while nothing is heard: fetching, or unlocking.
                StageIndicator(stage = StageInfo(waiting.step.label, waiting.fraction), modifier = Modifier.widthIn(max = Sizes.traySlotWidth * 2))
            } else {
                Text(
                    text = entry.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (daoName != null && !LocalWidthClass.current.isCompact) {
            val daoKey = entry.daoKey
            DaoBadge(
                name = daoName,
                onClick = if (daoKey != null) ({ navigator.open(DataDaoRoute(daoKey)) }) else null,
            )
        }
    }
}

/** Play or pause. Off when there is nothing to play. */
@Composable
private fun PlayPause(state: PlayerUiState, onClick: () -> Unit) {
    PlayPauseButton(showsPause = state.showsPause, onClick = onClick, enabled = !state.isEmpty)
}

/** The queue button, with how many rows the queue holds. Also where a carried item is queued. */
@Composable
private fun QueueButton(count: Int, open: Boolean, onToggle: () -> Unit, onRest: () -> Unit) {
    Box(
        modifier = Modifier.dropTarget(
            target = DropTarget.QueueButton,
            // Holding an item over the button opens the queue, so it can be placed in it.
            onRest = onRest,
        ),
    ) {
        IconAction(
            icon = AppIcons.Queue,
            label = if (open) "Hide the queue" else "Show the queue",
            onClick = onToggle,
            selected = open,
            shortcut = ShortcutAction.TOGGLE_QUEUE,
        )
        if (count > 0) CountBadge(count = count, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun ProgressLine(positionMs: Long, durationMs: Long?) {
    val colors = appColors
    val fraction = if (durationMs != null && durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Sizes.dropLine)
            .background(colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(colors.accent),
        )
    }
}

/**
 * Why the queue has stopped, and the one thing that restarts it.
 *
 * For a signature this is the explanation that comes *before* any wallet prompt: what will be
 * asked, why, and that it costs nothing. Only "Sign and play" sends anything to the wallet.
 */
@Composable
private fun BlockedStrip(blocked: Blocked, shell: ShellViewModel) {
    val colors = appColors
    val library = LocalLibraryState.current
    val (what, todo, action) = when (blocked) {
        is Blocked.NeedsSignature -> {
            val dao = library.dao(blocked.daoKey)?.name
            val message = describeError(StagingException.SIGNATURE_REQUIRED)
            Triple(
                if (dao != null) "“${blocked.title}” is for members of $dao." else "“${blocked.title}” is for members.",
                message.todo.orEmpty().let { "Your wallet will ask for one signature. $it" },
                "Sign and play",
            )
        }
        is Blocked.Failed -> Triple("“${blocked.title}”: ${blocked.message.what}", blocked.message.todo.orEmpty(), "Try again")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .padding(horizontal = Space.x4, vertical = Space.x2)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(
            imageVector = if (blocked is Blocked.NeedsSignature) AppIcons.Sign else AppIcons.Error,
            contentDescription = null,
            tint = if (blocked is Blocked.NeedsSignature) colors.accent else colors.error,
            modifier = Modifier.size(Sizes.iconLarge),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = what, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            if (todo.isNotBlank()) {
                Text(text = todo, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        if (shell.playerState.value.hasNext) {
            TextButton(onClick = { shell.player.next() }) { Text("Skip") }
        }
        AccentButton(
            text = action,
            onClick = {
                when (blocked) {
                    is Blocked.NeedsSignature -> shell.player.approveSignature()
                    is Blocked.Failed -> shell.player.retry()
                }
            },
        )
    }
}

private const val SIDE_WEIGHT = 3f
private const val CENTRE_WEIGHT = 4f
