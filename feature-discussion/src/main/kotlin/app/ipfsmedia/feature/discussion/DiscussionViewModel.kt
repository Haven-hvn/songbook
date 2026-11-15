package app.ipfsmedia.feature.discussion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.library.UserMessage
import app.ipfsmedia.core.library.toUserMessage
import app.ipfsmedia.core.social.DiscussionRepository
import app.ipfsmedia.core.social.DiscussionState
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where an attempt to unseal the comments under one root stands. */
sealed interface UnsealState {
    data object Working : UnsealState
    data class Failed(val message: UserMessage) : UnsealState
}

/**
 * Reading discussion. There is nothing here that writes: no comment box, no reply, no vote
 * (docs/PLAN.md §11). The two things a reader can do are local and seen by nobody else: mute
 * an address, and hide a thread.
 */
@HiltViewModel
class DiscussionViewModel @Inject constructor(
    private val discussion: DiscussionRepository,
    private val userData: UserDataRepository,
) : ViewModel() {

    val state: StateFlow<DiscussionState> = discussion.state

    /** Per root key. Absent means: not tried, or done. */
    val unsealing = mutableStateMapOf<String, UnsealState>()

    /**
     * Unseals members' comments under [rootKey]. The first time in an epoch this asks the
     * wallet for one signature, which is why it only ever runs from a button that says so.
     */
    fun unseal(rootKey: String) {
        if (unsealing[rootKey] is UnsealState.Working) return
        unsealing[rootKey] = UnsealState.Working
        viewModelScope.launch {
            discussion.unseal(rootKey)
                .onSuccess { unsealing.remove(rootKey) }
                .onFailure { unsealing[rootKey] = UnsealState.Failed(it.toUserMessage()) }
        }
    }

    fun dismissUnsealError(rootKey: String) {
        unsealing.remove(rootKey)
    }

    fun setMuted(address: String, muted: Boolean) {
        viewModelScope.launch { userData.setMuted(address, muted) }
    }

    fun setThreadHidden(threadKey: String, hidden: Boolean) {
        viewModelScope.launch { userData.setThreadHidden(threadKey, hidden) }
    }
}
