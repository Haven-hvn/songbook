package app.ipfsmedia.core.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import haven.mobile.core.cache.SpoolPins
import haven.mobile.core.security.IdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops playback and empties the queue. Runs before every other store ([order]), so the player
 * has let go of its file before the spool that holds it is deleted.
 */
@Singleton
class PlaybackIdentityStore @Inject constructor(
    private val host: PlaybackHost,
    private val stager: MediaStager,
    private val spoolPins: SpoolPins,
    private val status: PlaybackStatus,
) : IdentityStore {
    override val name: String = "playback"
    override val wipeOnDisconnect: Boolean = true
    override val order: Int = -100

    override suspend fun wipe(walletAddress: String) {
        host.stopAndClear()
        stager.forgetAll()
        spoolPins.set(emptyList())
        status.clear()
    }
}

/** The saved queue names what the wallet was listening to, so it goes on every disconnect. */
@Singleton
class QueueIdentityStore @Inject constructor(
    private val store: QueueStore,
) : IdentityStore {
    override val name: String = "queue"
    override val wipeOnDisconnect: Boolean = true
    override val order: Int = -50

    override suspend fun wipe(walletAddress: String) {
        withContext(Dispatchers.IO) { store.delete(walletAddress) }
    }
}

/** Pictures taken out of unlocked files go with the identity that was allowed to see them. */
@Singleton
class ArtworkIdentityStore @Inject constructor(
    private val artwork: ArtworkStore,
) : IdentityStore {
    override val name: String = "artwork"
    override val wipeOnDisconnect: Boolean = true

    override suspend fun wipe(walletAddress: String) {
        artwork.wipe(walletAddress)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerDiModule {
    @Binds
    @IntoSet
    abstract fun bindPlaybackIdentityStore(impl: PlaybackIdentityStore): IdentityStore

    @Binds
    @IntoSet
    abstract fun bindQueueIdentityStore(impl: QueueIdentityStore): IdentityStore

    @Binds
    @IntoSet
    abstract fun bindArtworkIdentityStore(impl: ArtworkIdentityStore): IdentityStore
}
