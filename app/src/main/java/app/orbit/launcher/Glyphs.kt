package app.orbit.launcher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icons the core Material set does not include, drawn from Material's own path data.
 *
 * The extended icon set has them, but it is a very large dependency for a few shapes. These are
 * drawn black and tinted where they are used, like any other icon.
 */
object Glyphs {

    /** A frame with an arrow going in. The stock launcher uses it for inputs. */
    val Input: ImageVector = glyph(
        "Input",
        "M21 3.01H3c-1.1 0-2 .9-2 2V9h2V4.99h18v14.03H3V15H1v4.01c0 1.1.9 1.98 2 1.98h18" +
            "c1.1 0 2-.88 2-1.98v-14c0-1.11-.9-2-2-2zM11 16l4-4-4-4v3H1v2h10v3z",
    )

    private fun glyph(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()
}
