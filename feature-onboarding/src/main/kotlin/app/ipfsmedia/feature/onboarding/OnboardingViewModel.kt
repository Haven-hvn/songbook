package app.ipfsmedia.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.library.UserMessage
import app.ipfsmedia.core.library.describeError
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.wallet.WalletConfig
import haven.mobile.core.wallet.WalletError
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Connecting the first wallet. There is nothing else to do until one is connected. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val wallet: WalletSession,
    config: WalletConfig,
) : ViewModel() {

    /** False when the build has no WalletConnect project id: connecting cannot work at all. */
    val configured: Boolean = config.projectId.isNotBlank()

    /** The `wc:` link waiting for a wallet to approve it, for the copy-link fallback. */
    val pairingUri: StateFlow<String?> = wallet.pairingUri

    /** What the wallet stack has been doing, newest last, for reporting a failed connection. */
    val diagnostics: StateFlow<List<String>> = wallet.diagnostics

    var connecting: Boolean by mutableStateOf(false)
        private set

    var error: UserMessage? by mutableStateOf(null)
        private set

    fun connect() {
        if (connecting) return
        connecting = true
        error = null
        viewModelScope.launch {
            wallet.connect().onFailure { error = it.asWalletMessage() }
            connecting = false
        }
    }
}

/** A wallet failure in the user's words. */
internal fun Throwable.asWalletMessage(): UserMessage = when (this) {
    is WalletError.AppKitNotInitialized -> describeError("APPKIT_NOT_INITIALIZED")
    is WalletError.ConnectFailed -> UserMessage(
        what = "The wallet did not connect.",
        todo = "Open MetaMask, approve the connection, and try again. If no wallet opened, copy the link and paste it into your wallet.",
        code = "CONNECT_FAILED",
    )
    is WalletError.NoAddressReturned -> describeError("NO_ADDRESS_RETURNED")
    else -> describeError("INTERNAL", message)
}
