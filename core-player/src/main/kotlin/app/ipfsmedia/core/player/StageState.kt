package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.UserMessage
import app.ipfsmedia.core.library.describeError
import cloud.filecoin.foc.cache.PieceUnavailableException
import haven.mobile.core.domain.error.HavenError
import java.io.IOException

/** What the fetch-and-unlock pipeline is doing for one upload, in the words the user reads. */
enum class StageStep(val label: String) {
    /** A key is being asked for. "Gate" is Haven's word; the user is waiting on an access check. */
    CHECKING_ACCESS("Checking your access…"),

    /** The user agreed to sign, and the request is with their wallet. */
    WAITING_FOR_WALLET("Waiting for your wallet…"),

    /**
     * The file is coming from a storage provider. `foc-cache` reports nothing until the whole
     * piece has landed, so this stage never carries a fraction (docs/PLAN.md §10).
     */
    FETCHING("Fetching…"),

    /** Decrypting to the staging file. Byte counts are real here. */
    UNLOCKING("Unlocking…"),

    /** Copying an upload that has no gate to the staging file. Nothing is decrypted. */
    PREPARING("Preparing…"),
}

/** Where one upload stands on its way to being playable. Absent from the map means: nothing to say. */
sealed interface StageState {
    data class Working(
        val step: StageStep,
        val writtenBytes: Long? = null,
        val totalBytes: Long? = null,
    ) : StageState {
        /**
         * 0..1 while bytes are being written and the size is known; null otherwise, and always
         * null while fetching. Plaintext is slightly smaller than ciphertext, so this can reach
         * 1 a moment early, which reads better than stalling at 97%.
         */
        val fraction: Float?
            get() {
                if (step != StageStep.UNLOCKING && step != StageStep.PREPARING) return null
                val written = writtenBytes ?: return null
                val total = totalBytes?.takeIf { it > 0 } ?: return null
                return (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
    }

    /**
     * Opening this needs a wallet signature, and the user has not agreed to one yet. Nothing is
     * sent to the wallet until they do: a signing prompt with no explanation is how people learn
     * to sign blind.
     */
    data class NeedsSignature(val daoKey: String?) : StageState

    data class Failed(val message: UserMessage) : StageState
}

/**
 * A staging failure. An [IOException] because it is thrown through Media3's data source, which
 * only lets those through; the player finds it again in the cause chain of its own error.
 */
class StagingException(
    val recordKey: String,
    val code: String,
    val detail: String? = null,
    cause: Throwable? = null,
) : IOException("$code: ${detail ?: "staging failed"}", cause) {
    val userMessage: UserMessage get() = describeError(code, detail)

    companion object {
        const val SIGNATURE_REQUIRED = "SIGNATURE_REQUIRED"
        const val RECORD_GONE = "RECORD_GONE"
        const val SONG_CHANGED = "SONG_CHANGED"
        const val PIECE_UNAVAILABLE = "PIECE_UNAVAILABLE"
        const val NOT_A_MEMBER = "GATE_VERIFICATION_FAILED"
        const val NOT_OPEN_YET = "MARKET_CAP_NOT_REACHED"
        const val OUT_OF_CYCLES = "CANISTER_OUT_OF_CYCLES"
    }
}

/** The first [StagingException] in this throwable's cause chain, if staging is why it failed. */
fun Throwable.stagingFailure(): StagingException? =
    generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).filterIsInstance<StagingException>().firstOrNull()

/**
 * Maps anything the pipeline throws onto a stable code. A provider-side "unavailable" keeps its
 * meaning through the cache and spool wrappers; a dry key service is told apart from a wallet
 * problem, because the fix is different and neither is the user's fault.
 */
internal fun Throwable.toStagingException(recordKey: String, fallbackCode: String = "INTERNAL"): StagingException {
    if (this is StagingException) return this
    val chain = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
    if (chain.any { it is PieceUnavailableException }) {
        return StagingException(recordKey, StagingException.PIECE_UNAVAILABLE, null, this)
    }
    if (isOutOfCycles()) return StagingException(recordKey, StagingException.OUT_OF_CYCLES, null, this)
    val haven = chain.filterIsInstance<HavenError>().firstOrNull()
    return if (haven != null) {
        StagingException(recordKey, haven.code, haven.message, this)
    } else {
        StagingException(recordKey, fallbackCode, message, this)
    }
}

/** True when the key service itself is dry, rather than the wallet or the gate being at fault. */
internal fun Throwable.isOutOfCycles(): Boolean {
    val haystack = ((message ?: "") + " " + (cause?.message ?: "")).lowercase()
    return "out of cycles" in haystack || "out-of-cycles" in haystack || "insufficient cycles" in haystack
}

private const val MAX_CAUSE_DEPTH = 12
