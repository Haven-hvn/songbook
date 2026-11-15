package app.ipfsmedia.player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import haven.mobile.core.wallet.WalletSession
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class IpfsMediaApp : Application() {

    /**
     * Injected here, not first in the activity, so the wallet stack is initialised and the
     * stored identity is being read before the window exists. The activity registers itself
     * with AppKit in `onCreate`, which fails if AppKit has not been initialised by then.
     */
    @Inject
    lateinit var walletSession: WalletSession

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        CrashNote.install(this)
        Timber.i("wallet session ready: %s", walletSession.javaClass.simpleName)
    }
}
