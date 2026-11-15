package app.ipfsmedia.core.library

import app.ipfsmedia.core.userdata.DaoStateEntity
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenStandard

/** One cached chapter row, in this module's terms so the derivation needs no Room types. */
data class ChapterRow(
    val albumId: String,
    /** -1 marks "parsed, and the album has no chapters". */
    val index: Int,
    val startMs: Long,
    val title: String,
    val pieceCid: String,
)

/** Everything [deriveLibrary] reads. Plain data, so the whole derivation is a pure function. */
data class LibraryInputs(
    val walletAddress: String?,
    val media: List<MediaItem>,
    val chapters: List<ChapterRow> = emptyList(),
    val daoStates: List<DaoStateEntity> = emptyList(),
    val pinnedDaoKeys: List<String> = emptyList(),
    /** Favourited record keys (whole albums and videos; song favourites do not mark the album). */
    val favouriteRecordKeys: Set<String> = emptySet(),
    /** Followed address -> newest block the user has been shown for it. */
    val follows: Map<String, Long> = emptyMap(),
    val clock: BlockClock? = null,
    /** Items a market-cap gate refused this session. */
    val notOpenYet: Set<String> = emptySet(),
)

/**
 * The catalogue as the app shows it, from the mirror plus what this identity knows.
 *
 * The mirror holds every live music and video record. What the wallet may open is decided here,
 * by joining each record's gate with the membership the last refresh read from the chain. That
 * is why a locked album can sit in a DataDAO's page and say why it is locked, and why browsing
 * never needs another index read.
 */
fun deriveLibrary(inputs: LibraryInputs): LibraryState {
    val wallet = inputs.walletAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val states = inputs.daoStates.associateBy { it.daoKey }
    val pinned = inputs.pinnedDaoKeys.toSet()
    val chaptersByAlbum = inputs.chapters.groupBy { it.albumId }
    // An uploader's name for themselves is on some of their records and not others; the newest
    // one that has it is used for all of them, so one person does not appear under two names.
    val handles = inputs.media
        .filter { !it.creatorHandle.isNullOrBlank() }
        .sortedBy { it.createdAtBlock ?: Long.MIN_VALUE }
        .associate { it.owner.trim().lowercase() to it.creatorHandle!!.trim() }

    val items = inputs.media.mapNotNull { media ->
        val kind = when (media.kind) {
            MediaKind.AUDIO -> ItemKind.ALBUM
            MediaKind.VIDEO -> ItemKind.VIDEO
            // Images, documents and files are not this app's subject.
            else -> return@mapNotNull null
        }
        if (media.id.isEmpty()) return@mapNotNull null
        val key = media.daoKeyOrNull()
        val state = key?.let { states[it] }
        val durationMs = media.durationSeconds?.takeIf { it > 0 }?.times(1_000)
        val owner = media.owner.trim().lowercase()
        LibraryItem(
            id = media.id,
            kind = kind,
            title = media.title.ifBlank { "Untitled" },
            uploaderAddress = owner,
            uploaderName = handles[owner] ?: shortAddress(owner),
            daoKey = key,
            daoName = key?.let { daoName(it, state, media) },
            access = accessOf(media, state?.membership, media.id in inputs.notOpenYet),
            offline = when (media.contentCacheStatus) {
                ContentCacheStatus.CACHED -> OfflineState.ON_DEVICE
                ContentCacheStatus.PARTIAL -> OfflineState.PARTIAL
                ContentCacheStatus.UNCACHED -> OfflineState.NOT_DOWNLOADED
                ContentCacheStatus.EXPIRED -> OfflineState.EXPIRED
            },
            createdAtBlock = media.createdAtBlock,
            createdAtMillis = media.createdAt.toEpochMilliseconds().takeIf { it > 0 }
                ?: media.createdAtBlock?.let { block -> inputs.clock?.millisAt(block) },
            expiresAtMillis = media.expiresAtBlock?.let { block -> inputs.clock?.millisAt(block) },
            durationMs = durationMs,
            pieceCid = media.pieceRef?.pieceCid,
            isOwn = wallet != null && owner == wallet,
            isFavourite = media.id in inputs.favouriteRecordKeys,
            songs = if (kind == ItemKind.ALBUM) {
                songsOf(media.id, media.pieceRef?.pieceCid, chaptersByAlbum[media.id], durationMs)
            } else {
                emptyList()
            },
            lastOpenedAtMillis = media.lastAccessedAt?.toEpochMilliseconds(),
            media = media,
        )
    }.sortedWith(NEWEST_FIRST)

    return LibraryState(
        ready = true,
        walletAddress = wallet,
        items = items,
        daos = daosOf(items, states, inputs.pinnedDaoKeys, pinned),
        uploaders = uploadersOf(items, inputs.follows, wallet),
        clock = inputs.clock,
    )
}

