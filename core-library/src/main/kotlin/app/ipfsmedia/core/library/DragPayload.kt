package app.ipfsmedia.core.library

/**
 * What is being acted on: picked up with a finger or the mouse, right-clicked, or focused when a
 * shortcut is pressed.
 *
 * One typed payload for every input method (docs/PLAN.md §7). The tray, the right-click menu,
 * the keyboard commands and TalkBack's custom actions are all views of
 * [destinationsFor] applied to one of these, so they cannot offer different things.
 *
 * Plain data, with no reference to the library or the player: everything the rules need is
 * copied in when the payload is built, so the rules can be tested as a table.
 */
sealed interface DragPayload {
    /** Shown on the carried card and in menus' headers. */
    val label: String

    /** How many things are held; the carried card shows a count above one. */
    val count: Int

    /** Songs, albums or videos, or a selection of them. */
    data class Media(
        /**
         * What playing, queueing or adding to a playlist uses: an album's songs when they are
         * known, otherwise the whole file as one entry.
         */
        val entries: List<PlayableEntry>,
        /**
         * Set when the payload stands for one whole album or video (a card, a page header)
         * rather than a selection of songs. Favouriting then marks the album itself, not each
         * of its songs, and the carried card shows the album.
         */
        val whole: PlayableEntry? = null,
        /**
         * Why this cannot be played or queued, or null when it can: "Members of Night Shift
         * only", "Not open yet". A locked item can still go into a playlist.
         */
        val lockedReason: String? = null,
        val isFavourite: Boolean = false,
        /** Already on the device, so Download has nothing to do. */
        val isOnDevice: Boolean = false,
    ) : DragPayload {
        override val label: String
            get() = whole?.title ?: entries.singleOrNull()?.title ?: "${entries.size} items"
        override val count: Int get() = if (whole != null) 1 else entries.size

        /** What favouriting acts on: the album or video itself, or else each selected song. */
        val favouriteTargets: List<PlayableEntry> get() = whole?.let(::listOf) ?: entries

        /** The records behind this payload, each once: what Download fetches. */
        val recordKeys: List<String> get() = entries.map { it.recordKey }.distinct()
    }

    data class Playlist(
        val id: Long,
        val name: String,
        val entryCount: Int,
    ) : DragPayload {
        override val label: String get() = name
        override val count: Int get() = 1
    }

    data class Uploader(
        val address: String,
        val name: String,
        val followed: Boolean,
        val isSelf: Boolean = false,
    ) : DragPayload {
        override val label: String get() = name
        override val count: Int get() = 1
    }

    data class DataDao(
        val key: String,
        val name: String,
        val pinned: Boolean,
    ) : DragPayload {
        override val label: String get() = name
        override val count: Int get() = 1
    }
}

/** Where a payload came from. Decides which "remove" an item offers, if any. */
sealed interface Origin {
    /** A library surface: a grid, a page, the feed, search. */
    data object Library : Origin

    /** Row [index] of the queue. */
    data class Queue(val index: Int) : Origin

    /** Rows of playlist [playlistId], by their row ids. */
    data class Playlist(val playlistId: Long, val entryIds: List<Long>) : Origin

    /** A row of the history page. */
    data class History(val historyId: Long) : Origin
}

/** Builds the payload for one library item, with the lock, favourite and offline facts copied in. */
fun LibraryItem.asPayload(): DragPayload.Media = DragPayload.Media(
    entries = playEntries(),
    whole = wholeEntry(),
    lockedReason = lockedReason(),
    isFavourite = isFavourite,
    isOnDevice = offline == OfflineState.ON_DEVICE,
)

/** Builds the payload for one song of this album. */
fun LibraryItem.asPayload(song: Song): DragPayload.Media = DragPayload.Media(
    entries = listOf(songEntry(song)),
    lockedReason = lockedReason(),
    isOnDevice = offline == OfflineState.ON_DEVICE,
)

/** Why this item cannot be opened, in the words every surface uses, or null when it can. */
fun LibraryItem.lockedReason(): String? = when (access) {
    AccessState.NOT_MEMBER -> "Members of ${daoName ?: "its DataDAO"} only"
    AccessState.NOT_OPEN_YET -> "Not open yet"
    AccessState.OPEN, AccessState.MEMBER, AccessState.UNKNOWN -> null
}

fun Uploader.asPayload(): DragPayload.Uploader =
    DragPayload.Uploader(address = address, name = displayName, followed = followed, isSelf = isSelf)

fun DataDao.asPayload(): DragPayload.DataDao = DragPayload.DataDao(key = key, name = name, pinned = pinned)
