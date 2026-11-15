package app.ipfsmedia.core.social

import app.ipfsmedia.core.library.AppScope
import app.ipfsmedia.core.library.BlockClock
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.library.LibraryState
import app.ipfsmedia.core.library.shortAddress
import app.ipfsmedia.core.userdata.UserDataRepository
import haven.mobile.core.crypto.HavenCipher
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A thread as a list row and as the header of its page. */
data class ThreadView(
    val key: String,
    val daoKey: String?,
    val subject: String,
    val authorAddress: String,
    val authorName: String,
    /** The thread's own text: clear text, unsealed text, or null when sealed or absent. */
    val text: String?,
    val isSealed: Boolean,
    val createdAtMillis: Long?,
    val expiresAtMillis: Long?,
    val commentCount: Int,
    val receipt: ReceiptStatus,
    val hasAttachment: Boolean,
    val isHidden: Boolean,
)

/** A comment as shown: placed in its thread, with what the reader may see of it. */
data class CommentView(
    val key: String,
    val authorAddress: String,
    val authorName: String,
    /** Null when the text is sealed and has not been unsealed, or the author is muted. */
    val text: String?,
    val isSealed: Boolean,
    val isMuted: Boolean,
    val depth: Int,
    val createdAtMillis: Long?,
    val expiresAtMillis: Long?,
    val receipt: ReceiptStatus,
)

/** Everything discussion screens read, derived from the last refresh and local moderation. */
data class DiscussionState(
    private val threadsByDao: Map<String, List<ThreadView>> = emptyMap(),
    private val threadsByKey: Map<String, ThreadView> = emptyMap(),
    private val commentsByRoot: Map<String, List<CommentView>> = emptyMap(),
    /** True when the discussion listing was cut short; counts are then a lower bound. */
    val truncated: Boolean = false,
) {
    /** A DataDAO's threads, newest first. Hidden ones are left out unless [includeHidden]. */
    fun threadsFor(daoKey: String, includeHidden: Boolean = false): List<ThreadView> =
        threadsByDao[daoKey].orEmpty().filter { includeHidden || !it.isHidden }

    /** Whether a DataDAO has any discussion at all; its Discussion tab exists only if so. */
    fun hasThreads(daoKey: String): Boolean = !threadsByDao[daoKey].isNullOrEmpty()

    fun thread(key: String): ThreadView? = threadsByKey[key.lowercase()]

    /** Comments under a thread or a media record, each followed by its replies, oldest first. */
    fun commentsUnder(rootKey: String): List<CommentView> = commentsByRoot[rootKey.lowercase()].orEmpty()

    /**
     * How many comments a record has. A page shows no comments area at all when this is zero
     * (docs/PLAN.md §11): the answer is local, so asking costs nothing.
     */
    fun commentCount(rootKey: String): Int = commentsByRoot[rootKey.lowercase()]?.size ?: 0

    /** Sealed comments under [rootKey] that have not been unsealed. */
    fun sealedCount(rootKey: String): Int = commentsUnder(rootKey).count { it.isSealed && it.text == null && !it.isMuted }
}

/**
 * Reading discussion: threads in a DataDAO, comments under a thread, an album or a video.
 *
 * Read-only, and secondary by design. Most uploads have no comments, and then nothing about
 * comments appears. Where there are some, they are shown with a mark saying whether the author's
 * membership receipt verifies. Muting an address and hiding a thread are local and visible to
 * nobody.
 */
@Singleton
class DiscussionRepository @Inject constructor(
    private val boardStore: BoardStore,
    private val userData: UserDataRepository,
    private val library: LibraryRepository,
    private val havenAol: HavenAol,
    private val havenCipher: HavenCipher,
    private val walletSession: WalletSession,
    @AppScope private val scope: CoroutineScope,
) {
    /** Text of sealed records unsealed this session, by record key. Memory only. */
    private val unsealed = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        // Unsealed text belongs to the identity that unsealed it.
        scope.launch { walletSession.address.collect { unsealed.value = emptyMap() } }
    }

    val state: StateFlow<DiscussionState> = combine(
        boardStore.snapshot,
        userData.mutedAddresses,
        userData.hiddenThreads,
        library.state,
        unsealed,
    ) { snapshot, muted, hidden, libraryState, texts ->
        buildDiscussion(snapshot, muted, hidden, libraryState, texts)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, DiscussionState())

    /**
     * Tries to unseal the sealed text under [rootKey] (the root itself included).
     *
     * A sealed comment is readable by members: its key comes from the same key service that
     * unlocks media, for the comment's own gate and 30-day epoch, so the first unseal in an
     * epoch asks the wallet for one signature and the rest reuse it. Returns how many were
     * unsealed. Whether haven-mobile's unlock path opens redhaven's small-text framing has not
     * been verified against live records (docs/PLAN.md §15); a failure leaves the comment
     * reading "Sealed comment" and returns the reason.
     */
    suspend fun unseal(rootKey: String): Result<Int> = withContext(Dispatchers.Default) {
        val root = rootKey.lowercase()
        val snapshot = boardStore.snapshot.value
        val sealed = (snapshot.threads.filter { it.key == root } + snapshot.posts.filter { it.rootRef == root })
            .filter { it.body is PostBody.Sealed && it.key !in unsealed.value }
        if (sealed.isEmpty()) return@withContext Result.success(0)

        var opened = 0
        var firstFailure: Throwable? = null
        for (record in sealed) {
            val gate = record.sealedGate
            if (gate == null) {
                firstFailure = firstFailure ?: HavenError.UnsupportedGateMetadata(
                    "This comment's seal was not kept. Refresh, then try again.",
                )
                continue
            }
            val ciphertext = (record.body as PostBody.Sealed).ciphertext
            val text = havenAol.decrypt(record.asKeyRequest(gate), walletSession)
                .mapCatching { key -> havenCipher.decrypt(key, decodeBase64(ciphertext), null).getOrThrow() }
                .mapCatching { plain -> sealedTextOf(plain) ?: throw HavenError.PlaybackDecryptFailed("Unreadable sealed text") }
            text.onSuccess { value ->
                unsealed.update { it + (record.key to value) }
                opened++
            }.onFailure { error ->
                Timber.w(error, "Sealed comment %s could not be unsealed", record.key)
                firstFailure = firstFailure ?: error
                // A refused signature or a non-member will fail the same way for every comment.
                if (error is HavenError.SigningFailed || error is HavenError.GateVerificationFailed) {
                    return@withContext Result.failure(error)
                }
            }
        }
        val failure = firstFailure
        if (opened == 0 && failure != null) Result.failure(failure) else Result.success(opened)
    }
}

