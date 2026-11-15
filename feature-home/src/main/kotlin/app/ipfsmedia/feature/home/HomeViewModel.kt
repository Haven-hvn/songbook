package app.ipfsmedia.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.library.LibraryRepository
import app.ipfsmedia.core.social.RefreshCoordinator
import app.ipfsmedia.core.social.RefreshState
import app.ipfsmedia.core.userdata.HistoryEntity
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.ResumeEntity
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What Home needs beyond the catalogue: the refresh, the shelf order, where long items were
 * left, what was played lately, and which uploads are new.
 *
 * **"New" is about uploads, never plays.** An upload is new when its creation block is later
 * than the newest one the feed had shown before the last refresh (or before this session, for
 * the refresh that happens when the app opens). Nothing about what was played enters into it.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val coordinator: RefreshCoordinator,
    private val userData: UserDataRepository,
    libraryRepository: LibraryRepository,
) : ViewModel() {

    val refreshState: StateFlow<RefreshState> = coordinator.state

    val shelfOrder: StateFlow<List<HomeShelf>> =
        userData.pref(PrefKeys.HOME_SHELF_ORDER)
            .map(::shelfOrderOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeShelf.DEFAULT_ORDER)

    /** Where long items were left, most recent first. */
    val resume: StateFlow<List<ResumeEntity>> =
        userData.resumePositions
            .map { rows -> rows.sortedByDescending { it.updatedAtMs } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Local play history, newest first. It never leaves the device; here it fills one shelf. */
    val history: StateFlow<List<HistoryEntity>> =
        userData.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _seenBlock = MutableStateFlow<Long?>(null)

    /**
     * The newest upload block that does not count as new. Null until it has been read for the
     * active identity, so the feed does not flash everything as new and then take it back.
     */
    val seenBlock: StateFlow<Long?> = _seenBlock.asStateFlow()

    /** The newest block the feed has actually drawn this session. */
    private var shownBlock = 0L

    init {
        viewModelScope.launch {
            // Per identity: another wallet's feed has its own "seen" mark.
            libraryRepository.state.map { it.walletAddress }.distinctUntilChanged().collectLatest { address ->
                _seenBlock.value = null
                shownBlock = 0L
                if (address != null) {
                    _seenBlock.value = userData.getPref(PrefKeys.FEED_SEEN_BLOCK)?.toLongOrNull() ?: 0L
                }
            }
        }
        viewModelScope.launch {
            // Whoever starts a refresh (pull down, the button, Ctrl+R), what was on screen before
            // it stops being new, so only what this refresh brings is marked.
            coordinator.state.map { it.running }.distinctUntilChanged().collect { running ->
                if (running && shownBlock > 0L && _seenBlock.value != null) _seenBlock.value = shownBlock
            }
        }
    }

    fun refresh() = coordinator.refresh()

    /** The feed has drawn uploads up to [newestBlock]: remember that for the next session. */
    fun feedShown(newestBlock: Long) {
        if (newestBlock <= shownBlock) return
        shownBlock = newestBlock
        viewModelScope.launch {
            val stored = userData.getPref(PrefKeys.FEED_SEEN_BLOCK)?.toLongOrNull() ?: 0L
            if (newestBlock > stored) userData.setPref(PrefKeys.FEED_SEEN_BLOCK, newestBlock.toString())
        }
    }

    fun moveShelf(shelf: HomeShelf, delta: Int, visible: Set<HomeShelf>) {
        val order = shelfOrder.value
        val moved = shelfMoved(order, shelf, delta, visible)
        if (moved === order) return
        viewModelScope.launch { userData.setPref(PrefKeys.HOME_SHELF_ORDER, encodeShelfOrder(moved)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
