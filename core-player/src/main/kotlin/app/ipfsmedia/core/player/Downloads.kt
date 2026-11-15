package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.AccessState
import app.ipfsmedia.core.library.AppScope
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.library.OfflineState
import app.ipfsmedia.core.library.UserMessage
import haven.mobile.core.cache.HavenCache
import haven.mobile.core.cache.PlaintextSpool
import haven.mobile.core.cache.SpoolPins
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.domain.MediaKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where a requested download stands. A record with nothing in progress is absent from the map. */
sealed interface DownloadState {
    /** `foc-cache` reports nothing while it fetches, so there is no fraction to show. */
    data object Running : DownloadState

    /** It can only be kept in unlocked form, and unlocking needs a signature the user has not agreed to. */
    data object NeedsSignature : DownloadState

    data class Failed(val message: UserMessage) : DownloadState
}

/** What this identity's content takes up on the device. */
data class StorageUse(
    /** Pieces as fetched: encrypted when gated, unless "keep unlocked" is on. */
    val cacheUsedBytes: Long,
    /** The room the cache may use; what it was sized to when the setting is automatic. */
    val cacheQuotaBytes: Long,
    val cachedItems: Int,
    /** Unlocked copies staged for playback. Rebuilt from the cache when needed. */
    val stagedBytes: Long,
)

/**
 * "Download": have an upload on the device before it is wanted (docs/PLAN.md §9).
 *
 * In the default mode this is `foc-cache`'s `fetch`: the piece goes into the cache as stored,
 * encrypted if it is gated, and plays later with no network. Nothing is unlocked, so no
 * signature is needed to download.
 *
 * Two additions:
 *
 *  - An album's songs are only known from its file, so after fetching an album this also reads
 *    its song list, when that costs no signature (the upload has no gate, or a key is already
 *    held) or the user agreed to one. Otherwise the songs appear the first time it is played.
 *  - With "keep unlocked content on this device" on, the store holds one form per piece and
 *    does not record which, so a plain `fetch` would file ciphertext where decrypted files are
 *    expected. In that mode a download goes through the full unlock path instead.
 */
