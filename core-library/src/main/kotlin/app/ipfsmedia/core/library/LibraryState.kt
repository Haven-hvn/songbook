package app.ipfsmedia.core.library

/**
 * Everything the app knows about the catalogue for the active identity, derived in one pass
 * (see [deriveLibrary]). Screens read this; none of them queries the index.
 */
data class LibraryState(
    /** False until the mirror has answered once, so screens can tell "loading" from "empty". */
    val ready: Boolean = false,
    val walletAddress: String? = null,
    /** Every live album and video, newest first. Includes locked ones. */
    val items: List<LibraryItem> = emptyList(),
    /** Yours first (pinned, then members), then the rest, each group by latest upload. */
    val daos: List<DataDao> = emptyList(),
    val uploaders: List<Uploader> = emptyList(),
    val clock: BlockClock? = null,
) {
    private val itemsById: Map<String, LibraryItem> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        items.associateBy { it.id }
    }
    private val daosByKey: Map<String, DataDao> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        daos.associateBy { it.key }
    }
    private val uploadersByAddress: Map<String, Uploader> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        uploaders.associateBy { it.address }
    }

    fun item(id: String): LibraryItem? = itemsById[id]

    fun dao(key: String?): DataDao? = key?.let { daosByKey[it] }

    fun uploader(address: String): Uploader? = uploadersByAddress[address.trim().lowercase()]

    /** A DataDAO's uploads, newest first. [PUBLIC_GROUP_KEY] selects the ungated ones. */
    fun itemsOf(daoKey: String): List<LibraryItem> =
        if (daoKey == PUBLIC_GROUP_KEY) items.filter { it.daoKey == null } else items.filter { it.daoKey == daoKey }

    fun itemsBy(address: String): List<LibraryItem> {
        val id = address.trim().lowercase()
        return items.filter { it.uploaderAddress == id }
    }

    /** "Your media": what the wallet can open, what it published, and what it favourited. */
    val collection: List<LibraryItem> get() = items.filter { it.inCollection }

    /** DataDAOs the wallet is a member of, pinned first. */
    val yourDaos: List<DataDao>
        get() = daos.filter { !it.isPublicGroup && (it.pinned || it.membership == Membership.MEMBER) }

    /** Everything else in the directory: DataDAOs the wallet is not in, then the public group. */
    val otherDaos: List<DataDao>
        get() = daos.filter { it.isPublicGroup || !(it.pinned || it.membership == Membership.MEMBER) }

    companion object {
        val EMPTY = LibraryState()
    }
}
