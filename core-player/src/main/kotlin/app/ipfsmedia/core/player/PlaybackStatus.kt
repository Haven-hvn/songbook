package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.UserMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Why the queue has stopped at a row and is waiting for the user. */
sealed interface Blocked {
    val recordKey: String
    val title: String

    /**
     * The row needs a wallet signature. The queue waits here, however it got here, so a signing
     * prompt only ever follows the user saying yes to this explanation.
     */
    data class NeedsSignature(
        override val recordKey: String,
        override val title: String,
        val daoKey: String?,
    ) : Blocked

    /** The row could not be played and there was nothing else to move on to. */
    data class Failed(
        override val recordKey: String,
        override val title: String,
        val message: UserMessage,
    ) : Blocked
}

/** Something that happened in the background and is worth one line: a row that was stepped over. */
data class PlayerNotice(val text: String)

/**
 * What the playback service has to say beyond what Media3's session carries. The service and the
 * window live in one process, so this is shared memory; nothing here crosses to other apps.
 */
@Singleton
class PlaybackStatus @Inject constructor() {
    private val _blocked = MutableStateFlow<Blocked?>(null)
    val blocked: StateFlow<Blocked?> = _blocked

    private val _notices = MutableSharedFlow<PlayerNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<PlayerNotice> = _notices

    internal fun block(reason: Blocked) {
        _blocked.value = reason
    }

    internal fun clear() {
        _blocked.value = null
    }

    internal fun notify(notice: PlayerNotice) {
        _notices.tryEmit(notice)
    }
}
