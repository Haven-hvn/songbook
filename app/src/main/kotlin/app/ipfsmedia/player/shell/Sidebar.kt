package app.ipfsmedia.player.shell

import app.ipfsmedia.core.library.shortAddress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.GroupLabel
import app.ipfsmedia.core.design.HexAvatar
import app.ipfsmedia.core.design.IconAction
import app.ipfsmedia.core.design.LocalLibraryState
import app.ipfsmedia.core.design.SidebarItem
import app.ipfsmedia.core.design.Space
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.dragAutoScroll
import app.ipfsmedia.core.design.dropTarget
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.ShortcutAction
import app.ipfsmedia.core.library.asPayload
import app.ipfsmedia.core.navigation.CollectionRoute
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.DataDaosRoute
import app.ipfsmedia.core.navigation.HistoryRoute
import app.ipfsmedia.core.navigation.HomeRoute
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.PlaylistRoute
import app.ipfsmedia.core.navigation.PlaylistsRoute
import app.ipfsmedia.core.navigation.Route
import app.ipfsmedia.core.navigation.SearchRoute
import app.ipfsmedia.core.navigation.SectionRoute
import app.ipfsmedia.core.navigation.SettingsRoute
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.feature.identity.IdentityButton
import app.ipfsmedia.player.actions.ShellViewModel

/**
 * The sidebar: back and forward, search, Home, *Your media*, *Your DataDAOs*, *Following*,
 * Settings, and the identity.
 *
 * It is also where most carried items are put down: a playlist takes songs, Collection
 * favourites, Following an uploader, Your DataDAOs a pin. With [rail] it is collapsed to
 * icons; the labels move into tooltips.
 */
