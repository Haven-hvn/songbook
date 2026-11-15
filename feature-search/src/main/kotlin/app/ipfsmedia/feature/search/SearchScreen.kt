package app.ipfsmedia.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.DaoCard
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.FilterChips
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.LocalSearchFocusRequests
import app.ipfsmedia.core.design.MediaRow
import app.ipfsmedia.core.design.MediaRowSkeleton
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.RowLeading
import app.ipfsmedia.core.design.SectionHeader
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.UploaderCard
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.mediaCards
import app.ipfsmedia.core.design.plays
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.ItemKind
import app.ipfsmedia.core.library.LibraryItem
import app.ipfsmedia.core.library.SearchResults
import app.ipfsmedia.core.library.SongHit
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.library.formatClock
import app.ipfsmedia.core.library.search
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.mediaRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The groups of a search, in the order the page shows them. */
private enum class Group(val label: String) {
    ALBUMS("Albums"),
    SONGS("Songs"),
    VIDEOS("Videos"),
    UPLOADERS("Uploaders"),
    DATADAOS("DataDAOs"),
    ;

    fun count(results: SearchResults): Int = when (this) {
        ALBUMS -> results.albums.size
        SONGS -> results.songs.size
        VIDEOS -> results.videos.size
        UPLOADERS -> results.uploaders.size
        DATADAOS -> results.daos.size
    }
}

/** Results with the query that produced them, so stale results can be told from current ones. */
private data class Searched(val query: String, val results: SearchResults)

/**
 * Search, over what the last refresh stored: albums, songs (of albums that have been fetched),
 * videos, uploaders and DataDAOs.
 *
 * Nothing typed here leaves the device. The index is not asked; the catalogue on the device is
 * searched, which is also why a song only turns up once its album has been fetched.
 *
 * Ctrl+F puts the cursor in the field from anywhere. The query is saved state, so resizing the
 * window keeps it.
 */
