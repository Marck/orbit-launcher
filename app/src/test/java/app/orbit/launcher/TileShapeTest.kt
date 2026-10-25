package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A tile has to be the shape of the artwork that goes in it.
 *
 * The requirement asked for a 111 x 66dp tile and, a few lines later, for the app's own
 * 320x180 banner to be drawn in it. Those are 1.68:1 and 16:9, and they do not fit. The tile
 * drew the banner to its own width and centred what was left, so every app had a band of empty
 * tile above and below its banner: measured on the set, a 132px tile with 126px of banner in
 * it, four pixels above and three below. Dark on a dark tile, so it read as a black line.
 *
 * Fitting is still the right way to draw it, because an app that ships a banner at some other
 * shape should be letterboxed rather than cropped. The fix is for the common case to have
 * nothing to letterbox.
 */
class TileShapeTest {

    /** What Android TV asks an app for, and what every banner on the set measured. */
    private val bannerAspect = 320f / 180f

    /** 1080p at density 320: one dp is two pixels. */
    private val density = 2f

    private fun aspect(w: Float, h: Float) = w / h

    @Test
    fun `a tile is the shape of the banner it holds`() {
        assertEquals(
            "a tile of a different shape letterboxes every banner on the set",
            bannerAspect,
            aspect(TileW.value, TileH.value),
            0.001f,
        )
    }

    @Test
    fun `a tile lands on whole pixels, so no hairline is left by rounding`() {
        listOf("width" to TileW.value, "height" to TileH.value).forEach { (name, dp) ->
            val px = dp * density
            assertTrue("tile $name is $px pixels, which is not whole", abs(px - px.toInt()) < 0.001f)
        }
    }

    @Test
    fun `the old tile fails both of those`() {
        // Without this, the tests above only describe whatever is in the file today.
        val wasW = 111f
        val wasH = 66f
        assertTrue(
            "the shape rule would have passed the tile that caused this",
            abs(aspect(wasW, wasH) - bannerAspect) > 0.001f,
        )
        // And the band it left, in pixels, at the density the set runs at.
        val bannerHeight = wasW * density / bannerAspect
        val band = wasH * density - bannerHeight
        assertTrue("the old tile left no band to explain the black line", band > 4f)
    }
}
