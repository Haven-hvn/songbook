package haven.mobile.core.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The user's cache preferences, as the cache sees them.
 *
 * An inversion, and for a concrete reason: the preferences live in `SettingsRepository` inside
 * `:core-cache-mirror`, and that module already depends on this one — so reaching for it directly
 * here would be a dependency cycle. `:core-cache` declares what it needs, `:core-cache-mirror`
 * supplies it.
 *
 * Without this the quota and TTL sliders wrote to DataStore and were never read: `foc`'s `Config` was
 * built from static defaults, so the two controls in Settings did nothing at all.
 */
interface CacheSettingsSource {
    /** Bytes, or [AUTOMATIC_QUOTA] to let foc size the cache from free space. */
    val quotaBytes: Flow<Long>
    val ttlDays: Flow<Int>

    /**
     * "Keep unlocked content on this device": gated pieces are stored decrypted (see
     * `decryptOnStore`) instead of as ciphertext. Off by default.
     */
    val keepUnlocked: Flow<Boolean>

    /**
     * Budget for staged plaintext ([PlaintextSpool]), in bytes. A constant 1 GiB in haven-mobile;
     * a setting here, because one long video on a laptop is larger than that.
     */
    val spoolBudgetBytes: Flow<Long> get() = flowOf(DEFAULT_SPOOL_BUDGET_BYTES)
}

/** Default staged-plaintext budget: 8 GiB. */
const val DEFAULT_SPOOL_BUDGET_BYTES: Long = 8L * 1024 * 1024 * 1024

/** Smallest staged-plaintext budget the app accepts: 1 GiB, haven-mobile's constant. */
const val MIN_SPOOL_BUDGET_BYTES: Long = 1L * 1024 * 1024 * 1024

/** [CacheSettingsSource.quotaBytes] value meaning "size automatically" (foc `AutoQuota`). */
const val AUTOMATIC_QUOTA: Long = 0L
