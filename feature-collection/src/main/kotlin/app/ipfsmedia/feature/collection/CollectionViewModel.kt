package app.ipfsmedia.feature.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.userdata.EntrySnapshot
import app.ipfsmedia.core.userdata.FavouriteEntity
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Collection's own state: grid or list (kept per identity), and the favourite songs. */
@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val userData: UserDataRepository,
) : ViewModel() {

    /** True for the list view. A grid until the user says otherwise. */
    val listView: StateFlow<Boolean> =
        userData.pref(PrefKeys.COLLECTION_VIEW)
            .map { it == VIEW_LIST }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Favourited songs, newest favourite first. Whole albums and videos are favourites too, but
     * those are items of the catalogue and the page already has them.
     */
    val favouriteSongs: StateFlow<List<FavouriteEntity>> =
        userData.favourites
            .map { all ->
                all.filter { it.entry.chapterIndex != EntrySnapshot.WHOLE_FILE }.sortedByDescending { it.addedAtMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setListView(list: Boolean) {
        viewModelScope.launch { userData.setPref(PrefKeys.COLLECTION_VIEW, if (list) VIEW_LIST else VIEW_GRID) }
    }

    private companion object {
        const val VIEW_LIST = "LIST"
        const val VIEW_GRID = "GRID"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
