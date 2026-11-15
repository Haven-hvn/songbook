package app.ipfsmedia.core.player

import android.content.Context
import app.ipfsmedia.core.library.EntryKind
import app.ipfsmedia.core.library.PlayableEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queue, kept per identity between runs.
 *
 * It holds entries (record key, song index, the copied titles and times), never file paths: a
 * staged file can be trimmed at any time, so resuming re-stages what it needs. It is removed on
 * every disconnect, because it names what the wallet was listening to.
 */
@Singleton
class QueueStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Blocking; call off the main thread. */
    fun load(walletAddress: String): SavedQueue? {
        val file = fileFor(walletAddress)
        if (!file.isFile) return null
        return runCatching { QueueJson.decode(file.readText()) }
            .onFailure { Timber.w(it, "Queue: the saved queue could not be read") }
            .getOrNull()
    }

    /** Blocking. An empty queue is stored as no file at all. */
    fun save(walletAddress: String, queue: SavedQueue) {
        val file = fileFor(walletAddress)
        if (queue.entries.isEmpty()) {
            file.delete()
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, "${file.name}.part")
            partial.writeText(QueueJson.encode(queue))
            if (!partial.renameTo(file)) {
                partial.delete()
                error("could not move the saved queue into place")
            }
        }.onFailure { Timber.w(it, "Queue: could not be saved") }
    }

    fun delete(walletAddress: String) {
        val directory = File(context.noBackupFilesDir, DIRECTORY)
        val name = fileName(walletAddress)
        directory.listFiles()
            ?.filter { it.name.equals(name, ignoreCase = true) || it.name.equals("$name.part", ignoreCase = true) }
            ?.forEach { it.delete() }
    }

    private fun fileFor(walletAddress: String): File =
        File(File(context.noBackupFilesDir, DIRECTORY), fileName(walletAddress))

    private fun fileName(walletAddress: String): String {
        val safe = walletAddress.trim().lowercase()
            .map { c -> if (c.isLetterOrDigit()) c else '_' }
            .joinToString("")
            .take(MAX_NAME_LENGTH)
        return "$safe.json"
    }

    private companion object {
        const val DIRECTORY = "queue"
        const val MAX_NAME_LENGTH = 96
    }
}

/** The saved queue's file format. Plain functions so the round trip is tested without a device. */
internal object QueueJson {
    private const val VERSION = 1

    /** Longest queue that is written. Anything longer is cut from the end. */
    const val MAX_ENTRIES = 5_000

    fun encode(queue: SavedQueue): String {
        val entries = JSONArray()
        queue.entries.take(MAX_ENTRIES).forEach { entries.put(encodeEntry(it)) }
        return JSONObject()
            .put("v", VERSION)
            .put("index", queue.index)
            .put("positionMs", queue.positionMs)
            .put("shuffle", queue.shuffle)
            .put("repeat", queue.repeatMode)
            .put("volume", queue.volume.toDouble())
            .put("entries", entries)
            .toString()
    }

    /** Null when [text] is not a queue this version understands. Rows that do not parse are dropped. */
    fun decode(text: String): SavedQueue? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (root.optInt("v", 0) != VERSION) return null
        val array = root.optJSONArray("entries") ?: return null
        val entries = ArrayList<PlayableEntry>(array.length())
        var index = root.optInt("index", 0)
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i)?.let(::decodeEntry)
            if (entry != null) {
                entries += entry
            } else if (i < root.optInt("index", 0)) {
                // A dropped row before the current one shifts it up.
                index--
            }
        }
        if (entries.isEmpty()) return null
        return SavedQueue(
            entries = entries,
            index = index.coerceIn(0, entries.lastIndex),
            positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L),
            shuffle = root.optBoolean("shuffle", false),
            repeatMode = root.optInt("repeat", 0).coerceIn(0, 2),
            volume = root.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        )
    }

    private fun encodeEntry(entry: PlayableEntry): JSONObject = JSONObject()
        .put("recordKey", entry.recordKey)
        .put("chapter", entry.chapterIndex)
        .put("kind", entry.kind.name)
        .put("pieceCid", entry.pieceCid ?: JSONObject.NULL)
        .put("startMs", entry.startMs)
        .put("endMs", entry.endMs ?: JSONObject.NULL)
        .put("title", entry.title)
        .put("album", entry.albumTitle)
        .put("uploader", entry.uploaderName)
        .put("uploaderAddress", entry.uploaderAddress)
        .put("dao", entry.daoKey ?: JSONObject.NULL)
        .put("durationMs", entry.durationMs ?: JSONObject.NULL)

    private fun decodeEntry(json: JSONObject): PlayableEntry? {
        val recordKey = json.optString("recordKey").takeIf { it.isNotBlank() } ?: return null
        val chapter = json.optInt("chapter", PlayableEntry.WHOLE_FILE)
        val kind = EntryKind.entries.firstOrNull { it.name == json.optString("kind") }
            ?: if (chapter == PlayableEntry.WHOLE_FILE) EntryKind.ALBUM else EntryKind.SONG
        return PlayableEntry(
            recordKey = recordKey,
            chapterIndex = chapter,
            kind = kind,
            pieceCid = json.stringOrNull("pieceCid"),
            startMs = json.optLong("startMs", 0L),
            endMs = json.longOrNull("endMs"),
            title = json.optString("title"),
            albumTitle = json.optString("album"),
            uploaderName = json.optString("uploader"),
            uploaderAddress = json.optString("uploaderAddress"),
            daoKey = json.stringOrNull("dao"),
            durationMs = json.longOrNull("durationMs"),
        )
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)
}
