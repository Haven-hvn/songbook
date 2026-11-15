package app.ipfsmedia.core.userdata

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouritesDao {
    @Query("SELECT * FROM favourites ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<FavouriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FavouriteEntity>)

    @Query("DELETE FROM favourites WHERE recordKey = :recordKey AND chapterIndex = :chapterIndex")
    suspend fun delete(recordKey: String, chapterIndex: Int)

    @Query("SELECT COUNT(*) FROM favourites WHERE recordKey = :recordKey AND chapterIndex = :chapterIndex")
    suspend fun count(recordKey: String, chapterIndex: Int): Int
}

@Dao
interface PlaylistsDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_entries e WHERE e.playlistId = p.id) AS entryCount " +
            "FROM playlists p ORDER BY p.position ASC, p.id ASC",
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY position ASC, id ASC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    suspend fun getEntries(playlistId: Long): List<PlaylistEntryEntity>

    @Query("SELECT * FROM playlist_entries WHERE id IN (:ids)")
    suspend fun getEntriesByIds(ids: List<Long>): List<PlaylistEntryEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>): List<Long>

    @Query("UPDATE playlists SET name = :name, updatedAtMs = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAtMs = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE playlists SET position = :position WHERE id = :id")
    suspend fun setPlaylistPosition(id: Long, position: Int)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE id IN (:ids)")
    suspend fun deleteEntries(ids: List<Long>)

    @Query("UPDATE playlist_entries SET position = :position WHERE id = :id")
    suspend fun setEntryPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlists")
    suspend fun lastPlaylistPosition(): Int

    /** Rewrites positions so they run 0..n-1 in [orderedIds] order. */
    @Transaction
    suspend fun renumberEntries(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setEntryPosition(id, index) }
    }

    @Transaction
    suspend fun renumberPlaylists(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setPlaylistPosition(id, index) }
    }

    /**
     * Inserts [snapshots] into [playlistId] starting at [atIndex] (the end when null or past it)
     * and returns the new rows' ids. One transaction, so a reader never sees a half-shifted list.
     */
    @Transaction
    suspend fun insertAt(
        playlistId: Long,
        snapshots: List<EntrySnapshot>,
        atIndex: Int?,
        now: Long,
    ): List<Long> {
        if (snapshots.isEmpty()) return emptyList()
        val existing = getEntries(playlistId)
        val index = (atIndex ?: existing.size).coerceIn(0, existing.size)
        val ids = insertEntries(
            snapshots.map { snapshot ->
                PlaylistEntryEntity(playlistId = playlistId, position = 0, addedAtMs = now, entry = snapshot)
            },
        )
        val order = existing.map { it.id }.toMutableList().apply { addAll(index, ids) }
        renumberEntries(order)
        touch(playlistId, now)
        return ids
    }

    /** Puts back rows removed earlier, at the positions they had. Used by Undo. */
    @Transaction
    suspend fun restoreEntries(entries: List<PlaylistEntryEntity>) {
        if (entries.isEmpty()) return
        insertEntries(entries)
        entries.map { it.playlistId }.distinct().forEach { playlistId ->
            val restored = entries.filter { it.playlistId == playlistId }.associateBy { it.id }
            val current = getEntries(playlistId).filter { it.id !in restored }.map { it.id }.toMutableList()
            restored.values.sortedBy { it.position }.forEach { entry ->
                current.add(entry.position.coerceIn(0, current.size), entry.id)
            }
            renumberEntries(current)
        }
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAtMs DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY playedAtMs DESC, id DESC LIMIT 1")
    suspend fun latest(): HistoryEntity?

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM history")
    suspend fun getAll(): List<HistoryEntity>

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keeps the newest [keep] rows; history is a convenience, not an archive. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY playedAtMs DESC, id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface SocialDao {
    @Query("SELECT * FROM follows ORDER BY addedAtMs ASC")
    fun observeFollows(): Flow<List<FollowEntity>>

    @Query("SELECT * FROM follows WHERE address = :address")
    suspend fun getFollow(address: String): FollowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollow(follow: FollowEntity)

    @Query("DELETE FROM follows WHERE address = :address")
    suspend fun deleteFollow(address: String)

    @Query("UPDATE follows SET lastSeenBlock = :block WHERE address = :address AND lastSeenBlock < :block")
    suspend fun markSeen(address: String, block: Long)

    @Query("SELECT * FROM pins ORDER BY position ASC, addedAtMs ASC")
    fun observePins(): Flow<List<PinEntity>>

    @Query("SELECT * FROM pins ORDER BY position ASC, addedAtMs ASC")
    suspend fun getPins(): List<PinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPin(pin: PinEntity)

    @Query("DELETE FROM pins WHERE daoKey = :daoKey")
    suspend fun deletePin(daoKey: String)

    @Query("UPDATE pins SET position = :position WHERE daoKey = :daoKey")
    suspend fun setPinPosition(daoKey: String, position: Int)

    @Transaction
    suspend fun renumberPins(orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { index, key -> setPinPosition(key, index) }
    }

    @Query("SELECT * FROM muted_addresses")
    fun observeMutes(): Flow<List<MuteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMute(mute: MuteEntity)

    @Query("DELETE FROM muted_addresses WHERE address = :address")
    suspend fun deleteMute(address: String)

    @Query("SELECT * FROM hidden_threads")
    fun observeHiddenThreads(): Flow<List<HiddenThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHiddenThread(thread: HiddenThreadEntity)

    @Query("DELETE FROM hidden_threads WHERE threadKey = :threadKey")
    suspend fun deleteHiddenThread(threadKey: String)
}

@Dao
interface StateDao {
    @Query("SELECT * FROM dao_state")
    fun observeDaoStates(): Flow<List<DaoStateEntity>>

    @Query("SELECT * FROM dao_state")
    suspend fun getDaoStates(): List<DaoStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaoStates(states: List<DaoStateEntity>)

    @Query("DELETE FROM dao_state")
    suspend fun clearDaoStates()

    @Transaction
    suspend fun replaceDaoStates(states: List<DaoStateEntity>) {
        clearDaoStates()
        upsertDaoStates(states)
    }

    @Query("SELECT * FROM resume_positions WHERE recordKey = :recordKey AND chapterIndex = :chapterIndex")
    suspend fun getResume(recordKey: String, chapterIndex: Int): ResumeEntity?

    @Query("SELECT * FROM resume_positions")
    fun observeResume(): Flow<List<ResumeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResume(resume: ResumeEntity)

    @Query("DELETE FROM resume_positions WHERE recordKey = :recordKey AND chapterIndex = :chapterIndex")
    suspend fun deleteResume(recordKey: String, chapterIndex: Int)

    @Query("SELECT pref_value FROM prefs WHERE pref_key = :key")
    fun observePref(key: String): Flow<String?>

    @Query("SELECT pref_value FROM prefs WHERE pref_key = :key")
    suspend fun getPref(key: String): String?

    @Query("SELECT * FROM prefs")
    fun observePrefs(): Flow<List<PrefEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPref(pref: PrefEntity)

    @Query("DELETE FROM prefs WHERE pref_key = :key")
    suspend fun deletePref(key: String)
}
