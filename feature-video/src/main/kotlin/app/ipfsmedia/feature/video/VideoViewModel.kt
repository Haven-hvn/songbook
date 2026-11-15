package app.ipfsmedia.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.ipfsmedia.core.player.PlayerConnection
import app.ipfsmedia.core.player.PlayerUiState
import app.ipfsmedia.core.userdata.ResumeEntity
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What a video surface needs: the player itself (to draw on), its state, and where each long
 * item was left.
 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    val connection: PlayerConnection,
    userData: UserDataRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = connection.state
    val positionMs: StateFlow<Long> = connection.positionMs
    val player: StateFlow<Player?> = connection.player

    /** Where playback of each long item stopped, by record key. */
    val resume: StateFlow<Map<String, ResumeEntity>> =
        userData.resumePositions
            .map { rows -> rows.associateBy { it.recordKey } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
