package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.AppScope
import app.ipfsmedia.core.library.AudioChapter
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.library.readId3Chapters
import cloud.filecoin.foc.cache.PieceRef
import cloud.filecoin.foc.cache.PieceTransform
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.PlaintextSpool
import haven.mobile.core.cache.mirror.CachedChapter
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.crypto.AesKeyCache
import haven.mobile.core.crypto.HavenCipher
import haven.mobile.core.crypto.decryptOnStore
import haven.mobile.core.crypto.stripCarContainer
import haven.mobile.core.domain.KeyGateVerdict
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.domain.gateKeyAcquisition
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.haven.aol.hasSessionKey
import haven.mobile.core.haven.aol.walletPieceKey
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What the player, or Download, asks to have made playable. */
data class StageRequest(
    val recordKey: String,
    /**
     * For a song: the file its start and end times were read from. If the record now points at a
     * different file the album was uploaded again, and staging refuses rather than play the
     * saved times against the wrong audio.
     */
    val expectedPieceCid: String? = null,
)

/** Published when an upload's file has become playable, with what was read from it. */
data class Staged(
    val recordKey: String,
    val pieceCid: String,
    /** An album's songs. Empty for a video, and for an album with no chapters. */
    val chapters: List<AudioChapter>,
    val durationMs: Long?,
)

/**
 * The one path by which anything becomes playable (docs/PLAN.md §9), ported from haven-mobile's
 * `WatchViewModel`:
 *
 * ```
 * spool hit ──────────────────────────────────────────────────────────▶ file
 * availability check ─▶ key (cached, or one wallet signature + canister call)
 *   ─▶ foc-cache stream ─▶ strip CAR container ─▶ AES-GCM chunked decrypt ─▶ spool ─▶ file
 * ```
 *
 * What differs from the original, and why:
 *
 *  - It is not a view model. The original staged the one item a screen had open. Here the
 *    *player* asks, from its loading thread, for whatever the queue reaches next, so staging is a
 *    process-wide service keyed by record, and concurrent requests for one record share one run.
 *  - It never sends anything to the wallet on its own. haven-mobile stops at a disclosure before
 *    the first signature; so does this, but as a refusal ([StagingException.SIGNATURE_REQUIRED])
 *    the player turns into "stopped here, sign to continue". A queue that advanced in the
 *    background must not be able to raise a signing prompt nobody is looking at.
 *  - A record the last refresh found the wallet is not a member of is refused before any key
 *    request, unless its content is already on the device.
 *  - Uploads with no gate also have their CAR container stripped. Retrieval serves the stored
 *    CAR whether or not the content inside was encrypted; the original passed ungated bytes
 *    through as fetched. Non-CAR bytes are unaffected by the strip.
 *  - Artwork goes to a per-identity store ([ArtworkStore]).
 */
