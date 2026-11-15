package app.ipfsmedia.core.library

import app.ipfsmedia.core.userdata.EntrySnapshot

/** What a [PlayableEntry] is. */
enum class EntryKind { ALBUM, SONG, VIDEO }

/**
 * Something that can be played, queued, put in a playlist or carried: a whole album, one song
 * or a video.
 *
 * This is the one shape that travels between the library, the queue, playlists, history and the
 * drag layer. It is self-contained on purpose — it carries the times and the titles, not a
 * reference to a library item — so it still means something when the album's chapter cache has
 * been dropped or the record has expired (docs/PLAN.md §5, "Songs").
 */
data class PlayableEntry(
    val recordKey: String,
    /** [WHOLE_FILE] for a whole album or a video. */
    val chapterIndex: Int,
    val kind: EntryKind,
    /** The file the times were read from. A different CID later means the album was re-uploaded. */
    val pieceCid: String?,
    val startMs: Long,
    val endMs: Long?,
    val title: String,
    val albumTitle: String,
    val uploaderName: String,
    val uploaderAddress: String,
    val daoKey: String?,
    val durationMs: Long?,
) {
    /** Identity within a queue or selection: the record plus which part of it. */
    val id: String get() = entryId(recordKey, chapterIndex)

    val isWholeFile: Boolean get() = chapterIndex == WHOLE_FILE

    companion object {
        const val WHOLE_FILE = EntrySnapshot.WHOLE_FILE

        fun entryId(recordKey: String, chapterIndex: Int): String = "$recordKey#$chapterIndex"

        /** Splits an [id] back into record key and chapter index; null when it is not one. */
        fun parseId(id: String): Pair<String, Int>? {
            val at = id.lastIndexOf('#')
            if (at <= 0) return null
            val index = id.substring(at + 1).toIntOrNull() ?: return null
            return id.substring(0, at) to index
        }
    }
}

fun PlayableEntry.toSnapshot(): EntrySnapshot = EntrySnapshot(
    recordKey = recordKey,
    chapterIndex = chapterIndex,
    kind = kind.name,
    pieceCid = pieceCid,
    startMs = startMs,
    endMs = endMs,
    title = title,
    albumTitle = albumTitle,
    uploaderName = uploaderName,
    uploaderAddress = uploaderAddress,
    daoKey = daoKey,
    durationMs = durationMs,
)

fun EntrySnapshot.toPlayable(): PlayableEntry = PlayableEntry(
    recordKey = recordKey,
    chapterIndex = chapterIndex,
    // An unknown kind (written by a later version) is read as what its index implies.
    kind = EntryKind.entries.firstOrNull { it.name == kind }
        ?: if (chapterIndex == EntrySnapshot.WHOLE_FILE) EntryKind.ALBUM else EntryKind.SONG,
    pieceCid = pieceCid,
    startMs = startMs,
    endMs = endMs,
    title = title,
    albumTitle = albumTitle,
    uploaderName = uploaderName,
    uploaderAddress = uploaderAddress,
    daoKey = daoKey,
    durationMs = durationMs,
)

/** The whole album or video as one entry. */
fun LibraryItem.wholeEntry(): PlayableEntry = PlayableEntry(
    recordKey = id,
    chapterIndex = PlayableEntry.WHOLE_FILE,
    kind = if (kind == ItemKind.VIDEO) EntryKind.VIDEO else EntryKind.ALBUM,
    pieceCid = pieceCid,
    startMs = 0L,
    endMs = null,
    title = title,
    albumTitle = title,
    uploaderName = uploaderName,
    uploaderAddress = uploaderAddress,
    daoKey = daoKey,
    durationMs = durationMs,
)

/** One song of this album as an entry. */
fun LibraryItem.songEntry(song: Song): PlayableEntry = PlayableEntry(
    recordKey = id,
    chapterIndex = song.index,
    kind = EntryKind.SONG,
    pieceCid = pieceCid,
    startMs = song.startMs,
    endMs = song.endMs,
    title = song.title,
    albumTitle = title,
    uploaderName = uploaderName,
    uploaderAddress = uploaderAddress,
    daoKey = daoKey,
    durationMs = song.durationMs,
)

/**
 * What playing or queueing this item means: its songs in order when they are known and there
 * are some, otherwise the whole file as one entry. An album that has never been fetched
 * therefore moves as a whole album, which is the plan's rule.
 */
fun LibraryItem.playEntries(): List<PlayableEntry> {
    val known = songs
    return if (kind == ItemKind.ALBUM && !known.isNullOrEmpty()) known.map { songEntry(it) } else listOf(wholeEntry())
}

/**
 * The songs of the album behind [whole], built from a chapter list that has just been read from
 * its file. Used by the player to turn a queued whole album into its songs the moment the file
 * arrives, before the library has re-derived. Empty when the album has no chapters: it stays one
 * entry.
 */
fun songEntriesOf(whole: PlayableEntry, chapters: List<AudioChapter>, albumDurationMs: Long?): List<PlayableEntry> {
    if (chapters.isEmpty()) return emptyList()
    val rows = chapters.mapIndexed { index, chapter ->
        ChapterRow(whole.recordKey, index, chapter.startMs, chapter.title, whole.pieceCid.orEmpty())
    }
    return songsOf(whole.recordKey, null, rows, albumDurationMs).orEmpty().map { song ->
        whole.copy(
            chapterIndex = song.index,
            kind = EntryKind.SONG,
            startMs = song.startMs,
            endMs = song.endMs,
            title = song.title,
            durationMs = song.durationMs,
        )
    }
}
