package app.ipfsmedia.core.social

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the last refresh read about discussion, for one identity. */
data class BoardSnapshot(
    val defs: List<BoardDef> = emptyList(),
    val threads: List<BoardRecord> = emptyList(),
    val posts: List<BoardRecord> = emptyList(),
    /** Receipt verdict per record key. Absent means unchecked. */
    val receipts: Map<String, ReceiptStatus> = emptyMap(),
    /** True when the listing was cut short by its page cap: counts are a lower bound. */
    val truncated: Boolean = false,
) {
    val isEmpty: Boolean get() = defs.isEmpty() && threads.isEmpty() && posts.isEmpty()
}

/**
 * Holds the discussion records between refreshes.
 *
 * Kept in memory, and written to a small file so that an app start that cannot refresh (offline,
 * or rate limited) still shows the comments it had. The file is a cache of public records: it
 * lives in the cache directory, per identity, and is deleted with the identity.
 */
@Singleton
class BoardStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _snapshot = MutableStateFlow(BoardSnapshot())
    val snapshot: StateFlow<BoardSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var loadedFor: String? = null

    /** Points the store at [walletAddress]'s cache, loading it from disk. Null clears it. */
    suspend fun switchTo(walletAddress: String?) {
        val id = walletAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (id == loadedFor) return
        loadedFor = id
        _snapshot.value = if (id == null) BoardSnapshot() else withContext(Dispatchers.IO) { read(id) }
    }

    /** Replaces what is held for [walletAddress] and writes it through to disk. */
    suspend fun replace(walletAddress: String, snapshot: BoardSnapshot) {
        val id = walletAddress.trim().lowercase()
        // An identity switch mid-refresh must not show one identity the other's (identical, but
        // differently verified) snapshot; the file is still written for when it comes back.
        if (loadedFor == id) _snapshot.value = snapshot
        withContext(Dispatchers.IO) { write(id, snapshot) }
    }

    suspend fun delete(walletAddress: String) {
        val id = walletAddress.trim().lowercase()
        if (loadedFor == id) _snapshot.value = BoardSnapshot()
        withContext(Dispatchers.IO) { runCatching { directory(id).deleteRecursively() } }
    }

    private fun directory(id: String): File =
        File(File(context.cacheDir, "social"), id.filter { it.isLetterOrDigit() })

    private fun file(id: String): File = File(directory(id), "board.json")

    private fun write(id: String, snapshot: BoardSnapshot) {
        runCatching {
            val json = JSONObject()
                .put("v", FORMAT)
                .put("truncated", snapshot.truncated)
                .put("defs", JSONArray(snapshot.defs.map { it.toJson() }))
                .put("threads", JSONArray(snapshot.threads.map { it.toJson() }))
                .put("posts", JSONArray(snapshot.posts.map { it.toJson() }))
                .put("receipts", JSONObject(snapshot.receipts.mapValues { it.value.name }))
            val target = file(id)
            target.parentFile?.mkdirs()
            // Write-then-rename, so a crash mid-write leaves the previous file intact.
            val partial = File(target.parentFile, "board.json.tmp")
            partial.writeText(json.toString())
            if (!partial.renameTo(target)) partial.delete()
        }.onFailure { Timber.w(it, "Board cache could not be written") }
    }

    private fun read(id: String): BoardSnapshot = runCatching {
        val target = file(id)
        if (!target.isFile) return BoardSnapshot()
        val json = JSONObject(target.readText())
        if (json.optInt("v") != FORMAT) return BoardSnapshot()
        BoardSnapshot(
            defs = json.optJSONArray("defs").objects().mapNotNull { it.toDef() },
            threads = json.optJSONArray("threads").objects().mapNotNull { it.toRecord() },
            posts = json.optJSONArray("posts").objects().mapNotNull { it.toRecord() },
            receipts = json.optJSONObject("receipts")?.let { map ->
                map.keys().asSequence().mapNotNull { key ->
                    ReceiptStatus.entries.firstOrNull { it.name == map.optString(key) }?.let { key to it }
                }.toMap()
            }.orEmpty(),
            truncated = json.optBoolean("truncated"),
        )
    }.getOrElse {
        Timber.w(it, "Board cache could not be read")
        BoardSnapshot()
    }

    private companion object {
        const val FORMAT = 1
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun BoardDef.toJson(): JSONObject = JSONObject()
    .put("daoKey", daoKey)
    .putOpt("slug", slug)
    .putOpt("title", title)
    .putOpt("about", about)
    .putOpt("createdAtBlock", createdAtBlock)

private fun JSONObject.toDef(): BoardDef? {
    val key = optString("daoKey", "").takeIf { it.isNotEmpty() } ?: return null
    return BoardDef(
        daoKey = key,
        slug = optStringOrNull("slug"),
        title = optStringOrNull("title"),
        about = optStringOrNull("about"),
        createdAtBlock = optLongOrNull("createdAtBlock"),
    )
}

private fun BoardRecord.toJson(): JSONObject = JSONObject()
    .put("key", key)
    .put("type", type.name)
    .putOpt("daoKey", daoKey)
    .putOpt("gateChain", gate?.chainId)
    .putOpt("gateToken", gate?.token)
    .putOpt("gateThreshold", gate?.threshold?.toString())
    .put("author", author)
    .putOpt("subject", subject)
    .putOpt("rootRef", rootRef)
    .putOpt("parentRef", parentRef)
    .putOpt("createdAtBlock", createdAtBlock)
    .putOpt("expiresAtBlock", expiresAtBlock)
    .putOpt("clear", (body as? PostBody.Clear)?.text)
    .putOpt("sealed", (body as? PostBody.Sealed)?.ciphertext)
    .putOpt("rEpoch", receipt?.epoch)
    .putOpt("rAt", receipt?.at)
    .putOpt("rBal", receipt?.bal)
    .putOpt("rSig", receipt?.sig)
    .put("hasMedia", hasMedia)

private fun JSONObject.toRecord(): BoardRecord? {
    val key = optString("key", "").takeIf { it.isNotEmpty() } ?: return null
    val type = BoardRecordType.entries.firstOrNull { it.name == optString("type") } ?: return null
    val clear = optStringOrNull("clear")
    val sealed = optStringOrNull("sealed")
    val threshold = optStringOrNull("gateThreshold")?.let { runCatching { BigInteger(it) }.getOrNull() }
    val token = optStringOrNull("gateToken")
    val chainId = optLongOrNull("gateChain")
    return BoardRecord(
        key = key,
        type = type,
        daoKey = optStringOrNull("daoKey"),
        gate = if (chainId != null && token != null && threshold != null) GateSpec(chainId, token, threshold) else null,
        author = optString("author", ""),
        subject = optStringOrNull("subject"),
        rootRef = optStringOrNull("rootRef"),
        parentRef = optStringOrNull("parentRef"),
        createdAtBlock = optLongOrNull("createdAtBlock"),
        expiresAtBlock = optLongOrNull("expiresAtBlock"),
        body = when {
            clear != null -> PostBody.Clear(clear)
            sealed != null -> PostBody.Sealed(sealed)
            else -> PostBody.None
        },
        receipt = run {
            val epoch = optLongOrNull("rEpoch")
            val at = optLongOrNull("rAt")
            val bal = optStringOrNull("rBal")
            val sig = optStringOrNull("rSig")
            if (epoch != null && at != null && bal != null && sig != null) MemberReceipt(epoch, at, bal, sig) else null
        },
        hasMedia = optBoolean("hasMedia"),
    )
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name, "").takeIf { it.isNotEmpty() } else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
