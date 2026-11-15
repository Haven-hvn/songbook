package app.ipfsmedia.feature.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
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
import app.ipfsmedia.core.design.HeroFact
import app.ipfsmedia.core.design.HeroHeader
import app.ipfsmedia.core.design.HeroSkeleton
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.InlineNotice
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.MainActionButton
import app.ipfsmedia.core.design.MediaRowSkeleton
import app.ipfsmedia.core.design.ReadingColumn
import app.ipfsmedia.core.design.RowDrop
import app.ipfsmedia.core.design.SavedEntryRow
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.SplitChoice
import app.ipfsmedia.core.design.asPayload
import app.ipfsmedia.core.design.dragAutoScroll
import app.ipfsmedia.core.design.dropTarget
import app.ipfsmedia.core.design.heroArtSize
import app.ipfsmedia.core.design.pageGutter
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.formatLength
import app.ipfsmedia.core.library.resolveEntry
import app.ipfsmedia.core.library.toPlayable
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.PlaylistsRoute

/**
 * One playlist: its entries in the user's order.
 *
 * Rows are moved by their grip, at once, with no long-press (or Alt+Up and Alt+Down). Anything
 * carried here from elsewhere is inserted where it is dropped. A swipe to the left, Delete, or
 * the row's menu takes an entry out; every one of these can be undone.
 *
 * An entry is a saved reference, and outlives what it points at. When the record has expired,
 * or the wallet has left its DataDAO, or the album was re-uploaded without that song, the row
 * stays where it was put and says so, instead of quietly vanishing from a list someone made.
 */
@Composable
fun PlaylistScreen(playlistId: Long) {
    val viewModel: PlaylistsViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val content by remember(playlistId) { viewModel.content(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val listState = rememberLazyListState()
    val gutter = pageGutter
    val rowInset = gutter - Space.x2
    var selected by rememberSaveable(playlistId) { mutableStateOf<Long?>(null) }

    val loaded = content
    val playlist = loaded?.playlist
    val rows = loaded?.entries.orEmpty()
    val resolved = remember(rows, library) { rows.map { resolveEntry(it.entry.toPlayable(), library) } }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragAutoScroll(listState),
        contentPadding = PaddingValues(bottom = Space.x12),
    ) {
        when {
            loaded == null -> {
                item(key = "loading", contentType = "hero") { HeroSkeleton() }
                items(count = SKELETON_ROWS, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                    MediaRowSkeleton(modifier = Modifier.padding(horizontal = rowInset))
                }
            }

            playlist == null -> item(key = "gone") {
                EmptyState(
                    icon = AppIcons.Playlist,
                    title = "This playlist is not here any more",
                    body = "It was deleted. If that was a moment ago, Undo (Ctrl+Z) brings it back.",
                    actionLabel = "All playlists",
                    onAction = { navigator.open(PlaylistsRoute) },
                )
            }

            else -> {
                val payload = DragPayload.Playlist(playlist.id, playlist.name, rows.size)
                item(key = "hero", contentType = "hero") {
                    val covers = remember(rows) {
                        rows.map { CoverSource(it.entry.recordKey, it.entry.albumTitle) }.distinctBy { it.recordKey }
                    }
                    val length = remember(resolved) { resolved.sumOf { it.entry.durationMs ?: 0L } }
                    val empty = "This playlist is empty".takeIf { rows.isEmpty() }
                    HeroHeader(
                        title = playlist.name,
                        seed = "playlist-${playlist.id}",
                        overline = "Playlist",
                        subtitle = "Kept on this device",
                        art = {
                            PlaylistArt(
                                covers = covers,
                                seed = "playlist-${playlist.id}",
                                title = playlist.name,
                                modifier = Modifier.heroArtSize(),
                            )
                        },
                        facts = {
                            HeroFact(text = if (rows.isEmpty()) "Empty" else countOf(rows.size, "entry", "entries"))
                            formatLength(length)?.let { HeroFact(text = it) }
                        },
                        actions = {
                            MainActionButton(
                                label = "Play",
                                icon = AppIcons.Play,
                                onClick = { actions.playFrom(resolved.map { it.entry }, 0, playlist.name) },
                                refusal = empty,
                                choices = listOf(
                                    SplitChoice("Play next", AppIcons.PlayNext, { actions.perform(Destination.PlayNext, payload) }, empty),
                                    SplitChoice("Add to queue", AppIcons.AddToQueue, { actions.perform(Destination.AddToQueue, payload) }, empty),
                                ),
                            )
                            IconAction(
                                icon = AppIcons.Edit,
                                label = "Rename",
                                onClick = { actions.perform(Destination.RenamePlaylist, payload) },
                                tint = LocalContentColor.current,
                            )
                            IconAction(
                                icon = AppIcons.Delete,
                                label = "Delete this playlist",
                                onClick = { actions.perform(Destination.DeletePlaylist, payload) },
                                tint = LocalContentColor.current,
                            )
                        },
                    )
                }

                val unplayable = resolved.count { !it.isPlayable }
                if (unplayable > 0) {
                    item(key = "unplayable", contentType = "notice") {
                        ReadingColumn(modifier = Modifier.padding(top = Space.x4)) {
                            InlineNotice(
                                text = if (unplayable == 1) {
                                    "One entry cannot be played right now. It stays in the list, marked, and is skipped."
                                } else {
                                    "$unplayable entries cannot be played right now. They stay in the list, marked, and are skipped."
                                },
                            )
                        }
                    }
                }

                if (rows.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = AppIcons.PlaylistAdd,
                            title = "Nothing in here yet",
                            body = "Carry songs, albums or videos onto this page, or choose this playlist from an item's actions.",
                            // The empty page still takes a drop.
                            modifier = Modifier.dropTarget(DropTarget.PlaylistPage(playlist.id, 0)),
                        )
                    }
                } else {
                    item(key = "top-gap", contentType = "gap") { Box(modifier = Modifier.height(Space.x4)) }
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.id },
                        contentType = { _, _ -> "row" },
                    ) { index, row ->
                        val entry = resolved.getOrNull(index) ?: return@itemsIndexed
                        val origin = Origin.Playlist(playlist.id, listOf(row.id))
                        SavedEntryRow(
                            resolved = entry,
                            origin = origin,
                            selected = selected == row.id,
                            onSelect = { selected = row.id },
                            onPlay = { actions.playFrom(resolved.map { it.entry }, index, playlist.name) },
                            grip = true,
                            drop = RowDrop(index) { DropTarget.PlaylistPage(playlist.id, it) },
                            onMoveRow = { delta ->
                                // Alt+Up and Alt+Down: the same move a drop makes, with the same Undo.
                                val before = if (delta < 0) index - 1 else index + 2
                                actions.perform(Destination.PlaylistAt(playlist.id, before), entry.asPayload(), origin)
                            },
                            modifier = Modifier
                                .padding(horizontal = rowInset)
                                .animateItem(),
                        )
                    }
                    item(key = "end", contentType = "end") {
                        // Room after the last row to drop "at the end".
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Sizes.rowHeight)
                                .dropTarget(DropTarget.PlaylistPage(playlist.id, rows.size)),
                        )
                    }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 8
