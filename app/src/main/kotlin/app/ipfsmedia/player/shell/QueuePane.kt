package app.ipfsmedia.player.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AlwaysDark
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.MediaRow
import app.ipfsmedia.core.design.RowDrop
import app.ipfsmedia.core.design.RowLeading
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.dragAutoScroll
import app.ipfsmedia.core.design.dropTarget
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.player.QueueRow
import app.ipfsmedia.player.actions.ShellViewModel

/**
 * The queue: dark in both themes, a pane of the shell. What is playing, then what is up next
 * in the order it will really play (the shuffled order when shuffle is on).
 *
 * Rows are moved by their grip, without a long-press, and anything carried here from elsewhere
 * is inserted where it is dropped. Twelve songs of one album are twelve rows but one file: a
 * fetch in progress is shown on every row of that album, as one download.
 */
@Composable
fun QueuePane(shell: ShellViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by shell.playerState.collectAsStateWithLifecycle()
    val actions = LocalItemActions.current
    val library = LocalLibraryState.current
    val listState = rememberLazyListState()
    val player = shell.player

    // Shown in play order. Each row keeps its index in the player's list, which is what a move
    // or an insert refers to.
    val shown: List<Pair<Int, QueueRow>> = remember(state.queue, state.playOrder, state.currentIndex) {
        if (state.shuffle) {
            state.playOrder.mapNotNull { index -> state.queue.getOrNull(index)?.let { index to it } }
        } else {
            state.queue.mapIndexed { index, row -> index to row }
        }
    }

    AlwaysDark {
        val colors = appColors
        Surface(color = colors.ground, contentColor = colors.textPrimary, modifier = modifier) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Space.x4, end = Space.x1, top = Space.x2, bottom = Space.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = if (state.isEmpty) "Empty" else countOf(state.queue.size, "item"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                    IconAction(
                        icon = AppIcons.Delete,
                        label = "Clear the queue",
                        enabled = !state.isEmpty,
                        onClick = {
                            val before = player.clear()
                            shell.queueCleared(before)
                        },
                    )
                    IconAction(
                        icon = AppIcons.Close,
                        label = "Hide the queue",
                        onClick = onClose,
                        shortcut = ShortcutAction.TOGGLE_QUEUE,
                    )
                }

                if (state.isEmpty) {
                    EmptyState(
                        icon = AppIcons.Queue,
                        title = "Nothing queued",
                        body = "Play something, or carry it here. Swiping a row to the right adds it too.",
                        // The empty queue still takes a drop.
                        modifier = Modifier
                            .weight(1f)
                            .dropTarget(DropTarget.QueueDrawer(0)),
                    )
                } else {
                    if (state.shuffle) {
                        GroupLabel(
                            text = "Shuffle is on. Turn it off to reorder.",
                            modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x1),
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .dragAutoScroll(listState),
                        contentPadding = PaddingValues(horizontal = Space.x2, vertical = Space.x1),
                        verticalArrangement = Arrangement.spacedBy(Space.x1),
                    ) {
                        itemsIndexed(
                            items = shown,
                            key = { _, (_, row) -> row.uid },
                            contentType = { _, _ -> "row" },
                        ) { position, (index, row) ->
                            val entry = row.entry
                            val reason = library.item(entry.recordKey)?.let { item ->
                                if (item.access.isLocked) "Locked" else null
                            }
                            // In list order the playing row can be anywhere; in shuffle order it is first.
                            val upNextStartsAt = if (state.shuffle) 1 else state.currentIndex + 1
                            if (position == upNextStartsAt) {
                                GroupLabel(text = "Up next", modifier = Modifier.padding(start = Space.x2, top = Space.x2, bottom = Space.x1))
                            }
                            MediaRow(
                                title = entry.title,
                                subtitle = "${entry.uploaderName} · ${entry.albumTitle}",
                                payload = { DragPayload.Media(entries = listOf(entry)) },
                                origin = Origin.Queue(index),
                                leading = RowLeading.Art(entry.recordKey, entry.albumTitle),
                                recordKey = entry.recordKey,
                                chapterIndex = entry.chapterIndex,
                                trailing = formatClock(entry.durationMs),
                                unavailable = reason,
                                // The grip moves a row at once. In shuffle order a move would not
                                // mean what it looks like, so there is none.
                                grip = !state.shuffle,
                                drop = if (state.shuffle) null else RowDrop(index) { DropTarget.QueueDrawer(it) },
                                onMoveRow = if (state.shuffle) {
                                    null
                                } else {
                                    { delta ->
                                        // Alt+Up and Alt+Down: the same move a drop makes.
                                        val before = if (delta < 0) index - 1 else index + 2
                                        actions.perform(
                                            Destination.QueueAt(before),
                                            DragPayload.Media(entries = listOf(entry)),
                                            Origin.Queue(index),
                                        )
                                    }
                                },
                                swipeToQueue = false,
                                onActivate = { player.skipTo(row.uid) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(key = "end", contentType = "end") {
                            // Room after the last row to drop "at the end".
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(Sizes.rowHeight)
                                    .dropTarget(DropTarget.QueueDrawer(state.queue.size)),
                            )
                        }
                    }
                }
            }
        }
    }
}
