package app.ipfsmedia.core.design

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cover art for an upload: the picture taken from its file if there is one, otherwise the
 * generated cover. The picture only exists once the upload has been fetched and unlocked, so
 * most covers are generated ones, and that is by design (docs/PLAN.md §5).
 *
 * No image-loading library: the pictures are small local files, decoded here off the
 * composition thread, downsampled, and kept in a small memory cache.
 */
@Composable
fun Artwork(
    recordKey: String,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = Radius.md,
    showInitials: Boolean = true,
) {
    val file = LocalArtworkSource.current.fileFor(recordKey)
    val stamp = file?.let { "${it.path}:${it.lastModified()}:${it.length()}" }
    val bitmap by produceState(initialValue = stamp?.let(ArtworkCache::get), stamp) {
        value = when {
            file == null || stamp == null -> null
            else -> ArtworkCache.get(stamp) ?: withContext(Dispatchers.IO) { decode(file) }
                ?.also { ArtworkCache.put(stamp, it) }
        }
    }
    Box(modifier = modifier.clip(shape)) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(
                seed = recordKey,
                title = title,
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                showInitials = showInitials,
            )
        }
    }
}

private object ArtworkCache {
    private val cache = LruCache<String, ImageBitmap>(MAX_PICTURES)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    private const val MAX_PICTURES = 96
}

/** Decodes [file] at no more than about [MAX_EDGE_PX] on its longer side. Null if it is not a picture. */
private fun decode(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return@runCatching null
    var sample = 1
    while (longest / (sample * 2) >= MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}.getOrNull()

private const val MAX_EDGE_PX = 512