@Singleton
class MediaStager @Inject constructor(
    private val havenAol: HavenAol,
    private val havenCache: HavenCache,
    private val havenCipher: HavenCipher,
    private val plaintextSpool: PlaintextSpool,
    private val aesKeyCache: AesKeyCache,
    private val mediaRepository: MediaRepository,
    private val libraryRepository: LibraryRepository,
    private val walletSession: WalletSession,
    private val artwork: ArtworkStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, StageState>>(emptyMap())

    /** Per record key. A record with nothing in progress and nothing wrong is absent. */
    val states: StateFlow<Map<String, StageState>> = _states

    private val _staged = MutableSharedFlow<Staged>(extraBufferCapacity = 32)

    /** One event each time a file is confirmed playable through the full path (not the fast path). */
    val staged: SharedFlow<Staged> = _staged

    private class Task(val job: Deferred<File>) {
        var waiters = 0
    }

    private class Ready(val wallet: String, val pieceCid: String, val file: File)

    private class RecentFailure(val error: StagingException, val atMillis: Long)

    /** Guarded by itself. */
    private val tasks = HashMap<String, Task>()
    private val ready = ConcurrentHashMap<String, Ready>()
    private val consents: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val recentFailures = ConcurrentHashMap<String, RecentFailure>()

    /**
     * Returns the playable file for [request], fetching and unlocking it if needed.
     *
     * Safe to call from many places at once: requests for one record wait on one run. The run is
     * cancelled when the last caller waiting on it goes away (the user skipped on), so an
     * abandoned download does not keep going behind the next one.
     *
     * @throws StagingException with a stable code for every failure.
     */
    suspend fun stage(request: StageRequest): File {
        val key = request.recordKey
        fastPath(request)?.let { return it }
        recentFailures[key]?.let { recent ->
            // The player asks again for every queued song of one album; one answer is enough.
            if (System.currentTimeMillis() - recent.atMillis < FAILURE_MEMO_MS) throw recent.error
            recentFailures.remove(key, recent)
        }
        val task = synchronized(tasks) {
            val task = tasks[key] ?: Task(
                scope.async(Dispatchers.Default, start = CoroutineStart.LAZY) { run(request) },
            ).also { tasks[key] = it }
            task.waiters++
            task
        }
        try {
            return task.job.await()
        } finally {
            synchronized(tasks) {
                task.waiters--
                if (task.job.isCompleted || task.waiters <= 0) {
                    if (tasks[key] === task) tasks.remove(key)
                    if (!task.job.isCompleted) task.job.cancel()
                }
            }
        }
    }

    /**
     * Whether opening [recordKey] would have to ask the wallet for a signature: it is gated, and
     * neither its content nor a key for it is already held. Costs no network call.
     */
    suspend fun needsSignature(recordKey: String): Boolean {
        val media = libraryRepository.mediaItem(recordKey) ?: return false
        val wallet = walletSession.address.value ?: return false
        return media.isEncrypted && !hasUnlockKey(media, wallet)
    }

    /**
     * The user read the explanation and agreed to sign for [recordKey]. Good for one attempt:
     * the next staging of this record may go to the wallet, and no other.
     */
    fun approveSignature(recordKey: String) {
        consents += recordKey
        clearFailure(recordKey)
    }

    /** Forgets a failure or a pending signature request, so the next attempt starts clean. */
    fun clearFailure(recordKey: String) {
        recentFailures.remove(recordKey)
        _states.update { states ->
            if (states[recordKey] is StageState.Working) states else states - recordKey
        }
    }

    /** Drops the remembered file for [recordKey]; used when its download is removed. */
    fun forget(recordKey: String) {
        ready.remove(recordKey)
        clearFailure(recordKey)
    }

    /** Drops everything remembered. Called when the identity leaves. */
    fun forgetAll() {
        synchronized(tasks) {
            tasks.values.forEach { it.job.cancel() }
            tasks.clear()
        }
        ready.clear()
        consents.clear()
        recentFailures.clear()
        _states.value = emptyMap()
    }

    // ── The run ─────────────────────────────────────────────────────────────────────────────

    private fun fastPath(request: StageRequest): File? {
        val hit = ready[request.recordKey] ?: return null
        val wallet = walletSession.address.value?.lowercase()
        if (hit.wallet != wallet || !hit.file.isFile || hit.file.length() == 0L) {
            ready.remove(request.recordKey, hit)
            return null
        }
        if (request.expectedPieceCid != null && request.expectedPieceCid != hit.pieceCid) {
            throw StagingException(request.recordKey, StagingException.SONG_CHANGED)
        }
        return hit.file
    }

    private suspend fun run(request: StageRequest): File {
        val key = request.recordKey
        try {
            val file = pipeline(request)
            _states.update { it - key }
            return file
        } catch (e: CancellationException) {
            _states.update { states -> if (states[key] is StageState.Working) states - key else states }
            throw e
        } catch (e: Throwable) {
            val failure = e.toStagingException(key)
            if (failure.code == StagingException.SIGNATURE_REQUIRED) {
                val daoKey = libraryRepository.state.value.item(key)?.daoKey
                _states.update { it + (key to StageState.NeedsSignature(daoKey)) }
            } else {
                Timber.tag(TAG).w(e, "staging failed id=%s code=%s", key, failure.code)
                recentFailures[key] = RecentFailure(failure, System.currentTimeMillis())
                _states.update { it + (key to StageState.Failed(failure.userMessage)) }
            }
            throw failure
        }
    }

    private suspend fun pipeline(request: StageRequest): File {
        val key = request.recordKey
        val wallet = walletSession.address.value
            ?: throw StagingException(key, "WALLET_NOT_CONNECTED")
        val media = awaitMedia(key)
            ?: throw StagingException(key, StagingException.RECORD_GONE)
        val piece = media.pieceRef
            ?: throw StagingException(key, "NO_PIECE_REF")
        if (request.expectedPieceCid != null && request.expectedPieceCid != piece.pieceCid) {
            throw StagingException(key, StagingException.SONG_CHANGED)
        }

        // Already staged by an earlier open. No key, no network.
        plaintextSpool.find(piece.pieceCid)?.let { return finish(media, piece, wallet, it, fetched = false) }

        val unlockedMode = havenCache.storesUnlocked()
        var signing = false
        if (media.isEncrypted && !hasUnlockKey(media, wallet)) {
            // Nothing local opens this, so a key has to be asked for. Three reasons not to, each
            // cheaper than the request and each with a clearer answer than its refusal.
            if (libraryRepository.state.value.item(key)?.access == AccessState.NOT_MEMBER) {
                throw StagingException(key, StagingException.NOT_A_MEMBER)
            }
            publish(key, StageState.Working(StageStep.CHECKING_ACCESS))
            (havenAol.precheck(media) as? HavenError.MarketCapNotReached)?.let {
                libraryRepository.markNotOpenYet(key)
                throw StagingException(key, StagingException.NOT_OPEN_YET, null, it)
            }
            // Never ask for a signature for content no provider can serve.
            if (!havenCache.isAvailable(piece)) {
                throw StagingException(key, StagingException.PIECE_UNAVAILABLE)
            }
            if (!consents.remove(key)) {
                throw StagingException(key, StagingException.SIGNATURE_REQUIRED)
            }
            signing = true
        }

        val file = if (unlockedMode) {
            stageUnlocked(media, piece, wallet, signing)
        } else {
            stageToSpool(media, piece, wallet, signing)
        }
        return finish(media, piece, wallet, file, fetched = true)
    }

    /** The record as mirrored. Waits briefly for the mirror when the process has only just started. */
    private suspend fun awaitMedia(recordKey: String): MediaItem? {
        libraryRepository.mediaItem(recordKey)?.let { return it }
        if (libraryRepository.state.value.ready) return null
        val state = withTimeoutOrNull(LIBRARY_WAIT_MS) { libraryRepository.state.first { it.ready } }
        return state?.item(recordKey)?.media
    }

    /** Staged file, a decrypted copy, or a key already held: any of them means no signature. */
    private suspend fun hasUnlockKey(media: MediaItem, wallet: String): Boolean {
        val cid = media.pieceRef?.pieceCid ?: return false
        if (plaintextSpool.find(cid) != null) return true
        if (havenCache.storesUnlocked() && havenCache.exists(cid)) return true
        if (aesKeyCache.getSuspend(walletPieceKey(wallet, cid)) != null) return true
        return havenAol.hasCachedKey(media)
    }

    /** Ciphertext cache mode: stream the piece through the cipher into the spool. */
    private suspend fun stageToSpool(media: MediaItem, piece: PieceRef, wallet: String, signing: Boolean): File {
        val key = media.id
        var contentKey: ByteArray? = null
        if (media.isEncrypted) {
            // A cached key costs nothing, so the availability probe is skipped for it; otherwise a
            // key is never requested for a piece that cannot be fetched.
            val verdict = gateKeyAcquisition(
                items = listOf(media),
                isKeyCached = { havenAol.hasSessionKey(it, aesKeyCache, wallet) },
                isAvailable = havenCache::isAvailable,
            ).single().second
            if (verdict == KeyGateVerdict.UNAVAILABLE) {
                throw StagingException(key, StagingException.PIECE_UNAVAILABLE)
            }
            contentKey = acquireKey(media, piece, wallet, signing)
        }

        val total = media.sizeBytes ?: piece.size.takeIf { it > 0 }
        // foc emits nothing until the whole piece is on disk, so the download is one silent
        // stage. Claiming a fraction here would show 0% for the entire fetch.
        publish(key, StageState.Working(StageStep.FETCHING, totalBytes = total))

        // Retrieval serves the stored CAR file; the payload is what was uploaded.
        val payload = havenCache.stream(piece).stripCarContainer()
        val step = if (contentKey != null) StageStep.UNLOCKING else StageStep.PREPARING
        val plaintext = if (contentKey != null) havenCipher.decryptStream(contentKey, payload, null) else payload
        val progress = ProgressGate()
        return plaintextSpool.write(piece.pieceCid, plaintext) { written ->
            if (progress.shouldReport(written)) publish(key, StageState.Working(step, written, total))
        }.getOrElse { error ->
            throw error.toStagingException(key, if (contentKey != null) "PLAYBACK_DECRYPT_FAILED" else "CACHE_WRITE_FAILED")
        }
    }

    /**
     * "Keep unlocked content on this device": foc stores the decrypted file and serves it from
     * then on. The transform, and the key request inside it, only runs when the piece is not on
     * the device yet, so a hit costs nothing.
     */
    private suspend fun stageUnlocked(media: MediaItem, piece: PieceRef, wallet: String, signing: Boolean): File {
        val key = media.id
        val total = media.sizeBytes ?: piece.size.takeIf { it > 0 }
        publish(key, StageState.Working(StageStep.FETCHING, totalBytes = total))
        val progress = ProgressGate()
        var keyFailure: Throwable? = null
        val transform: PieceTransform = if (media.isEncrypted) {
            havenCipher.decryptOnStore(
                key = {
                    try {
                        acquireKey(media, piece, wallet, signing)
                    } catch (e: Throwable) {
                        keyFailure = e
                        throw e
                    }
                },
                onBytesWritten = { written ->
                    if (progress.shouldReport(written)) {
                        publish(key, StageState.Working(StageStep.UNLOCKING, written, total))
                    }
                },
            )
        } else {
            stripOnStore { written ->
                if (progress.shouldReport(written)) {
                    publish(key, StageState.Working(StageStep.PREPARING, written, total))
                }
            }
        }
        return havenCache.file(piece, transform).getOrElse { error ->
            throw (keyFailure ?: error).toStagingException(key, "PLAYBACK_DECRYPT_FAILED")
        }
    }

    /**
     * The content key: the session cache first, else the key service. With [signing] the user has
     * agreed to a wallet prompt and the stage says so.
     */
    private suspend fun acquireKey(media: MediaItem, piece: PieceRef, wallet: String, signing: Boolean): ByteArray {
        val cacheKey = walletPieceKey(wallet, piece.pieceCid)
        aesKeyCache.getSuspend(cacheKey)?.let { return it }
        publish(
            media.id,
            StageState.Working(if (signing) StageStep.WAITING_FOR_WALLET else StageStep.CHECKING_ACCESS),
        )
        val contentKey = havenAol.decrypt(media, walletSession).getOrElse { error ->
            if (error is HavenError.MarketCapNotReached) libraryRepository.markNotOpenYet(media.id)
            throw error.toStagingException(media.id, "CANISTER_CALL_FAILED")
        }
        aesKeyCache.putSuspend(cacheKey, contentKey)
        libraryRepository.clearNotOpenYet(media.id)
        return contentKey
    }

    /**
     * Shared last step: read what only the file can tell (an album's songs, a picture), remember
     * the file, and announce it.
     */
    private suspend fun finish(media: MediaItem, piece: PieceRef, wallet: String, file: File, fetched: Boolean): File {
        val chapters = if (media.kind == MediaKind.AUDIO) {
            runCatching { resolveChapters(media, file) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        if (media.kind == MediaKind.AUDIO || media.kind == MediaKind.VIDEO) {
            runCatching { artwork.extract(media.id, file, isVideo = media.kind == MediaKind.VIDEO) }
        }
        // Playing is caching: the fetch just filled the piece cache, so touching the row lets
        // every "on this device" label recompute now instead of at the next refresh.
        if (fetched) runCatching { mediaRepository.noteAccessed(media.id) }
        ready[media.id] = Ready(wallet.lowercase(), piece.pieceCid, file)
        recentFailures.remove(media.id)
        _staged.tryEmit(Staged(media.id, piece.pieceCid, chapters, media.durationSeconds?.let { it * 1000 }))
        return file
    }

    /** Cached chapters while the piece is the same piece, else read the tag and write through. */
    private suspend fun resolveChapters(media: MediaItem, file: File): List<AudioChapter> {
        mediaRepository.cachedChapters(media, file.length())?.let { cached ->
            return cached.map { AudioChapter(it.startMs, it.title) }
        }
        val parsed = withContext(Dispatchers.IO) { readId3Chapters(file) }
        mediaRepository.cacheChapters(media, file.length(), parsed.map { CachedChapter(it.startMs, it.title) })
        return parsed
    }

    private fun publish(recordKey: String, state: StageState) {
        _states.update { it + (recordKey to state) }
    }

    /** Lets a byte counter through about once per megabyte, so progress is not a state write per chunk. */
    private class ProgressGate {
        private var last = -REPORT_EVERY_BYTES

        fun shouldReport(written: Long): Boolean {
            if (written - last < REPORT_EVERY_BYTES) return false
            last = written
            return true
        }
    }

    private companion object {
        const val TAG = "Staging"
        const val LIBRARY_WAIT_MS = 15_000L
        const val FAILURE_MEMO_MS = 8_000L
        const val REPORT_EVERY_BYTES = 1L shl 20
    }
}

/**
 * foc-cache transform for an upload with no gate, in "keep unlocked" mode: store the payload
 * rather than the CAR file that wraps it, so the stored file plays as it is.
 */
internal fun stripOnStore(onBytesWritten: (Long) -> Unit = {}): PieceTransform = PieceTransform { _, input, output ->
    val wrapped = flow {
        val buffer = ByteArray(TRANSFORM_CHUNK_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            emit(buffer.copyOf(read))
        }
    }
    var written = 0L
    wrapped.stripCarContainer().collect { chunk ->
        output.write(chunk)
        written += chunk.size
        onBytesWritten(written)
    }
}

private const val TRANSFORM_CHUNK_BYTES = 256 * 1024
