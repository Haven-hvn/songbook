package app.ipfsmedia.core.design

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A hexagon with a flat top and bottom, the shape Parachord draws people in. Here it marks an
 * uploader, so an address is never mistaken for an album or a DataDAO at a glance.
 */
val HexagonShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val radius = minOf(size.width, size.height) / 2f
        val centreX = size.width / 2f
        val centreY = size.height / 2f
        for (corner in 0 until 6) {
            // Pointy sides left and right: corners at 0, 60, 120… degrees.
            val angle = PI / 3.0 * corner
            val x = centreX + radius * cos(angle).toFloat()
            val y = centreY + radius * sin(angle).toFloat()
            if (corner == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** An uploader's picture: a generated pattern in a hexagon, with their initials. */
@Composable
fun HexAvatar(address: String, name: String, modifier: Modifier = Modifier, size: Dp = Sizes.avatar) {
    GeneratedCover(seed = address, title = name, modifier = modifier.size(size), shape = HexagonShape)
}
