package app.ipfsmedia.core.library

import app.ipfsmedia.core.userdata.DaoStateEntity
import app.ipfsmedia.core.userdata.EntrySnapshot
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import haven.mobile.core.cache.mirror.MediaRepository
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The catalogue for the active identity, as one shared [StateFlow].
 *
 * This is the only place the mirror is observed. haven-mobile's mirror closes the previous
 * wallet's database when the wallet changes, so two independent collectors can see one of them
 * handed a closed database during a switch; routing every reader through this one collection
 * (re-pointed with `flatMapLatest` when the identity changes) removes that case.
 *
 * Nothing here calls the index. The mirror is filled by a refresh (`core-social`), and
 * everything else — access state, the DataDAO directory, uploader profiles, an album's songs —
 * is derived from what is on the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
    private val walletSession: WalletSession,
    private val mediaRepository: MediaRepository,
    private val userData: UserDataRepository,
    @AppScope scope: CoroutineScope,
) {
    /** Items a market-cap gate refused this session; they show "Not open yet" until it opens. */
    private val notOpenYet = MutableStateFlow<Set<String>>(emptySet())

    /** What this identity keeps beside the mirror, gathered into one value per change. */
    private data class UserSlice(
        val daoStates: List<DaoStateEntity>,
        val pinnedDaoKeys: List<String>,
        val favouriteRecordKeys: Set<String>,
        val follows: Map<String, Long>,
        val clock: BlockClock?,
    )

    private val userSlice: Flow<UserSlice> = combine(
        userData.daoStates,
        userData.pins,
        userData.favourites,
        userData.follows,
        userData.prefs,
    ) { daoStates, pins, favourites, follows, prefs ->
        UserSlice(
            daoStates = daoStates,
            pinnedDaoKeys = pins.map { it.daoKey },
            favouriteRecordKeys = favourites
                .filter { it.entry.chapterIndex == EntrySnapshot.WHOLE_FILE }
                .map { it.entry.recordKey }
                .toSet(),
            follows = follows.associate { it.address to it.lastSeenBlock },
            clock = BlockClock.of(prefs[PrefKeys.CLOCK_HEAD_BLOCK], prefs[PrefKeys.CLOCK_OBSERVED_AT_MS]),
        )
    }

    val state: StateFlow<LibraryState> = walletSession.address
        .map { it?.trim()?.lowercase()?.takeIf { address -> address.isNotEmpty() } }
        .distinctUntilChanged()
        .flatMapLatest { address ->
            if (address == null) {
                flowOf(LibraryState.EMPTY)
            } else {
                combine(mirrorItems(), mirrorChapters(), userSlice, notOpenYet) { media, chapters, user, locked ->
                    deriveLibrary(
                        LibraryInputs(
                            walletAddress = address,
                            media = media,
                            chapters = chapters,
                            daoStates = user.daoStates,
                            pinnedDaoKeys = user.pinnedDaoKeys,
                            favouriteRecordKeys = user.favouriteRecordKeys,
                            follows = user.follows,
                            clock = user.clock,
                            notOpenYet = locked,
                        ),
                    )
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LibraryState.EMPTY)

    /** The record for [id] as last mirrored, or null. Reads the shared state; never the index. */
    fun mediaItem(id: String): MediaItem? = state.value.item(id)?.media

    /** Remembers that a market-cap gate refused [id], so every surface shows why it is locked. */
    fun markNotOpenYet(id: String) {
        notOpenYet.update { it + id }
    }

    /** Called when an item opened after all: the refusal no longer holds. */
    fun clearNotOpenYet(id: String) {
        notOpenYet.update { it - id }
    }

    private fun mirrorItems(): Flow<List<MediaItem>> =
        runCatching { mediaRepository.observeAccessible() }
            .getOrElse { flowOf(emptyList()) }
            .catch { error ->
                Timber.w(error, "Library: the mirror could not be read")
                emit(emptyList())
            }

    private fun mirrorChapters(): Flow<List<ChapterRow>> =
        runCatching { mediaRepository.observeChapters() }
            .getOrElse { flowOf(emptyList()) }
            .map { rows -> rows.map { ChapterRow(it.albumId, it.chapterIndex, it.startMs, it.title, it.pieceCid) } }
            .catch { error ->
                Timber.w(error, "Library: cached chapters could not be read")
                emit(emptyList())
            }
}
