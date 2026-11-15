package app.ipfsmedia.core.userdata

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import haven.mobile.core.security.IdentityStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the user database with the identity cleanup. It is user-made data, so it survives a
 * disconnect and is deleted only when the identity is removed.
 */
@Singleton
class UserDataIdentityStore @Inject constructor(
    private val databases: UserDatabases,
) : IdentityStore {
    override val name: String = "user_data"
    override val wipeOnDisconnect: Boolean = false

    override suspend fun wipe(walletAddress: String) {
        databases.delete(walletAddress)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataDiModule {
    @Binds
    @IntoSet
    abstract fun bindUserDataIdentityStore(impl: UserDataIdentityStore): IdentityStore
}
