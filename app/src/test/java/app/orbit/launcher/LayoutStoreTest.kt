package app.orbit.launcher

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The order of the home screen's rows, and which of them are drawn.
 *
 * Kept as a store with no screen attached so the rules can be checked one at a time: what a
 * fresh install gets, what happens at the ends, and what an order written by another version
 * does when it arrives here.
 */
@RunWith(AndroidJUnit4::class)
class LayoutStoreTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `an install that has never chosen gets the order Orbit shipped with`() {
        assertEquals(LayoutStore.default, LayoutStore.rows(context))
    }

    @Test
    fun `the notifications row is known but not offered, since nothing fills it yet`() {
        assertTrue(HomeRow.NOTIFICATIONS in LayoutStore.ordered(context))
        assertFalse(HomeRow.NOTIFICATIONS in LayoutStore.offered(context))
        assertFalse(HomeRow.NOTIFICATIONS in LayoutStore.rows(context))
    }

    @Test
    fun `a row moves one place`() {
        LayoutStore.move(context, HomeRow.INPUTS, -1)

        assertEquals(
            listOf(HomeRow.FAVORITES, HomeRow.INPUTS, HomeRow.POSTERS),
            LayoutStore.rows(context),
        )
    }

    @Test
    fun `a row at the end stays there rather than coming round`() {
        // Wrapping would read as a mistake in a list this short, where the list is a picture
        // of the screen.
        LayoutStore.move(context, HomeRow.FAVORITES, -1)
        assertEquals(LayoutStore.default, LayoutStore.rows(context))

        LayoutStore.move(context, HomeRow.INPUTS, 1)
        assertEquals(LayoutStore.default, LayoutStore.rows(context))
    }

    @Test
    fun `a row switched off is not drawn, and comes back when switched on`() {
        LayoutStore.setShown(context, HomeRow.POSTERS, false)
        assertEquals(listOf(HomeRow.FAVORITES, HomeRow.INPUTS), LayoutStore.rows(context))

        LayoutStore.setShown(context, HomeRow.POSTERS, true)
        assertEquals(LayoutStore.default, LayoutStore.rows(context))
    }

    @Test
    fun `the favorites row cannot be switched off`() {
        // A launcher with nothing to launch is not one, and Back and Home both aim at it.
        LayoutStore.setShown(context, HomeRow.FAVORITES, false)

        assertTrue(HomeRow.FAVORITES in LayoutStore.rows(context))
        assertTrue(LayoutStore.shown(context, HomeRow.FAVORITES))
    }

    @Test
    fun `moving a row keeps the ones that are not offered`() {
        LayoutStore.move(context, HomeRow.INPUTS, -1)

        assertTrue(
            "notifications should keep its place for when it has a source",
            HomeRow.NOTIFICATIONS in LayoutStore.ordered(context),
        )
    }

    @Test
    fun `a row this version does not know about is ignored, not crashed on`() {
        context.getSharedPreferences("orbit.layout", 0).edit()
            .putString("order", "INPUTS\nSOMETHING_ELSE\nFAVORITES")
            .apply()

        val rows = LayoutStore.rows(context)

        assertEquals(listOf(HomeRow.INPUTS, HomeRow.FAVORITES, HomeRow.POSTERS), rows)
    }

    @Test
    fun `a row added by a later version appears rather than being lost`() {
        // The stored order is what was known when it was written. Anything missing from it is
        // new, and a new row has to be somewhere or it can never be reached.
        context.getSharedPreferences("orbit.layout", 0).edit()
            .putString("order", "FAVORITES\nINPUTS")
            .apply()

        assertTrue(HomeRow.POSTERS in LayoutStore.rows(context))
    }

    @Test
    fun `a change tells the home screen to rebuild`() {
        val before = LayoutStore.revision

        LayoutStore.setShown(context, HomeRow.INPUTS, false)

        assertTrue(LayoutStore.revision > before)
    }
}
