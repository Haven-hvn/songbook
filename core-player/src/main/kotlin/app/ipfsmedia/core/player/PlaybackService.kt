package app.ipfsmedia.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.UserMessage
import app.ipfsmedia.core.library.describeError
import app.ipfsmedia.core.library.songEntriesOf
import app.ipfsmedia.core.library.toSnapshot
import app.ipfsmedia.core.userdata.UserDataRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import haven.mobile.core.cache.SpoolPins
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Playback that does not depend on the window: one service, one player, one session
 * (docs/PLAN.md §10). The window talks to it through a `MediaController` ([PlayerConnection]);
 * the system's media controls, media keys and Bluetooth talk to the same session.
 *
 * What this service owns beyond holding the player:
 *
 *  - **Opening items.** Items are `ipfsmedia://` addresses; [StagingResolver] turns one into a
 *    file when the player opens it.
 *  - **Stopping for a signature.** If an item needs the wallet, the queue stops on that row and
 *    says so ([Blocked.NeedsSignature]). It never continues past it and never prompts by itself.
 *  - **Stepping over what cannot play**, with a notice, and stopping with the reason when there
 *    is nothing left to step to.
 *  - **Songs from a whole album.** An album that has never been fetched is queued as one row,
 *    because its song list is inside the file. When the file arrives, the row becomes its songs.
 *  - **The saved queue**, per identity, and emptying the player when the identity changes.
 *  - **History and resume positions.** Both stay on the device (docs/PLAN.md §2).
 *  - **Telling the spool what not to trim.**
 *
 * Media3's defaults are kept for who may connect: this app and trusted system controllers get
 * playback commands; any other app gets read access only.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    /** Hilt entry point, read from the application: a service base class rewrite is not needed. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun stager(): MediaStager
        fun queueStore(): QueueStore
        fun queueIds(): QueueIds
        fun status(): PlaybackStatus
        fun host(): PlaybackHost
        fun artwork(): ArtworkStore
        fun spoolPins(): SpoolPins
        fun walletSession(): WalletSession
        fun userData(): UserDataRepository
    }

    private lateinit var deps: Dependencies
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The identity whose queue the player holds. */
    private var owner: String? = null

    /** The player's rows, refreshed when its list changes. Indices match the player's. */
    private var rows: List<QueueRow> = emptyList()

    private var consecutiveErrors = 0
    private var retriedMissingFile = false
    private var saveJob: Job? = null
    private var tickerJob: Job? = null

    /** What has been heard of the current row, for history. */
    private var listeningUid = NO_ROW
    private var listenedMs = 0L
    private var recorded = false

    override fun onCreate() {
        super.onCreate()
        deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)

        val dataSources = ResolvingDataSource.Factory(FileDataSource.Factory(), StagingResolver(deps.stager()))
        // Index seeking is the fallback for an MP3 with no usable seek table: a song is a seek
        // into its album's file, so "not seekable" would mean "cannot start".
        val extractors = DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
        val sources = DefaultMediaSourceFactory(dataSources, extractors)
            .setLoadErrorHandlingPolicy(StagingAwareRetryPolicy())

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // The next album is fetched while this one plays, possibly with the lid closed.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)

        val builder = MediaSession.Builder(this, player).setCallback(callback)
        sessionActivity()?.let(builder::setSessionActivity)
        session = builder.build()
        deps.host().attach(player)

        scope.launch {
            deps.walletSession().address
                .map { it?.trim()?.lowercase()?.takeIf { address -> address.isNotEmpty() } }
                .distinctUntilChanged()
                .collect { onIdentity(it) }
        }
        scope.launch { deps.stager().staged.collect { onStaged(it) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveNow()
        // Media3's default: keep going if something is playing, otherwise stop the service.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveNow()
        scope.cancel()
        deps.host().detach(player)
        deps.spoolPins().set(emptyList())
        session?.release()
        session = null
        player.removeListener(listener)
        player.release()
        super.onDestroy()
    }

    // ── Session ─────────────────────────────────────────────────────────────────────────────

    private val callback = object : MediaSession.Callback {
        /**
         * Everything added to the player passes through here. Items are rebuilt from the row
         * they carry, so the address and the clip are always this service's own, whatever the
         * sender set; anything that is not one of this app's rows is dropped.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNull { QueueItems.rowOf(it) }.map { itemFor(it) }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        /** "Resume" from the system's media controls after the app was closed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (player.mediaItemCount > 0) {
                val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(items, player.currentMediaItemIndex, player.currentPosition),
                )
            }
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                // The wallet session restores itself shortly after the process starts.
                val address = withTimeoutOrNull(RESUME_WAIT_MS) {
                    deps.walletSession().address.first { !it.isNullOrBlank() }
                }?.trim()?.lowercase()
                val saved = address?.let { load(it) }
                if (saved == null || saved.entries.isEmpty()) {
                    future.setException(UnsupportedOperationException("nothing to resume"))
                } else {
                    val items = saved.entries.map { itemFor(QueueRow(deps.queueIds().next(), it)) }
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            items,
                            saved.index.coerceIn(0, items.lastIndex),
                            saved.positionMs,
                        ),
                    )
                }
            }
            return future
        }
    }

    private fun itemFor(row: QueueRow): MediaItem =
        QueueItems.toMediaItem(row, deps.artwork().slotFor(row.entry.recordKey))

    /** Opens the app's own window, with a hint to show what is playing. */
    private fun sessionActivity(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.putExtra(EXTRA_OPEN_NOW_PLAYING, true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    // ── Player events ───────────────────────────────────────────────────────────────────────

    private val listener = object : Player.Listener {
        /** Kept in the per-event callback, which runs first, so every later callback sees current rows. */
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            rows = player.queueRows()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                updatePins()
            }
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_VOLUME_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                scheduleSave()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                val current = currentRow()
                val blocked = deps.status().blocked.value
                if (blocked != null && blocked.recordKey != current?.entry?.recordKey) deps.status().clear()
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_READY) {
                // Something is actually playing: earlier failures no longer count against it.
                consecutiveErrors = 0
                retriedMissingFile = false
                deps.status().clear()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) startTicker() else stopTicker()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    private fun currentRow(): QueueRow? = rows.getOrNull(player.currentMediaItemIndex)

    /**
     * A row could not be played.
     *
     * Media3 reports a failure to open the *next* item while still positioned on the previous
     * one, so the failed row is found by the record the staging error names, not by the current
     * index.
     */
    private fun handleError(error: PlaybackException) {
        val entries = rows.map { it.entry }
        val current = player.currentMediaItemIndex
        val staging = error.stagingFailure()
        val failedIndex = failedRow(entries, player.playOrderFrom(current), staging?.recordKey) ?: return
        val failed = rows.getOrNull(failedIndex) ?: return
        val status = deps.status()

        if (staging?.code == StagingException.SIGNATURE_REQUIRED) {
            if (failedIndex != current) player.seekToDefaultPosition(failedIndex)
            player.pause()
            status.block(Blocked.NeedsSignature(failed.entry.recordKey, failed.entry.title, failed.entry.daoKey))
            return
        }

        if (staging == null && error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND && !retriedMissingFile) {
            // The staged file was trimmed or cleared while it sat in the queue. Fetch it again, once.
            retriedMissingFile = true
            deps.stager().forget(failed.entry.recordKey)
            player.prepare()
            return
        }

        val message = staging?.userMessage ?: describePlaybackError(error)
        Timber.tag(TAG).w(error, "cannot play %s (%s)", failed.entry.id, message.code)
        consecutiveErrors++
        val next = skipTarget(entries, player.playOrderFrom(failedIndex))
        if (next != null && consecutiveErrors <= MAX_CONSECUTIVE_SKIPS) {
            status.notify(PlayerNotice("Skipped “${failed.entry.title}”. ${message.what}"))
            player.seekToDefaultPosition(next)
            player.prepare()
        } else {
            if (failedIndex != current) player.seekToDefaultPosition(failedIndex)
            player.pause()
            status.block(Blocked.Failed(failed.entry.recordKey, failed.entry.title, message))
        }
    }

    private fun describePlaybackError(error: PlaybackException): UserMessage = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> describeError("PLAYBACK_FILE_MISSING")
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> describeError("PLAYBACK_DECRYPT_FAILED")
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> describeError("PLAYBACK_CODEC_UNSUPPORTED")
        else -> describeError("INTERNAL", error.message)
    }

    /**
     * An album's file has arrived and its song list has been read. Whole-album rows for it
     * become their songs, so next, previous and shuffle work per song from then on. The row
     * that is playing is only replaced if it has barely started; an album already being listened
     * to is left alone rather than restarted.
     */
    private fun onStaged(event: Staged) {
        if (event.chapters.isEmpty()) return
        for (index in rows.indices.reversed()) {
            val row = rows.getOrNull(index) ?: continue
            val entry = row.entry
            if (entry.recordKey != event.recordKey || entry.kind != EntryKind.ALBUM || !entry.isWholeFile) continue
            val isCurrent = index == player.currentMediaItemIndex
            if (isCurrent && player.currentPosition > EXPAND_CURRENT_UNTIL_MS) continue
            val songs = songEntriesOf(entry.copy(pieceCid = event.pieceCid), event.chapters, event.durationMs ?: entry.durationMs)
            if (songs.isEmpty()) continue
            val items = songs.map { itemFor(QueueRow(deps.queueIds().next(), it)) }
            player.replaceMediaItems(index, index + 1, items)
            // With shuffle on, the player would move to some other row when its current one is
            // replaced. Nothing has been heard yet, so start the album at its first song.
            if (isCurrent) player.seekToDefaultPosition(index)
        }
    }

    private fun updatePins() {
        val order = player.playOrderFrom(player.currentMediaItemIndex, PIN_LOOKAHEAD_ROWS)
        deps.spoolPins().set(piecesToPin(rows.map { it.entry }, order))
    }

    // ── History and resume ──────────────────────────────────────────────────────────────────

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
        scope.launch { rememberPosition() }
    }

    private suspend fun tick() {
        val row = currentRow() ?: return
        if (!player.isPlaying) return
        if (listeningUid != row.uid) {
            listeningUid = row.uid
            listenedMs = 0L
            recorded = false
        }
        listenedMs += TICK_MS
        if (!recorded && listenedMs >= historyThresholdMs(currentDurationMs())) {
            recorded = true
            // On this device only. Nothing about what was played is sent anywhere.
            runCatching { deps.userData().recordPlay(row.entry.toSnapshot()) }
        }
        rememberPosition()
        scheduleSave()
    }

    private suspend fun rememberPosition() {
        val row = currentRow() ?: return
        val duration = currentDurationMs()
        if (!remembersPosition(row.entry, duration) || duration == null) return
        val position = player.currentPosition
        runCatching {
            deps.userData().saveResumePosition(row.entry.recordKey, PlayableEntry.WHOLE_FILE, position, duration)
        }
    }

    private fun currentDurationMs(): Long? = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }

    // ── The saved queue, and identities ─────────────────────────────────────────────────────

    /**
     * The active identity changed (or became known). Whatever was playing belonged to the
     * previous one: stop, empty the player, and bring back the new identity's own queue, paused
     * and not prepared, so nothing is fetched until the user presses play.
     */
    private suspend fun onIdentity(address: String?) {
        if (address == owner) return
        saveJob?.cancel()
        if (owner != null) {
            player.stop()
            player.clearMediaItems()
            deps.status().clear()
        }
        owner = address
        if (address == null) return
        val saved = load(address) ?: return
        // The window may have started something while the file was being read.
        if (owner != address || player.mediaItemCount > 0 || saved.entries.isEmpty()) return
        val items = saved.entries.map { itemFor(QueueRow(deps.queueIds().next(), it)) }
        player.setMediaItems(items, saved.index.coerceIn(0, items.lastIndex), saved.positionMs)
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeatMode
        player.volume = saved.volume
    }

    private suspend fun load(address: String): SavedQueue? =
        withContext(Dispatchers.IO) { runCatching { deps.queueStore().load(address) }.getOrNull() }

    private fun snapshot(): SavedQueue = SavedQueue(
        entries = rows.map { it.entry },
        index = player.currentMediaItemIndex.coerceAtLeast(0),
        positionMs = player.currentPosition.coerceAtLeast(0L),
        shuffle = player.shuffleModeEnabled,
        repeatMode = player.repeatMode,
        volume = player.volume,
    )

    private fun scheduleSave() {
        val address = owner ?: return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val saved = snapshot()
            withContext(Dispatchers.IO) { deps.queueStore().save(address, saved) }
        }
    }

    /** Synchronous, for the two moments the service may not get another turn. */
    private fun saveNow() {
        val address = owner ?: return
        saveJob?.cancel()
        runCatching { deps.queueStore().save(address, snapshot()) }
    }

    companion object {
        /** Set on the intent that opens the app from the media notification. */
        const val EXTRA_OPEN_NOW_PLAYING = "app.ipfsmedia.extra.OPEN_NOW_PLAYING"

        private const val TAG = "Playback"
        private const val NO_ROW = Long.MIN_VALUE
        private const val TICK_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 800L
        private const val RESUME_WAIT_MS = 5_000L
        private const val MAX_CONSECUTIVE_SKIPS = 3
        private const val PIN_LOOKAHEAD_ROWS = 200
        private const val EXPAND_CURRENT_UNTIL_MS = 3_000L
    }
}
