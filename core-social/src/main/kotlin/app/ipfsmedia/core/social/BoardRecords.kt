package app.ipfsmedia.core.social

import app.ipfsmedia.core.library.daoKey
import haven.mobile.core.arkiv.ArkivEntity
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import org.json.JSONObject

/**
 * Discussion records as redhaven and Fairhaven write them (`redhaven/arkiv/schema.md`):
 * `haven.board.def` names a DataDAO's board, `haven.board.thread` is a thread, and
 * `haven.board.post` is a comment under a thread or under any media record of the same gate.
 *
 * This app only reads them, and reads them defensively. redhaven's own README says its code was
 * written without being run, so records in the wild may not match its schema: anything that does
 * not parse is skipped, a missing field is a missing field, and nothing here throws.
 */
const val GROUP_BOARD_DEF = "haven.board.def"
const val GROUP_BOARD_THREAD = "haven.board.thread"
const val GROUP_BOARD_POST = "haven.board.post"

/** The text of a thread or comment. */
sealed interface PostBody {
    /** Written in the clear; shown as plain text. */
    data class Clear(val text: String) : PostBody

    /** Sealed to members. [ciphertext] is base64 of the framed AES-GCM bytes. */
    data class Sealed(val ciphertext: String) : PostBody

    /** No text at all (a media-only thread, or a record that did not carry any). */
    data object None : PostBody
}

enum class BoardRecordType { THREAD, POST }

/** A thread or a comment. */
data class BoardRecord(
    val key: String,
    val type: BoardRecordType,
    /** The DataDAO it was written under, or null when its gate names a chain this app does not know. */
    val daoKey: String?,
    val gate: GateSpec?,
    /** The writing address, lowercased. */
    val author: String,
    /** A thread's public subject line. Null for comments. */
    val subject: String?,
    /** What a comment is under: a thread's key, or a media record's. Null for threads. */
    val rootRef: String?,
    /** The comment being answered, for nesting. Null for a top-level comment. */
    val parentRef: String?,
    val createdAtBlock: Long?,
    val expiresAtBlock: Long?,
    val body: PostBody,
    val receipt: MemberReceipt?,
    /** A thread that carries a file. This app does not open thread attachments; it says one exists. */
    val hasMedia: Boolean,
    /** The gate blob needed to unseal [PostBody.Sealed] text. Held in memory only. */
    val sealedGate: GateMetadata? = null,
)

/** A board definition: the name and description a DataDAO's discussion gave it. */
data class BoardDef(
    val daoKey: String,
    val slug: String?,
    val title: String?,
    val about: String?,
    val createdAtBlock: Long?,
)

/** Reads one raw record as a thread or comment. Null when it is neither, or is unreadable. */
fun ArkivEntity.toBoardRecord(): BoardRecord? = runCatching {
    val type = when (string("grp")) {
        GROUP_BOARD_THREAD -> BoardRecordType.THREAD
        GROUP_BOARD_POST -> BoardRecordType.POST
        else -> return null
    }
    val rootRef = string("root_ref")?.lowercase()
    // A comment that does not say what it is under cannot be placed anywhere.
    if (type == BoardRecordType.POST && rootRef == null) return null
    val gate = gateSpec()
    val payload = payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    BoardRecord(
        key = key.lowercase(),
        type = type,
        daoKey = daoKeyOf(gate),
        gate = gate,
        author = owner,
        subject = string("subject")?.take(MAX_SUBJECT_CHARS)?.takeIf { type == BoardRecordType.THREAD },
        rootRef = rootRef.takeIf { type == BoardRecordType.POST },
        parentRef = string("parent_ref")?.lowercase()?.takeIf { type == BoardRecordType.POST },
        createdAtBlock = createdAtBlock,
        expiresAtBlock = expiresAtBlock,
        body = payload?.optJSONObject("text").toBody(),
        receipt = payload?.optJSONObject("attn")?.let { attn ->
            parseReceipt(attn.opt("epoch"), attn.opt("at"), attn.opt("bal"), attn.opt("sig"))
        },
        hasMedia = payload?.optJSONObject("media") != null,
        sealedGate = sealedGate,
    )
}.getOrNull()

/** Reads one raw record as a board definition. Null when it is not one, or names no usable gate. */
fun ArkivEntity.toBoardDef(): BoardDef? = runCatching {
    if (string("grp") != GROUP_BOARD_DEF) return null
    val key = daoKeyOf(gateSpec()) ?: return null
    val payload = payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    BoardDef(
        daoKey = key,
        slug = string("board"),
        title = string("title")?.take(MAX_SUBJECT_CHARS),
        about = payload?.optString("about", "")?.takeIf { it.isNotBlank() }?.take(MAX_ABOUT_CHARS),
        createdAtBlock = createdAtBlock,
    )
}.getOrNull()

/** The gate triple every board record carries as attributes. */
private fun ArkivEntity.gateSpec(): GateSpec? =
    GateSpec.of(
        chainId = long("gate_chain"),
        token = string("gate_token"),
        threshold = when (val raw = fields["gate_threshold"]) {
            is Long -> raw.toString()
            is String -> raw
            // A JSON number too large for a Long arrives as a Double; its digits are not exact.
            is Double -> java.math.BigDecimal(raw).toBigInteger().toString()
            else -> null
        },
    )

private fun daoKeyOf(gate: GateSpec?): String? {
    if (gate == null) return null
    val chain = HavenChain.entries.firstOrNull { it.chainId == gate.chainId } ?: return null
    return daoKey(chain, gate.token)
}

private fun JSONObject?.toBody(): PostBody {
    if (this == null) return PostBody.None
    optString("clear", "").takeIf { it.isNotEmpty() }?.let { return PostBody.Clear(it.take(MAX_TEXT_CHARS)) }
    optString("sealed", "").takeIf { it.isNotEmpty() }?.let { return PostBody.Sealed(it) }
    return PostBody.None
}

/** Subjects and titles are at most 128 bytes on Arkiv; anything longer is not a real one. */
private const val MAX_SUBJECT_CHARS = 128
private const val MAX_ABOUT_CHARS = 2_000

/** Payloads are at most 128 KiB; this bounds what a single comment can put on screen. */
private const val MAX_TEXT_CHARS = 20_000
