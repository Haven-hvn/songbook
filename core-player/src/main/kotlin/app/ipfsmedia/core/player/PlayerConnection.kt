package app.ipfsmedia.core.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.ipfsmedia.core.library.AppScope
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.userdata.UserDataRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The window's side of playback: a Media3 controller bound to [PlaybackService], shown to the
 * interface as plain state and plain commands.
 *
 * The queue is not kept here. It is read from the player ([PlayerUiState.queue]), so what the
 * window shows is what will play, including changes that did not come from the window (an album
 * turning into its songs, a row stepped over).
 *
 * Commands may be called from any thread and before the service has been reached; they run in
 * order on the main thread once it has. Anything that reports a result ([QueueOutcome], a removed
 * row) is computed from the current state before the command is sent.
 *
 * The window binds in `onStart` and lets go in `onStop` ([connect], [disconnect]). Media3 can
 * only stop the service once every controller has let go, and playback in the background does
 * not need one.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stager: MediaStager,
    private val status: PlaybackStatus,
    private val library: LibraryRepository,
    private val userData: UserDataRepository,
    private val ids: QueueIds,
    private val artwork: ArtworkStore,
    @AppScope appScope: CoroutineScope,
) {
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var pendingController: ListenableFuture<MediaController>? = null
    private val controller = MutableStateFlow<MediaController?>(null)
    private val base = MutableStateFlow(PlayerUiState())
    private val position = MutableStateFlow(0L)
    private var positionJob: Job? = null

    val state: StateFlow<PlayerUiState> =
        combine(base, stager.states, status.blocked) { player, stages, blocked ->
            val recordKey = player.current?.entry?.recordKey
            player.copy(
                stage = recordKey?.let(stages::get),
                blocked = blocked?.takeIf { it.recordKey == recordKey },
            )
        }.stateIn(appScope, SharingStarted.Eagerly, PlayerUiState())

    /** Position within the current row, refreshed a few times a second while playing. */
    val positionMs: StateFlow<Long> = position

    /** The player itself, for drawing video. Null while the service has not been reached. */
    val player: StateFlow<Player?> get() = controller

    /**
     * Per record key: uploads being fetched or unlocked right now, for album and video pages and
     * for queue rows. One entry per upload, however many of its songs are queued.
     */
    val working: StateFlow<Map<String, StageState.Working>> =
        stager.states
            .map { states ->
                states.mapNotNull { (key, state) -> (state as? StageState.Working)?.let { key to it } }.toMap()
            }
            .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    /** One-line reports of things that happened without the user: a row that was stepped over. */
    val notices: SharedFlow<PlayerNotice> = status.notices

    // ── Binding ─────────────────────────────────────────────────────────────────────────────

    /** Binds to the playback service, starting it if needed. Safe to call again. */
    fun connect() {
        main.launch { bind() }
    }

    /** Lets go of the service. Playback carries on without the window. */
    fun disconnect() {
        main.launch { unbind() }
    }

    private fun bind() {
        if (pendingController != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        // The service went away (stopped, or the session was released).
                        if (this@PlayerConnection.controller.value === controller) unbind()
                    }
                },
            )
            .buildAsync()
        pendingController = future
        future.addListener(
            {
                if (pendingController !== future) return@addListener
                val connected = runCatching { future.get() }
                    .onFailure { Timber.w(it, "Player: could not reach the playback service") }
                    .getOrNull()
                if (connected == null) {
                    pendingController = null
                    return@addListener
                }
                connected.addListener(listener)
                controller.value = connected
                publish(connected, rowsChanged = true)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun unbind() {
        val future = pendingController ?: return
        pendingController = null
        controller.value?.removeListener(listener)
        controller.value = null
        positionJob?.cancel()
        positionJob = null
        MediaController.releaseFuture(future)
        base.update { it.copy(connected = false, isPlaying = false, isBuffering = false) }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player, rowsChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    private fun publish(player: Player, rowsChanged: Boolean) {
        val previous = base.value
        val rows = if (rowsChanged || !previous.connected) player.queueRows() else previous.queue
        val index = if (rows.isEmpty()) -1 else player.currentMediaItemIndex
        val size = player.videoSize
        base.value = PlayerUiState(
            connected = true,
            queue = rows,
            currentIndex = index,
            playOrder = player.playOrderFrom(index, PLAY_ORDER_LIMIT),
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            volume = player.volume,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            videoAspectRatio = if (size.width > 0 && size.height > 0) {
                size.width * size.pixelWidthHeightRatio / size.height
            } else {
                null
            },
        )
        position.value = player.currentPosition.coerceAtLeast(0L)
        if (player.isPlaying) startPositionUpdates() else stopPositionUpdates()
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = main.launch {
            while (isActive) {
                controller.value?.let { position.value = it.currentPosition.coerceAtLeast(0L) }
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    /** Runs [block] on the main thread once the service has been reached; dropped if it never is. */
    private fun command(block: suspend (MediaController) -> Unit) {
        main.launch {
            bind()
            val reached = controller.value
                ?: withTimeoutOrNull(CONNECT_TIMEOUT_MS) { controller.filterNotNull().first() }
                ?: return@launch
            block(reached)
        }
    }

    private fun itemsFor(entries: List<PlayableEntry>): List<MediaItem> = entries.map { entry ->
        QueueItems.toMediaItem(QueueRow(ids.next(), entry), artwork.slotFor(entry.recordKey))
    }

    private fun admit(entries: List<PlayableEntry>, startIndex: Int = 0): Admission =
        admitEntries(entries, startIndex, library.state.value)

    // ── Queueing ────────────────────────────────────────────────────────────────────────────

    /**
     * Replaces the queue with [entries] and plays from [startIndex]. This is "Play now", the
     * Play button on a page, and choosing a row in a list (which plays that list from that row).
     *
     * A long item that is played as one piece (a video, an album with no songs) continues from
     * where it was left when [continueFromSaved] is set.
     */
    fun playEntries(entries: List<PlayableEntry>, startIndex: Int = 0, continueFromSaved: Boolean = true): QueueOutcome {
        val admission = admit(entries, startIndex)
        if (admission.entries.isEmpty()) return QueueOutcome(0, admission.skipped)
        val items = itemsFor(admission.entries)
        val first = admission.entries[admission.startIndex]
        command { player ->
            val startMs = if (continueFromSaved && first.isWholeFile) {
                runCatching { userData.resumePosition(first.recordKey, PlayableEntry.WHOLE_FILE) }.getOrNull()?.positionMs
            } else {
                null
            }
            status.clear()
            player.setMediaItems(items, admission.startIndex, startMs ?: C.TIME_UNSET)
            player.prepare()
            player.play()
        }
        return QueueOutcome(admission.entries.size, admission.skipped)
    }

    /** Inserts [entries] straight after the current row. */
    fun playNext(entries: List<PlayableEntry>): QueueOutcome {
        val admission = admit(entries)
        if (admission.entries.isEmpty()) return QueueOutcome(0, admission.skipped)
        val items = itemsFor(admission.entries)
        command { player ->
            val at = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
            player.addMediaItems(at.coerceAtMost(player.mediaItemCount), items)
        }
        return QueueOutcome(admission.entries.size, admission.skipped)
    }

    /** Appends [entries] to the end of the queue. */
    fun addToQueue(entries: List<PlayableEntry>): QueueOutcome {
        val admission = admit(entries)
        if (admission.entries.isEmpty()) return QueueOutcome(0, admission.skipped)
        val items = itemsFor(admission.entries)
        command { player -> player.addMediaItems(items) }
        return QueueOutcome(admission.entries.size, admission.skipped)
    }

    /** Inserts [entries] so the first lands at row [index]: an item carried into the open queue. */
    fun insertAt(index: Int, entries: List<PlayableEntry>): QueueOutcome {
        val admission = admit(entries)
        if (admission.entries.isEmpty()) return QueueOutcome(0, admission.skipped)
        val items = itemsFor(admission.entries)
        command { player -> player.addMediaItems(index.coerceIn(0, player.mediaItemCount), items) }
        return QueueOutcome(admission.entries.size, admission.skipped)
    }

    /** Moves the row at [from] to [to] (both in list order). */
    fun move(from: Int, to: Int) {
        command { player ->
            val count = player.mediaItemCount
            if (from in 0 until count && to in 0 until count && from != to) player.moveMediaItem(from, to)
        }
    }

    /** Removes the row with [uid] and returns it with its index, for Undo; null if it is not there. */
    fun remove(uid: Long): Pair<Int, QueueRow>? {
        val index = state.value.queue.indexOfFirst { it.uid == uid }
        if (index < 0) return null
        val row = state.value.queue[index]
        command { player ->
            // Find it again: the list may have changed since the caller looked.
            val at = player.queueRows().indexOfFirst { it.uid == uid }
            if (at >= 0) player.removeMediaItem(at)
        }
        return index to row
    }

    /** Empties the queue and returns what it held, for Undo. */
    fun clear(): QueueSnapshot {
        val before = snapshot()
        command { player ->
            player.stop()
            player.clearMediaItems()
            status.clear()
        }
        return before
    }

    fun snapshot(): QueueSnapshot {
        val now = state.value
        return QueueSnapshot(
            entries = now.queue.map { it.entry },
            index = now.currentIndex.coerceAtLeast(0),
            positionMs = position.value,
            wasPlaying = now.playWhenReady,
        )
    }

    /** Puts a queue back as it was. Entries are restored as they were, without being re-checked. */
    fun restore(snapshot: QueueSnapshot) {
        val items = itemsFor(snapshot.entries)
        command { player ->
            status.clear()
            if (items.isEmpty()) {
                player.stop()
                player.clearMediaItems()
                return@command
            }
            player.setMediaItems(items, snapshot.index.coerceIn(0, items.lastIndex), snapshot.positionMs)
            if (snapshot.wasPlaying) {
                player.prepare()
                player.play()
            }
        }
    }

    // ── Transport ───────────────────────────────────────────────────────────────────────────

    fun togglePlay() {
        command { player ->
            if (player.playWhenReady && player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                player.pause()
            } else {
                start(player)
            }
        }
    }

    fun resume() {
        command { player -> start(player) }
    }

    fun pause() {
        command { player -> player.pause() }
    }

    private fun start(player: Player) {
        if (player.mediaItemCount == 0) return
        when (player.playbackState) {
            // A restored queue, or one that stopped on an error, has not been prepared.
            Player.STATE_IDLE -> player.prepare()
            // The queue ran out. Carry on if something was added since, otherwise start over.
            Player.STATE_ENDED ->
                if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekToDefaultPosition(0)
            else -> Unit
        }
        player.play()
    }

    /** Jumps to the row with [uid] and plays it. */
    fun skipTo(uid: Long) {
        command { player ->
            val at = player.queueRows().indexOfFirst { it.uid == uid }
            if (at < 0) return@command
            status.clear()
            player.seekToDefaultPosition(at)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun next() {
        command { player ->
            if (!player.hasNextMediaItem()) return@command
            status.clear()
            player.seekToNextMediaItem()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        }
    }

    /** Back to the start of the row, or to the previous row when already near its start. */
    fun previous() {
        command { player ->
            status.clear()
            player.seekToPrevious()
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        }
    }

    fun seekTo(positionMs: Long) {
        position.value = positionMs.coerceAtLeast(0L)
        command { player -> player.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        command { player ->
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
            player.seekTo(if (duration != null) target.coerceAtMost(duration) else target)
        }
    }

    fun setShuffle(enabled: Boolean) {
        command { player -> player.shuffleModeEnabled = enabled }
    }

    fun toggleShuffle() {
        command { player -> player.shuffleModeEnabled = !player.shuffleModeEnabled }
    }

    /** Off, then the whole queue, then the one row, then off again. */
    fun cycleRepeat() {
        command { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    /** The app's own volume, 0 to 1, on top of the system's. */
    fun setVolume(volume: Float) {
        command { player -> player.volume = volume.coerceIn(0f, 1f) }
    }

    fun changeVolume(delta: Float) {
        command { player -> player.volume = (player.volume + delta).coerceIn(0f, 1f) }
    }

    // ── When the queue is waiting for the user ──────────────────────────────────────────────

    /**
     * The user read why a signature is needed and agreed. The current row is tried again, and
     * this time staging may go to the wallet, once.
     */
    fun approveSignature() {
        val blocked = status.blocked.value as? Blocked.NeedsSignature ?: return
        stager.approveSignature(blocked.recordKey)
        status.clear()
        command { player ->
            player.prepare()
            player.play()
        }
    }

    /** Tries the row that failed again. */
    fun retry() {
        val blocked = status.blocked.value ?: return
        stager.clearFailure(blocked.recordKey)
        status.clear()
        command { player ->
            player.prepare()
            player.play()
        }
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 250L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val PLAY_ORDER_LIMIT = 2_000
    }
}
