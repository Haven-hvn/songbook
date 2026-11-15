package app.ipfsmedia.core.library

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.TokenStandard

/**
 * The product model (docs/PLAN.md §5): Parachord's vocabulary over Haven's records.
 *
 * | Here      | On Arkiv                                   |
 * |-----------|--------------------------------------------|
 * | album     | one `haven.audio.full` record (one file)   |
 * | song      | an ID3 chapter of that file                |
 * | video     | one `haven.video.full` record              |
 * | uploader  | the publishing address                     |
 * | DataDAO   | a gate: (chain, token, threshold)          |
 */

/** What kind of upload a catalogue item is. Songs are not items; they live inside an album. */
enum class ItemKind { ALBUM, VIDEO }

/** Whether the connected wallet meets a DataDAO's gate, as of the last refresh. */
enum class Membership { MEMBER, NOT_MEMBER, UNKNOWN }

/**
 * The one access state every item shows. The meaning of each value is fixed; so is its colour
 * (`core-design`).
 */
enum class AccessState {
    /** Not gated: anyone can open it. */
    OPEN,

    /** Gated, and the wallet is a member of the DataDAO. */
    MEMBER,

    /** Gated, and the wallet is not a member. Locked; the item says which DataDAO. */
    NOT_MEMBER,

    /** Gated, and membership could not be read (offline, or the chain was unreachable). */
    UNKNOWN,

    /** A market-cap gate that has not been reached. Locked for everyone, with that as the reason. */
    NOT_OPEN_YET,
    ;

    /**
     * Whether the app will try to open the item. [UNKNOWN] is attempted on purpose: content
     * already on the device plays with no network, and the signed unlock is the authority when
     * there is one.
     */
    val canOpen: Boolean get() = this == OPEN || this == MEMBER || this == UNKNOWN

    val isLocked: Boolean get() = !canOpen
}

/** Whether an item's content is on the device. Parachord's "source badge", repurposed. */
enum class OfflineState { ON_DEVICE, PARTIAL, NOT_DOWNLOADED, EXPIRED }

/** Key of the pseudo-group that collects uploads with no gate. Not a real DataDAO. */
const val PUBLIC_GROUP_KEY = "public"

/**
 * Stable key for a DataDAO: one chain, one token contract. Thresholds vary per upload; the
 * community does not. The same spelling `core-collections` uses for gate keys, so a membership
 * verdict from the access checker can be looked up by it directly.
 */
fun daoKey(chain: HavenChain, tokenAddress: String): String =
    "${chain.aolVariant}:${tokenAddress.trim().lowercase()}"

/** This gate's DataDAO key, or null when it names a chain Haven-AOL cannot check. */
fun TokenGate.daoKeyOrNull(): String? = HavenChain.parse(chain)?.let { daoKey(it, tokenAddress) }

/** A community: the gate, what the wallet's standing is, and what it holds. */
data class DataDao(
    val key: String,
    /** The name its discussion board gave it, else a short form of the token address. */
    val name: String,
    val about: String?,
    val chain: HavenChain?,
    val tokenAddress: String,
    /** Lowest threshold among its live uploads: meeting it opens at least something. */
    val threshold: Double,
    val standard: TokenStandard,
    val membership: Membership,
    val pinned: Boolean,
    val albumCount: Int,
    val videoCount: Int,
    val uploaderCount: Int,
    /** Creation block of its newest upload, for "latest" ordering. */
    val latestBlock: Long?,
) {
    val isPublicGroup: Boolean get() = key == PUBLIC_GROUP_KEY
    val itemCount: Int get() = albumCount + videoCount

    /**
     * The membership rule in words, for the About tab. States what to hold and where; never a
     * price, a balance or a place to buy (docs/PLAN.md §5, "DataDAOs and access").
     */
    val ruleInWords: String
        get() {
            if (isPublicGroup) return "No membership needed. Anyone can open these uploads."
            val amount = if (threshold == Math.floor(threshold)) threshold.toLong().toString() else threshold.toString()
            val where = chain?.label ?: "an unsupported network"
            return when (standard) {
                TokenStandard.ERC20 -> "Hold at least $amount of token $tokenAddress on $where."
                TokenStandard.ERC721, TokenStandard.ERC1155 ->
                    "Hold at least $amount item${if (amount == "1") "" else "s"} from collection $tokenAddress on $where."
            }
        }
}

/** An address that has published. "Artist" in Parachord's layout. */
data class Uploader(
    /** Lowercased `0x` address. */
    val address: String,
    /** The uploader's own name for themselves, if any. Self-asserted, so shown beside the address. */
    val handle: String?,
    val uploadCount: Int,
    val latestBlock: Long?,
    val latestTitle: String?,
    val followed: Boolean,
    /** Uploads newer than the user has been shown. Only meaningful when [followed]. */
    val unseenCount: Int,
    val isSelf: Boolean,
) {
    val displayName: String get() = handle?.takeIf { it.isNotBlank() } ?: shortAddress(address)
}

/** One song: a chapter of its album's file. */
data class Song(
    val albumId: String,
    /** Position in the album's chapter list. */
    val index: Int,
    val title: String,
    val startMs: Long,
    /** Null for the last song when the album's length is not known. */
    val endMs: Long?,
) {
    val durationMs: Long? get() = endMs?.let { (it - startMs).coerceAtLeast(0) }
}

/** One upload the app can show: an album or a video, with everything a card or row needs. */
data class LibraryItem(
    /** The Arkiv record key. */
    val id: String,
    val kind: ItemKind,
    val title: String,
    val uploaderAddress: String,
    val uploaderName: String,
    /** Null when the upload has no gate (it belongs to the public group). */
    val daoKey: String?,
    val daoName: String?,
    val access: AccessState,
    val offline: OfflineState,
    val createdAtBlock: Long?,
    /** Estimated from the block height; null until a refresh has seen the chain head. */
    val createdAtMillis: Long?,
    /** When the Arkiv record expires, estimated the same way. */
    val expiresAtMillis: Long?,
    val durationMs: Long?,
    val pieceCid: String?,
    /** Published by the connected wallet. */
    val isOwn: Boolean,
    val isFavourite: Boolean,
    /**
     * An album's songs. Null means *not known yet*: the chapter list is inside the file, so it is
     * only known once the album has been fetched (docs/PLAN.md §5). Empty means the album was
     * read and has no chapters; it plays as one track. Always empty for a video.
     */
    val songs: List<Song>?,
    val lastOpenedAtMillis: Long?,
    /** The record itself, for the pipeline that fetches and unlocks it. */
    val media: MediaItem,
) {
    val songsKnown: Boolean get() = songs != null

    /** Whether this belongs in "Your media": what the wallet can open, published, or favourited. */
    val inCollection: Boolean
        get() = isOwn || isFavourite || access == AccessState.MEMBER
}

/** `0x1234…abcd`. Short enough for a card, long enough to tell two addresses apart. */
fun shortAddress(address: String): String {
    val clean = address.trim()
    return if (clean.length > 12) "${clean.take(6)}…${clean.takeLast(4)}" else clean
}