@Composable
fun SearchScreen() {
    val library = LocalLibraryState.current
    val navigator = LocalNavigator.current
    val focusRequests = LocalSearchFocusRequests.current
    val gridState = rememberLazyGridState()
    val fieldFocus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    // Which group is showing in full; null shows a few of each.
    var onlyGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSong by rememberSaveable { mutableStateOf<String?>(null) }
    var firstShow by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(focusRequests) {
        // On Ctrl+F, always. On first arriving, only when there is nothing typed yet, so coming
        // back to a page of results does not raise the keyboard over them.
        val wanted = focusRequests != firstShow || query.isEmpty()
        firstShow = focusRequests
        if (wanted) {
            // The field is the first thing on the page; it has to be on screen to take focus.
            gridState.scrollToItem(0)
            withFrameNanos { }
            runCatching { fieldFocus.requestFocus() }
        }
    }

    val trimmed = query.trim()
    val searched by produceState(initialValue = Searched("", SearchResults()), trimmed, library) {
        if (trimmed.isEmpty()) {
            value = Searched("", SearchResults())
            return@produceState
        }
        // A beat, so a word typed quickly is searched once, not once per letter.
        delay(SEARCH_DELAY_MS)
        val results = withContext(Dispatchers.Default) { search(trimmed, library) }
        value = Searched(trimmed, results)
    }
    val results = searched.results
    val waiting = trimmed.isNotEmpty() && searched.query != trimmed
    val groups = remember(results) { Group.entries.filter { it.count(results) > 0 } }
    val only = groups.firstOrNull { it.name == onlyGroup }
    val openItem: (LibraryItem) -> Unit = { navigator.open(mediaRoute(it.id, it.kind == ItemKind.VIDEO)) }

    CardGrid(state = gridState) {
        fullWidth(key = "header", contentType = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                PageHeader(
                    title = "Search",
                    subtitle = "Searches what is on this device. Nothing you type is sent anywhere.",
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    shape = Radius.pill,
                    placeholder = { Text("Albums, songs, videos, uploaders, DataDAOs") },
                    leadingIcon = {
                        Icon(imageVector = AppIcons.Search, contentDescription = null, modifier = Modifier.size(Sizes.icon))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconAction(
                                icon = AppIcons.Close,
                                label = "Clear the search",
                                onClick = {
                                    query = ""
                                    runCatching { fieldFocus.requestFocus() }
                                },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = Sizes.readingWidth)
                        .focusRequester(fieldFocus)
                        .semantics { contentDescription = "Search" },
                )
            }
        }

        when {
            trimmed.isEmpty() -> fullWidth(key = "hint") {
                EmptyState(
                    icon = AppIcons.Search,
                    title = "Find something to play",
                    body = if (library.ready && library.items.isEmpty()) {
                        "There is nothing on this device to search yet. Refresh from Home first."
                    } else {
                        "Type a title, an uploader's name or address, or a DataDAO. A song is found once its album has been fetched."
                    },
                )
            }

            waiting && results.isEmpty -> items(
                count = SKELETON_ROWS,
                key = { "skeleton-$it" },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "rowSkeleton" },
            ) { MediaRowSkeleton() }

            results.isEmpty -> fullWidth(key = "none") {
                EmptyState(
                    icon = AppIcons.Search,
                    title = "Nothing found for “$trimmed”",
                    body = "Every word has to appear in a title, a name or an address. Try fewer words, or refresh from Home " +
                        "if it was uploaded recently.",
                )
            }

            else -> {
                if (groups.size > 1) {
                    fullWidth(key = "groups", contentType = "filters") {
                        FilterChips(
                            options = listOf("All ${results.total}") + groups.map { "${it.label} ${it.count(results)}" },
                            selectedIndex = if (only == null) 0 else groups.indexOf(only) + 1,
                            onSelect = { index -> onlyGroup = groups.getOrNull(index - 1)?.name },
                        )
                    }
                }
                val showing = if (only != null) listOf(only) else groups
                val full = only != null || groups.size == 1
                showing.forEach { group ->
                    val total = group.count(results)
                    val limit = if (full) Int.MAX_VALUE else group.preview
                    fullWidth(key = "title-${group.name}", contentType = "title") {
                        SectionHeader(
                            title = group.label,
                            count = total,
                            actionLabel = if (total > limit) "Show all" else null,
                            onAction = { onlyGroup = group.name },
                            modifier = Modifier.padding(top = Space.x2),
                        )
                    }
                    when (group) {
                        Group.ALBUMS -> mediaCards(items = results.albums.take(limit), onOpen = openItem, keyPrefix = "album-")
                        Group.VIDEOS -> mediaCards(items = results.videos.take(limit), onOpen = openItem, keyPrefix = "video-")
                        Group.SONGS -> songRows(
                            hits = results.songs.take(limit),
                            selected = selectedSong,
                            onSelect = { selectedSong = it },
                        )
                        Group.UPLOADERS -> items(
                            items = results.uploaders.take(limit),
                            key = { "uploader-${it.address}" },
                            contentType = { "uploader" },
                        ) { uploader ->
                            UploaderCard(
                                uploader = uploader,
                                onOpen = { navigator.open(UploaderRoute(uploader.address)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        Group.DATADAOS -> items(
                            items = results.daos.take(limit),
                            key = { "dao-${it.key}" },
                            contentType = { "dao" },
                        ) { dao ->
                            DaoCard(
                                dao = dao,
                                onOpen = { navigator.open(DataDaoRoute(dao.key)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Songs found, as rows. A tap plays the song; its menu has "Go to album". */
private fun LazyGridScope.songRows(hits: List<SongHit>, selected: String?, onSelect: (String) -> Unit) {
    items(
        items = hits,
        key = { "song-${it.album.id}#${it.song.index}" },
        span = { GridItemSpan(maxLineSpan) },
        contentType = { "songRow" },
    ) { hit ->
        val actions = LocalItemActions.current
        val payload = remember(hit) { hit.album.asPayload(hit.song) }
        val id = "${hit.album.id}#${hit.song.index}"
        MediaRow(
            title = hit.song.title,
            subtitle = "${hit.album.uploaderName} · ${hit.album.title}",
            payload = { payload },
            onActivate = { by ->
                if (by.plays) actions.perform(Destination.PlayNow, payload) else onSelect(id)
            },
            leading = RowLeading.Art(hit.album.id, hit.album.title),
            recordKey = hit.album.id,
            chapterIndex = hit.song.index,
            trailing = formatClock(hit.song.durationMs),
            badge = hit.album.daoName,
            unavailable = if (hit.album.access.isLocked) "Locked" else null,
            selected = selected == id,
            modifier = Modifier.animateItem(),
        )
    }
}

/** How many of a group the "All" view shows before "Show all". */
private val Group.preview: Int
    get() = when (this) {
        Group.ALBUMS, Group.VIDEOS, Group.UPLOADERS, Group.DATADAOS -> PREVIEW_CARDS
        Group.SONGS -> PREVIEW_ROWS
    }

private const val SEARCH_DELAY_MS = 140L
private const val SKELETON_ROWS = 6
private const val PREVIEW_CARDS = 6
private const val PREVIEW_ROWS = 5
