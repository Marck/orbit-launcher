package app.orbit.launcher

import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three buttons over the app grid, and what they leave on screen.
 *
 * A television carries far more factory apps than installed ones: on the set this was written
 * for, 18 of the 28 with a launcher came with the TV, and sorted by name alone the ten the
 * owner installed were scattered among them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppPickerFilterTest : TvTest() {
    override val outDir = "build/tv-shots/picker-filter"

    @Before
    fun pickerFixture() {
        favorites(myApps, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        ShotHooks.apps = listOf(
            app("org.jellyfin.androidtv", "Jellyfin", banner("Jellyfin", 0xFF101828.toInt(), 0xFFFFFFFF.toInt())),
            app("com.disney.disneyplus", "Disney Plus", banner("Disney", 0xFF0B1C4A.toInt(), 0xFFFFFFFF.toInt())),
            app("com.sony.dtv.smarthelp", "Help", banner("Help", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()), removable = false),
            app("com.netflix.ninja", "Netflix", banner("Netflix", 0xFF000000.toInt(), 0xFFE50914.toInt()), removable = false),
        )
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun says(text: String) {
        waitFor("\"$text\" on screen", diagnose = { "focus: ${focused()}" }) { onScreen(text) }
        assertTrue("not on screen: $text", onScreen(text))
    }

    @Test
    fun `the three buttons say how many are behind each of them`() {
        // Counted off the whole list rather than the filtered one, so the owner can see what is
        // behind the other two before pressing them.
        launch(AppPickerActivity::class.java)
        says("All apps (4)")
        says("Installed (2)")
        says("Factory (2)")
    }

    @Test
    fun `the counts do not move when a filter is on`() {
        val s = launch(AppPickerActivity::class.java)
        says("All apps (4)")
        assertTrue(pressUntilChosen(s, "Installed"))
        says("All apps (4)")
        says("Factory (2)")
    }

    @Test
    fun `installed only leaves the factory apps out`() {
        val s = launch(AppPickerActivity::class.java)
        says("Jellyfin")
        says("Netflix")

        assertTrue("never reached the Installed button", pressUntilChosen(s, "Installed"))

        waitFor("the factory apps to go", diagnose = { "focus: ${focused()}" }) { !onScreen("Netflix") }
        assertFalse("a factory app survived the filter", onScreen("Netflix"))
        assertFalse("a factory app survived the filter", onScreen("Help"))
        assertTrue("an installed app was dropped", onScreen("Jellyfin"))
        assertTrue("an installed app was dropped", onScreen("Disney Plus"))
    }

    @Test
    fun `factory only leaves what the owner installed out`() {
        val s = launch(AppPickerActivity::class.java)
        says("Jellyfin")
        assertTrue("never reached the Factory button", pressUntilChosen(s, "Factory"))

        waitFor("the installed apps to go", diagnose = { "focus: ${focused()}" }) { !onScreen("Jellyfin") }
        assertFalse("an installed app survived the filter", onScreen("Jellyfin"))
        assertFalse("an installed app survived the filter", onScreen("Disney Plus"))
        assertTrue("a factory app was dropped", onScreen("Netflix"))
        assertTrue("a factory app was dropped", onScreen("Help"))
    }

    /**
     * Walks the remote up into the button row and along it until [label] has focus, then
     * presses OK. Reports whether it got there.
     *
     * Driven rather than called directly, because the point is that the buttons are reachable
     * with the remote at all: they sit above the grid and nothing else leads to them.
     */
    private fun pressUntilChosen(
        s: androidx.test.core.app.ActivityScenario<AppPickerActivity>,
        label: String,
    ): Boolean {
        repeat(4) { press(s, KeyEvent.KEYCODE_DPAD_UP) }
        repeat(6) {
            if (focused()?.contains(label) == true) {
                press(s, KeyEvent.KEYCODE_DPAD_CENTER)
                return true
            }
            press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        return false
    }
}
