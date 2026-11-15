package haven.mobile.core.cache.mirror

import haven.mobile.core.domain.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * The mirror, as the screens see it.
 *
 * Two questions, and the difference is the whole reading model: **you join a community, and that is
 * how you read.** So the interesting set is not "what did I publish" — it is "what are the communities
 * I belong to keeping".
 */
interface MediaRepository {
    /**
     * Items published by [owner].
     *
     * Parity with `haven-dapp`'s `fetchAllVideos(ownerAddress)`. Kept because it is a real question
     * with a real answer, but it is not what a reader's library is built from: on mobile, publishing
     * happens in `haven-cli`, so a reader's own-publication list is empty and stays empty.
     */
    fun observeLibrary(owner: String): Flow<List<MediaItem>>

    suspend fun refreshLibrary(owner: String): Result<Unit>

    /**
     * Everything this wallet can open: every item under every gate it belongs to, plus anything it
     * published itself.
     *
     * Mirrors what `haven-dapp` shows after its cache merge — Arkiv's answer *plus* what is already
     * mirrored, including entities that have since expired on Arkiv (FR-CACHE-3). Nothing is filtered
     * out for not being downloaded; residency is reported per item by its cache status, exactly as the
     * dapp reports it with a badge.
     */
    fun observeAccessible(): Flow<List<MediaItem>>

    /**
     * Resolve the wallet's communities, then page each archive into the mirror.
     *
     * Communities come from two places, unioned:
     *  - what the wallet **holds** (the collections access check) — the reader's path;
     *  - what the wallet has **published under** (Arkiv's own discovery, which derives gates from your
     *    entities) — the publisher's path, and all `haven-dapp` has.
     *
     * Partial success is success: one unreachable community must not blank a library that four others
     * answered for.
     */
    suspend fun refreshAccessible(): Result<Unit>

    /**
     * Replaces the mirror's contents with one catalogue scan (`ArkivClient.scanCatalog`).
     *
     * This is how Googlebook IPFSMedia fills the mirror; [refreshAccessible] is haven-mobile's
     * path and is not called by it. The difference is what the mirror means afterwards: it holds
     * every live music and video record, not only what the wallet can open. Whether an item can be
     * opened is decided by the caller from the wallet's memberships, so locked items can be
     * browsed (and explain themselves) without any further index read.
     *
     * Rows keep their `lastAccessedAt`. With [pruneMissing] true, rows absent from [items] are
     * deleted along with their cached chapters; pass false when the scan was cut short, so a
     * partial answer never deletes what it did not reach.
     */
    suspend fun replaceCatalog(items: List<MediaItem>, pruneMissing: Boolean): Result<Unit>

    fun observeItem(id: String): Flow<MediaItem?>

    suspend fun refreshItem(id: String): Result<Unit>

    /**
     * Records that the item's bytes were staged for playback. This only touches
     * `lastAccessedAt` — the row re-emits, residency recomputes against the
     * piece cache (which staging just filled), and every observer's label flips
     * without a network round trip.
     */
    suspend fun noteAccessed(id: String)

    /**
     * Makes the row for [id] re-emit after its content was downloaded or removed, without
     * claiming it was opened. Residency is recomputed against the piece cache on every emission,
     * so this only has to cause one. Not in haven-mobile, which has no "remove download".
     */
    suspend fun noteResidencyChanged(id: String) {}

    suspend fun clearFor(walletAddress: String)

    /**
     * Cached ID3 chapters for [item], or null on a cache miss / fingerprint mismatch.
     * An empty list is a hit: the album was parsed and has no chapters.
     */
    suspend fun cachedChapters(item: MediaItem, fileSizeBytes: Long): List<CachedChapter>?

    /** Persist parsed chapters (possibly empty) against the item's current piece fingerprint. */
    suspend fun cacheChapters(item: MediaItem, fileSizeBytes: Long, chapters: List<CachedChapter>)

    /**
     * Every album's cached chapters, as stored (the `-1` row marks "parsed, has none").
     *
     * [cachedChapters] answers for one album whose file is at hand. This answers for all of them
     * without any file, which is what lets a library show an album's songs — and lets a playlist
     * hold one — after the staged file has been trimmed. The rows carry the piece CID they were
     * read from, so a reader can tell when an album has been re-uploaded. Not in haven-mobile.
     */
    fun observeChapters(): Flow<List<CachedChapterEntity>>
}
