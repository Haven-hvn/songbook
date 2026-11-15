package app.ipfsmedia.core.library

/**
 * Turns a block height into wall-clock time.
 *
 * Arkiv records carry a creation block, not a timestamp. haven-mobile asks the chain for each
 * block's header, which is one call per upload; a catalogue scan cannot afford that. redhaven
 * estimates instead, from one observation of the chain head and a nominal block time, and so
 * does this app: accurate to within minutes, which is enough for "3 days ago" and for grouping
 * a feed by day. Ordering never uses the estimate; it uses the block height itself.
 */
data class BlockClock(
    /** A chain head the app observed… */
    val headBlock: Long,
    /** …and when it observed it. */
    val observedAtMillis: Long,
    val blockMillis: Long = RABDI_BLOCK_MILLIS,
) {
    /** Estimated wall-clock time of [block]. Blocks after the anchor project forward. */
    fun millisAt(block: Long): Long = observedAtMillis - (headBlock - block) * blockMillis

    companion object {
        /** Arkiv's Rabdi testnet produces a block about every two seconds. */
        const val RABDI_BLOCK_MILLIS = 2_000L

        fun of(headBlock: String?, observedAtMillis: String?): BlockClock? {
            val head = headBlock?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val at = observedAtMillis?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return BlockClock(head, at)
        }
    }
}
