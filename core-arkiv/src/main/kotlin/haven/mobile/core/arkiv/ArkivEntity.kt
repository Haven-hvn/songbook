package haven.mobile.core.arkiv

import haven.mobile.core.domain.GateMetadata

/**
 * One Arkiv record, decoded but not interpreted.
 *
 * Media records are turned into `MediaItem`s inside this module. Everything else a client wants
 * to read — discussion threads and comments, for one — is handed out in this neutral shape, so
 * the module that understands those records (`core-social`) does not need the wire format.
 *
 * [fields] is the record's attributes with the decoded JSON payload merged over them, the same
 * merge media records get: strings, `Long`s, `Double`s and `Boolean`s, plus nested JSON kept as
 * its text. [payloadJson] is the payload as written, for readers that need its exact structure.
 */
data class ArkivEntity(
    /** Entity key, `0x` + 64 hex. */
    val key: String,
    /** Owning address, lowercased. */
    val owner: String,
    /** Creating address as the chain recorded it; null when the node did not return one. */
    val creator: String?,
    val createdAtBlock: Long?,
    val expiresAtBlock: Long?,
    val fields: Map<String, Any?>,
    val payloadJson: String?,
    /**
     * The record's `gate` blob, parsed the way a media record's is. Present when part of the
     * record is sealed to members; it is what the key service needs to unwrap that part.
     */
    val sealedGate: GateMetadata? = null,
) {
    fun string(name: String): String? = (fields[name] as? String)?.takeIf { it.isNotEmpty() }

    fun long(name: String): Long? = when (val value = fields[name]) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
}

/** A page of raw records, with the chain head the node reported alongside it. */
data class ArkivEntityPage(
    val items: List<ArkivEntity>,
    val nextCursor: String?,
    val headBlock: Long?,
)

/**
 * The result of walking a whole listing.
 *
 * [complete] is false when the walk stopped at its page cap rather than at the end of the
 * listing; callers must not prune local state against an incomplete scan.
 */
data class ArkivScan<T>(
    val items: List<T>,
    val headBlock: Long?,
    val complete: Boolean,
    /** How many `arkiv_query` pages the walk read. */
    val pages: Int,
)

/** Query text builders. Literals are type-tagged; see `ArkivClientImpl.arkivQuery` for why. */
object ArkivPredicates {
    /** `name = str('value')`. Single quotes are stripped from the value: the node has no escape for them. */
    fun str(name: String, value: String): String = "$name = str('${value.replace("'", "")}')"

    /** `name = i32(value)`. */
    fun num(name: String, value: Long): String = "$name = i32($value)"

    /** `$owner = addr(0x…)`. */
    fun owner(address: String): String = "\$owner = addr(${address.trim().lowercase()})"

    /** `a OR b OR c`, flat, the only grouping verified against the node. */
    fun anyOf(vararg predicates: String): String = predicates.joinToString(" OR ")

    /** `a AND b`, flat. Do not nest inside [anyOf]: parenthesised groups are unverified. */
    fun allOf(vararg predicates: String): String = predicates.joinToString(" AND ")
}
