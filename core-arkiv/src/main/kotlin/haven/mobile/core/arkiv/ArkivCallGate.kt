package haven.mobile.core.arkiv

import haven.mobile.core.domain.error.HavenError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every `arkiv_query` passes through here, so the read budget is spent in one place.
 *
 * Arkiv's public endpoint allows roughly a hundred charged queries an hour per address. The rules
 * are the ones redhaven's browser store settled on (`apps/web/src/lib/arkiv.ts`), minus its polling:
 *
 *  - at most [MAX_CONCURRENT] queries at once;
 *  - identical queries in flight collapse into one, and an identical query within
 *    [RESULT_TTL_MILLIS] is answered from memory, so one refresh never pays twice for a page;
 *  - nothing is retried here: a failure goes back to the caller, who decides;
 *  - a 429, or the quota header reaching [QUOTA_BLOCK_PERCENT], starts a cooldown during which
 *    every call fails immediately with [HavenError.RateLimited] instead of spending more budget.
 *
 * Not in haven-mobile, which calls Arkiv directly.
 */
@Singleton
class ArkivCallGate(private val now: () -> Long) {

    @Inject
    constructor() : this({ System.currentTimeMillis() })

    data class State(
        /** Queries sent in the last hour by this process. */
        val callsLastHour: Int = 0,
        /** Epoch millis until which calls are refused; 0 when there is no cooldown. */
        val cooldownUntilMillis: Long = 0L,
        /** Last `Arkiv-Quota-Used-Percent` the node reported, if it reported one. */
        val quotaUsedPercent: Int? = null,
        val lastCallAtMillis: Long = 0L,
    ) {
        fun isCoolingDown(nowMillis: Long): Boolean = cooldownUntilMillis > nowMillis
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private class Entry(val atMillis: Long, val value: Any?)

    private val permits = Semaphore(MAX_CONCURRENT)
    private val results = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Any>()
    private val callTimes = ArrayDeque<Long>()

    /**
     * Runs [block] under the budget rules, keyed by [key] (the query text, limit and cursor).
     * Blocking, like the HTTP call it wraps; call it from an IO thread.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> call(key: String, block: () -> T): T {
        refuseDuringCooldown()
        val lock = locks.computeIfAbsent(key) { Any() }
        synchronized(lock) {
            results[key]?.takeIf { now() - it.atMillis < RESULT_TTL_MILLIS }?.let { return it.value as T }
            permits.acquire()
            try {
                refuseDuringCooldown()
                noteCall()
                val value = block()
                results[key] = Entry(now(), value)
                dropStaleResults()
                return value
            } finally {
                permits.release()
            }
        }
    }

    /** Forget cached answers, so an explicit refresh reads the chain again. */
    fun invalidate() {
        results.clear()
    }

    /**
     * Feed back what the node said. [retryAfterSeconds] and [quotaUsedPercent] come from the
     * `Retry-After` and `Arkiv-Quota-Used-Percent` headers when present.
     */
    fun noteResponse(httpCode: Int, retryAfterSeconds: Long?, quotaUsedPercent: Int?) {
        val current = _state.value
        var cooldown = current.cooldownUntilMillis
        if (httpCode == HTTP_TOO_MANY_REQUESTS) {
            val wait = retryAfterSeconds?.takeIf { it > 0 } ?: DEFAULT_COOLDOWN_SECONDS
            cooldown = maxOf(cooldown, now() + wait * 1_000)
        } else if (quotaUsedPercent != null && quotaUsedPercent >= QUOTA_BLOCK_PERCENT) {
            cooldown = maxOf(cooldown, now() + DEFAULT_COOLDOWN_SECONDS * 1_000)
        }
        _state.value = current.copy(
            cooldownUntilMillis = cooldown,
            quotaUsedPercent = quotaUsedPercent ?: current.quotaUsedPercent,
        )
    }

    /** Start (or extend) a cooldown when the node refused in the response body rather than by status. */
    fun noteRateLimited(retryAfterSeconds: Long? = null) {
        noteResponse(HTTP_TOO_MANY_REQUESTS, retryAfterSeconds, null)
    }

    /** The error to throw while cooling down. */
    fun rateLimited(): HavenError.RateLimited {
        val until = _state.value.cooldownUntilMillis.takeIf { it > now() }
            ?: (now() + DEFAULT_COOLDOWN_SECONDS * 1_000)
        return HavenError.RateLimited(retryAtEpochMillis = until)
    }

    private fun refuseDuringCooldown() {
        if (_state.value.isCoolingDown(now())) throw rateLimited()
    }

    private fun noteCall() {
        val at = now()
        val count = synchronized(callTimes) {
            callTimes.addLast(at)
            while (callTimes.isNotEmpty() && at - callTimes.first() > HOUR_MILLIS) callTimes.removeFirst()
            callTimes.size
        }
        _state.value = _state.value.copy(callsLastHour = count, lastCallAtMillis = at)
    }

    private fun dropStaleResults() {
        val cutoff = now() - RESULT_TTL_MILLIS
        results.entries.removeIf { it.value.atMillis < cutoff }
        if (locks.size > MAX_LOCKS) locks.keys.retainAll(results.keys)
    }

    companion object {
        const val MAX_CONCURRENT = 2
        const val RESULT_TTL_MILLIS = 60_000L
        const val QUOTA_BLOCK_PERCENT = 95
        const val DEFAULT_COOLDOWN_SECONDS = 60L
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HOUR_MILLIS = 3_600_000L
        private const val MAX_LOCKS = 512
    }
}
