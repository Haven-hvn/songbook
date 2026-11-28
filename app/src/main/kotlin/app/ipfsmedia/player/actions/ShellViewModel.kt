package app.ipfsmedia.player.actions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.design.ItemActions
import app.ipfsmedia.core.design.StageInfo
import app.ipfsmedia.core.design.ThemeMode
import app.ipfsmedia.core.library.Destination
import app.ipfsmedia.core.library.DragPayload
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.library.LibraryState
import app.ipfsmedia.core.library.Origin
import app.ipfsmedia.core.library.PUBLIC_GROUP_KEY
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.PlaylistChoice
import app.ipfsmedia.core.library.countOf
import app.ipfsmedia.core.library.toPlayable
import app.ipfsmedia.core.library.toSnapshot
import app.ipfsmedia.core.navigation.AlbumRoute
import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.PlaylistRoute
import app.ipfsmedia.core.navigation.Route
import app.ipfsmedia.core.navigation.UploaderRoute
import app.ipfsmedia.core.navigation.VideoRoute
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.player.ArtworkStore
import app.ipfsmedia.core.player.DownloadState
import app.ipfsmedia.core.player.Downloads
import app.ipfsmedia.core.player.PlayerConnection
import app.ipfsmedia.core.player.PlayerUiState
import app.ipfsmedia.core.player.QueueOutcome
import app.ipfsmedia.core.player.QueueSnapshot
import app.ipfsmedia.core.social.RefreshCoordinator
import app.ipfsmedia.core.social.RefreshState
import app.ipfsmedia.core.userdata.EntrySnapshot
import app.ipfsmedia.core.userdata.FollowEntity
import app.ipfsmedia.core.userdata.HistoryEntity
import app.ipfsmedia.core.userdata.PlaylistSummary
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.wallet.WalletIdentity
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Something the shell has to do in the window: it cannot be done from a view model. */
sealed interface ShellEvent {
    data class Navigate(val route: Route, val inHomeSection: Boolean = false) : ShellEvent

    /** A one-line notice. With [undo] it offers Undo, which takes back the latest action. */
    data class Notice(val text: String, val undo: Boolean = false) : ShellEvent

    /** Leave [route] if it is the page being shown: its subject was just deleted. */
    data class LeavePage(val route: Route) : ShellEvent

    /** Bring the search field into focus. */
    data object FocusSearch : ShellEvent
}

/** A question the shell asks before an action can finish. */
sealed interface ShellDialog {
    /** Name a new playlist that will hold [entries]. */
    data class NamePlaylist(val suggested: String, val entries: List<EntrySnapshot>) : ShellDialog

    data class RenamePlaylist(val id: Long, val current: String) : ShellDialog

    /** Ask before signing to unlock a gated download in "keep unlocked" mode. */
    data class ApproveDownloadSignature(val recordKey: String, val title: String) : ShellDialog
}

/** Whether there is an identity to show the app for. */
enum class Gate { Loading, NeedsWallet, Open }

