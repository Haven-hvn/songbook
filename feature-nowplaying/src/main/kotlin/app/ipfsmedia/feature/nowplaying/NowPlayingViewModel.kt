package app.ipfsmedia.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.player.PlayerConnection
import app.ipfsmedia.core.player.PlayerUiState
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The player as the full-size view reads it, and which entries are favourites. */
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val connection: PlayerConnection,
    userData: UserDataRepository,
) : ViewModel() {
    val state: StateFlow<PlayerUiState> = connection.state
    val positionMs: StateFlow<Long> = connection.positionMs

    /** Ids of favourited entries (`record#chapter`), so the heart can show a song's own state. */
    val favouriteIds: StateFlow<Set<String>> =
        userData.favourites
            .map { rows -> rows.map { PlayableEntry.entryId(it.entry.recordKey, it.entry.chapterIndex) }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
