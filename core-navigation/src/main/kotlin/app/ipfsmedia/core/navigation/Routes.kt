package app.ipfsmedia.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every place the content area can show.
 *
 * Routes are plain serializable keys (Navigation 3): the history is a list of these that the app
 * owns, so it can be saved, restored after the window is resized, and inspected in tests.
 *
 * A route says *what* to show, never how it got there. Arguments are ids (a record key, an
 * address, a DataDAO key), never objects: the screen looks the thing up, so a restored history
 * shows current data.
 */
sealed interface Route : NavKey

/** A sidebar section. Each one keeps its own history (see [NavigationState]). */
sealed interface SectionRoute : Route

@Serializable data object HomeRoute : SectionRoute

@Serializable data object CollectionRoute : SectionRoute

@Serializable data object PlaylistsRoute : SectionRoute

@Serializable data object HistoryRoute : SectionRoute

/** The DataDAO directory: yours first, then the rest. */
@Serializable data object DataDaosRoute : SectionRoute

@Serializable data object SearchRoute : SectionRoute

@Serializable data object SettingsRoute : SectionRoute

/** One audio upload: an album, with its songs once the file has been fetched. */
@Serializable data class AlbumRoute(val recordKey: String) : Route

@Serializable data class VideoRoute(val recordKey: String) : Route

/** [daoKey] is `core-library`'s DataDAO key; [tab] is a `DataDaoTab` name, or null for the default. */
@Serializable data class DataDaoRoute(val daoKey: String, val tab: String? = null) : Route

@Serializable data class UploaderRoute(val address: String) : Route

@Serializable data class PlaylistRoute(val playlistId: Long) : Route

/** One discussion thread, by its Arkiv entity key. */
@Serializable data class ThreadRoute(val threadKey: String) : Route

/** The full-size view of what is playing. */
@Serializable data object NowPlayingRoute : Route

/** Sidebar sections in display order. Ctrl+1 to Ctrl+9 select by position in this list. */
val SECTION_ROUTES: List<SectionRoute> = listOf(
    HomeRoute,
    CollectionRoute,
    PlaylistsRoute,
    HistoryRoute,
    DataDaosRoute,
    SearchRoute,
    SettingsRoute,
)

/**
 * The section a route belongs to when it is opened from the sidebar, or null when it should
 * open on top of whatever section is showing (an album opened from the feed stays under Home).
 */
fun Route.homeSection(): SectionRoute? = when (this) {
    is SectionRoute -> this
    is DataDaoRoute -> DataDaosRoute
    is PlaylistRoute -> PlaylistsRoute
    is AlbumRoute, is VideoRoute, is UploaderRoute, is ThreadRoute, NowPlayingRoute -> null
}

/** The page for one upload: an album page or a video page. */
fun mediaRoute(recordKey: String, isVideo: Boolean): Route =
    if (isVideo) VideoRoute(recordKey) else AlbumRoute(recordKey)
