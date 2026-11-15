package app.ipfsmedia.core.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * Pinned by redhaven's `packages/vote-core/tests/fixtures/receipt-vectors.json` ("haven
 * member-receipt vectors v1"): the binding strings below are copied from it verbatim, so this
 * implementation and redhaven's verify the same bytes. The fixture does not pin signatures (they
 * depend on the canister's key), so signature checks here use a key generated for the test.
 */
class MemberReceiptTest {

    // ── From the fixture ────────────────────────────────────────────────────────────────────
    private val token = "0xabcdef0123456789abcdef0123456789abcdef01"
    private val gateSpec = "gate:8453:0xabcdef0123456789abcdef0123456789abcdef01:1"
    private val now = 1_760_000_000L
    private val epochOfNow = 679L
    private val holder = "0x1111111111111111111111111111111111111111"
    private val cidHash679 = "073a6ce2c4a24edd0dbb8a568cc2384b94496e5ecee1d4c546448ea1cdfdc465"
    private val cidHash680 = "817f4eb591b1dad37cf1fa03b9f6868c7f95fced05a96fa03cb0a84d3cc99846"
    private val expectedPreimage =
        "HAVEN_ATTEST_V1:BaseMainnet:0xabcdef0123456789abcdef0123456789abcdef01:1:" +
            "0x1111111111111111111111111111111111111111:" +
            "073a6ce2c4a24edd0dbb8a568cc2384b94496e5ecee1d4c546448ea1cdfdc465:1760000000:5"

    private val gate = GateSpec.of(8453, token, "1")!!
    private val keys: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun sign(message: String, with: KeyPair = keys): String {
        val signature = Signature.getInstance("Ed25519").run {
            initSign(with.private)
            update(message.toByteArray(Charsets.UTF_8))
            sign()
        }
        return signature.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun receipt(at: Long = now, bal: String = "5", epoch: Long = epochOf(at), signer: KeyPair = keys): MemberReceipt {
        val message = attestPreimage("BaseMainnet", token, BigInteger.ONE, holder, memberCidHash(gateSpec, epoch), at, bal)
        return MemberReceipt(epoch = epoch, at = at, bal = bal, sig = sign(message, signer))
    }

    private fun check(receipt: MemberReceipt?, postedAt: Long = now, by: String = holder) =
        verifyMemberReceipt(receipt, gate, by, keys.public, postedAt)

    @Test
    fun `the gate spec is spelled as in the fixture`() {
        assertEquals(gateSpec, gate.spec)
        assertEquals(gateSpec, GateSpec.of(8453, token.uppercase().replace("0X", "0x"), "1")!!.spec)
    }

    @Test
    fun `the epoch of the fixture's time`() {
        assertEquals(epochOfNow, epochOf(now))
        assertEquals(2_592_000L, EPOCH_LENGTH_SECONDS)
    }

    @Test
    fun `the binding hash matches the fixture for both epochs`() {
        assertEquals(cidHash679, memberCidHash(gateSpec, 679))
        assertEquals(cidHash680, memberCidHash(gateSpec, 680))
    }

    @Test
    fun `the signed text matches the fixture`() {
        assertEquals(
            expectedPreimage,
            attestPreimage("BaseMainnet", token, BigInteger.ONE, holder, memberCidHash(gateSpec, 679), now, "5"),
        )
    }

    @Test
    fun `a receipt signed by the canister key verifies`() {
        assertEquals(ReceiptCheck.Ok, check(receipt()))
    }

    @Test
    fun `the holder's case does not matter`() {
        assertEquals(ReceiptCheck.Ok, check(receipt(), by = holder.uppercase().replace("0X", "0x")))
    }

    @Test
    fun `no receipt is a rejection`() {
        assertEquals(ReceiptCheck.Rejected(ReceiptRejection.MISSING), check(null))
    }

    @Test
    fun `a receipt signed by another key is forged`() {
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        assertEquals(ReceiptCheck.Rejected(ReceiptRejection.SIGNATURE), check(receipt(signer = other)))
    }

    @Test
    fun `a receipt for someone else does not verify for this author`() {
        assertEquals(
            ReceiptCheck.Rejected(ReceiptRejection.SIGNATURE),
            check(receipt(), by = "0x2222222222222222222222222222222222222222"),
        )
    }

    @Test
    fun `a balance below the threshold is rejected before the signature is looked at`() {
        val strict = GateSpec.of(8453, token, "10")!!
        assertEquals(
            ReceiptCheck.Rejected(ReceiptRejection.BALANCE),
            verifyMemberReceipt(receipt(bal = "5"), strict, holder, keys.public, now),
        )
    }

    @Test
    fun `an epoch that is not the epoch of the receipt's own time is rejected`() {
        assertEquals(ReceiptCheck.Rejected(ReceiptRejection.EPOCH), check(receipt(epoch = 680)))
    }

    @Test
    fun `a receipt dated after the post, or more than an epoch before it, is rejected`() {
        assertEquals(
            ReceiptCheck.Rejected(ReceiptRejection.WINDOW),
            check(receipt(at = now + RECEIPT_FUTURE_SLACK_SECONDS + 1), postedAt = now),
        )
        assertEquals(
            ReceiptCheck.Rejected(ReceiptRejection.WINDOW),
            check(receipt(at = now), postedAt = now + RECEIPT_TTL_SECONDS + 1),
        )
        // Inside the window on both sides.
        assertEquals(ReceiptCheck.Ok, check(receipt(at = now), postedAt = now + RECEIPT_TTL_SECONDS))
    }

    @Test
    fun `a chain the canister cannot attest on is rejected`() {
        val unknown = GateSpec.of(137, token, "1")!!
        assertEquals(
            ReceiptCheck.Rejected(ReceiptRejection.UNSUPPORTED_CHAIN),
            verifyMemberReceipt(receipt(), unknown, holder, keys.public, now),
        )
    }

    @Test
    fun `receipts are read only when shaped like one`() {
        val sig = "ab".repeat(64)
        assertEquals(MemberReceipt(679, now, "5", sig), parseReceipt(679, now, "5", "0x${sig.uppercase()}"))
        assertNull(parseReceipt(679, now, "5", "abcd"))
        assertNull(parseReceipt(679, now, "05", sig))
        assertNull(parseReceipt(679, now, 5, sig))
        assertNull(parseReceipt("679", now, "5", sig))
        assertNull(parseReceipt(-1, now, "5", sig))
    }

    @Test
    fun `gates are read only when complete`() {
        assertNull(GateSpec.of(null, token, "1"))
        assertNull(GateSpec.of(8453, "not-an-address", "1"))
        assertNull(GateSpec.of(8453, token, "0"))
        assertNull(GateSpec.of(8453, token, "1.5"))
        // Raw units past a Long keep every digit.
        assertEquals(
            "gate:8453:$token:25000000000000000000",
            GateSpec.of(8453, token, "25000000000000000000")!!.spec,
        )
    }

    @Test
    fun `a raw 32 byte key becomes a usable public key`() {
        val raw = keys.public.encoded.takeLast(32).toByteArray()
        val rebuilt = ed25519PublicKeyOrNull(raw)
        assertNotNull(rebuilt)
        assertEquals(ReceiptCheck.Ok, verifyMemberReceipt(receipt(), gate, holder, rebuilt!!, now))
        assertNull(ed25519PublicKeyOrNull(ByteArray(5)))
    }
}
