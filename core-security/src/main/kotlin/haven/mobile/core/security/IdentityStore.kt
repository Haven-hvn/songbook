package haven.mobile.core.security

/**
 * Something that keeps data per identity and knows how to delete it.
 *
 * haven-mobile's cleanup lists its stores by hand, which works while every store lives in a module
 * `core-security` depends on. This app adds stores in modules that sit above it (playlists and
 * history, extracted artwork, the saved queue, cached discussion), so they register themselves
 * here through a Hilt set binding and [SecurityCleanupImpl] runs whatever is in the set.
 *
 * Not in haven-mobile.
 */
interface IdentityStore {
    /** Step name in the [DisconnectReport]. */
    val name: String

    /**
     * True for anything derived from unlocked content (artwork taken from a file, the queue):
     * it goes on every disconnect, whatever the user chose about cached content. False for data
     * the user made (playlists, history, follows): it goes only when the identity is removed.
     */
    val wipeOnDisconnect: Boolean

    /** Lower runs earlier. Playback stops first, so nothing is reading a file about to be deleted. */
    val order: Int get() = 0

    suspend fun wipe(walletAddress: String)
}
