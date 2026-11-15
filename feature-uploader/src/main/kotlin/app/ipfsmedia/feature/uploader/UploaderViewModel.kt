package app.ipfsmedia.feature.uploader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.userdata.FollowEntity
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The local side of an uploader's profile: the follow list (which is published nowhere), and
 * the mark that says which of their uploads the user has been shown.
 */
@HiltViewModel
class UploaderViewModel @Inject constructor(
    private val userData: UserDataRepository,
) : ViewModel() {

    /** Followed addresses with what was saved when they were followed, by lowercased address. */
    val follows: StateFlow<Map<String, FollowEntity>> =
        userData.follows
            .map { rows -> rows.associateBy { it.address } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /**
     * The profile has shown uploads up to [block], so they stop counting as unseen in the
     * sidebar. This is about what was *shown*; nothing about what was played is recorded.
     */
    fun markSeen(address: String, block: Long) {
        viewModelScope.launch { userData.markUploaderSeen(address, block) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
