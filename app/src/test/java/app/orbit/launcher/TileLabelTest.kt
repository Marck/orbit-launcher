package app.orbit.launcher

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The captions under the two plus tiles.
 *
 * `basicMarquee` scrolls anything too wide for its tile, forever. Under an app's own name that
 * is right, because a name can be any length and cutting it short is worse. Under a caption
 * Orbit writes itself it meant the row was never still: "Add input to favorites" is close to
 * twice the width of the 100dp tile it sat under, so any glance at the row caught it part way
 * through a word and it read as clipped rather than as scrolling.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TileLabelTest {

    /** 1080p at density 320: one dp is two pixels. */
    private val density = 2f

    @Test
    fun `the captions Orbit writes itself fit their tiles, so they never scroll`() {
        val paint = Paint().apply { textSize = TileLabelSize.value * density }

        for ((label, width) in listOf(AddAppLabel to TileW, AddInputLabel to InputW)) {
            val drawn = paint.measureText(label)
            val room = width.value * density
            assertTrue("\"$label\" is ${drawn}px in a ${room}px tile, so it scrolls", drawn <= room)
        }
    }

    @Test
    fun `the measurement is the one that would have caught this`() {
        // The caption that was there, measured the same way, does not fit. Without this the
        // test above could pass by measuring nothing at all.
        val paint = Paint().apply { textSize = TileLabelSize.value * density }
        assertTrue(
            "the old caption fits after all, so this test proves nothing",
            paint.measureText("Add input to favorites") > InputW.value * density,
        )
    }
}
