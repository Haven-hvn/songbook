package app.ipfsmedia.feature.playlists

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ipfsmedia.core.design.AccentButton
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.CardGrid
import app.ipfsmedia.core.design.EmptyState
import app.ipfsmedia.core.design.EntityCard
import app.ipfsmedia.core.design.LocalItemActions
import app.ipfsmedia.core.design.PageHeader
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.Sizes
import app.ipfsmedia.core.design.appColors
import app.ipfsmedia.core.design.cardSkeletons
import app.ipfsmedia.core.design.dropTarget
import app.ipfsmedia.core.design.fullWidth
import app.ipfsmedia.core.design.rememberDropVisual
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.DropTarget
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.navigation.LocalNavigator
import app.ipfsmedia.core.navigation.PlaylistRoute
import app.ipfsmedia.core.userdata.PlaylistSummary

/**
 * Every playlist, as cards. A playlist is a local list of references: to songs, whole albums
 * and videos. It lives on this device, per identity, and is published nowhere.
 *
 * Each card is also somewhere to put things: carrying a song, an album, a video or another
 * playlist onto it adds to it, exactly as the playlist's row in the sidebar does.
 */
@Composable
fun PlaylistsScreen() {
    val viewModel: PlaylistsViewModel = hiltViewModel()
    val navigator = LocalNavigator.current
    val actions = LocalItemActions.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    // "New playlist" with nothing to put in it: the shell asks for a name and opens the result.
    val newPlaylist = { actions.perform(Destination.NewPlaylist, DragPayload.Media(entries = emptyList())) }
    val all = playlists

    CardGrid(state = gridState) {
        fullWidth(key = "header", contentType = "header") {
            PageHeader(
                title = "Playlists",
                subtitle = when {
                    all == null -> "Reading your playlists…"
                    all.isEmpty() -> "Lists of songs, albums and videos, kept on this device"
                    else -> "${countOf(all.size, "playlist")}, kept on this device"
                },
                actions = {
                    AccentButton(text = "New playlist", icon = AppIcons.PlaylistAdd, onClick = newPlaylist)
                },
            )
        }
        when {
            all == null -> cardSkeletons(count = SKELETON_CARDS)

            all.isEmpty() -> fullWidth(key = "empty") {
                EmptyState(
                    icon = AppIcons.Playlist,
                    title = "No playlists yet",
                    body = "Make one, then carry songs, albums and videos onto it, or choose it from an item's actions. " +
                        "A playlist can hold things you cannot open yet; they play once you can.",
                    actionLabel = "New playlist",
                    onAction = newPlaylist,
                )
            }

            else -> items(items = all, key = { it.playlist.id }, contentType = { "playlist" }) { summary ->
                PlaylistCard(
                    summary = summary,
                    onOpen = { navigator.open(PlaylistRoute(summary.playlist.id)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(summary: PlaylistSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = appColors
    val playlist = summary.playlist
    val target = remember(playlist.id, playlist.name) { DropTarget.SidebarPlaylist(playlist.id, playlist.name) }
    val targetId = remember { Any() }
    val visual = rememberDropVisual(targetId, target)
    val payload = remember(summary) { DragPayload.Playlist(playlist.id, playlist.name, summary.entryCount) }
    Box(
        modifier = modifier
            .dropTarget(target, targetId)
            // Lights up while something that can go into it is held over it.
            .border(
                width = Sizes.focusRing,
                color = when {
                    visual.hovered && visual.accepts -> colors.accent
                    visual.hovered && visual.refuses -> colors.error
                    else -> Color.Transparent
                },
                shape = Radius.xl,
            ),
    ) {
        EntityCard(
            title = playlist.name,
            subtitle = if (summary.entryCount == 0) "Empty" else countOf(summary.entryCount, "entry", "entries"),
            seed = "playlist-${playlist.id}",
            payload = payload,
            onOpen = onOpen,
            icon = AppIcons.Playlist,
        )
    }
}

private const val SKELETON_CARDS = 6
