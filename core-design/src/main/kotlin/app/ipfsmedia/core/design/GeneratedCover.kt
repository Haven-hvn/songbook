package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * How to draw the cover for something that has no picture.
 *
 * Records carry no thumbnail, and most uploads have no embedded art, so this is what most of
 * the library looks like. It is Parachord's `generateArtistPattern`: the same palettes and the
 * same six patterns, chosen by a hash of the item's id, so an item always gets the same cover
 * and neighbours rarely match.
 */
data class CoverSpec(
    val palette: Int,
    val pattern: CoverPattern,
    val angleDegrees: Int,
    /** Focus of the radial patterns, as fractions of the box. */
    val focusX: Float,
    val focusY: Float,
    /** Up to two letters from the title, drawn over the pattern. */
    val initials: String,
)

enum class CoverPattern { LINEAR, RADIAL, DIAGONAL_SPLIT, SOFT_RADIAL, CORNER, BANDS }

/** Deterministic: the same [seed] and [title] always give the same cover. */
fun coverSpec(seed: String, title: String): CoverSpec {
    val hash = stableHash(seed.lowercase())
    val patterns = CoverPattern.entries
    return CoverSpec(
        palette = hash % COVER_PALETTES.size,
        pattern = patterns[(hash shr 4) % patterns.size],
        angleDegrees = ((hash shr 8) % 12) * 30,
        focusX = (30 + hash % 40) / 100f,
        focusY = (30 + (hash shr 2) % 40) / 100f,
        initials = initialsOf(title),
    )
}

/** FNV-1a, folded to a non-negative Int. Stable across runs and devices, unlike `hashCode`'s contract. */
internal fun stableHash(text: String): Int {
    var hash = -0x7ee3623b // 2166136261 as a signed Int
    for (char in text) {
        hash = hash xor char.code
        hash *= 16777619
    }
    return hash and Int.MAX_VALUE
}

/** First letters of the first two words, upper-cased; a single word gives one letter. */
internal fun initialsOf(title: String): String =
    title.split(' ', '-', '_', '.')
        .filter { word -> word.any { it.isLetterOrDigit() } }
        .take(2)
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
        .joinToString("")

/** Parachord's palettes: three ground colours and a light one for the lettering. */
internal val COVER_PALETTES: List<List<Color>> = listOf(
    listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460), Color(0xFFE94560)),
    listOf(Color(0xFF2D3436), Color(0xFF636E72), Color(0xFFB2BEC3), Color(0xFFDFE6E9)),
    listOf(Color(0xFF0C0C0C), Color(0xFF1C1C1C), Color(0xFF2D2D2D), Color(0xFFFF6B35)),
    listOf(Color(0xFF1E3A5F), Color(0xFF3D5A80), Color(0xFF98C1D9), Color(0xFFE0FBFC)),
    listOf(Color(0xFF2B2D42), Color(0xFF8D99AE), Color(0xFFEDF2F4), Color(0xFFEF233C)),
    listOf(Color(0xFF003049), Color(0xFFD62828), Color(0xFFF77F00), Color(0xFFFCBF49)),
    listOf(Color(0xFF0A0908), Color(0xFF22333B), Color(0xFFEAE0D5), Color(0xFFC6AC8F)),
    listOf(Color(0xFF10002B), Color(0xFF240046), Color(0xFF3C096C), Color(0xFF5A189A)),
    listOf(Color(0xFF1B4332), Color(0xFF2D6A4F), Color(0xFF40916C), Color(0xFF52B788)),
    listOf(Color(0xFF212529), Color(0xFF343A40), Color(0xFF495057), Color(0xFFADB5BD)),
    listOf(Color(0xFF003459), Color(0xFF007EA7), Color(0xFF00A8E8), Color(0xFFFFFFFF)),
    listOf(Color(0xFF1D3557), Color(0xFF457B9D), Color(0xFFA8DADC), Color(0xFFF1FAEE)),
    listOf(Color(0xFF283618), Color(0xFF606C38), Color(0xFFDDA15E), Color(0xFFBC6C25)),
    listOf(Color(0xFF231942), Color(0xFF5E548E), Color(0xFF9F86C0), Color(0xFFBE95C4)),
    listOf(Color(0xFF0B090A), Color(0xFF161A1D), Color(0xFF660708), Color(0xFFA4161A)),
)

/** The generated cover for [seed], filling its box. */
@Composable
fun GeneratedCover(
    seed: String,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = Radius.md,
    showInitials: Boolean = true,
) {
    val spec = remember(seed, title) { coverSpec(seed, title) }
    val palette = COVER_PALETTES[spec.palette]
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .drawBehind { drawRect(coverBrush(spec, palette, size)) },
        contentAlignment = Alignment.Center,
    ) {
        if (showInitials && spec.initials.isNotEmpty()) {
            // Lettering scales with the box, so a row thumbnail and a hero read alike.
            val fontSize = with(LocalDensity.current) { (maxWidth * 0.34f).toSp() }
            Text(
                text = spec.initials,
                color = palette[3].copy(alpha = 0.9f),
                style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Light, letterSpacing = 0.08.em),
                maxLines = 1,
            )
        }
    }
}

/** A wide, quiet version of the same pattern for hero backgrounds: no lettering. */
@Composable
fun CoverBackdrop(seed: String, modifier: Modifier = Modifier) {
    val spec = remember(seed) { coverSpec(seed, "") }
    val palette = COVER_PALETTES[spec.palette]
    Box(modifier = modifier.drawBehind { drawRect(coverBrush(spec, palette, size)) })
}

private fun coverBrush(spec: CoverSpec, palette: List<Color>, size: Size): Brush {
    val focus = Offset(size.width * spec.focusX, size.height * spec.focusY)
    val reach = max(size.width, size.height)
    return when (spec.pattern) {
        CoverPattern.LINEAR -> angled(spec.angleDegrees, size, 0f to palette[0], 0.5f to palette[1], 1f to palette[2])
        CoverPattern.RADIAL -> Brush.radialGradient(
            0f to palette[2], 0.4f to palette[1], 1f to palette[0],
            center = focus,
            radius = reach,
        )
        CoverPattern.DIAGONAL_SPLIT -> angled(
            spec.angleDegrees, size,
            0f to palette[0], 0.45f to palette[0], 0.45f to palette[2],
            0.55f to palette[2], 0.55f to palette[1], 1f to palette[1],
        )
        CoverPattern.SOFT_RADIAL -> Brush.radialGradient(
            0f to palette[3], 0.5f to palette[1], 1f to palette[0],
            center = focus,
            radius = reach * 0.9f,
        )
        CoverPattern.CORNER -> Brush.radialGradient(
            0f to palette[2], 0.4f to palette[1], 1f to palette[0],
            center = Offset(
                if (spec.focusX < 0.5f) 0f else size.width,
                if (spec.focusY < 0.5f) 0f else size.height,
            ),
            radius = reach * 1.2f,
        )
        CoverPattern.BANDS -> angled(
            spec.angleDegrees, size,
            0f to palette[0], 0.25f to palette[1], 0.5f to palette[2], 0.75f to palette[1], 1f to palette[0],
        )
    }
}

/** A linear gradient across the box at [degrees], like CSS `linear-gradient(<angle>, …)`. */
private fun angled(degrees: Int, size: Size, vararg stops: Pair<Float, Color>): Brush {
    val radians = degrees * PI / 180.0
    // CSS angles run clockwise from "up".
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        *stops,
        start = Offset(centre.x - dx * half, centre.y - dy * half),
        end = Offset(centre.x + dx * half, centre.y + dy * half),
    )
}
