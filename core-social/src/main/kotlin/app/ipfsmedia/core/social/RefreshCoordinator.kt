package app.ipfsmedia.core.social

import app.ipfsmedia.core.library.AppScope
import app.ipfsmedia.core.library.BlockClock
import app.ipfsmedia.core.library.UserMessage
import app.ipfsmedia.core.library.daoKeyOrNull
import app.ipfsmedia.core.library.toUserMessage
import app.ipfsmedia.core.userdata.DaoStateEntity
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import haven.mobile.core.arkiv.ArkivCallGate
import haven.mobile.core.arkiv.ArkivClient
import haven.mobile.core.arkiv.ArkivEntity
import haven.mobile.core.arkiv.ArkivPredicates
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.collections.GateAccessChecker
import haven.mobile.core.collections.gateKeyOrNull
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.error.HavenError
import haven.mobile.core.haven.aol.HavenAol
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

/** What a refresh is doing, so the header can say something better than "loading". */
enum class RefreshStage(val label: String) {
    CATALOGUE("Reading the catalogue…"),
    MEMBERSHIP("Checking your memberships…"),
    DISCUSSION("Reading discussion…"),
}

data class RefreshState(
    val running: Boolean = false,
    val stage: RefreshStage? = null,
    /** Wall-clock time of the last refresh that stored a catalogue, for "Last refreshed …". */
    val lastRefreshedAtMillis: Long? = null,
    /** Why the last refresh failed, in the user's words. Cleared by the next success. */
    val error: UserMessage? = null,
    /** When the index will accept reads again, while it is rate limited. */
    val rateLimitedUntilMillis: Long? = null,
    /** Something that went wrong without failing the refresh ("Discussion could not be read"). */
    val note: String? = null,
)

/**
 * The only thing in the app that reads the index.
 *
 * A refresh happens when the app opens and when the user asks for one (pull down, the Refresh
 * button, Ctrl+R). Never on a timer, never because a screen was opened: Arkiv allows about a
 * hundred charged reads an hour, and between refreshes every screen reads what this stored.
 *
 * One refresh is:
 *
 *  1. one walk of the music and video listing (`scanCatalog`), written to the mirror;
 *  2. one walk of the discussion listing, kept in [BoardStore];
 *  3. a balance read per DataDAO on the gate's own chain (not an index read), which decides
 *     what the wallet can open.
 *
 * A failure in step 1 leaves everything as it was. Steps 2 and 3 failing leave the catalogue
 * refreshed and say what could not be read.
 */
