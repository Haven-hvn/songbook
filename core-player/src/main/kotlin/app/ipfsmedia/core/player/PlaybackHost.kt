package app.ipfsmedia.core.player

import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A handle on the service's player for the one caller that cannot go through a controller: the
 * identity cleanup, which has to stop playback and empty the queue *before* it deletes the files
 * being played, whether or not a window is open.
 */
@Singleton
class PlaybackHost @Inject constructor() {
    @Volatile
    private var player: Player? = null

    internal fun attach(player: Player) {
        this.player = player
    }

    internal fun detach(player: Player) {
        if (this.player === player) this.player = null
    }

    /** Stops playback and empties the queue. Does nothing when the service is not running. */
    suspend fun stopAndClear() {
        withContext(Dispatchers.Main.immediate) {
            player?.run {
                stop()
                clearMediaItems()
            }
        }
    }
}
