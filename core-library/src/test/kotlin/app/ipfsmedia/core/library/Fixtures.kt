package app.ipfsmedia.core.library

import app.ipfsmedia.core.userdata.DaoStateEntity
import cloud.filecoin.foc.cache.PieceRef
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate

internal const val TOKEN_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
internal const val TOKEN_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
internal const val DAO_A = "BaseMainnet:$TOKEN_A"
internal const val DAO_B = "BaseMainnet:$TOKEN_B"
internal const val WALLET = "0x1111111111111111111111111111111111111111"
internal const val OTHER = "0x2222222222222222222222222222222222222222"

internal fun gate(token: String = TOKEN_A, threshold: Double = 1.0) =
    TokenGate(chain = "eip155:8453", tokenAddress = token, threshold = threshold)

internal fun media(
    id: String,
    kind: MediaKind = MediaKind.AUDIO,
    title: String = "Title $id",
    owner: String = OTHER,
    gate: TokenGate? = gate(),
    encrypted: Boolean = gate != null,
    block: Long? = 1_000,
    pieceCid: String = "bafkzcib-$id",
    durationSeconds: Long? = 600,
    handle: String? = null,
    cache: ContentCacheStatus = ContentCacheStatus.UNCACHED,
): MediaItem = MediaItem(
    id = id,
    kind = kind,
    owner = owner,
    title = title,
    description = null,
    mimeType = null,
    fileExtension = null,
    filenameHint = null,
    sizeBytes = null,
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    createdAtBlock = block,
    expiresAtBlock = null,
    pieceRef = PieceRef(pieceCid = pieceCid, size = 0, providerServiceUrls = emptyList()),
    filecoinCid = null,
    encryptedCid = null,
    cidHash = null,
    gate = gate,
    isEncrypted = encrypted,
    encryptionMetadata = if (encrypted) GateMetadata.Sealed(version = 3, encryptedAesKey = "k") else null,
    cidEncryptionMetadata = null,
    attestation = null,
    arkivStatus = ArkivStatus.FRESH,
    contentCacheStatus = cache,
    lastAccessedAt = null,
    durationSeconds = durationSeconds,
    creatorHandle = handle,
)

internal fun member(key: String = DAO_A, title: String? = null) =
    DaoStateEntity(daoKey = key, membership = DaoStateEntity.MEMBER, checkedAtMs = 1, title = title, about = null)

internal fun notMember(key: String = DAO_A) =
    DaoStateEntity(daoKey = key, membership = DaoStateEntity.NOT_MEMBER, checkedAtMs = 1, title = null, about = null)

internal fun library(
    vararg media: MediaItem,
    daoStates: List<DaoStateEntity> = listOf(member()),
    chapters: List<ChapterRow> = emptyList(),
    follows: Map<String, Long> = emptyMap(),
    favourites: Set<String> = emptySet(),
    pins: List<String> = emptyList(),
    notOpenYet: Set<String> = emptySet(),
    clock: BlockClock? = null,
): LibraryState = deriveLibrary(
    LibraryInputs(
        walletAddress = WALLET,
        media = media.toList(),
        chapters = chapters,
        daoStates = daoStates,
        pinnedDaoKeys = pins,
        favouriteRecordKeys = favourites,
        follows = follows,
        clock = clock,
        notOpenYet = notOpenYet,
    ),
)

internal fun chapters(albumId: String, pieceCid: String = "bafkzcib-$albumId", vararg titles: String): List<ChapterRow> =
    titles.mapIndexed { index, title -> ChapterRow(albumId, index, index * 60_000L, title, pieceCid) }