/** The key service takes a media record; a comment is presented to it as one, with its own seal. */
private fun BoardRecord.asKeyRequest(gate: haven.mobile.core.domain.GateMetadata): MediaItem = MediaItem(
    id = key,
    kind = MediaKind.FILE,
    owner = author,
    title = subject.orEmpty(),
    description = null,
    mimeType = null,
    fileExtension = null,
    filenameHint = null,
    sizeBytes = null,
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    createdAtBlock = createdAtBlock,
    expiresAtBlock = expiresAtBlock,
    pieceRef = null,
    filecoinCid = null,
    encryptedCid = null,
    cidHash = null,
    gate = null,
    isEncrypted = true,
    encryptionMetadata = gate,
    cidEncryptionMetadata = null,
    attestation = null,
    arkivStatus = ArkivStatus.FRESH,
    contentCacheStatus = ContentCacheStatus.UNCACHED,
    lastAccessedAt = null,
)

internal fun decodeBase64(text: String): ByteArray {
    val clean = text.trim()
    return runCatching { java.util.Base64.getDecoder().decode(clean) }
        .getOrElse { java.util.Base64.getUrlDecoder().decode(clean) }
}

/**
 * Sealed text is `{"t":"…"}` in UTF-8, padded to a fixed bucket so its length says little about
 * the message. Whatever follows the closing brace is padding.
 */
internal fun sealedTextOf(plain: ByteArray): String? {
    val text = plain.toString(Charsets.UTF_8)
    val end = text.lastIndexOf('}')
    if (end < 0) return null
    return runCatching { JSONObject(text.substring(0, end + 1)).optString("t", "") }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
}

/** Pure, so nesting, counting, muting and hiding are tested without Android. */
internal fun buildDiscussion(
    snapshot: BoardSnapshot,
    muted: Set<String>,
    hidden: Set<String>,
    library: LibraryState,
    unsealed: Map<String, String>,
): DiscussionState {
    if (snapshot.isEmpty) return DiscussionState()
    val clock = library.clock

    fun nameOf(address: String): String = library.uploader(address)?.displayName ?: shortAddress(address)
    fun textOf(record: BoardRecord): String? = when (val body = record.body) {
        is PostBody.Clear -> body.text
        is PostBody.Sealed -> unsealed[record.key]
        PostBody.None -> null
    }

    val commentsByRoot = snapshot.posts
        .filter { it.rootRef != null }
        .groupBy { it.rootRef!! }
        .mapValues { (_, posts) ->
            nestComments(posts).map { placed ->
                val record = placed.record
                val isMuted = record.author in muted
                CommentView(
                    key = record.key,
                    authorAddress = record.author,
                    authorName = nameOf(record.author),
                    text = textOf(record).takeIf { !isMuted },
                    isSealed = record.body is PostBody.Sealed,
                    isMuted = isMuted,
                    depth = placed.depth,
                    createdAtMillis = millis(record.createdAtBlock, clock),
                    expiresAtMillis = millis(record.expiresAtBlock, clock),
                    receipt = snapshot.receipts[record.key] ?: ReceiptStatus.UNCHECKED,
                )
            }
        }

    val threads = snapshot.threads
        .filter { it.author !in muted }
        .sortedWith(compareByDescending<BoardRecord> { it.createdAtBlock ?: Long.MIN_VALUE }.thenBy { it.key })
        .map { record ->
            ThreadView(
                key = record.key,
                daoKey = record.daoKey,
                subject = record.subject?.takeIf { it.isNotBlank() } ?: "Untitled thread",
                authorAddress = record.author,
                authorName = nameOf(record.author),
                text = textOf(record),
                isSealed = record.body is PostBody.Sealed,
                createdAtMillis = millis(record.createdAtBlock, clock),
                expiresAtMillis = millis(record.expiresAtBlock, clock),
                commentCount = commentsByRoot[record.key]?.size ?: 0,
                receipt = snapshot.receipts[record.key] ?: ReceiptStatus.UNCHECKED,
                hasAttachment = record.hasMedia,
                isHidden = record.key in hidden,
            )
        }

    return DiscussionState(
        threadsByDao = threads.filter { it.daoKey != null }.groupBy { it.daoKey!! },
        threadsByKey = threads.associateBy { it.key },
        commentsByRoot = commentsByRoot,
        truncated = snapshot.truncated,
    )
}

private fun millis(block: Long?, clock: BlockClock?): Long? = block?.let { clock?.millisAt(it) }