/** Newest upload first; uploads with no block last; ties by title so the order is stable. */
internal val NEWEST_FIRST: Comparator<LibraryItem> =
    compareByDescending<LibraryItem> { it.createdAtBlock ?: Long.MIN_VALUE }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }

/**
 * The DataDAO an upload belongs to: its gate attributes when it has them, otherwise the gate
 * named inside a sealed key record. Null for an upload with no gate at all.
 */
fun MediaItem.daoKeyOrNull(): String? {
    gate?.daoKeyOrNull()?.let { return it }
    val sealed = (encryptionMetadata as? GateMetadata.Sealed) ?: return null
    val chain = HavenChain.parse(sealed.chain) ?: return null
    return sealed.tokenAddress.takeIf { it.isNotBlank() }?.let { daoKey(chain, it) }
}

/**
 * The one access state an item shows.
 *
 * Whether a record is encrypted decides whether a gate applies at all: an upload that names a
 * DataDAO but is not sealed is open to everyone. For sealed uploads the wallet's membership
 * decides, and "could not check" is its own answer, never "no".
 */
internal fun accessOf(media: MediaItem, membership: String?, notOpenYet: Boolean): AccessState = when {
    notOpenYet -> AccessState.NOT_OPEN_YET
    !media.isEncrypted -> AccessState.OPEN
    membership == DaoStateEntity.MEMBER -> AccessState.MEMBER
    membership == DaoStateEntity.NOT_MEMBER -> AccessState.NOT_MEMBER
    else -> AccessState.UNKNOWN
}

private fun daoName(key: String, state: DaoStateEntity?, media: MediaItem): String =
    state?.title?.takeIf { it.isNotBlank() }
        ?: shortAddress(media.gate?.tokenAddress ?: key.substringAfter(':'))

/**
 * An album's songs from its cached chapters.
 *
 * Null (not known) when the album was never read, or when the cache was read from a different
 * file than the record now points at — the album was re-uploaded, and old cue points must not be
 * shown against a new file. Each song ends where the next begins; the last ends with the album.
 */
internal fun songsOf(
    albumId: String,
    pieceCid: String?,
    rows: List<ChapterRow>?,
    albumDurationMs: Long?,
): List<Song>? {
    if (rows.isNullOrEmpty()) return null
    if (pieceCid != null && rows.any { it.pieceCid != pieceCid }) return null
    if (rows.any { it.index < 0 }) return emptyList()
    val ordered = rows.sortedBy { it.startMs }
    return ordered.mapIndexed { position, row ->
        Song(
            albumId = albumId,
            index = row.index,
            title = row.title.ifBlank { "Track ${position + 1}" },
            startMs = row.startMs,
            endMs = ordered.getOrNull(position + 1)?.startMs ?: albumDurationMs?.takeIf { it > row.startMs },
        )
    }
}

