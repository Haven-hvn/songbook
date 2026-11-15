package app.ipfsmedia.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ipfsmedia.core.design.ThemeMode
import app.ipfsmedia.core.library.formatBytes
import app.ipfsmedia.core.player.Downloads
import app.ipfsmedia.core.player.StorageUse
import app.ipfsmedia.core.social.RefreshCoordinator
import app.ipfsmedia.core.social.RefreshState
import app.ipfsmedia.core.userdata.PrefKeys
import app.ipfsmedia.core.userdata.UserDataRepository
import cloud.filecoin.foc.cache.FocChain
import dagger.hilt.android.lifecycle.HiltViewModel
import haven.mobile.core.cache.AUTOMATIC_QUOTA
import haven.mobile.core.cache.DEFAULT_SPOOL_BUDGET_BYTES
import haven.mobile.core.cache.MIN_SPOOL_BUDGET_BYTES
import haven.mobile.core.cache.mirror.SettingsRepository
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Every setting, as the page shows it. */
data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pictureInPicture: Boolean = true,
    /** Bytes, or [AUTOMATIC_QUOTA] for "size it from free space". */
    val quotaBytes: Long = AUTOMATIC_QUOTA,
    val ttlDays: Int = DEFAULT_TTL_DAYS,
    val keepUnlocked: Boolean = false,
    val clearOnDisconnect: Boolean = true,
    val spoolBudgetBytes: Long = DEFAULT_SPOOL_BUDGET_BYTES,
    /** Chains membership is checked on. Empty is read as "the mainnets", never "none". */
    val chains: Set<HavenChain> = emptySet(),
    /** Null is automatic. */
    val focNetwork: FocChain? = null,
) {
    companion object {
        const val DEFAULT_TTL_DAYS = 30
    }
}

/**
 * Settings: appearance, storage, networks, video, and the refresh.
 *
 * The storage settings are haven-mobile's, per identity, read by the cache itself; this only
 * writes them. Anything that deletes content goes through `Downloads`, which keeps what is
 * playing and puts every item's offline state right afterwards.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val userData: UserDataRepository,
    private val downloads: Downloads,
    private val wallet: WalletSession,
    private val coordinator: RefreshCoordinator,
) : ViewModel() {

    private data class Storage(
        val quotaBytes: Long,
        val ttlDays: Int,
        val keepUnlocked: Boolean,
        val clearOnDisconnect: Boolean,
        val spoolBudgetBytes: Long,
    )

    private val storage = combine(
        settings.cacheQuotaBytes,
        settings.cacheTtlDays,
        settings.keepUnlocked,
        settings.clearOnDisconnect,
        settings.spoolBudgetBytes,
    ) { quota, ttl, keep, clear, spool -> Storage(quota, ttl, keep, clear, spool) }

    private val networks = combine(settings.enabledChains, settings.focNetwork) { chains, foc -> chains to foc }

    val state: StateFlow<SettingsUiState> =
        combine(storage, networks, userData.prefs) { kept, (chains, foc), prefs ->
            SettingsUiState(
                theme = ThemeMode.of(prefs[PrefKeys.THEME]),
                // On unless it was turned off: leaving a playing video keeps it in a small window.
                pictureInPicture = prefs[PrefKeys.VIDEO_PIP] != "false",
                quotaBytes = kept.quotaBytes,
                ttlDays = kept.ttlDays,
                keepUnlocked = kept.keepUnlocked,
                clearOnDisconnect = kept.clearOnDisconnect,
                spoolBudgetBytes = kept.spoolBudgetBytes.coerceAtLeast(MIN_SPOOL_BUDGET_BYTES),
                chains = chains,
                focNetwork = foc,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    val refreshState: StateFlow<RefreshState> = coordinator.state

    /** What is on the device for this identity. Null until measured. */
    var use: StorageUse? by mutableStateOf(null)
        private set

    /** True while something is being deleted; the buttons that delete are off meanwhile. */
    var clearing: Boolean by mutableStateOf(false)
        private set

    /** What the last storage action did, said under the buttons. */
    var result: String? by mutableStateOf(null)
        private set

    init {
        measure()
    }

    fun measure() {
        viewModelScope.launch {
            use = runCatching { withContext(Dispatchers.IO) { downloads.storageUse() } }.getOrNull()
        }
    }

    fun setTheme(mode: ThemeMode) = pref(PrefKeys.THEME, mode.name)

    fun setPictureInPicture(enabled: Boolean) = pref(PrefKeys.VIDEO_PIP, enabled.toString())

    fun setQuotaBytes(bytes: Long) {
        viewModelScope.launch {
            settings.setCacheQuotaBytes(bytes)
            measure()
        }
    }

    fun setTtlDays(days: Int) {
        viewModelScope.launch { settings.setCacheTtlDays(days) }
    }

    fun setSpoolBudgetBytes(bytes: Long) {
        viewModelScope.launch { settings.setSpoolBudgetBytes(bytes.coerceAtLeast(MIN_SPOOL_BUDGET_BYTES)) }
    }

    fun setClearOnDisconnect(enabled: Boolean) {
        viewModelScope.launch { settings.setClearOnDisconnect(enabled) }
    }

    fun setKeepUnlocked(enabled: Boolean) {
        val address = wallet.address.value ?: return
        viewModelScope.launch {
            clearing = true
            settings.setKeepUnlocked(enabled)
            runCatching { downloads.unlockedModeChanged(address, enabled) }
            clearing = false
            result = if (enabled) {
                "From now on, what you unlock is kept unlocked on this device."
            } else {
                "Unlocked copies were deleted from this device."
            }
            measure()
        }
    }

    /** Adds or removes one chain. The last one cannot be removed: nothing would open. */
    fun setChain(chain: HavenChain, enabled: Boolean) {
        val current = state.value.chains.ifEmpty { HavenChain.mainnets.toSet() }
        val next = if (enabled) current + chain else current - chain
        if (next.isEmpty()) return
        viewModelScope.launch { settings.setEnabledChains(next) }
    }

    fun setFocNetwork(network: FocChain?) {
        viewModelScope.launch { settings.setFocNetwork(network) }
    }

    fun refreshNow() = coordinator.refresh()

    fun clearExpired() = clear("Expired content was removed.") { address -> downloads.clearExpired(address) }

    fun clearEverything() = clear(null) { address -> downloads.clearAll(address) }

    private fun clear(done: String?, action: suspend (String) -> Unit) {
        val address = wallet.address.value ?: return
        if (clearing) return
        viewModelScope.launch {
            clearing = true
            val before = use?.let { it.cacheUsedBytes + it.stagedBytes }
            val outcome = runCatching { withContext(Dispatchers.IO) { action(address) } }
            val after = runCatching { withContext(Dispatchers.IO) { downloads.storageUse() } }.getOrNull()
            use = after
            clearing = false
            result = when {
                outcome.isFailure -> "That could not be finished. Some content may still be on this device; try again."
                done != null -> done
                before != null && after != null ->
                    "Freed ${formatBytes((before - after.cacheUsedBytes - after.stagedBytes).coerceAtLeast(0L))}. " +
                        "What is playing was kept until it ends."
                else -> "Downloads and cached content were removed from this device."
            }
        }
    }

    private fun pref(key: String, value: String) {
        viewModelScope.launch { userData.setPref(key, value) }
    }
}
