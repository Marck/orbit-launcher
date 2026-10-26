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

    /** A frame with an arrow going in. The stock launcher uses it for inputs and for open. */
    val Input: ImageVector = glyph(
        "Input",
        "M21 3.01H3c-1.1 0-2 .9-2 2V9h2V4.99h18v14.03H3V15H1v4.01c0 1.1.9 1.98 2 1.98h18" +
            "c1.1 0 2-.88 2-1.98v-14c0-1.11-.9-2-2-2zM11 16l4-4-4-4v3H1v2h10v3z",
    )

    /** A magnifier, for the TV's own search. */
    val Search: ImageVector = glyph(
        "Search",
        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5" +
            " 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0" +
            "C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
    )

    /** A three by three grid, for the screen that lists every app. */
    val Apps: ImageVector = glyph(
        "Apps",
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4" +
            "h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
    )

    /** A framed picture with a hill in it, for the screen that chooses the backdrops. */
    val Picture: ImageVector = glyph(
        "Picture",
        "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5" +
            "l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z",
    )

    /** Two arrows passing each other, for moving a tile. */
    val Swap: ImageVector = glyph(
        "Swap",
        "M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z",
    )

    /** An i in a ring, for what the build is. */
    val Info: ImageVector = glyph(
        "Info",
        "M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2" +
            " 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z",
    )

    /** An outlined heart with a minus beside it, for leaving the favorites. */
    val HeartMinus: ImageVector = heartWith("HeartMinus", MINUS)

    /** The same heart with a plus, for joining them. Used where an app may not be a favorite yet. */
    val HeartPlus: ImageVector = heartWith("HeartPlus", PLUS)

    /** Material's own outlined heart. */
    private const val HEART =
        "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5" +
            "c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5" +
            " 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5" +
            " 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5" +
            "c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"

    private const val MINUS = "M15 18h8v2h-8z"

    /** The minus with an upright through it, both centred on the same point. */
    private const val PLUS = "M15 18h8v2h-8zM18 15h2v8h-2z"

    private fun heartWith(name: String, sign: String): ImageVector = builder(name)
        // The heart shrunk into the top left, to leave the corner free for the sign.
        .addGroup(scaleX = 0.82f, scaleY = 0.82f, translationY = 0.5f)
        .addPath(pathData = addPathNodes(HEART), fill = SolidColor(Color.Black))
        .clearGroup()
        .addPath(pathData = addPathNodes(sign), fill = SolidColor(Color.Black))
        .build()

    private fun glyph(name: String, path: String): ImageVector =
        builder(name)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
}
