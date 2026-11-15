package app.ipfsmedia.feature.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.MediaRowSkeleton
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.SavedEntryRow
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.asPayload
import app.ipfsmedia.core.design.dragAutoScroll
import app.ipfsmedia.core.design.pageGutter
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.groupByDayOf
import app.ipfsmedia.core.library.resolveEntry
import app.ipfsmedia.core.library.toPlayable
import app.ipfsmedia.core.navigation.HomeRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * History: what was played on this device, newest first, by day.
 *
 * It is local. Plays are written to a table on the device for this identity and go nowhere
 * else; the page says so, because that is the first thing someone looking at a list of what
 * they listened to wants to know. A row can be removed (swipe left, Delete, or its menu) and
 * the whole list can be cleared; both can be undone.
 */
@Composable
fun HistoryScreen() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val history by viewModel.history.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val gutter = pageGutter
    val rowInset = gutter - Space.x2
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    val rows = history
    val days = remember(rows) {
        groupByDayOf(rows.orEmpty(), System.currentTimeMillis()) { it.playedAtMs }
    }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragAutoScroll(listState),
        contentPadding = PaddingValues(bottom = Space.x12),
    ) {
        item(key = "header", contentType = "header") {
            PageHeader(
                title = "History",
                subtitle = when {
                    rows == null -> "Reading your history…"
                    rows.isEmpty() -> "Kept on this device only. It is never sent anywhere."
                    else -> "${countOf(rows.size, "play")}, kept on this device only. It is never sent anywhere."
                },
                modifier = Modifier.padding(horizontal = gutter),
                actions = {
                    IconAction(
                        icon = AppIcons.Delete,
                        label = "Clear history",
                        enabled = !rows.isNullOrEmpty(),
                        onClick = { actions.clearHistory() },
                    )
                },
            )
        }

        when {
            rows == null -> items(count = SKELETON_ROWS, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                MediaRowSkeleton(modifier = Modifier.padding(horizontal = rowInset))
            }

            rows.isEmpty() -> item(key = "empty") {
                EmptyState(
                    icon = AppIcons.History,
                    title = "Nothing played yet",
                    body = "What you play is listed here so you can find it again. The list stays on this device.",
                    actionLabel = "Go to Home",
                    onAction = { navigator.open(HomeRoute) },
                )
            }

            else -> days.forEach { (label, dayRows) ->
                item(key = "day-$label", contentType = "day") {
                    GroupLabel(
                        text = label,
                        modifier = Modifier.padding(start = gutter, end = gutter, top = Space.x4, bottom = Space.x1),
                    )
                }
                items(items = dayRows, key = { it.id }, contentType = { "row" }) { row ->
                    val resolved = remember(row, library) { resolveEntry(row.entry.toPlayable(), library) }
                    val origin = Origin.History(row.id)
                    SavedEntryRow(
                        resolved = resolved,
                        origin = origin,
                        selected = selected == row.id,
                        onSelect = { selected = row.id },
                        // Playing from History plays that one thing again.
                        onPlay = { actions.perform(Destination.PlayNow, resolved.asPayload(), origin) },
                        // When it was played, in place of how long it is.
                        trailing = timeFormat.format(Instant.ofEpochMilli(row.playedAtMs).atZone(zone)),
                        modifier = Modifier
                            .padding(horizontal = rowInset)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 10
