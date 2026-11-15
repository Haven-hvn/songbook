package app.ipfsmedia.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.userdata.HistoryEntity
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Reads the local play history. It is a table on this device, per identity, and that is the
 * whole of it: there is no code path that sends a play anywhere (docs/PLAN.md §2).
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    userData: UserDataRepository,
) : ViewModel() {

    /** Newest first. Null until first read, so the page can tell loading from empty. */
    val history: StateFlow<List<HistoryEntity>?> =
        userData.history
            .map<List<HistoryEntity>, List<HistoryEntity>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
