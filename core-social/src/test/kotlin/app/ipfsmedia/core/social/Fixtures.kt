package app.ipfsmedia.core.social

internal const val ROOT = "0xroot"
internal const val DAO = "BaseMainnet:0xabcdef0123456789abcdef0123456789abcdef01"
internal const val ALICE = "0x1111111111111111111111111111111111111111"
internal const val BOB = "0x2222222222222222222222222222222222222222"

internal fun post(
    key: String,
    parent: String? = null,
    block: Long? = 100,
    author: String = ALICE,
    root: String = ROOT,
    body: PostBody = PostBody.Clear("text of $key"),
) = BoardRecord(
    key = key,
    type = BoardRecordType.POST,
    daoKey = DAO,
    gate = null,
    author = author,
    subject = null,
    rootRef = root,
    parentRef = parent,
    createdAtBlock = block,
    expiresAtBlock = null,
    body = body,
    receipt = null,
    hasMedia = false,
)

internal fun thread(key: String, block: Long? = 100, author: String = ALICE, subject: String? = "Subject $key") =
    BoardRecord(
        key = key,
        type = BoardRecordType.THREAD,
        daoKey = DAO,
        gate = null,
        author = author,
        subject = subject,
        rootRef = null,
        parentRef = null,
        createdAtBlock = block,
        expiresAtBlock = block?.plus(1_000),
        body = PostBody.Clear("opening text of $key"),
        receipt = null,
        hasMedia = false,
    )
