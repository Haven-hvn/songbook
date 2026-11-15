package app.ipfsmedia.core.player

import app.ipfsmedia.core.library.ChapterRow
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.LibraryInputs
import app.ipfsmedia.core.library.LibraryState
import app.ipfsmedia.core.library.PlayableEntry
import app.ipfsmedia.core.library.deriveLibrary
import app.ipfsmedia.core.userdata.DaoStateEntity
import cloud.filecoin.foc.cache.PieceRef
import haven.mobile.core.domain.ArkivStatus
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.GateMetadata
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import haven.mobile.core.domain.TokenGate

internal const val TOKEN = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
internal const val DAO = "BaseMainnet:$TOKEN"
internal const val WALLET = "0x1111111111111111111111111111111111111111"
internal const val UPLOADER = "0x2222222222222222222222222222222222222222"

internal fun song(record: String, index: Int, title: String = "Song $index", pieceCid: String = "piece-$record") =
    PlayableEntry(
        recordKey = record,
        chapterIndex = index,
        kind = EntryKind.SONG,
        pieceCid = pieceCid,
        startMs = index * 60_000L,
        endMs = (index + 1) * 60_000L,
        title = title,
        albumTitle = "Album $record",
        uploaderName = "uploader",
        uploaderAddress = UPLOADER,
        daoKey = DAO,
        durationMs = 60_000L,
    )

internal fun album(record: String, pieceCid: String? = "piece-$record", durationMs: Long? = 600_000L) = PlayableEntry(
    recordKey = record,
    chapterIndex = PlayableEntry.WHOLE_FILE,
    kind = EntryKind.ALBUM,
    pieceCid = pieceCid,
    startMs = 0L,
    endMs = null,
    title = "Album $record",
    albumTitle = "Album $record",
    uploaderName = "uploader",
    uploaderAddress = UPLOADER,
    daoKey = DAO,
    durationMs = durationMs,
)

internal fun video(record: String, durationMs: Long? = 3_600_000L) =
    album(record, durationMs = durationMs).copy(kind = EntryKind.VIDEO, title = "Video $record")

internal fun record(
    id: String,
    kind: MediaKind = MediaKind.AUDIO,
    gated: Boolean = true,
    pieceCid: String = "piece-$id",
): MediaItem = MediaItem(
    id = id,
    kind = kind,
    owner = UPLOADER,
    title = "Album $id",
    description = null,
    mimeType = null,
    fileExtension = null,
    filenameHint = null,
    sizeBytes = null,
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    createdAtBlock = 1_000,
    expiresAtBlock = null,
    pieceRef = PieceRef(pieceCid = pieceCid, size = 0, providerServiceUrls = emptyList()),
    filecoinCid = null,
    encryptedCid = null,
    cidHash = null,
    gate = if (gated) TokenGate(chain = "eip155:8453", tokenAddress = TOKEN, threshold = 1.0) else null,
    isEncrypted = gated,
    encryptionMetadata = if (gated) GateMetadata.Sealed(version = 3, encryptedAesKey = "k") else null,
    cidEncryptionMetadata = null,
    attestation = null,
    arkivStatus = ArkivStatus.FRESH,
    contentCacheStatus = ContentCacheStatus.UNCACHED,
    lastAccessedAt = null,
    durationSeconds = 600,
    creatorHandle = null,
)

internal fun catalogue(
    vararg media: MediaItem,
    member: Boolean = true,
    chapters: List<ChapterRow> = emptyList(),
): LibraryState = deriveLibrary(
    LibraryInputs(
        walletAddress = WALLET,
        media = media.toList(),
        chapters = chapters,
        daoStates = listOf(
            DaoStateEntity(
                daoKey = DAO,
                membership = if (member) DaoStateEntity.MEMBER else DaoStateEntity.NOT_MEMBER,
                checkedAtMs = 1,
                title = null,
                about = null,
            ),
        ),
        pinnedDaoKeys = emptyList(),
        favouriteRecordKeys = emptySet(),
        follows = emptyMap(),
        clock = null,
        notOpenYet = emptySet(),
    ),
)
