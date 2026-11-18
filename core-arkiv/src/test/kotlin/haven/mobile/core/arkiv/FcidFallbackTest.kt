package haven.mobile.core.arkiv

import haven.mobile.core.domain.MediaKind
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Clear records carry `fcid` (the UnixFS root) instead of `piece` (the
 * CommP) — haven-cli writes exactly one locator per record class, and every
 * ungated upload (all StepMania packs/files) is clear. The reader must
 * synthesize an IPFS PieceRef from `fcid` so those uploads play instead of
 * failing with NO_PIECE_REF. Retrieval is then `SP /ipfs/{root}?format=raw`
 * (plus gateways via Delegated Routing), never `/piece`.
 */
class FcidFallbackTest {

    private val client = ArkivClientImpl(ArkivConfig(endpointUrl = "https://example.com"))

    private fun hexPayload(json: String): String =
        "0x" + json.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun attr(type: String, value: Any) =
        JSONObject().put("type", type).put("value", value)

    private fun row(payloadJson: String): JSONObject = JSONObject()
        .put("\$key", "0x" + "ab".repeat(32))
        .put("\$owner", "0x5C32469325d4093AB142dDDC1305F91d76e45141")
        .put("\$creator", "0x5C32469325d4093AB142dDDC1305F91d76e45141")
        .put("\$createdAt", "0x9b877")
        .put("\$expiresAt", "0x1c2d77")
        .put("content_type", "application/json")
        .put("payload", hexPayload(payloadJson))
        .put(
            "\$attributes",
            JSONObject()
                .put("title", attr("str", "Sai Memes - Whopper.ogg"))
                .put("mime", attr("i32", 6))
                .put("grp", attr("str", "haven.video.full"))
                .put("sha256_ct", attr("str", "abc123")),
        )

    private fun itemOf(payloadJson: String) = with(client) {
        normalizeRpcEntity(row(payloadJson)).toMediaItem()
    }

    @Test
    fun `clear fcid-only record builds an IPFS piece ref`() {
        // The exact Whopper.ogg shape from Rabdi: no gate, no piece,
        // just fcid + size in the payload.
        val fcid = "bafkreieqpwefkiabpuxof7tiwelfbefwqwc4zxyxw7lc4z75bmvsbzm7me"
        val payload = JSONObject().put("fcid", fcid).put("size", 514198).toString()
        val item = itemOf(payload)

        assertEquals(fcid, item.filecoinCid)
        assertFalse(item.isEncrypted)
        assertEquals(MediaKind.AUDIO, item.kind)
        val ref = item.pieceRef ?: throw AssertionError("fcid must synthesize a pieceRef")
        assertEquals(fcid, ref.pieceCid)
        assertTrue(ref.ipfsIndexed, "fcid is retrievable only over /ipfs")
        assertEquals(fcid, ref.unixFsRoot)
        assertFalse(ref.cdnEnabled, "FilBeam serves /piece, not /ipfs")
        assertNull(ref.filBeamUrl)
        assertEquals(514198L, ref.size, "payload size seeds progress for root locators")
        assertTrue(ref.providerServiceUrls.isEmpty())
        assertTrue(ref.trustlessGateways.isNotEmpty(), "IPFS needs explicit gateway candidates")
    }

    @Test
    fun `fcid without size still builds a ref with zero size`() {
        val payload = JSONObject().put("fcid", "bafkrefcid").toString()
        val ref = itemOf(payload).pieceRef
            ?: throw AssertionError("fcid must synthesize a pieceRef")
        assertEquals("bafkrefcid", ref.pieceCid)
        assertTrue(ref.ipfsIndexed)
        assertEquals("bafkrefcid", ref.unixFsRoot)
        assertEquals(0L, ref.size)
    }

    @Test
    fun `gated piece record keeps its piece path`() {
        val payload = JSONObject()
            .put("piece", "bafkzcibpiece")
            .put("fcid", "bafkrefcid")
            .put("size", 10888)
            .toString()
        val item = itemOf(payload)

        // Both present (writers say never both) prefers piece.
        val ref = item.pieceRef ?: throw AssertionError("piece must build a pieceRef")
        assertEquals("bafkzcibpiece", ref.pieceCid)
        assertFalse(ref.ipfsIndexed)
        assertNull(ref.unixFsRoot)
        assertTrue(ref.cdnEnabled)
        assertEquals(0L, ref.size)
        assertEquals("bafkrefcid", item.filecoinCid)
        assertTrue(ref.trustlessGateways.isEmpty())
    }

    @Test
    fun `record with neither locator has no piece ref`() {
        val payload = JSONObject().put("size", 100).toString()
        assertNull(itemOf(payload).pieceRef)
    }
}