/**
 * The app's one action dispatcher, and the state the shell itself draws from.
 *
 * Every tap, drop, menu choice, shortcut and TalkBack action on an item arrives at [perform]
 * with a destination and a payload, so what "Add to queue" or "Favourite" means is written
 * once. Anything that changes the user's data pushes an entry on the undo stack and says what
 * it did.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val wallet: WalletSession,
    private val libraryRepository: LibraryRepository,
    private val userData: UserDataRepository,
    val player: PlayerConnection,
    private val downloads: Downloads,
    private val artwork: ArtworkStore,
    private val refresh: RefreshCoordinator,
) : ViewModel(), ItemActions {

    private val undoStack = UndoStack()
    private val _events = MutableSharedFlow<ShellEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ShellEvent> = _events

    var dialog: ShellDialog? by mutableStateOf(null)
        private set

    override var playlists: List<PlaylistChoice> by mutableStateOf(emptyList())
        private set

    val gate: StateFlow<Gate> = combine(wallet.restored, wallet.address) { restored, address ->
        when {
            !address.isNullOrBlank() -> Gate.Open
            restored -> Gate.NeedsWallet
            else -> Gate.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.Loading)

    val identity: StateFlow<WalletIdentity?> = wallet.activeIdentity
    val isLive: StateFlow<Boolean> = wallet.isLive

    val library: StateFlow<LibraryState> = libraryRepository.state
    val playerState: StateFlow<PlayerUiState> = player.state
    val refreshState: StateFlow<RefreshState> = refresh.state

    val playlistSummaries: StateFlow<List<PlaylistSummary>> =
        userData.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Everyone followed, including uploaders with nothing live, who are not in the catalogue. */
    val follows: StateFlow<List<FollowEntity>> =
        userData.follows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val history: StateFlow<List<HistoryEntity>> =
        userData.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val prefs: StateFlow<Map<String, String>> =
        userData.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val theme: StateFlow<ThemeMode> =
        prefs.map { ThemeMode.of(it[PrefKeys.THEME]) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Uploads being fetched, unlocked or downloaded, as items show it on themselves. */
    val stages: StateFlow<Map<String, StageInfo>> =
        combine(player.working, downloads.states) { working, downloading ->
            val fromDownloads = downloading
                .filterValues { it is DownloadState.Running }
                .mapValues { StageInfo(label = "Downloading…") }
            // What staging says is more specific than "downloading", so it wins.
            fromDownloads + working.mapValues { (_, stage) -> StageInfo(stage.step.label, stage.fraction) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Bumped when a picture is extracted, so cards look again. */
    val artworkRevision: StateFlow<Int> = artwork.revision

    init {
        viewModelScope.launch {
            userData.playlists.collect { summaries ->
                playlists = summaries.map { PlaylistChoice(it.playlist.id, it.playlist.name, it.playlist.updatedAtMs) }
            }
        }
        viewModelScope.launch {
            // An undo must never write into another identity's data.
            wallet.address.map { it?.lowercase() }.distinctUntilChanged().collect {
                undoStack.clear()
                dialog = null
            }
        }
        viewModelScope.launch {
            player.notices.collect { _events.tryEmit(ShellEvent.Notice(it.text)) }
        }
        viewModelScope.launch {
            // A download that stopped says why once, as a notice, and is then forgotten: the
            // item goes back to showing "not downloaded", which is the truth.
            downloads.states.collect { states ->
                states.forEach { (recordKey, state) ->
                    val title = library.value.item(recordKey)?.title ?: "This upload"
                    when (state) {
                        DownloadState.Running -> Unit
                        DownloadState.NeedsSignature -> {
                            dialog = ShellDialog.ApproveDownloadSignature(recordKey, title)
                            downloads.dismiss(recordKey)
                        }
                        is DownloadState.Failed -> {
                            notice(listOfNotNull("$title: ${state.message.what}", state.message.todo).joinToString(" "))
                            downloads.dismiss(recordKey)
                        }
                    }
                }
            }
        }
    }

    fun artworkFor(recordKey: String): File? = artwork.find(recordKey)

    /** The refresh that happens when the app opens. Safe to call on every composition. */
    fun refreshOnOpen() = refresh.refreshOnOpen()

    fun refreshNow() = refresh.refresh()

    fun setPref(key: String, value: String?) {
        viewModelScope.launch { userData.setPref(key, value) }
    }

    fun dismissDialog() {
        dialog = null
    }

    fun confirmDownloadSignature(recordKey: String) {
        dialog = null
        downloads.download(recordKey, approveSignature = true)
    }

    fun undo() {
        viewModelScope.launch {
            val label = undoStack.undo()
            notice(if (label != null) "Undone: $label" else "Nothing to undo")
        }
    }

    fun redo() {
        viewModelScope.launch {
            val label = undoStack.redo()
            notice(if (label != null) "Redone: $label" else "Nothing to redo")
        }
    }

    fun focusSearch() {
        _events.tryEmit(ShellEvent.FocusSearch)
    }

    /** Ctrl+N, and the sidebar's "New playlist": ask for a name, then create it empty. */
    fun newPlaylist() {
        dialog = ShellDialog.NamePlaylist(suggested = "", entries = emptyList())
    }

    fun confirmNewPlaylist(name: String, entries: List<EntrySnapshot>) {
        dialog = null
        viewModelScope.launch {
            val id = userData.createPlaylist(name, entries) ?: return@launch
            val created = userData.getPlaylistEntries(id)
            val shown = name.trim().ifEmpty { UserDataRepository.DEFAULT_PLAYLIST_NAME }
            done(
                label = "Created $shown",
                undo = { userData.deletePlaylist(id) },
            )
            if (created.isEmpty()) _events.tryEmit(ShellEvent.Navigate(PlaylistRoute(id), inHomeSection = true))
        }
    }

    fun confirmRenamePlaylist(id: Long, name: String) {
        val before = (dialog as? ShellDialog.RenamePlaylist)?.current
        dialog = null
        viewModelScope.launch {
            userData.renamePlaylist(id, name)
            if (before != null) {
                done(
                    label = "Renamed to ${name.trim()}",
                    undo = { userData.renamePlaylist(id, before) },
                    redo = { userData.renamePlaylist(id, name) },
                )
            }
        }
    }

    /** The queue was emptied from its pane: say so, and let it be put back. */
    fun queueCleared(before: QueueSnapshot) {
        if (before.entries.isEmpty()) return
        done(label = "Queue cleared", undo = { player.restore(before) })
    }

    /** Moves a playlist in the sidebar. */
    fun movePlaylist(from: Int, to: Int) {
        viewModelScope.launch { userData.movePlaylist(from, to) }
    }

    /** Moves a pinned DataDAO in the sidebar. */
    fun movePin(from: Int, to: Int) {
        viewModelScope.launch { userData.movePin(from, to) }
    }

    override fun playFrom(entries: List<PlayableEntry>, startIndex: Int, label: String) {
        if (entries.isEmpty()) {
            notice("Nothing to play")
            return
        }
        val before = player.snapshot()
        // A song chosen by hand starts at its beginning; only whole files resume.
        val outcome = player.playEntries(entries, startIndex.coerceIn(0, entries.lastIndex))
        report(outcome, "Playing $label")
        if (!outcome.nothingAdded && before.entries.size > 1) {
            undoStack.push(UndoEntry("Queue replaced", undo = { player.restore(before) }))
        }
    }

    override fun clearHistory() {
        viewModelScope.launch {
            val removed = userData.clearHistory()
            if (removed.isEmpty()) {
                notice("History is already empty")
                return@launch
            }
            done(
                label = "Cleared history",
                undo = { userData.restoreHistory(removed) },
                redo = { userData.clearHistory() },
            )
        }
    }

    override fun removeDownloads(recordKeys: List<String>, label: String) {
        if (recordKeys.isEmpty()) return
        recordKeys.forEach(downloads::remove)
        notice("Removed $label from this device")
    }

    // ── The dispatcher ──────────────────────────────────────────────────────────────────────

    override fun perform(destination: Destination, payload: DragPayload, origin: Origin) {
        viewModelScope.launch {
            when (payload) {
                is DragPayload.Media -> performMedia(destination, payload, origin)
                is DragPayload.Playlist -> performPlaylist(destination, payload)
                is DragPayload.Uploader -> performUploader(destination, payload)
                is DragPayload.DataDao -> performDataDao(destination, payload)
            }
        }
    }

    private suspend fun performMedia(destination: Destination, payload: DragPayload.Media, origin: Origin) {
        val entries = payload.entries
        when (destination) {
            Destination.PlayNow, Destination.PlayNext, Destination.AddToQueue, is Destination.QueueAt ->
                queue(destination, entries, payload.lockedReason, origin, payload.label)

            Destination.Favourite -> {
                val targets = payload.favouriteTargets.map { it.toSnapshot() }
                if (targets.isEmpty()) return
                userData.addFavourites(targets)
                done(
                    label = "Added ${payload.label} to favourites",
                    undo = { targets.forEach { userData.removeFavourite(it.recordKey, it.chapterIndex) } },
                    redo = { userData.addFavourites(targets) },
                )
            }

            Destination.Unfavourite -> {
                val targets = payload.favouriteTargets.map { it.toSnapshot() }
                targets.forEach { userData.removeFavourite(it.recordKey, it.chapterIndex) }
                done(
                    label = "Removed ${payload.label} from favourites",
                    undo = { userData.addFavourites(targets) },
                    redo = { targets.forEach { userData.removeFavourite(it.recordKey, it.chapterIndex) } },
                )
            }

            Destination.Download -> {
                val locked = payload.lockedReason
                if (locked != null) {
                    notice(locked)
                } else {
                    downloads.downloadAll(payload.recordKeys)
                    notice("Downloading ${payload.label}")
                }
            }

            is Destination.Playlist -> addToPlaylist(destination.id, destination.name, entries, atIndex = null)

            is Destination.PlaylistAt -> {
                val within = origin as? Origin.Playlist
                if (within != null && within.playlistId == destination.id) {
                    movePlaylistRow(destination.id, within.entryIds, destination.index)
                } else {
                    val name = playlists.firstOrNull { it.id == destination.id }?.name ?: "playlist"
                    addToPlaylist(destination.id, name, entries, atIndex = destination.index)
                }
            }

            Destination.NewPlaylist -> {
                dialog = ShellDialog.NamePlaylist(
                    suggested = payload.whole?.title ?: entries.singleOrNull()?.albumTitle.orEmpty(),
                    entries = entries.map { it.toSnapshot() },
                )
            }

            Destination.RemoveFromQueue -> {
                val index = (origin as? Origin.Queue)?.index ?: return
                val row = player.state.value.queue.getOrNull(index) ?: return
                player.remove(row.uid)
                done(
                    label = "Removed ${row.entry.title} from the queue",
                    undo = { player.insertAt(index, listOf(row.entry)) },
                )
            }

            Destination.RemoveFromPlaylist -> {
                val rows = (origin as? Origin.Playlist)?.entryIds ?: return
                val removed = userData.removePlaylistEntries(rows)
                if (removed.isEmpty()) return
                done(
                    label = "Removed ${countOf(removed.size, "entry", "entries")} from the playlist",
                    undo = { userData.restorePlaylistEntries(removed) },
                    redo = { userData.removePlaylistEntries(removed.map { it.id }) },
                )
            }

            Destination.RemoveFromHistory -> {
                val id = (origin as? Origin.History)?.historyId ?: return
                val row = history.value.firstOrNull { it.id == id }
                userData.removeHistoryEntry(id)
                if (row != null) {
                    done(
                        label = "Removed ${row.entry.title} from history",
                        undo = { userData.restoreHistory(listOf(row)) },
                        redo = { userData.removeHistoryEntry(id) },
                    )
                }
            }

            Destination.GoToAlbum -> (payload.whole ?: entries.firstOrNull())?.let { entry ->
                val route = if (entry.kind == EntryKind.VIDEO) VideoRoute(entry.recordKey) else AlbumRoute(entry.recordKey)
                _events.tryEmit(ShellEvent.Navigate(route))
            }

            Destination.GoToUploader -> (payload.whole ?: entries.firstOrNull())?.let { entry ->
                _events.tryEmit(ShellEvent.Navigate(UploaderRoute(entry.uploaderAddress)))
            }

            Destination.GoToDataDao -> (payload.whole ?: entries.firstOrNull())?.daoKey?.let { key ->
                _events.tryEmit(ShellEvent.Navigate(DataDaoRoute(key)))
            }

            // Not offered for media; listed so a new destination cannot be forgotten here.
            Destination.Follow, Destination.Unfollow, Destination.Pin, Destination.Unpin,
            Destination.RenamePlaylist, Destination.DeletePlaylist,
            -> Unit
        }
    }

    private suspend fun performPlaylist(destination: Destination, payload: DragPayload.Playlist) {
        when (destination) {
            Destination.PlayNow, Destination.PlayNext, Destination.AddToQueue, is Destination.QueueAt -> {
                val entries = userData.getPlaylistEntries(payload.id).map { it.entry.toPlayable() }
                queue(destination, entries, lockedReason = null, origin = Origin.Library, label = payload.name)
            }

            is Destination.Playlist -> {
                val entries = userData.getPlaylistEntries(payload.id).map { it.entry.toPlayable() }
                addToPlaylist(destination.id, destination.name, entries, atIndex = null)
            }

            Destination.NewPlaylist -> {
                val entries = userData.getPlaylistEntries(payload.id).map { it.entry }
                val copyName = "${payload.name} copy"
                val id = userData.createPlaylist(copyName, entries) ?: return
                done(label = "Created $copyName", undo = { userData.deletePlaylist(id) })
            }

            Destination.RenamePlaylist -> dialog = ShellDialog.RenamePlaylist(payload.id, payload.name)

            Destination.DeletePlaylist -> {
                val deleted = userData.deletePlaylist(payload.id) ?: return
                _events.tryEmit(ShellEvent.LeavePage(PlaylistRoute(payload.id)))
                done(
                    label = "Deleted ${payload.name}",
                    undo = { userData.restorePlaylist(deleted) },
                    redo = { userData.deletePlaylist(payload.id) },
                )
            }

            else -> Unit
        }
    }

    private suspend fun performUploader(destination: Destination, payload: DragPayload.Uploader) {
        when (destination) {
            Destination.Follow -> {
                if (payload.isSelf) {
                    notice("This is you")
                    return
                }
                // Following is a local list: it is published nowhere.
                val latest = library.value.uploader(payload.address)?.latestBlock ?: 0L
                userData.follow(payload.address, payload.name, latest)
                done(
                    label = "Following ${payload.name}",
                    undo = { userData.unfollow(payload.address) },
                    redo = { userData.follow(payload.address, payload.name, latest) },
                )
            }

            Destination.Unfollow -> {
                val removed = userData.unfollow(payload.address) ?: return
                done(
                    label = "Unfollowed ${payload.name}",
                    undo = { userData.restoreFollow(removed) },
                    redo = { userData.unfollow(payload.address) },
                )
            }

            Destination.GoToUploader -> _events.tryEmit(ShellEvent.Navigate(UploaderRoute(payload.address)))

            else -> Unit
        }
    }

    private suspend fun performDataDao(destination: Destination, payload: DragPayload.DataDao) {
        when (destination) {
            Destination.Pin -> {
                if (payload.key == PUBLIC_GROUP_KEY) {
                    // Not a DataDAO: the uploads with no gate. It is always in the directory.
                    notice("Public uploads are always listed under Browse")
                    return
                }
                userData.pin(payload.key)
                done(
                    label = "Pinned ${payload.name}",
                    undo = { userData.unpin(payload.key) },
                    redo = { userData.pin(payload.key) },
                )
            }

            Destination.Unpin -> {
                userData.unpin(payload.key)
                done(
                    label = "Unpinned ${payload.name}",
                    undo = { userData.pin(payload.key) },
                    redo = { userData.unpin(payload.key) },
                )
            }

            Destination.GoToDataDao -> _events.tryEmit(ShellEvent.Navigate(DataDaoRoute(payload.key), inHomeSection = true))

            else -> Unit
        }
    }

    /** Playing and queueing, shared by media and playlists. */
    private fun queue(
        destination: Destination,
        entries: List<PlayableEntry>,
        lockedReason: String?,
        origin: Origin,
        label: String,
    ) {
        if (lockedReason != null) {
            // The item says why, in the same words its tray slot and its badge use.
            notice(lockedReason)
            return
        }
        if (entries.isEmpty()) {
            notice("Nothing to play")
            return
        }
        val fromQueue = (origin as? Origin.Queue)?.index
        if (destination is Destination.QueueAt && fromQueue != null) {
            // A queue row dropped on the queue is a move, not a second copy.
            val size = player.state.value.queue.size
            val target = moveTarget(fromQueue, destination.index, size) ?: return
            player.move(fromQueue, target)
            done(
                label = "Moved ${entries.first().title}",
                undo = { player.move(target, fromQueue) },
                redo = { player.move(fromQueue, target) },
            )
            return
        }
        when (destination) {
            Destination.PlayNow -> {
                val before = player.snapshot()
                val outcome = player.playEntries(entries)
                report(outcome, "Playing $label")
                if (!outcome.nothingAdded && before.entries.size > 1) {
                    // Replacing a queue someone built by hand is the one destructive thing
                    // "Play" does, so it can be taken back.
                    undoStack.push(UndoEntry("Queue replaced", undo = { player.restore(before) }))
                }
            }
            Destination.PlayNext -> report(player.playNext(entries), "Playing $label next")
            Destination.AddToQueue -> report(player.addToQueue(entries), "Added $label to the queue")
            is Destination.QueueAt -> report(player.insertAt(destination.index, entries), "Added $label to the queue")
            else -> Unit
        }
    }

    private fun report(outcome: QueueOutcome, done: String) {
        val text = when {
            outcome.nothingAdded && outcome.skipped > 0 -> "This is no longer available"
            outcome.nothingAdded -> "Nothing to play"
            outcome.skipped > 0 ->
                "$done. ${countOf(outcome.skipped, "unavailable entry was", "unavailable entries were")} left out"
            else -> done
        }
        notice(text)
    }

    private suspend fun addToPlaylist(id: Long, name: String, entries: List<PlayableEntry>, atIndex: Int?) {
        if (entries.isEmpty()) {
            notice("Nothing to add")
            return
        }
        // A locked or unfetched item can go into a playlist: a playlist is a list of references.
        val added = userData.addToPlaylist(id, entries.map { it.toSnapshot() }, atIndex)
        if (added.isEmpty()) return
        done(
            label = "Added ${countOf(added.size, "entry", "entries")} to $name",
            undo = { userData.removePlaylistEntries(added) },
        )
    }

    private suspend fun movePlaylistRow(playlistId: Long, entryIds: List<Long>, insertBefore: Int) {
        val moving = entryIds.firstOrNull() ?: return
        val order = userData.getPlaylistEntries(playlistId).map { it.id }
        val from = order.indexOf(moving)
        val target = moveTarget(from, insertBefore, order.size) ?: return
        userData.movePlaylistEntry(playlistId, from, target)
        done(
            label = "Moved in playlist",
            undo = { userData.movePlaylistEntry(playlistId, target, from) },
            redo = { userData.movePlaylistEntry(playlistId, from, target) },
        )
    }

    private fun done(label: String, undo: suspend () -> Unit, redo: (suspend () -> Unit)? = null) {
        undoStack.push(UndoEntry(label, undo, redo))
        _events.tryEmit(ShellEvent.Notice(label, undo = true))
    }

    private fun notice(text: String) {
        _events.tryEmit(ShellEvent.Notice(text))
    }
}
