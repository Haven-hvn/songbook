package app.ipfsmedia.feature.identity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.security.DisconnectReport
import haven.mobile.core.security.SecurityCleanup
import haven.mobile.core.wallet.WalletIdentity
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Identities: one per wallet, each with its own cache, mirror, keys, playlists, history and
 * settings, fully isolated from the others (docs/PLAN.md §4).
 *
 * Switching clears nothing. Disconnecting ends the wallet session and removes what was derived
 * from unlocked content (keys, staged files, the queue, artwork) while keeping what the user
 * made. Removing an identity deletes everything it has on this device.
 */
@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val wallet: WalletSession,
    private val cleanup: SecurityCleanup,
) : ViewModel() {

    val identities: StateFlow<List<WalletIdentity>> = wallet.identities
    val active: StateFlow<WalletIdentity?> = wallet.activeIdentity
    val isLive: StateFlow<Boolean> = wallet.isLive

    /** Something is in progress: "Waiting for your wallet…", "Removing…". Null when idle. */
    var busy: String? by mutableStateOf(null)
        private set

    /** The outcome of the last action, when there is something to tell. */
    var message: String? by mutableStateOf(null)
        private set

    fun dismissMessage() {
        message = null
    }

    fun switchTo(address: String) = act("Switching…") {
        wallet.switchTo(address).onFailure { message = "Could not switch: ${it.message ?: "unknown identity"}" }
    }

    /** Re-establishes a wallet session for the active identity, so it can sign again. */
    fun reconnect() = act("Waiting for your wallet…") {
        wallet.reconnect()
            .onSuccess { message = "Wallet connected." }
            .onFailure { message = "The wallet did not connect. Open it, approve the connection, and try again." }
    }

    fun addWallet() = act("Waiting for your wallet…") {
        wallet.connectNewIdentity()
            .onFailure { message = "The wallet did not connect. Open it, approve the connection, and try again." }
    }

    fun rename(address: String, label: String) = act(null) {
        wallet.renameIdentity(address, label)
    }

    /** Ends the wallet session and removes keys, staged files, the queue and artwork. */
    fun disconnect(address: String) = act("Disconnecting…") {
        message = summarise(cleanup.runDisconnect(address), done = "Wallet disconnected. Your playlists, history and follows are still here.")
    }

    /** Deletes everything this identity has on the device. */
    fun remove(address: String) = act("Removing…") {
        message = summarise(cleanup.removeIdentity(address), done = "Identity removed from this device.")
    }

    private fun act(label: String?, block: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = null
            }
        }
    }

    /**
     * Every clean-up step runs even if an earlier one failed, and a partial wipe is exactly the
     * case the user must be told about, so failed steps are named.
     */
    private fun summarise(report: DisconnectReport, done: String): String {
        if (report.overallOk) return done
        val failed = report.steps.filterNot { it.ok }.joinToString(", ") { it.name.replace('_', ' ') }
        return "Some of it could not be removed: $failed. Try again; nothing else was left half done."
    }
}
