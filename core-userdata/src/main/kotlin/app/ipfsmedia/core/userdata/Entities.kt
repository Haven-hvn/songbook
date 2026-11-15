package app.ipfsmedia.core.userdata

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What a saved entry pointed at, copied at the moment it was saved.
 *
 * An album is one file and a song is a range inside it (docs/PLAN.md §5, "Songs"), so an entry
 * is: which upload ([recordKey]), which exact file the times were read from ([pieceCid]), which
 * song ([chapterIndex], [WHOLE_FILE] for a whole album or a video) and where it starts and ends.
 * The titles and names are copies. Arkiv records expire; a playlist row must still read correctly
 * — and say that it is unavailable — when the record it came from is gone.
 */
data class EntrySnapshot(
    val recordKey: String,
    val chapterIndex: Int,
    /** `ALBUM`, `SONG` or `VIDEO`. A string so a later kind cannot break an older database. */
    val kind: String,
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
    companion object {
        /** haven-mobile's own marker for "the whole file": a chapterless album, or a video. */
        const val WHOLE_FILE = -1
        const val KIND_ALBUM = "ALBUM"
        const val KIND_SONG = "SONG"
        const val KIND_VIDEO = "VIDEO"
    }
}

@Entity(tableName = "favourites", primaryKeys = ["recordKey", "chapterIndex"])
data class FavouriteEntity(
    @Embedded val entry: EntrySnapshot,
    val addedAtMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
    /** Bumped whenever the playlist is added to; the tray offers the three most recent. */
    val updatedAtMs: Long,
    /** Order in the sidebar; the user can rearrange it. */
    val position: Int,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "position")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val addedAtMs: Long,
    @Embedded val entry: EntrySnapshot,
)

/** One play. Stays on the device, per identity, and can be cleared; never sent anywhere. */
@Entity(tableName = "history", indices = [Index("playedAtMs")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playedAtMs: Long,
    @Embedded val entry: EntrySnapshot,
)

/** An uploader the user follows. A local list: following is published nowhere. */
@Entity(tableName = "follows")
data class FollowEntity(
    /** Lowercased `0x` address. */
    @PrimaryKey val address: String,
    /** The name shown when it was followed, kept for when the uploader has nothing live. */
    val label: String,
    val addedAtMs: Long,
    /** Newest upload block the user has been shown for this uploader; later uploads are "unseen". */
    val lastSeenBlock: Long,
)

/** A DataDAO pinned to the sidebar. */
@Entity(tableName = "pins")
data class PinEntity(
    @PrimaryKey val daoKey: String,
    val position: Int,
    val addedAtMs: Long,
)

/** Where playback of a long item stopped. */
@Entity(tableName = "resume_positions", primaryKeys = ["recordKey", "chapterIndex"])
data class ResumeEntity(
    val recordKey: String,
    val chapterIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

/**
 * What the last refresh learned about a DataDAO for this identity: whether the wallet meets its
 * gate, and the name its discussion board gave it, if it has one.
 */
@Entity(tableName = "dao_state")
data class DaoStateEntity(
    @PrimaryKey val daoKey: String,
    /** `MEMBER`, `NOT_MEMBER` or `UNKNOWN`. */
    val membership: String,
    val checkedAtMs: Long,
    val title: String?,
    val about: String?,
) {
    companion object {
        const val MEMBER = "MEMBER"
        const val NOT_MEMBER = "NOT_MEMBER"
        const val UNKNOWN = "UNKNOWN"
    }
}

/** An address whose comments are hidden on this device. */
@Entity(tableName = "muted_addresses")
data class MuteEntity(
    @PrimaryKey val address: String,
    val addedAtMs: Long,
)

/** A discussion thread hidden on this device. */
@Entity(tableName = "hidden_threads")
data class HiddenThreadEntity(
    @PrimaryKey val threadKey: String,
    val addedAtMs: Long,
)

/** Small per-identity values: pane widths, shelf order, the time of the last refresh. */
@Entity(tableName = "prefs")
data class PrefEntity(
    @PrimaryKey @ColumnInfo(name = "pref_key") val key: String,
    @ColumnInfo(name = "pref_value") val value: String,
)

/** A playlist with how many entries it holds, for the sidebar and the playlists page. */
data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    val entryCount: Int,
)