@Singleton
class RefreshCoordinator @Inject constructor(
    private val walletSession: WalletSession,
    private val arkivClient: ArkivClient,
    private val callGate: ArkivCallGate,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val gateAccessChecker: GateAccessChecker,
    private val userData: UserDataRepository,
    private val boardStore: BoardStore,
    private val havenAol: HavenAol,
    @AppScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var running: Job? = null

    /** The identity the automatic on-open refresh last ran for, so it runs once per identity per start. */
    @Volatile
    private var openedFor: String? = null

    @Volatile
    private var attestationKey: PublicKey? = null

    init {
        // Follow the identity: load its cached discussion and its last refresh time.
        scope.launch {
            walletSession.address.collect { address ->
                boardStore.switchTo(address)
                val last = if (address == null) null else userData.getPref(PrefKeys.LAST_REFRESH_AT_MS)?.toLongOrNull()
                _state.update { RefreshState(lastRefreshedAtMillis = last) }
            }
        }
    }

    /** The refresh that happens when the app opens. Safe to call from every composition. */
    fun refreshOnOpen() {
        val address = walletSession.address.value?.lowercase() ?: return
        if (openedFor == address) return
        openedFor = address
        refresh()
    }

    /** Starts a refresh unless one is already running. Returns immediately. */
    fun refresh() {
        if (running?.isActive == true) return
        running = scope.launch { refreshNow() }
    }

    /** Runs one refresh to completion. Concurrent callers wait for the one in progress. */
    suspend fun refreshNow() = mutex.withLock {
        val address = walletSession.address.value ?: return@withLock
        _state.update { it.copy(running = true, stage = RefreshStage.CATALOGUE, error = null, note = null) }
        // An explicit refresh must read the chain, not this process's cached answers.
        callGate.invalidate()

        val scan = arkivClient.scanCatalog().getOrElse { error ->
            fail(error)
            return@withLock
        }
        mediaRepository.replaceCatalog(scan.items, pruneMissing = scan.complete).onFailure { error ->
            fail(error)
            return@withLock
        }
        val now = System.currentTimeMillis()
        scan.headBlock?.let { head ->
            userData.setPref(PrefKeys.CLOCK_HEAD_BLOCK, head.toString())
            userData.setPref(PrefKeys.CLOCK_OBSERVED_AT_MS, now.toString())
        }
        val notes = mutableListOf<String>()
        if (!scan.complete) notes += "The catalogue is larger than one refresh reads; the newest part is shown."

        _state.update { it.copy(stage = RefreshStage.DISCUSSION) }
        val board = readBoard(scan.headBlock, now)
        if (board == null) notes += "Discussion could not be read this time."

        _state.update { it.copy(stage = RefreshStage.MEMBERSHIP) }
        val unreadable = updateMemberships(address, scan.items, board?.defs ?: boardStore.snapshot.value.defs, now)
        if (unreadable) notes += "Some memberships could not be checked; the last known answer is shown."

        if (board != null && walletSession.address.value.equals(address, ignoreCase = true)) {
            boardStore.replace(address, board)
        }
        userData.setPref(PrefKeys.LAST_REFRESH_AT_MS, now.toString())
        _state.value = RefreshState(
            lastRefreshedAtMillis = now,
            note = notes.joinToString(" ").takeIf { it.isNotEmpty() },
        )
    }

    private fun fail(error: Throwable) {
        Timber.w(error, "Refresh failed")
        _state.update {
            it.copy(
                running = false,
                stage = null,
                error = error.toUserMessage(),
                rateLimitedUntilMillis = (error as? HavenError.RateLimited)?.retryAtEpochMillis,
            )
        }
    }

    /**
     * Threads, comments and board names in one paged read. Null when the read failed; the
     * previous snapshot then stays in place. Bounded by [MAX_BOARD_PAGES]: discussion is short
     * lived (threads 14 days, comments 7), so the live set is small, and a busy day must not eat
     * the hour's budget.
     */
    private suspend fun readBoard(headBlock: Long?, nowMillis: Long): BoardSnapshot? {
        val entities = mutableListOf<ArkivEntity>()
        var cursor: String? = null
        var pages = 0
        var head = headBlock
        do {
            val page = arkivClient.queryEntities(BOARD_QUERY, BOARD_PAGE_SIZE, cursor).getOrElse { error ->
                Timber.w(error, "Refresh: discussion could not be read")
                if (error is HavenError.RateLimited) {
                    _state.update { it.copy(rateLimitedUntilMillis = error.retryAtEpochMillis) }
                }
                return null
            }
            entities += page.items
            page.headBlock?.let { head = it }
            cursor = page.nextCursor
            pages++
        } while (cursor != null && pages < MAX_BOARD_PAGES)

        val live = entities.filter { entity ->
            val expires = entity.expiresAtBlock
            // A node can still return a record whose lease has run out; readers drop it.
            expires == null || head == null || expires > head!!
        }
        val records = live.mapNotNull { it.toBoardRecord() }
        val clock = head?.let { BlockClock(it, nowMillis) }
        return BoardSnapshot(
            defs = live.mapNotNull { it.toBoardDef() },
            threads = records.filter { it.type == BoardRecordType.THREAD },
            posts = records.filter { it.type == BoardRecordType.POST },
            receipts = verifyReceipts(records, clock, nowMillis),
            truncated = cursor != null,
        )
    }

    /**
     * Checks each record's membership receipt against the canister's key. The key is fetched
     * once per process (a canister query, not an index read); without it — offline, or a device
     * with no Ed25519 — every record is left unchecked rather than marked as failing.
     */
    private suspend fun verifyReceipts(
        records: List<BoardRecord>,
        clock: BlockClock?,
        nowMillis: Long,
    ): Map<String, ReceiptStatus> {
        if (records.isEmpty()) return emptyMap()
        val key = attestationKey ?: havenAol.attestationPublicKey().getOrNull()
            ?.let(::ed25519PublicKeyOrNull)
            ?.also { attestationKey = it }
            ?: return emptyMap()
        return withContext(Dispatchers.Default) {
            records.associate { record ->
                val gate = record.gate
                val postedAtSeconds = (record.createdAtBlock?.let { clock?.millisAt(it) } ?: nowMillis) / 1_000
                val status = if (gate == null) {
                    ReceiptStatus.FAILED
                } else {
                    when (verifyMemberReceipt(record.receipt, gate, record.author, key, postedAtSeconds)) {
                        ReceiptCheck.Ok -> ReceiptStatus.VERIFIED
                        is ReceiptCheck.Rejected -> ReceiptStatus.FAILED
                    }
                }
                record.key to status
            }
        }
    }

    /**
     * Reads the wallet's balance for every DataDAO in the catalogue and stores the verdicts.
     * Returns true when some could not be read.
     *
     * A gate that could not be read keeps its previous verdict when there is one: a chain being
     * unreachable for a minute must not empty the Collection. With no previous verdict it is
     * stored as unknown, which items show as "Can't check right now".
     */
    private suspend fun updateMemberships(
        address: String,
        items: List<MediaItem>,
        defs: List<BoardDef>,
        nowMillis: Long,
    ): Boolean {
        val gates: Map<String, TokenGate> = items
            .mapNotNull { item -> item.gate?.let { gate -> item.daoKeyOrNull()?.let { it to gate } } }
            .groupBy({ it.first }, { it.second })
            // The lowest threshold under a DataDAO: meeting it opens at least something there.
            .mapValues { (_, group) -> group.minByOrNull { it.threshold } ?: group.first() }
        val names = defs.sortedBy { it.createdAtBlock ?: Long.MAX_VALUE }.associateBy { it.daoKey }
        val previous = userData.getDaoStates().associateBy { it.daoKey }

        val chains = runCatching { settingsRepository.enabledChains.first() }.getOrDefault(HavenChain.mainnets.toSet())
            .ifEmpty { HavenChain.mainnets.toSet() }
        val check = runCatching { gateAccessChecker.check(address, gates.values.toList(), chains) }.getOrNull()

        var anyUnreadable = false
        val states = (gates.keys + names.keys).distinct().map { key ->
            val gate = gates[key]
            val checkerKey = gate?.gateKeyOrNull()
            val readable = check != null && checkerKey != null && checkerKey !in check.unreadable
            val before = previous[key]
            val membership = when {
                readable && checkerKey in check!!.satisfied -> DaoStateEntity.MEMBER
                readable -> DaoStateEntity.NOT_MEMBER
                // No upload under this DataDAO names a gate we can read; nothing to check against.
                gate == null -> before?.membership ?: DaoStateEntity.UNKNOWN
                else -> {
                    anyUnreadable = true
                    before?.membership ?: DaoStateEntity.UNKNOWN
                }
            }
            DaoStateEntity(
                daoKey = key,
                membership = membership,
                checkedAtMs = if (readable) nowMillis else before?.checkedAtMs ?: 0L,
                title = names[key]?.title ?: before?.title,
                about = names[key]?.about ?: before?.about,
            )
        }
        if (walletSession.address.value.equals(address, ignoreCase = true)) {
            userData.replaceDaoStates(states)
        }
        return anyUnreadable
    }

    private companion object {
        /** Flat `OR`, the one grouping verified against the node (see `ArkivClientImpl`). */
        val BOARD_QUERY: String = ArkivPredicates.anyOf(
            ArkivPredicates.str("grp", GROUP_BOARD_THREAD),
            ArkivPredicates.str("grp", GROUP_BOARD_POST),
            ArkivPredicates.str("grp", GROUP_BOARD_DEF),
        )
        const val BOARD_PAGE_SIZE = 100
        const val MAX_BOARD_PAGES = 5
    }
}
