package app.ipfsmedia.core.social

import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Membership receipts on discussion records.
 *
 * Every thread and comment carries its author's receipt: the Haven-AOL canister's Ed25519
 * signature saying "this address held at least the DataDAO's threshold at this time". Readers
 * check it offline, so a post can be marked as coming from a member without anyone asking a
 * chain for a balance.
 *
 * This is a port of redhaven's `packages/vote-core/src/attest.ts` (`verifyMemberReceipt`), and
 * the binding strings are pinned by the same fixture (`receipt-vectors.json`) in this module's
 * tests, so the two implementations verify the same bytes:
 *
 * ```
 * signed   = HAVEN_ATTEST_V1:{chain}:{token}:{threshold}:{holder}:{cidHash}:{at}:{bal}
 * cidHash  = sha256("HAVEN_MEMBER_V1:" + gateSpec + ":" + epoch)
 * gateSpec = gate:{chainId}:{token}:{threshold}
 * ```
 */
data class MemberReceipt(
    /** 30-day epoch the receipt was issued in. */
    val epoch: Long,
    /** Unix seconds when the canister checked the balance. */
    val at: Long,
    /** Balance at the check, in the token's raw units, as decimal text. */
    val bal: String,
    /** Ed25519 signature, 128 hex characters, lowercase, no `0x`. */
    val sig: String,
)

/** The gate a discussion record was written under, as the receipt spells it. */
data class GateSpec(
    val chainId: Long,
    /** Lowercased `0x` address. */
    val token: String,
    /** Raw `balanceOf` units, greater than zero. */
    val threshold: BigInteger,
) {
    /** `gate:8453:0xabc…:1`, the form hashed into a receipt. */
    val spec: String get() = "gate:$chainId:$token:$threshold"

    companion object {
        private val ADDRESS = Regex("^0x[0-9a-f]{40}$")

        /** Null unless the three parts form a gate the canister could have attested. */
        fun of(chainId: Long?, token: String?, threshold: String?): GateSpec? {
            val chain = chainId?.takeIf { it > 0 } ?: return null
            val address = token?.trim()?.lowercase()?.takeIf { ADDRESS.matches(it) } ?: return null
            val amount = threshold?.trim()?.takeIf { DECIMAL.matches(it) }?.let { BigInteger(it) }
                ?.takeIf { it.signum() > 0 } ?: return null
            return GateSpec(chain, address, amount)
        }
    }
}

/** What a reader can say about a post's receipt. */
enum class ReceiptStatus {
    /** The canister's signature checks out: the author was a member when they posted. */
    VERIFIED,

    /** There is no receipt, or it does not verify. The post is shown, and marked. */
    FAILED,

    /**
     * Not checked: the canister's key has not been fetched (offline since install), or this
     * device has no Ed25519 support. Says nothing about the author either way.
     */
    UNCHECKED,
}

const val EPOCH_LENGTH_SECONDS = 30L * 24 * 3600
const val RECEIPT_TTL_SECONDS = EPOCH_LENGTH_SECONDS
const val RECEIPT_FUTURE_SLACK_SECONDS = 3_600L

private const val MEMBER_RECEIPT_PREFIX = "HAVEN_MEMBER_V1"
private const val ATTEST_PREIMAGE_PREFIX = "HAVEN_ATTEST_V1"
private val HEX128 = Regex("^(0x)?[0-9a-fA-F]{128}$")
private val DECIMAL = Regex("^(0|[1-9][0-9]{0,77})$")

/** The canister's chain variant names, by EIP-155 id. Chains it cannot attest on are absent. */
internal val EIP155_TO_VARIANT: Map<Long, String> = mapOf(
    1L to "EthMainnet",
    11155111L to "EthSepolia",
    42161L to "ArbitrumOne",
    8453L to "BaseMainnet",
    10L to "OptimismMainnet",
)

fun epochOf(unixSeconds: Long): Long = Math.floorDiv(unixSeconds, EPOCH_LENGTH_SECONDS)

/** Lowercase hex sha256 of the binding string the canister is asked to sign over. */
fun memberCidHash(gateSpec: String, epoch: Long): String =
    sha256Hex("$MEMBER_RECEIPT_PREFIX:$gateSpec:$epoch")

/** The exact text the canister signs. */
fun attestPreimage(
    chainVariant: String,
    token: String,
    threshold: BigInteger,
    holder: String,
    cidHash: String,
    at: Long,
    bal: String,
): String = "$ATTEST_PREIMAGE_PREFIX:$chainVariant:$token:$threshold:$holder:$cidHash:$at:$bal"

