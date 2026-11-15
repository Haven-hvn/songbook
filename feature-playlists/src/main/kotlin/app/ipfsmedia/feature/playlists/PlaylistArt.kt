package app.ipfsmedia.feature.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import app.ipfsmedia.core.design.AppIcons
import app.ipfsmedia.core.design.Artwork
import app.ipfsmedia.core.design.GeneratedCover
import app.ipfsmedia.core.design.Radius
import app.ipfsmedia.core.design.Sizes

/** A record a playlist's picture can be drawn from. */
data class CoverSource(val recordKey: String, val title: String)

/**
 * A playlist's picture: the covers of the first four different uploads in it, as a two-by-two
 * mosaic. With fewer than four it is the first one's cover; with none, a generated pattern
 * from the playlist's own id, so an empty playlist still has a face.
 */
@Composable
fun PlaylistArt(covers: List<CoverSource>, seed: String, title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(Radius.lg), contentAlignment = Alignment.Center) {
        when {
            covers.size >= MOSAIC -> Column(modifier = Modifier.fillMaxSize()) {
                covers.take(MOSAIC).chunked(MOSAIC_ROW).forEach { pair ->
                    Row(modifier = Modifier.weight(1f)) {
                        pair.forEach { cover ->
                            Artwork(
                                recordKey = cover.recordKey,
                                title = cover.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                // Square tiles: the mosaic's own corners are the rounded ones.
                                shape = RectangleShape,
                                showInitials = false,
                            )
                        }
                    }
                }
            }
            covers.isNotEmpty() -> Artwork(
                recordKey = covers.first().recordKey,
                title = covers.first().title,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
            else -> {
                GeneratedCover(seed = seed, title = title, modifier = Modifier.fillMaxSize(), shape = RectangleShape, showInitials = false)
                Icon(
                    imageVector = AppIcons.Playlist,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(Sizes.iconHero),
                )
            }
        }
    }
}

private const val MOSAIC = 4
private const val MOSAIC_ROW = 2
