package app.ipfsmedia.core.library

import haven.mobile.core.domain.error.HavenError
import java.text.DateFormat
import java.util.Date

/**
 * An error as the user reads it: what happened, and what to do about it.
 *
 * Parachord's design audit asks for exactly this pair on every error, and the copied modules
 * report stable codes, so the wording lives here, once, instead of in each screen.
 */
data class UserMessage(
    val what: String,
    /** What to do next. Null only when there is genuinely nothing the user can do. */
    val todo: String?,
    /** Stable code for diagnostics; not shown as the message. */
    val code: String,
)

/** The message for a failure code from the fetch-and-unlock pipeline. */
fun describeError(code: String, detail: String? = null, retryAtMillis: Long? = null): UserMessage = when (code) {
    "WALLET_NOT_CONNECTED" -> UserMessage(
        "No wallet is connected.",
        "Connect your wallet again from the identity menu, then retry.",
        code,
    )
    "PIECE_UNAVAILABLE" -> UserMessage(
        "No storage provider can serve this right now.",
        "Try again later. If it was downloaded before, it still plays offline.",
        code,
    )
    "RATE_LIMITED" -> UserMessage(
        "The index has reached its hourly limit for this connection.",
        retryAtMillis?.let { "Refresh again after ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}." }
            ?: "Refresh again in a little while.",
        code,
    )
    "NETWORK_ERROR" -> UserMessage(
        // `ArkivClientImpl.networkError` puts the host, failing call, exception class, and
        // root cause in the message. Dropping it here is why a rejected RPC query read to
        // users (and to us) as a plain "no connectivity" banner; show what actually failed.
        detail?.takeIf { it.isNotBlank() } ?: "The network could not be reached.",
        "What is already on this device still plays.",
        code,
    )
    "CANISTER_OUT_OF_CYCLES" -> UserMessage(
        "The key service is out of cycles, so nothing can be unlocked right now.",
        "This is not about your wallet. It works again once the service is topped up.",
        code,
    )
    "CANISTER_CALL_FAILED" -> UserMessage(
        "The key service did not answer.",
        "Try again in a moment.",
        code,
    )
    "SIGNING_FAILED", "INVALID_SIGNATURE_FORMAT" -> UserMessage(
        "The wallet did not sign the request.",
        "Open your wallet, approve the message, and try again. It is a signature only: no transaction and no fee.",
        code,
    )
    "GATE_VERIFICATION_FAILED" -> UserMessage(
        "This wallet is not a member of the DataDAO this belongs to.",
        "See the DataDAO's About tab for what membership requires.",
        code,
    )
    "MARKET_CAP_NOT_REACHED" -> UserMessage(
        "This is not open yet.",
        "It opens for every member once its DataDAO reaches the set target.",
        code,
    )
    "PLAYBACK_DECRYPT_FAILED" -> UserMessage(
        detail?.takeIf { it.isNotBlank() } ?: "The file could not be unlocked.",
        "Remove the download and fetch it again.",
        code,
    )
    "PLAYBACK_CODEC_UNSUPPORTED" -> UserMessage(
        "This device cannot play this format.",
        null,
        code,
    )
    "CACHE_QUOTA_EXCEEDED" -> UserMessage(
        "The download cache is full.",
        "Free some space in Settings, under Storage, and try again.",
        code,
    )
    "CACHE_WRITE_FAILED" -> UserMessage(
        detail?.takeIf { it.isNotBlank() } ?: "The file could not be written to this device.",
        "Check free storage space and try again.",
        code,
    )
    "CACHE_READ_FAILED" -> UserMessage(
        detail?.takeIf { it.isNotBlank() } ?: "The file could not be read from this device.",
        "Check free storage space and try again.",
        code,
    )
    "NO_PIECE_REF" -> UserMessage(
        "This upload has no stored file.",
        null,
        code,
    )
    "UNSUPPORTED_GATE_METADATA" -> UserMessage(
        "This upload is locked in a way this version cannot open.",
        "Update the app and try again.",
        code,
    )
    "APPKIT_NOT_INITIALIZED", "CONNECT_FAILED", "NO_ADDRESS_RETURNED" -> UserMessage(
        "The wallet connection could not be started.",
        "Make sure MetaMask is installed, then try again.",
        code,
    )
    "RECORD_GONE" -> UserMessage(
        "This upload is no longer in the index.",
        "Its record has expired or was removed. Refresh to see what is there now.",
        code,
    )
    "SONG_CHANGED" -> UserMessage(
        "This album was uploaded again and the song could not be found in the new file.",
        "Open the album and add the song again.",
        code,
    )
    "SIGNATURE_REQUIRED" -> UserMessage(
        "Opening this needs one signature from your wallet.",
        "It proves membership of the DataDAO. It is a signature only: no transaction and no fee.",
        code,
    )
    "PLAYBACK_FILE_MISSING" -> UserMessage(
        "The file was cleared from this device while it was queued.",
        "Play it again to fetch it.",
        code,
    )
    "NO_KEY_AVAILABLE" -> UserMessage(
        "No key was returned for this upload.",
        "Try again in a moment.",
        code,
    )
    else -> UserMessage(
        detail?.takeIf { it.isNotBlank() } ?: "Something went wrong.",
        "Try again.",
        code,
    )
}

/** The message for any throwable; [HavenError]s map by code, anything else by its text. */
fun Throwable.toUserMessage(): UserMessage = when (this) {
    is HavenError.RateLimited -> describeError(code, message, retryAtEpochMillis)
    is HavenError -> describeError(code, message)
    else -> describeError("INTERNAL", message)
}
