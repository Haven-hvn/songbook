package app.ipfsmedia.core.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import haven.mobile.core.wallet.WalletSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover art taken out of the media itself.
 *
 * Records carry no thumbnail, so the only picture an album can have is the one embedded in its
 * file (ID3 `APIC`, MP4 `covr`), and that is only readable once the file has been unlocked.
 * Everything else shows a generated pattern (`core-design`).
 *
 * Kept **per identity**, under `cacheDir/artwork/<address>/`, and wiped with it. haven-mobile
 * writes to one shared `cacheDir/haven-artwork/` that no disconnect step deletes, so a picture
 * from gated content outlives the wallet that was allowed to see it (docs/PLAN.md §9).
 */
@Singleton
class ArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletSession: WalletSession,
) {
    private val _revision = MutableStateFlow(0)

    /** Bumped whenever a picture is written or the store is wiped, so the UI knows to look again. */
    val revision: StateFlow<Int> = _revision

    /** Where [recordKey]'s picture is, or would be. Null when no identity is active. */
    fun slotFor(recordKey: String): File? {
        val wallet = walletSession.address.value ?: return null
        return File(directoryFor(wallet), "${sanitise(recordKey)}.jpg")
    }

    /** The picture for [recordKey] if one has been extracted. Cheap: one `stat`. */
    fun find(recordKey: String): File? = slotFor(recordKey)?.takeIf { it.isFile && it.length() > 0 }

    /**
     * Pulls a picture out of [source] once: the embedded one, or for a video with none, a frame
     * from near its start. Returns null, not failure, when there is nothing to take: most uploads
     * carry no picture, and that must never get in the way of playing them.
     */
    suspend fun extract(recordKey: String, source: File, isVideo: Boolean = false): File? = withContext(Dispatchers.IO) {
        val target = slotFor(recordKey) ?: return@withContext null
        if (target.isFile && target.length() > 0) return@withContext target
        val retriever = MediaMetadataRetriever()
        val bytes = try {
            runCatching {
                retriever.setDataSource(source.absolutePath)
                retriever.embeddedPicture ?: if (isVideo) frameOf(retriever) else null
            }.getOrNull()
        } finally {
            runCatching { retriever.release() }
        }
        if (bytes == null || bytes.isEmpty()) return@withContext null
        val written = runCatching {
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, "${target.name}.part")
            partial.writeBytes(bytes)
            if (!partial.renameTo(target)) {
                partial.delete()
                error("could not move artwork into place")
            }
            target
        }.onFailure { Timber.w(it, "Artwork: could not store the picture for %s", recordKey) }.getOrNull()
        if (written != null) _revision.update { it + 1 }
        written
    }

    /** A small still from a tenth of the way in (at most [FRAME_AT_MOST_US]), as JPEG bytes. */
    private fun frameOf(retriever: MediaMetadataRetriever): ByteArray? {
        val durationUs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.times(1000) ?: 0L
        val atUs = (durationUs / 10).coerceIn(0L, FRAME_AT_MOST_US)
        val frame = retriever.getScaledFrameAtTime(
            atUs,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            FRAME_WIDTH_PX,
            FRAME_HEIGHT_PX,
        ) ?: return null
        return try {
            ByteArrayOutputStream().use { out ->
                if (frame.compress(Bitmap.CompressFormat.JPEG, FRAME_JPEG_QUALITY, out)) out.toByteArray() else null
            }
        } finally {
            frame.recycle()
        }
    }

    suspend fun wipe(walletAddress: String) {
        withContext(Dispatchers.IO) {
            val parent = File(context.cacheDir, DIRECTORY)
            val lower = sanitise(walletAddress).lowercase()
            parent.listFiles()
                ?.filter { it.isDirectory && it.name.equals(lower, ignoreCase = true) }
                ?.forEach { it.deleteRecursively() }
        }
        _revision.update { it + 1 }
    }

    private fun directoryFor(walletAddress: String): File =
        File(File(context.cacheDir, DIRECTORY), sanitise(walletAddress).lowercase())

    /** Record keys arrive from a remote index, so they are never trusted to be path-safe. */
    private fun sanitise(value: String): String =
        value.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }
            .joinToString("")
            .take(MAX_NAME_LENGTH)

    private companion object {
        const val DIRECTORY = "artwork"
        const val MAX_NAME_LENGTH = 96
        const val FRAME_AT_MOST_US = 20_000_000L
        const val FRAME_WIDTH_PX = 640
        const val FRAME_HEIGHT_PX = 360
        const val FRAME_JPEG_QUALITY = 85
    }
}