@Singleton
class Downloads @Inject constructor(
    private val havenCache: HavenCache,
    private val plaintextSpool: PlaintextSpool,
    private val spoolPins: SpoolPins,
    private val mediaRepository: MediaRepository,
    private val library: LibraryRepository,
    private val stager: MediaStager,
    @AppScope private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states

    /** Guarded by itself. */
    private val jobs = HashMap<String, Job>()

    /**
     * Starts downloading [recordKey]; does nothing if it is already running.
     *
     * @param approveSignature the user has read why a signature is needed and agreed to one.
     */
    fun download(recordKey: String, approveSignature: Boolean = false) {
        synchronized(jobs) {
            if (jobs[recordKey]?.isActive == true) return
            jobs[recordKey] = scope.launch { run(recordKey, approveSignature) }
        }
    }

    fun downloadAll(recordKeys: Collection<String>) {
        recordKeys.forEach { download(it) }
    }

    /** Removes [recordKey]'s content from the device. What is playing keeps its staged file until it ends. */
    fun remove(recordKey: String) {
        scope.launch {
            synchronized(jobs) { jobs.remove(recordKey) }?.cancel()
            val pieceCid = library.mediaItem(recordKey)?.pieceRef?.pieceCid
            if (pieceCid != null) {
                havenCache.remove(pieceCid)
                if (pieceCid !in spoolPins.snapshot()) plaintextSpool.evict(pieceCid)
            }
            stager.forget(recordKey)
            mediaRepository.noteResidencyChanged(recordKey)
            _states.update { it - recordKey }
        }
    }

    /**
     * Takes everything [walletAddress] has on this device off it: downloads, cached pieces and
     * unlocked copies. What is playing keeps its staged file until it ends, so clearing never
     * cuts the music. Every item goes back to "not downloaded", which is then the truth.
     */
    suspend fun clearAll(walletAddress: String) {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        val held = library.state.value.items.filter { it.offline != OfflineState.NOT_DOWNLOADED }
        havenCache.clearFor(walletAddress)
        plaintextSpool.clearUnpinned()
        stager.forgetAll()
        held.forEach { mediaRepository.noteResidencyChanged(it.id) }
        _states.value = emptyMap()
    }

    /**
     * "Keep unlocked content on this device" was switched. The store holds one form per piece,
     * so the other mode's content is no use: turning the setting off deletes the unlocked
     * copies at once, leaving no decrypted file behind. Either way gated items are fetched
     * again when next opened, and say so by going back to "not downloaded".
     */
    suspend fun unlockedModeChanged(walletAddress: String, enabled: Boolean) {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        val held = library.state.value.items.filter { it.offline != OfflineState.NOT_DOWNLOADED }
        if (!enabled) runCatching { havenCache.clearUnlockedFor(walletAddress) }
        stager.forgetAll()
        held.forEach { mediaRepository.noteResidencyChanged(it.id) }
        _states.value = emptyMap()
    }

    /** Drops cached pieces that have outlived the cache's keep-for setting. */
    suspend fun clearExpired(walletAddress: String) {
        val expired = library.state.value.items.filter { it.offline == OfflineState.EXPIRED }
        havenCache.clearExpiredFor(walletAddress)
        expired.forEach { mediaRepository.noteResidencyChanged(it.id) }
    }

    /** How much room this identity's content takes, for Settings. Reads the disk; call off the main thread. */
    suspend fun storageUse(): StorageUse {
        val space = havenCache.space()
        return StorageUse(
            cacheUsedBytes = space.usedBytes,
            cacheQuotaBytes = space.quotaBytes,
            cachedItems = space.itemCount,
            stagedBytes = plaintextSpool.usedBytes(),
        )
    }

    /** Clears a failure or a signature request the user has seen. */
    fun dismiss(recordKey: String) {
        _states.update { states -> if (states[recordKey] is DownloadState.Running) states else states - recordKey }
    }

    private suspend fun run(recordKey: String, approveSignature: Boolean) {
        _states.update { it + (recordKey to DownloadState.Running) }
        try {
            val item = library.state.value.item(recordKey)
                ?: throw StagingException(recordKey, StagingException.RECORD_GONE)
            val piece = item.media.pieceRef
                ?: throw StagingException(recordKey, "NO_PIECE_REF")
            when (item.access) {
                AccessState.NOT_MEMBER -> throw StagingException(recordKey, StagingException.NOT_A_MEMBER)
                AccessState.NOT_OPEN_YET -> throw StagingException(recordKey, StagingException.NOT_OPEN_YET)
                AccessState.OPEN, AccessState.MEMBER, AccessState.UNKNOWN -> Unit
            }

            if (havenCache.storesUnlocked()) {
                if (approveSignature) stager.approveSignature(recordKey)
                stager.stage(StageRequest(recordKey))
            } else {
                havenCache.fetch(piece).getOrElse { throw it.toStagingException(recordKey, "NETWORK_ERROR") }
                mediaRepository.noteResidencyChanged(recordKey)
                if (item.media.kind == MediaKind.AUDIO && !item.songsKnown) {
                    if (approveSignature) stager.approveSignature(recordKey)
                    if (approveSignature || !stager.needsSignature(recordKey)) {
                        try {
                            stager.stage(StageRequest(recordKey))
                        } catch (e: StagingException) {
                            // The download itself succeeded. The songs will show on first play.
                            stager.clearFailure(recordKey)
                        }
                    }
                }
            }
            _states.update { it - recordKey }
        } catch (e: CancellationException) {
            _states.update { it - recordKey }
            throw e
        } catch (e: Throwable) {
            val failure = e.toStagingException(recordKey)
            // The stager keeps its own note of this; the download's is the one that is shown.
            stager.clearFailure(recordKey)
            val state = if (failure.code == StagingException.SIGNATURE_REQUIRED) {
                DownloadState.NeedsSignature
            } else {
                DownloadState.Failed(failure.userMessage)
            }
            _states.update { it + (recordKey to state) }
        }
    }
}
