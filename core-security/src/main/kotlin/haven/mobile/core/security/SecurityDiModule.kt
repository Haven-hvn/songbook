package haven.mobile.core.security

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityDiModule {
    @Binds
    @Singleton
    abstract fun bindSecurityCleanup(impl: SecurityCleanupImpl): SecurityCleanup

    /** Declared so the set exists even when no module contributes to it. */
    @Multibinds
    abstract fun identityStores(): Set<IdentityStore>
}
