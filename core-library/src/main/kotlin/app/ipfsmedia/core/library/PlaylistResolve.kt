package app.ipfsmedia.core.library

/** Whether a saved entry can still be played, and if not, in which way. */
enum class EntryAvailability {
    /** Plays as saved. */
    AVAILABLE,

    /** The album was re-uploaded and this song could not be found in the new file. */
    CHANGED,

    /** The record is gone (Arkiv records expire). The row stays, marked, with its saved titles. */
    UNAVAILABLE,

    /** The record is there, but the wallet cannot open it. */
    LOCKED,
}

data class ResolvedEntry(
    /** The entry to play: the saved one, or the saved one re-pointed at the album's current file. */
    val entry: PlayableEntry,
    val availability: EntryAvailability,
    val item: LibraryItem?,
) {
    val isPlayable: Boolean get() = availability == EntryAvailability.AVAILABLE
}

/**
 * Matches a saved playlist, history or queue entry against the current catalogue
 * (docs/PLAN.md §5, "Songs").
 *
 * A song entry holds the file it was read from and its start and end times, so it plays even
 * when the album's chapter cache has been dropped. If the album has since been re-uploaded (the
 * piece CID differs), the saved times belong to a different file, and the entry is re-matched:
 * first the song at the same index if its title still matches, then a song with the same title
 * anywhere in the album when exactly one has it. If neither holds — or the new file has not been
 * read yet, so there is nothing to match against — the entry is [EntryAvailability.CHANGED]
 * rather than played from the wrong spot.
 */
fun resolveEntry(saved: PlayableEntry, state: LibraryState): ResolvedEntry {
    val item = state.item(saved.recordKey)
        ?: return ResolvedEntry(saved, EntryAvailability.UNAVAILABLE, null)

    val refreshed = saved.copy(
        albumTitle = item.title,
        uploaderName = item.uploaderName,
        daoKey = item.daoKey,
    )
    val locked = item.access.isLocked

    if (saved.isWholeFile) {
        val entry = refreshed.copy(title = item.title, pieceCid = item.pieceCid, durationMs = item.durationMs)
        return ResolvedEntry(entry, if (locked) EntryAvailability.LOCKED else EntryAvailability.AVAILABLE, item)
    }

    val samePiece = saved.pieceCid == null || item.pieceCid == null || saved.pieceCid == item.pieceCid
    if (samePiece) {
        return ResolvedEntry(refreshed, if (locked) EntryAvailability.LOCKED else EntryAvailability.AVAILABLE, item)
    }

    val match = rematch(saved, item.songs)
        ?: return ResolvedEntry(refreshed, EntryAvailability.CHANGED, item)
    return ResolvedEntry(item.songEntry(match), if (locked) EntryAvailability.LOCKED else EntryAvailability.AVAILABLE, item)
}

internal fun rematch(saved: PlayableEntry, songs: List<Song>?): Song? {
    if (songs.isNullOrEmpty()) return null
    val wanted = saved.title.trim().lowercase()
    songs.firstOrNull { it.index == saved.chapterIndex }
        ?.takeIf { it.title.trim().lowercase() == wanted }
        ?.let { return it }
    return songs.filter { it.title.trim().lowercase() == wanted }.singleOrNull()
}
