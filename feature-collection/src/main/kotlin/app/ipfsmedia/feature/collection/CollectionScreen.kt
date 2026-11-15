package app.ipfsmedia.feature.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.ChoiceMenu
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.FilterChips
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.ItemRow
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.MediaRowSkeleton
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.SavedEntryRow
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.mediaCards
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.resolveEntry
import app.ipfsmedia.core.library.toPlayable
import app.ipfsmedia.core.navigation.DataDaosRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.mediaRoute

/**
 * Collection: everything this wallet can open, what it published, and what it favourited.
 *
 * A filter and sort bar over a grid that can be switched to a list. "Favourite songs" is the
 * one filter whose rows are songs rather than uploads; they play in the order shown.
 *
 * Favouriting is local. Carrying anything onto Collection in the sidebar favourites it.
 */
@Composable
fun CollectionScreen() {
    val viewModel: CollectionViewModel = hiltViewModel()
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val listView by viewModel.listView.collectAsStateWithLifecycle()
    val favouriteSongs by viewModel.favouriteSongs.collectAsStateWithLifecycle()
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var sortIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedSong by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    val filter = CollectionFilter.entries[filterIndex.coerceIn(0, CollectionFilter.entries.lastIndex)]
    val sort = CollectionSort.entries[sortIndex.coerceIn(0, CollectionSort.entries.lastIndex)]
    val collection = remember(library) { library.collection }
    val shown = remember(collection, filter, sort) { arrangeCollection(collection, filter, sort) }
    val songKeys = remember(favouriteSongs) {
        favouriteSongs.map { "song-" + it.entry.recordKey + "#" + it.entry.chapterIndex }
    }
    val songs = remember(favouriteSongs, library) { favouriteSongs.map { resolveEntry(it.entry.toPlayable(), library) } }
    val openItem: (LibraryItem) -> Unit = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) }
    val showingSongs = filter == CollectionFilter.SONGS

    CardGrid(state = gridState) {
        fullWidth(key = "header", contentType = "header") {
            PageHeader(
                title = "Collection",
                subtitle = when {
                    !library.ready -> "Reading your collection…"
                    showingSongs -> countOf(songs.size, "favourite song")
                    else -> "${countOf(shown.size, "upload")} you can open, published or favourited"
                },
                actions = {
                    if (!showingSongs) {
                        ChoiceMenu(
                            icon = AppIcons.Sort,
                            label = "Sort",
                            options = CollectionSort.entries.map { it.label },
                            selectedIndex = sortIndex,
                            onSelect = { sortIndex = it },
                        )
                        IconAction(
                            icon = if (listView) AppIcons.Grid else AppIcons.List,
                            label = if (listView) "Show as a grid" else "Show as a list",
                            onClick = { viewModel.setListView(!listView) },
                        )
                    }
                },
            )
        }
        fullWidth(key = "filters", contentType = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                FilterChips(
                    options = CollectionFilter.entries.map { it.label },
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                )
            }
        }

        when {
            !library.ready ->
                if (listView) {
                    items(count = SKELETON_ROWS, key = { "skeleton-$it" }, span = { GridItemSpan(maxLineSpan) }, contentType = { "rowSkeleton" }) {
                        MediaRowSkeleton()
                    }
                } else {
                    cardSkeletons()
                }

            showingSongs && songs.isEmpty() -> fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.FavouriteOutline,
                    title = "No favourite songs yet",
                    body = "Open an album and favourite a song from its actions. It will be kept here, " +
                        "on this device only.",
                )
            }

            showingSongs -> itemsIndexed(
                items = songs,
                // Keyed by what was saved, which stays unique when two rows re-match to one song.
                key = { index, _ -> songKeys.getOrElse(index) { "song-$index" } },
                span = { _, _ -> GridItemSpan(maxLineSpan) },
                contentType = { _, _ -> "songRow" },
            ) { index, resolved ->
                SavedEntryRow(
                    resolved = resolved,
                    isFavourite = true,
                    selected = selectedSong == resolved.entry.id,
                    onSelect = { selectedSong = resolved.entry.id },
                    onPlay = { actions.playFrom(songs.map { it.entry }, index, "your favourite songs") },
                    modifier = Modifier.animateItem(),
                )
            }

            collection.isEmpty() -> fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.Collection,
                    title = "Your collection is empty",
                    body = "Uploads from DataDAOs this wallet is a member of appear here, along with anything " +
                        "you favourite. Browse the DataDAOs to see what is there.",
                    actionLabel = "Browse DataDAOs",
                    onAction = { navigator.open(DataDaosRoute) },
                )
            }

            shown.isEmpty() -> fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.Sort,
                    title = "Nothing under ${filter.label.lowercase()}",
                    body = filter.emptyBody(),
                    actionLabel = "Show everything",
                    onAction = { filterIndex = 0 },
                )
            }

            listView -> items(
                items = shown,
                key = { "row-${it.id}" },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "itemRow" },
            ) { item ->
                ItemRow(item = item, onOpen = { openItem(item) }, modifier = Modifier.animateItem())
            }

            else -> mediaCards(items = shown, onOpen = openItem)
        }
    }
}

private fun CollectionFilter.emptyBody(): String = when (this) {
    CollectionFilter.ALL -> "There is nothing in your collection."
    CollectionFilter.MUSIC -> "There are no albums in your collection."
    CollectionFilter.VIDEO -> "There are no videos in your collection."
    CollectionFilter.FAVOURITES -> "Favourite an album or a video from its actions, or carry it onto Collection in the sidebar."
    CollectionFilter.SONGS -> "Favourite a song from its actions."
    CollectionFilter.ON_DEVICE -> "Nothing has been downloaded. Download an upload from its page to keep it for offline."
    CollectionFilter.YOURS -> "This wallet has not published anything that is live."
}

private const val SKELETON_ROWS = 10
