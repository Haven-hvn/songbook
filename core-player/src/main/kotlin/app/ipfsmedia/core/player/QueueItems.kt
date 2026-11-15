package app.ipfsmedia.core.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.PlayableEntry
import java.io.File

/**
 * Queue rows as Media3 items, and back.
 *
 * A row's file is not known when it is queued, and may not be on the device at all, so an item
 * does not point at a file. It points at `ipfsmedia://item/<record key>`, which the service's
 * data source turns into a staged file at the moment the player opens it ([StagingResolver]).
 * A song is that same address clipped to the song's start and end, which is how haven-mobile
 * plays chapters.
 *
 * The whole [PlayableEntry] rides in the item's metadata extras. Media3 carries those between
 * the window's controller and the service's session in both directions, so the queue the window
 * shows is read straight from the player and there is no second list to keep in step.
 */
internal object QueueItems {
    const val SCHEME = "ipfsmedia"
    const val HOST = "item"
    private const val PARAM_PIECE = "piece"

    private const val KEY_UID = "ipfsmedia.uid"
    private const val KEY_RECORD = "ipfsmedia.recordKey"
    private const val KEY_CHAPTER = "ipfsmedia.chapter"
    private const val KEY_KIND = "ipfsmedia.kind"
    private const val KEY_PIECE = "ipfsmedia.pieceCid"
    private const val KEY_START = "ipfsmedia.startMs"
    private const val KEY_END = "ipfsmedia.endMs"
    private const val KEY_TITLE = "ipfsmedia.title"
    private const val KEY_ALBUM = "ipfsmedia.album"
    private const val KEY_UPLOADER = "ipfsmedia.uploader"
    private const val KEY_UPLOADER_ADDRESS = "ipfsmedia.uploaderAddress"
    private const val KEY_DAO = "ipfsmedia.dao"
    private const val KEY_DURATION = "ipfsmedia.durationMs"
    private const val UNSET = -1L

    fun uriFor(entry: PlayableEntry): Uri {
        val builder = Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(entry.recordKey)
        // A song's times only mean something against the file they were read from.
        val pieceCid = entry.pieceCid
        if (!entry.isWholeFile && pieceCid != null) builder.appendQueryParameter(PARAM_PIECE, pieceCid)
        return builder.build()
    }

    /** The staging request behind one of our addresses; null for any other URI. */
    fun requestOf(uri: Uri): StageRequest? {
        if (uri.scheme != SCHEME || uri.authority != HOST) return null
        val recordKey = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
        return StageRequest(recordKey, uri.getQueryParameter(PARAM_PIECE)?.takeIf { it.isNotBlank() })
    }

    /**
     * @param artwork where this upload's picture is or will be. It is set before the picture
     *   exists: the file appears when the upload is first staged, which is before it is heard.
     */
    fun toMediaItem(row: QueueRow, artwork: File?): MediaItem {
        val entry = row.entry
        val metadata = MediaMetadata.Builder()
            .setTitle(entry.title)
            .setArtist(entry.uploaderName)
            .setAlbumTitle(entry.albumTitle)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (entry.kind == EntryKind.VIDEO) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(artwork?.let(Uri::fromFile))
            .setExtras(extrasOf(row))
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(entry.id)
            .setUri(uriFor(entry))
            .setMediaMetadata(metadata)
        if (!entry.isWholeFile) {
            val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(entry.startMs.coerceAtLeast(0L))
            val end = entry.endMs
            if (end != null && end > entry.startMs) clip.setEndPositionMs(end)
            builder.setClippingConfiguration(clip.build())
        }
        return builder.build()
    }

    /** The row an item stands for; null when the item was not built here. */
    fun rowOf(item: MediaItem): QueueRow? {
        val extras = item.mediaMetadata.extras ?: return null
        val recordKey = extras.getString(KEY_RECORD)?.takeIf { it.isNotBlank() } ?: return null
        val chapter = extras.getInt(KEY_CHAPTER, PlayableEntry.WHOLE_FILE)
        val kind = EntryKind.entries.firstOrNull { it.name == extras.getString(KEY_KIND) }
            ?: if (chapter == PlayableEntry.WHOLE_FILE) EntryKind.ALBUM else EntryKind.SONG
        return QueueRow(
            uid = extras.getLong(KEY_UID, UNSET),
            entry = PlayableEntry(
                recordKey = recordKey,
                chapterIndex = chapter,
                kind = kind,
                pieceCid = extras.getString(KEY_PIECE),
                startMs = extras.getLong(KEY_START, 0L),
                endMs = extras.getLong(KEY_END, UNSET).takeIf { it != UNSET },
                title = extras.getString(KEY_TITLE).orEmpty(),
                albumTitle = extras.getString(KEY_ALBUM).orEmpty(),
                uploaderName = extras.getString(KEY_UPLOADER).orEmpty(),
                uploaderAddress = extras.getString(KEY_UPLOADER_ADDRESS).orEmpty(),
                daoKey = extras.getString(KEY_DAO),
                durationMs = extras.getLong(KEY_DURATION, UNSET).takeIf { it != UNSET },
            ),
        )
    }

    private fun extrasOf(row: QueueRow): Bundle {
        val entry = row.entry
        return Bundle().apply {
            putLong(KEY_UID, row.uid)
            putString(KEY_RECORD, entry.recordKey)
            putInt(KEY_CHAPTER, entry.chapterIndex)
            putString(KEY_KIND, entry.kind.name)
            putString(KEY_PIECE, entry.pieceCid)
            putLong(KEY_START, entry.startMs)
            putLong(KEY_END, entry.endMs ?: UNSET)
            putString(KEY_TITLE, entry.title)
            putString(KEY_ALBUM, entry.albumTitle)
            putString(KEY_UPLOADER, entry.uploaderName)
            putString(KEY_UPLOADER_ADDRESS, entry.uploaderAddress)
            putString(KEY_DAO, entry.daoKey)
            putLong(KEY_DURATION, entry.durationMs ?: UNSET)
        }
    }
}

/** The rows a player holds, in list order. Items that were not built here are left out. */
internal fun Player.queueRows(): List<QueueRow> {
    val count = mediaItemCount
    if (count == 0) return emptyList()
    val rows = ArrayList<QueueRow>(count)
    for (index in 0 until count) QueueItems.rowOf(getMediaItemAt(index))?.let(rows::add)
    return rows
}

/**
 * Row indices in the order they will play, starting at [from]: list order, or the player's
 * shuffle order when shuffle is on. Repeat is ignored, so the walk always ends.
 */
internal fun Player.playOrderFrom(from: Int, limit: Int = Int.MAX_VALUE): List<Int> {
    val timeline = currentTimeline
    val count = timeline.windowCount
    if (timeline.isEmpty || from !in 0 until count) return emptyList()
    val order = ArrayList<Int>(minOf(limit, count))
    var index = from
    while (index != C.INDEX_UNSET && order.size < limit && order.size < count) {
        order += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return order
}
