package app.ipfsmedia.core.social

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import haven.mobile.core.security.IdentityStore
import javax.inject.Inject
import javax.inject.Singleton

/** The cached discussion is a copy of public records; it goes when the identity is removed. */
@Singleton
class SocialIdentityStore @Inject constructor(
    private val boardStore: BoardStore,
) : IdentityStore {
    override val name: String = "discussion_cache"
    override val wipeOnDisconnect: Boolean = false

    override suspend fun wipe(walletAddress: String) {
        boardStore.delete(walletAddress)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SocialDiModule {
    @Binds
    @Singleton
    abstract fun bindSocialActions(impl: UnavailableSocialActions): SocialActions

    @Binds
    @IntoSet
    abstract fun bindSocialIdentityStore(impl: SocialIdentityStore): IdentityStore
}
