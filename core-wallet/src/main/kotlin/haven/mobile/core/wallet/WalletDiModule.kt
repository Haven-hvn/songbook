package haven.mobile.core.wallet

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Return link the wallet uses to hand control back. Must match the manifest's intent filter. */
const val WALLET_REDIRECT_URL = "ipfsmedia://connect"
const val WALLET_APP_NAME = "Googlebook IPFSMedia"
const val WALLET_APP_DESCRIPTION = "Music and video from DataDAOs, played from IPFS"

@Module
@InstallIn(SingletonComponent::class)
abstract class WalletDiModule {
    @Binds
    @Singleton
    abstract fun bindWalletSession(impl: WalletSessionImpl): WalletSession
}

@Module
@InstallIn(SingletonComponent::class)
object WalletDataStoreModule {
    @Provides
    @Singleton
    fun provideWalletDataStore(@ApplicationContext context: Context): WalletDataStore = WalletDataStore(context)

    /**
     * The WalletConnect project id comes from `wallet.projectId` in `local.properties` (in CI, the
     * `WALLET_PROJECT_ID` secret) and is never written into the source. Blank means the wallet
     * cannot connect; onboarding says so instead of failing like a network error.
     *
     * The name and return link differ from haven-mobile's (`Haven`, `haven://connect`) so both
     * apps can be installed on one device without competing for the same link.
     */
    @Provides
    @Singleton
    fun provideWalletConfig(): WalletConfig = WalletConfig(
        projectId = BuildConfig.WALLET_PROJECT_ID.trim(),
        appName = WALLET_APP_NAME,
        appDescription = WALLET_APP_DESCRIPTION,
        appIconUrl = "",
        redirectUrl = WALLET_REDIRECT_URL,
        appUrl = BuildConfig.WALLET_APP_URL.trim(),
    )
}
