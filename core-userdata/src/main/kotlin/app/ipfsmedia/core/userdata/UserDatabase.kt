package app.ipfsmedia.core.userdata

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * What the user made, per identity: favourites, playlists, history, follows, pins, resume
 * positions and layout.
 *
 * A separate database from haven-mobile's mirror on purpose. The mirror is a cache of Arkiv and is
 * dropped and refetched whenever its schema changes; nothing here can be refetched, so **this
 * database is migrated, never dropped.** Every schema change needs a [Migration] in [MIGRATIONS]
 * and a version bump; there is deliberately no destructive fallback, so a missing migration fails
 * in CI's instrumented tests rather than deleting someone's playlists.
 *
 * One file per identity (`ipfsmedia-user-<address>.db`), so switching identity shows none of the
 * other's data and removing an identity deletes exactly one file.
 */
@Database(
    entities = [
        FavouriteEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        HistoryEntity::class,
        FollowEntity::class,
        PinEntity::class,
        ResumeEntity::class,
        DaoStateEntity::class,
        MuteEntity::class,
        HiddenThreadEntity::class,
        PrefEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun favouritesDao(): FavouritesDao
    abstract fun playlistsDao(): PlaylistsDao
    abstract fun historyDao(): HistoryDao
    abstract fun socialDao(): SocialDao
    abstract fun stateDao(): StateDao

    companion object {
        fun fileName(walletAddress: String): String = "ipfsmedia-user-${walletAddress.trim().lowercase()}.db"

        /** Add one entry per schema change. Version 1 is the first release, so the list is empty. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
