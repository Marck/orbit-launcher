package app.orbit.launcher

import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * That the home screen is actually built from the stored order, rather than the store being a
 * set of preferences nothing reads.
 *
 * Asserted on where the labels land on screen, because the order is a thing you see. Both
 * directions are checked in the same test: the shipped order first, then the moved one, so it
 * cannot pass by the rows happening to be laid out the way the assertion expects.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HomeLayoutTvTest : TvTest() {
    override val outDir = "build/tv-shots/home-layout"

    @Before
    fun layoutFixture() {
        favorites(myApps, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
    }

    private fun topOf(text: String): Float? =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?.top

    private fun seen(text: String): Boolean = topOf(text) != null

    @Test
    fun `the rows are drawn in the order they are stored in`() {
        launch(HomeActivity::class.java)
        waitFor("the rows to be drawn") { seen("FAVORITE APPS") && seen("INPUTS") }
        val appsFirst = topOf("FAVORITE APPS")!!
        val inputsFirst = topOf("INPUTS")!!
        assertTrue(
            "as shipped the apps are above the inputs: $appsFirst against $inputsFirst",
            appsFirst < inputsFirst,
        )

        // Two places up, which puts the inputs above the apps.
        LayoutStore.move(context, HomeRow.INPUTS, -1)
        LayoutStore.move(context, HomeRow.INPUTS, -1)
        val order = LayoutStore.rows(context)
        assertTrue(
            "the inputs should now be above the apps in the order: $order",
            order.indexOf(HomeRow.INPUTS) < order.indexOf(HomeRow.FAVORITES),
        )

        launch(HomeActivity::class.java)
        waitFor("the rows to be drawn again") { seen("FAVORITE APPS") && seen("INPUTS") }
        val appsAfter = topOf("FAVORITE APPS")!!
        val inputsAfter = topOf("INPUTS")!!
        assertTrue(
            "moved to the top the inputs are above the apps: $inputsAfter against $appsAfter",
            inputsAfter < appsAfter,
        )
    }

    @Test
    fun `a row switched off is not on the screen at all`() {
        launch(HomeActivity::class.java)
        waitFor("the inputs row") { seen("INPUTS") }

        LayoutStore.setShown(context, HomeRow.INPUTS, false)
        launch(HomeActivity::class.java)
        waitFor("the apps row") { seen("FAVORITE APPS") }

        assertTrue("the apps row should still be there", seen("FAVORITE APPS"))
        assertEquals("the inputs row should be gone", null, topOf("INPUTS"))
    }
}
