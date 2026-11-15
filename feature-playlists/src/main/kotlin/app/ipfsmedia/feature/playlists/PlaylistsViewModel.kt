package app.ipfsmedia.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.userdata.PlaylistEntity
import app.ipfsmedia.core.userdata.PlaylistEntryEntity
import app.ipfsmedia.core.userdata.PlaylistSummary
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One playlist as its page reads it. [playlist] is null when it does not exist (any more). */
data class PlaylistContent(
    val playlist: PlaylistEntity?,
    val entries: List<PlaylistEntryEntity>,
)

/**
 * Reads playlists. Every change to one (adding, removing, reordering, renaming, deleting) goes
 * through the app's one action dispatcher, so each has an Undo; nothing is written from here.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val userData: UserDataRepository,
) : ViewModel() {

    /** Every playlist with its size, in the user's order. Null until first read. */
    val playlists: StateFlow<List<PlaylistSummary>?> =
        userData.playlists
            .map<List<PlaylistSummary>, List<PlaylistSummary>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The playlist with [id] and its rows in order. */
    fun content(id: Long): Flow<PlaylistContent> =
        combine(userData.playlist(id), userData.playlistEntries(id)) { playlist, entries ->
            PlaylistContent(playlist, entries)
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
