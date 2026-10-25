package app.orbit.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Orbit's own artwork for the TV's app list.
 *
 * Both of these pointed at @android:drawable/ic_menu_view, a grey system glyph, and the owner
 * could not find Orbit among the real banners at all. A drawable that is simply missing or
 * malformed is the same miss: a vector with bad path data draws nothing and says nothing, so
 * these check what comes out rather than what is declared.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BannerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun drawn(resId: Int, width: Int, height: Int): Bitmap {
        val drawable = ResourcesCompat.getDrawable(context.resources, resId, null)
        assertNotNull("no drawable behind the declaration", drawable)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable!!.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** Every pixel that is neither black nor transparent: the mark itself. */
    private fun inked(bitmap: Bitmap): List<Int> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.filter { pixel ->
            val a = (pixel ushr 24) and 0xFF
            a > 0x40 && (((pixel shr 16) and 0xFF) + ((pixel shr 8) and 0xFF) + (pixel and 0xFF)) > 60
        }
    }

    @Test
    fun `the banner and the icon are Orbit's own, not a system drawable`() {
        val info = context.applicationInfo
        for ((what, id) in listOf("banner" to info.banner, "icon" to info.icon)) {
            assertTrue("$what is not declared at all", id != 0)
            assertEquals(
                "$what still comes from another package, so it is not Orbit's artwork",
                context.packageName,
                context.resources.getResourcePackageName(id),
            )
        }
    }

    @Test
    fun `the banner actually draws the mark`() {
        val bitmap = drawn(context.applicationInfo.banner, 320, 180)
        val ink = inked(bitmap)

        // Something is drawn, and it is a mark rather than a filled rectangle. Bad path data in
        // a vector is silent: it draws nothing and the build still passes.
        val share = ink.size.toFloat() / (bitmap.width * bitmap.height)
        assertTrue("the banner draws nothing at all", share > 0.05f)
        assertTrue("the banner is one solid block, not a mark: $share", share < 0.6f)
    }

    @Test
    fun `the mark keeps the ring's blue to purple run and the white orbit`() {
        val ink = inked(drawn(context.applicationInfo.banner, 320, 180))
        fun blue(p: Int) = p and 0xFF
        fun red(p: Int) = (p shr 16) and 0xFF
        fun green(p: Int) = (p shr 8) and 0xFF

        // One end of the ring's gradient is blue led, the other has as much red as blue.
        assertTrue(
            "nothing in the banner is the ring's blue",
            ink.any { blue(it) > 180 && red(it) < 90 && green(it) in 90..200 },
        )
        assertTrue(
            "nothing in the banner is the ring's purple",
            ink.any { blue(it) > 160 && red(it) in 90..190 && green(it) < 110 },
        )
        // And the orbit itself, which is near white: all three channels high and together.
        assertTrue(
            "nothing in the banner is the white orbit",
            ink.any { p ->
                red(p) > 200 && green(p) > 200 && blue(p) > 200 &&
                    abs(red(p) - blue(p)) < 40
            },
        )
    }

    @Test
    fun `the icon draws the same mark on its own, with nothing behind it`() {
        val bitmap = drawn(context.applicationInfo.icon, 96, 96)
        assertTrue("the icon draws nothing", inked(bitmap).isNotEmpty())
        // A corner stays clear: the icon is the mark, not a tile with the mark on it.
        assertEquals("the icon has a background behind it", 0, (bitmap.getPixel(1, 1) ushr 24) and 0xFF)
    }
}
