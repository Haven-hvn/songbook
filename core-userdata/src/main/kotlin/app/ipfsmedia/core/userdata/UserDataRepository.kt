package app.ipfsmedia.core.userdata

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's own data for the active identity.
 *
 * Every flow follows the active identity: when the user switches, each flow re-points at the
 * other identity's database and emits that identity's rows, so no screen can show one
 * identity's playlists under another. With no identity, flows emit empty and writes do nothing.
 *
 * Nothing here ever leaves the device. There is no sync, and nothing in this module has network
 * access.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UserDataRepository @Inject constructor(
    private val databases: UserDatabases,
) {
    private fun <T> scoped(empty: T, query: (UserDatabase) -> Flow<T>): Flow<T> =
        databases.active.flatMapLatest { database -> database?.let(query) ?: flowOf(empty) }

    private fun now(): Long = System.currentTimeMillis()

    // ── Favourites ──────────────────────────────────────────────────────────────────────────

    val favourites: Flow<List<FavouriteEntity>> =
        scoped(emptyList()) { it.favouritesDao().observeAll() }

    suspend fun addFavourites(entries: List<EntrySnapshot>) {
        val dao = databases.current()?.favouritesDao() ?: return
        val at = now()
        dao.insertAll(entries.map { FavouriteEntity(entry = it, addedAtMs = at) })
    }

    suspend fun removeFavourite(recordKey: String, chapterIndex: Int) {
        databases.current()?.favouritesDao()?.delete(recordKey, chapterIndex)
    }

    suspend fun isFavourite(recordKey: String, chapterIndex: Int): Boolean =
        (databases.current()?.favouritesDao()?.count(recordKey, chapterIndex) ?: 0) > 0

    // ── Playlists ───────────────────────────────────────────────────────────────────────────

    val playlists: Flow<List<PlaylistSummary>> =
        scoped(emptyList()) { it.playlistsDao().observeSummaries() }

    fun playlist(id: Long): Flow<PlaylistEntity?> =
        scoped<PlaylistEntity?>(null) { it.playlistsDao().observePlaylist(id) }

    fun playlistEntries(id: Long): Flow<List<PlaylistEntryEntity>> =
        scoped(emptyList()) { it.playlistsDao().observeEntries(id) }

    suspend fun getPlaylistEntries(id: Long): List<PlaylistEntryEntity> =
        databases.current()?.playlistsDao()?.getEntries(id).orEmpty()

    /** Creates a playlist holding [entries] and returns its id, or null when no identity is active. */
    suspend fun createPlaylist(name: String, entries: List<EntrySnapshot> = emptyList()): Long? {
        val dao = databases.current()?.playlistsDao() ?: return null
        val at = now()
        val id = dao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifEmpty { DEFAULT_PLAYLIST_NAME },
                createdAtMs = at,
                updatedAtMs = at,
                position = dao.lastPlaylistPosition() + 1,
            ),
        )
        dao.insertAt(id, entries, atIndex = null, now = at)
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) {
        val clean = name.trim().ifEmpty { return }
        databases.current()?.playlistsDao()?.rename(id, clean, now())
    }

    /** Deletes a playlist and returns what is needed to put it back ([restorePlaylist]). */
    suspend fun deletePlaylist(id: Long): DeletedPlaylist? {
        val dao = databases.current()?.playlistsDao() ?: return null
        val playlist = dao.getPlaylist(id) ?: return null
        val entries = dao.getEntries(id)
        dao.deletePlaylist(id)
        return DeletedPlaylist(playlist, entries)
    }

    suspend fun restorePlaylist(deleted: DeletedPlaylist) {
        val dao = databases.current()?.playlistsDao() ?: return
        dao.insertPlaylist(deleted.playlist)
        dao.insertEntries(deleted.entries)
    }

    /** Adds [entries] at [atIndex] (the end when null) and returns the new rows' ids. */
    suspend fun addToPlaylist(id: Long, entries: List<EntrySnapshot>, atIndex: Int? = null): List<Long> =
        databases.current()?.playlistsDao()?.insertAt(id, entries, atIndex, now()).orEmpty()

    /** Removes rows and returns them, so the removal can be undone ([restorePlaylistEntries]). */
    suspend fun removePlaylistEntries(entryIds: List<Long>): List<PlaylistEntryEntity> {
        val dao = databases.current()?.playlistsDao() ?: return emptyList()
        if (entryIds.isEmpty()) return emptyList()
        val removed = dao.getEntriesByIds(entryIds)
        dao.deleteEntries(entryIds)
        removed.map { it.playlistId }.distinct().forEach { playlistId ->
            dao.renumberEntries(dao.getEntries(playlistId).map { it.id })
        }
        return removed
    }

    suspend fun restorePlaylistEntries(entries: List<PlaylistEntryEntity>) {
        databases.current()?.playlistsDao()?.restoreEntries(entries)
    }

    /** Moves the row at [from] to [to] within playlist [id]. Out-of-range indices do nothing. */
    suspend fun movePlaylistEntry(id: Long, from: Int, to: Int) {
        val dao = databases.current()?.playlistsDao() ?: return
        val order = dao.getEntries(id).map { it.id }
        dao.renumberEntries(moved(order, from, to) ?: return)
    }

    /** Moves the playlist at [from] to [to] in the sidebar order. */
    suspend fun movePlaylist(from: Int, to: Int) {
        val dao = databases.current()?.playlistsDao() ?: return
        val order = dao.getPlaylists().map { it.id }
        dao.renumberPlaylists(moved(order, from, to) ?: return)
    }

    // ── History ─────────────────────────────────────────────────────────────────────────────

    val history: Flow<List<HistoryEntity>> =
        scoped(emptyList()) { it.historyDao().observeRecent(HISTORY_LIMIT) }

    /**
     * Records that [entry] was played. A repeat of the entry already on top within
     * [HISTORY_REPEAT_WINDOW_MS] is not recorded again, so pausing and resuming one song does not
     * fill the page with it.
     */
    suspend fun recordPlay(entry: EntrySnapshot) {
        val dao = databases.current()?.historyDao() ?: return
        val at = now()
        val latest = dao.latest()
        if (latest != null &&
            latest.entry.recordKey == entry.recordKey &&
            latest.entry.chapterIndex == entry.chapterIndex &&
            at - latest.playedAtMs < HISTORY_REPEAT_WINDOW_MS
        ) {
            return
        }
        dao.insert(HistoryEntity(playedAtMs = at, entry = entry))
        dao.trim(HISTORY_LIMIT)
    }

    suspend fun removeHistoryEntry(id: Long) {
        databases.current()?.historyDao()?.delete(id)
    }

    /** Clears history and returns what was there, so "Clear" can be undone. */
    suspend fun clearHistory(): List<HistoryEntity> {
        val dao = databases.current()?.historyDao() ?: return emptyList()
        val all = dao.getAll()
        dao.clear()
        return all
    }

    suspend fun restoreHistory(entries: List<HistoryEntity>) {
        databases.current()?.historyDao()?.insertAll(entries)
    }

    // ── Following and pins ──────────────────────────────────────────────────────────────────

    val follows: Flow<List<FollowEntity>> = scoped(emptyList()) { it.socialDao().observeFollows() }

    suspend fun follow(address: String, label: String, lastSeenBlock: Long = 0L) {
        val dao = databases.current()?.socialDao() ?: return
        val id = address.trim().lowercase()
        if (id.isEmpty() || dao.getFollow(id) != null) return
        dao.upsertFollow(FollowEntity(address = id, label = label, addedAtMs = now(), lastSeenBlock = lastSeenBlock))
    }

    suspend fun unfollow(address: String): FollowEntity? {
        val dao = databases.current()?.socialDao() ?: return null
        val id = address.trim().lowercase()
        val existing = dao.getFollow(id) ?: return null
        dao.deleteFollow(id)
        return existing
    }

    suspend fun restoreFollow(follow: FollowEntity) {
        databases.current()?.socialDao()?.upsertFollow(follow)
    }

    /** Remembers that the user has been shown this uploader's uploads up to [block]. */
    suspend fun markUploaderSeen(address: String, block: Long) {
        databases.current()?.socialDao()?.markSeen(address.trim().lowercase(), block)
    }

    val pins: Flow<List<PinEntity>> = scoped(emptyList()) { it.socialDao().observePins() }

    suspend fun pin(daoKey: String) {
        val dao = databases.current()?.socialDao() ?: return
        val pins = dao.getPins()
        if (pins.any { it.daoKey == daoKey }) return
        dao.upsertPin(PinEntity(daoKey = daoKey, position = pins.size, addedAtMs = now()))
    }

    suspend fun unpin(daoKey: String) {
        val dao = databases.current()?.socialDao() ?: return
        dao.deletePin(daoKey)
        dao.renumberPins(dao.getPins().map { it.daoKey })
    }

    suspend fun movePin(from: Int, to: Int) {
        val dao = databases.current()?.socialDao() ?: return
        val order = dao.getPins().map { it.daoKey }
        dao.renumberPins(moved(order, from, to) ?: return)
    }

    // ── Local moderation ────────────────────────────────────────────────────────────────────

    val mutedAddresses: Flow<Set<String>> =
        scoped(emptyList<MuteEntity>()) { it.socialDao().observeMutes() }.map { rows -> rows.map { it.address }.toSet() }

    suspend fun setMuted(address: String, muted: Boolean) {
        val dao = databases.current()?.socialDao() ?: return
        val id = address.trim().lowercase()
        if (muted) dao.upsertMute(MuteEntity(id, now())) else dao.deleteMute(id)
    }

    val hiddenThreads: Flow<Set<String>> =
        scoped(emptyList<HiddenThreadEntity>()) { it.socialDao().observeHiddenThreads() }
            .map { rows -> rows.map { it.threadKey }.toSet() }

    suspend fun setThreadHidden(threadKey: String, hidden: Boolean) {
        val dao = databases.current()?.socialDao() ?: return
        if (hidden) dao.upsertHiddenThread(HiddenThreadEntity(threadKey, now())) else dao.deleteHiddenThread(threadKey)
    }

    // ── What the last refresh learned ───────────────────────────────────────────────────────

    val daoStates: Flow<List<DaoStateEntity>> = scoped(emptyList()) { it.stateDao().observeDaoStates() }

    suspend fun getDaoStates(): List<DaoStateEntity> = databases.current()?.stateDao()?.getDaoStates().orEmpty()

    suspend fun replaceDaoStates(states: List<DaoStateEntity>) {
        databases.current()?.stateDao()?.replaceDaoStates(states)
    }

    // ── Resume positions ────────────────────────────────────────────────────────────────────

    val resumePositions: Flow<List<ResumeEntity>> = scoped(emptyList()) { it.stateDao().observeResume() }

    suspend fun resumePosition(recordKey: String, chapterIndex: Int): ResumeEntity? =
        databases.current()?.stateDao()?.getResume(recordKey, chapterIndex)

    /**
     * Saves where playback stopped. Positions in the first [RESUME_MIN_MS] or the last
     * [RESUME_TAIL_MS] are treated as "not started" and "finished", and clear the saved position.
     */
    suspend fun saveResumePosition(recordKey: String, chapterIndex: Int, positionMs: Long, durationMs: Long) {
        val dao = databases.current()?.stateDao() ?: return
        val finished = durationMs > 0 && positionMs >= durationMs - RESUME_TAIL_MS
        if (positionMs < RESUME_MIN_MS || finished) {
            dao.deleteResume(recordKey, chapterIndex)
        } else {
            dao.upsertResume(ResumeEntity(recordKey, chapterIndex, positionMs, durationMs, now()))
        }
    }

    // ── Small values (layout, clock, refresh time) ──────────────────────────────────────────

    fun pref(key: String): Flow<String?> = scoped<String?>(null) { it.stateDao().observePref(key) }

    val prefs: Flow<Map<String, String>> =
        scoped(emptyList<PrefEntity>()) { it.stateDao().observePrefs() }
            .map { rows -> rows.associate { it.key to it.value } }

    suspend fun getPref(key: String): String? = databases.current()?.stateDao()?.getPref(key)

    suspend fun setPref(key: String, value: String?) {
        val dao = databases.current()?.stateDao() ?: return
        if (value == null) dao.deletePref(key) else dao.upsertPref(PrefEntity(key, value))
    }

    companion object {
        const val DEFAULT_PLAYLIST_NAME = "New playlist"
        const val HISTORY_LIMIT = 1_000
        const val HISTORY_REPEAT_WINDOW_MS = 30_000L
        const val RESUME_MIN_MS = 15_000L
        const val RESUME_TAIL_MS = 15_000L
    }
}

/** A deleted playlist with its rows, kept only long enough to offer Undo. */
data class DeletedPlaylist(
    val playlist: PlaylistEntity,
    val entries: List<PlaylistEntryEntity>,
)

/**
 * [list] with the element at [from] moved to [to], or null when either index is out of range or
 * nothing would change. Pure, so reordering rules are tested without a database.
 */
fun <T> moved(list: List<T>, from: Int, to: Int): List<T>? {
    if (from !in list.indices || to !in list.indices || from == to) return null
    val result = list.toMutableList()
    result.add(to, result.removeAt(from))
    return result
}
