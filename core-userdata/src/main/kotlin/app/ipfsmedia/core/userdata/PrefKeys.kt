package app.ipfsmedia.core.userdata

/** Keys for [UserDataRepository.pref]. One list, so two features cannot pick the same key. */
object PrefKeys {
    /** Chain head the last refresh saw, and the wall-clock time it saw it: the block clock's anchor. */
    const val CLOCK_HEAD_BLOCK = "clock.headBlock"
    const val CLOCK_OBSERVED_AT_MS = "clock.observedAtMs"

    /** Wall-clock time of the last successful refresh. */
    const val LAST_REFRESH_AT_MS = "refresh.lastAtMs"

    /** Newest upload block the Home feed has shown; newer uploads are marked new after a refresh. */
    const val FEED_SEEN_BLOCK = "feed.seenBlock"

    /** Sidebar width in dp, and whether it is collapsed to a rail by the user. */
    const val SIDEBAR_WIDTH_DP = "layout.sidebarWidthDp"

    /** Queue pane width in dp. */
    const val QUEUE_WIDTH_DP = "layout.queueWidthDp"

    /** Whether the queue pane was open. */
    const val QUEUE_OPEN = "layout.queueOpen"

    /** Home shelf ids in display order, comma separated. */
    const val HOME_SHELF_ORDER = "layout.homeShelves"

    /** `LIGHT`, `DARK` or `SYSTEM`. */
    const val THEME = "appearance.theme"

    /** Collection view: `GRID` or `LIST`. */
    const val COLLECTION_VIEW = "collection.view"

    /** Whether leaving a playing video opens picture-in-picture. */
    const val VIDEO_PIP = "video.pip"
}