/** Reads the `attn` object of a record's payload. Null when it is not shaped like a receipt. */
fun parseReceipt(epoch: Any?, at: Any?, bal: Any?, sig: Any?): MemberReceipt? {
    val epochValue = (epoch as? Number)?.toLong()?.takeIf { it >= 0 } ?: return null
    val atValue = (at as? Number)?.toLong()?.takeIf { it >= 0 } ?: return null
    val balance = (bal as? String)?.takeIf { DECIMAL.matches(it) } ?: return null
    val signature = (sig as? String)?.takeIf { HEX128.matches(it) } ?: return null
    return MemberReceipt(epochValue, atValue, balance, signature.removePrefix("0x").lowercase())
}

/** Why a receipt was rejected; kept for diagnostics, shown to the user only as "not verified". */
enum class ReceiptRejection { MISSING, UNSUPPORTED_CHAIN, BALANCE, EPOCH, WINDOW, SIGNATURE }

sealed interface ReceiptCheck {
    data object Ok : ReceiptCheck
    data class Rejected(val reason: ReceiptRejection) : ReceiptCheck
}

/**
 * Verifies [receipt] as the receipt of [holder] under [gate], relied on at [atUnixSeconds] (the
 * post's own time). Pure: the only thing fetched is [attestationKey], once per install.
 *
 * In order, as in the original: the balance meets the threshold; the receipt's epoch is the
 * epoch of its own timestamp; it is not dated after the post (beyond clock slack) nor more than
 * one epoch before it; and the signature is the canister's.
 */
fun verifyMemberReceipt(
    receipt: MemberReceipt?,
    gate: GateSpec,
    holder: String,
    attestationKey: PublicKey,
    atUnixSeconds: Long,
): ReceiptCheck {
    if (receipt == null) return ReceiptCheck.Rejected(ReceiptRejection.MISSING)
    val variant = EIP155_TO_VARIANT[gate.chainId] ?: return ReceiptCheck.Rejected(ReceiptRejection.UNSUPPORTED_CHAIN)
    if (BigInteger(receipt.bal) < gate.threshold) return ReceiptCheck.Rejected(ReceiptRejection.BALANCE)
    if (epochOf(receipt.at) != receipt.epoch) return ReceiptCheck.Rejected(ReceiptRejection.EPOCH)
    if (receipt.at > atUnixSeconds + RECEIPT_FUTURE_SLACK_SECONDS) return ReceiptCheck.Rejected(ReceiptRejection.WINDOW)
    if (atUnixSeconds - receipt.at > RECEIPT_TTL_SECONDS) return ReceiptCheck.Rejected(ReceiptRejection.WINDOW)
    val message = attestPreimage(
        chainVariant = variant,
        token = gate.token,
        threshold = gate.threshold,
        holder = holder.trim().lowercase(),
        cidHash = memberCidHash(gate.spec, receipt.epoch),
        at = receipt.at,
        bal = receipt.bal,
    ).toByteArray(Charsets.UTF_8)
    val signature = hexToBytesOrNull(receipt.sig)?.takeIf { it.size == 64 }
        ?: return ReceiptCheck.Rejected(ReceiptRejection.SIGNATURE)
    val valid = try {
        Signature.getInstance("Ed25519").run {
            initVerify(attestationKey)
            update(message)
            verify(signature)
        }
    } catch (_: Exception) {
        false
    }
    return if (valid) ReceiptCheck.Ok else ReceiptCheck.Rejected(ReceiptRejection.SIGNATURE)
}

/**
 * A raw 32-byte Ed25519 public key as a JCA [PublicKey], or null when the bytes are not a key or
 * the platform has no Ed25519 (Android before 13). A null key means "cannot check", never "forged".
 */
fun ed25519PublicKeyOrNull(raw: ByteArray): PublicKey? = try {
    val encoded = if (raw.size == 32) ED25519_X509_PREFIX + raw else raw
    KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(encoded))
} catch (_: Exception) {
    null
}

/** X.509 SubjectPublicKeyInfo header for an Ed25519 key (OID 1.3.101.112), before the 32 key bytes. */
private val ED25519_X509_PREFIX = byteArrayOf(
    0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00,
)

internal fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

internal fun hexToBytesOrNull(hex: String): ByteArray? {
    if (hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
        val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}
