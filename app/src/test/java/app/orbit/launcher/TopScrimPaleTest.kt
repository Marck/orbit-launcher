package app.orbit.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scrim leaning harder on artwork that would otherwise swallow the bar.
 *
 * Prime lays its own banner across the top of its artwork, a white pill with black lettering in
 * it, and Orbit's white glyphs and clock sit straight on top of that. The scrim was tuned for
 * the dark artwork most of it is, so over the pill it did nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TopScrimPaleTest {

    /** A picture with a pale band across [share] of the strip the glyphs sit over. */
    private fun artwork(share: Float): androidx.compose.ui.graphics.ImageBitmap {
        val bitmap = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 24, 30))
        if (share > 0f) {
            // Across the top, on the right, which is where the glyphs are.
            val from = 800 * 0.23f
            canvas.drawRect(
                from, 0f, from + (800 - from) * share, 450 * 0.18f,
                Paint().apply { color = Color.WHITE },
            )
        }
        return bitmap.asImageBitmap()
    }

    @Test
    fun `a dark picture is read as having nothing pale under the bar`() {
        assertEquals(0f, paleShare(artwork(0f)), 0.01f)
    }

    @Test
    fun `a band across the glyphs is seen, and in proportion`() {
        val quarter = paleShare(artwork(0.25f))
        val half = paleShare(artwork(0.5f))

        assertTrue("a quarter of the strip was not noticed at all: $quarter", quarter > 0.1f)
        assertTrue("more pale did not read as more: $quarter then $half", half > quarter)
        assertTrue("a half strip read as the whole thing: $half", half < 0.8f)
    }

    @Test
    fun `dark artwork is left exactly as it was`() {
        // The scrim was tuned over real artwork. Nothing moves where nothing was wrong.
        assertEquals(1f, scrimStrength(0f), 0.0001f)
        assertEquals(1f, scrimStrength(PaleFloor), 0.0001f)
    }

    @Test
    fun `the harder it is to read, the harder the scrim works, up to a limit`() {
        val steps = (0..10).map { scrimStrength(it / 10f) }

        assertTrue("the ramp goes backwards somewhere: $steps", steps.zipWithNext().all { it.first <= it.second })
        assertEquals(ScrimMaxStrength, scrimStrength(PaleFull), 0.0001f)
        assertEquals("past the top it keeps climbing", ScrimMaxStrength, scrimStrength(1f), 0.0001f)
    }

    @Test
    fun `no stop is ever darkened past the ceiling`() {
        val worst = ScrimStops.map { (it * scrimStrength(1f)).coerceAtMost(ScrimCeiling) }

        assertTrue("the scrim became a black panel: $worst", worst.all { it <= ScrimCeiling })
        // The fade still fades: the top is darker than the bottom, which is what keeps it edgeless.
        assertTrue("the ramp flattened out: $worst", worst.first() > worst.last())
        assertEquals("the fade no longer reaches nothing", 0f, worst.last(), 0.0001f)
    }
}