private fun daosOf(
    items: List<LibraryItem>,
    states: Map<String, DaoStateEntity>,
    pinOrder: List<String>,
    pinned: Set<String>,
): List<DataDao> {
    val byKey = items.filter { it.daoKey != null }.groupBy { it.daoKey!! }
    val daos = byKey.map { (key, members) ->
        val gates = members.mapNotNull { it.media.gate }
        val state = states[key]
        DataDao(
            key = key,
            name = members.first().daoName ?: shortAddress(key.substringAfter(':')),
            about = state?.about?.takeIf { it.isNotBlank() },
            chain = HavenChain.entries.firstOrNull { key.startsWith("${it.aolVariant}:") },
            tokenAddress = gates.firstOrNull()?.tokenAddress ?: key.substringAfter(':'),
            threshold = gates.minOfOrNull { it.threshold } ?: 1.0,
            standard = gates.firstOrNull()?.tokenStandard ?: TokenStandard.ERC20,
            membership = membershipOf(state?.membership),
            pinned = key in pinned,
            albumCount = members.count { it.kind == ItemKind.ALBUM },
            videoCount = members.count { it.kind == ItemKind.VIDEO },
            uploaderCount = members.map { it.uploaderAddress }.distinct().size,
            latestBlock = members.mapNotNull { it.createdAtBlock }.maxOrNull(),
        )
    }.toMutableList()

    // A pinned DataDAO whose uploads have all expired stays in the sidebar, empty, rather than
    // silently disappearing from where the user put it.
    pinned.filter { it !in byKey }.forEach { key ->
        val state = states[key]
        daos += DataDao(
            key = key,
            name = state?.title?.takeIf { it.isNotBlank() } ?: shortAddress(key.substringAfter(':')),
            about = state?.about,
            chain = HavenChain.entries.firstOrNull { key.startsWith("${it.aolVariant}:") },
            tokenAddress = key.substringAfter(':'),
            threshold = 1.0,
            standard = TokenStandard.ERC20,
            membership = membershipOf(state?.membership),
            pinned = true,
            albumCount = 0,
            videoCount = 0,
            uploaderCount = 0,
            latestBlock = null,
        )
    }

    val open = items.filter { it.daoKey == null }
    if (open.isNotEmpty()) {
        daos += DataDao(
            key = PUBLIC_GROUP_KEY,
            name = "Public uploads",
            about = null,
            chain = null,
            tokenAddress = "",
            threshold = 0.0,
            standard = TokenStandard.ERC20,
            membership = Membership.MEMBER,
            pinned = false,
            albumCount = open.count { it.kind == ItemKind.ALBUM },
            videoCount = open.count { it.kind == ItemKind.VIDEO },
            uploaderCount = open.map { it.uploaderAddress }.distinct().size,
            latestBlock = open.mapNotNull { it.createdAtBlock }.maxOrNull(),
        )
    }

    return daos.sortedWith(
        compareBy<DataDao> {
            when {
                it.pinned -> 0
                it.isPublicGroup -> 3
                it.membership == Membership.MEMBER -> 1
                else -> 2
            }
        }
            .thenBy { if (it.pinned) pinOrder.indexOf(it.key) else 0 }
            .thenByDescending { it.latestBlock ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() },
    )
}

internal fun membershipOf(stored: String?): Membership = when (stored) {
    DaoStateEntity.MEMBER -> Membership.MEMBER
    DaoStateEntity.NOT_MEMBER -> Membership.NOT_MEMBER
    else -> Membership.UNKNOWN
}

private fun uploadersOf(items: List<LibraryItem>, follows: Map<String, Long>, wallet: String?): List<Uploader> {
    val normalizedFollows = follows.mapKeys { it.key.trim().lowercase() }
    return items.groupBy { it.uploaderAddress }
        .filterKeys { it.isNotEmpty() }
        .map { (address, uploads) ->
            // `items` is newest first, so the first upload is the latest one.
            val latest = uploads.first()
            val seenBlock = normalizedFollows[address]
            Uploader(
                address = address,
                handle = uploads.firstNotNullOfOrNull { it.media.creatorHandle?.trim()?.takeIf { handle -> handle.isNotEmpty() } },
                uploadCount = uploads.size,
                latestBlock = latest.createdAtBlock,
                latestTitle = latest.title,
                followed = seenBlock != null,
                unseenCount = if (seenBlock == null) 0 else uploads.count { (it.createdAtBlock ?: 0L) > seenBlock },
                isSelf = wallet != null && address == wallet,
            )
        }
        .sortedWith(
            compareByDescending<Uploader> { it.latestBlock ?: Long.MIN_VALUE }.thenBy { it.displayName.lowercase() },
        )
}