@Composable
fun Sidebar(
    shell: ShellViewModel,
    rail: Boolean,
    queueOpen: Boolean,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigated: () -> Unit = {},
) {
    val colors = appColors
    val navigator = LocalNavigator.current
    val library = LocalLibraryState.current
    val playlists by shell.playlistSummaries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val current = navigator.current
    val section = navigator.section

    fun open(route: Route) {
        // Choosing the section that is already showing takes it back to its first page.
        if (route is SectionRoute && route == section && current != route) navigator.resetSection()
        navigator.open(route, inHomeSection = true)
        onNavigated()
    }

    val followed = remember(library.uploaders) { library.uploaders.filter { it.followed } }
    // Followed, but with nothing live: not in the catalogue, and still someone you follow. They
    // stay listed, or there would be no way to find them again and unfollow.
    val follows by shell.follows.collectAsStateWithLifecycle()
    val quiet = remember(follows, followed, library.ready) {
        val live = followed.map { it.address }.toSet()
        if (library.ready) follows.filter { it.address !in live } else emptyList()
    }
    val yourDaos = library.yourDaos

    Column(
        modifier = modifier
            .background(colors.groundSecondary)
            .padding(horizontal = if (rail) Space.x2 else Space.x3, vertical = Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (rail) Arrangement.Center else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(
                icon = AppIcons.Back,
                label = "Back",
                onClick = { navigator.back() },
                enabled = navigator.canGoBack,
            )
            if (!rail) {
                IconAction(
                    icon = AppIcons.Forward,
                    label = "Forward",
                    onClick = { navigator.forward() },
                    enabled = navigator.canGoForward,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .dragAutoScroll(listState),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
            contentPadding = PaddingValues(bottom = Space.x4),
        ) {
            item(key = "search", contentType = "entry") {
                SidebarItem(
                    label = "Search",
                    icon = AppIcons.Search,
                    selected = section == SearchRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SEARCH,
                    onClick = {
                        shell.focusSearch()
                        onNavigated()
                    },
                )
            }
            item(key = "home", contentType = "entry") {
                SidebarItem(
                    label = "Home",
                    icon = AppIcons.Home,
                    selected = section == HomeRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SECTION_1,
                    onClick = { open(HomeRoute) },
                )
            }

            if (!rail) item(key = "media-label", contentType = "label") { SidebarGroup("Your media") }
            item(key = "collection", contentType = "entry") {
                SidebarItem(
                    label = "Collection",
                    icon = AppIcons.Collection,
                    selected = section == CollectionRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SECTION_2,
                    // Dropping a song, album or video here favourites it.
                    dropTarget = DropTarget.SidebarCollection,
                    onClick = { open(CollectionRoute) },
                )
            }
            item(key = "playlists", contentType = "entry") {
                SidebarItem(
                    label = "Playlists",
                    icon = AppIcons.Playlist,
                    selected = section == PlaylistsRoute && current == PlaylistsRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SECTION_3,
                    dropTarget = DropTarget.SidebarNewPlaylist,
                    onClick = { open(PlaylistsRoute) },
                )
            }
            if (!rail) {
                itemsIndexed(playlists, key = { _, it -> "playlist-${it.playlist.id}" }, contentType = { _, _ -> "entry" }) { index, summary ->
                    val playlist = summary.playlist
                    SidebarItem(
                        label = playlist.name,
                        icon = AppIcons.Song,
                        selected = current == PlaylistRoute(playlist.id),
                        dropTarget = DropTarget.SidebarPlaylist(playlist.id, playlist.name),
                        payload = DragPayload.Playlist(playlist.id, playlist.name, summary.entryCount),
                        onMoveRow = { delta -> shell.movePlaylist(index, index + delta) },
                        onClick = { open(PlaylistRoute(playlist.id)) },
                    )
                }
                item(key = "new-playlist", contentType = "entry") {
                    SidebarItem(
                        label = "New playlist",
                        icon = AppIcons.Add,
                        selected = false,
                        shortcut = ShortcutAction.NEW_PLAYLIST,
                        // Dropping something here makes a playlist from it.
                        dropTarget = DropTarget.SidebarNewPlaylist,
                        onClick = { shell.newPlaylist() },
                    )
                }
            }
            item(key = "history", contentType = "entry") {
                SidebarItem(
                    label = "History",
                    icon = AppIcons.History,
                    selected = section == HistoryRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SECTION_4,
                    onClick = { open(HistoryRoute) },
                )
            }

            if (!rail) {
                item(key = "daos-label", contentType = "label") {
                    // The heading itself takes a carried DataDAO: that pins it.
                    SidebarGroup("Your DataDAOs", modifier = Modifier.dropTarget(DropTarget.SidebarDataDaos))
                }
                itemsIndexed(yourDaos, key = { _, it -> "dao-${it.key}" }, contentType = { _, _ -> "entry" }) { index, dao ->
                    SidebarItem(
                        label = dao.name,
                        icon = if (dao.pinned) AppIcons.Pin else AppIcons.DataDao,
                        selected = (current as? DataDaoRoute)?.daoKey == dao.key,
                        payload = dao.asPayload(),
                        onMoveRow = if (dao.pinned) ({ delta -> shell.movePin(index, index + delta) }) else null,
                        onClick = { open(DataDaoRoute(dao.key)) },
                    )
                }
            }
            item(key = "browse", contentType = "entry") {
                SidebarItem(
                    label = if (rail) "DataDAOs" else "Browse DataDAOs",
                    icon = AppIcons.DataDao,
                    selected = section == DataDaosRoute && current == DataDaosRoute,
                    rail = rail,
                    shortcut = ShortcutAction.SECTION_5,
                    dropTarget = DropTarget.SidebarDataDaos,
                    onClick = { open(DataDaosRoute) },
                )
            }

            if (!rail) {
                item(key = "following-label", contentType = "label") {
                    SidebarGroup("Following", modifier = Modifier.dropTarget(DropTarget.SidebarFollowing))
                }
                if (followed.isEmpty() && quiet.isEmpty()) {
                    item(key = "following-empty", contentType = "hint") {
                        Text(
                            text = "Uploaders you follow appear here. Following stays on this device.",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            modifier = Modifier
                                .padding(horizontal = Space.x3, vertical = Space.x1)
                                .dropTarget(DropTarget.SidebarFollowing),
                        )
                    }
                }
                itemsIndexed(followed, key = { _, it -> "uploader-${it.address}" }, contentType = { _, _ -> "entry" }) { _, uploader ->
                    SidebarItem(
                        label = uploader.displayName,
                        leading = { HexAvatar(address = uploader.address, name = uploader.displayName) },
                        supporting = uploader.latestTitle,
                        badgeCount = uploader.unseenCount,
                        selected = (current as? UploaderRoute)?.address == uploader.address,
                        payload = uploader.asPayload(),
                        onClick = { open(UploaderRoute(uploader.address)) },
                    )
                }
                itemsIndexed(quiet, key = { _, follow -> "quiet-${follow.address}" }, contentType = { _, _ -> "entry" }) { _, follow ->
                    val name = follow.label.ifBlank { shortAddress(follow.address) }
                    SidebarItem(
                        label = name,
                        leading = { HexAvatar(address = follow.address, name = name) },
                        supporting = "Nothing live right now",
                        selected = (current as? UploaderRoute)?.address == follow.address,
                        payload = DragPayload.Uploader(address = follow.address, name = name, followed = true),
                        onClick = { open(UploaderRoute(follow.address)) },
                    )
                }
            }
        }

        SidebarItem(
            label = "Queue",
            icon = AppIcons.Queue,
            selected = queueOpen,
            rail = rail,
            shortcut = ShortcutAction.TOGGLE_QUEUE,
            dropTarget = DropTarget.QueueButton,
            onClick = onToggleQueue,
        )
        SidebarItem(
            label = "Settings",
            icon = AppIcons.Settings,
            selected = section == SettingsRoute,
            rail = rail,
            shortcut = ShortcutAction.SECTION_7,
            onClick = { open(SettingsRoute) },
        )
        IdentityButton(rail = rail)
    }
}

@Composable
private fun SidebarGroup(title: String, modifier: Modifier = Modifier) {
    GroupLabel(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.x3, top = Space.x4, bottom = Space.x1),
    )
}

private data class BarEntry(val label: String, val icon: ImageVector, val route: SectionRoute)

private val BAR_ENTRIES = listOf(
    BarEntry("Home", AppIcons.Home, HomeRoute),
    BarEntry("Collection", AppIcons.Collection, CollectionRoute),
    BarEntry("DataDAOs", AppIcons.DataDao, DataDaosRoute),
    BarEntry("Search", AppIcons.Search, SearchRoute),
)

/**
 * Navigation under 600dp: the four sections reached most, and a button that brings the whole
 * sidebar over the content for everything else.
 */
@Composable
fun BottomBar(onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val section = navigator.section
    NavigationBar(modifier = modifier) {
        BAR_ENTRIES.forEach { entry ->
            NavigationBarItem(
                selected = section == entry.route,
                onClick = {
                    if (section == entry.route) navigator.resetSection() else navigator.open(entry.route)
                },
                icon = { Icon(imageVector = entry.icon, contentDescription = null) },
                label = { Text(entry.label, maxLines = 1) },
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onMenu,
            icon = { Icon(imageVector = AppIcons.Menu, contentDescription = null) },
            label = { Text("More", maxLines = 1) },
        )
    }
}
