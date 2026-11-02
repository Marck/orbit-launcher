package app.orbit.launcher

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Orbit's own settings screen: what it offers and what it changes.
 *
 * The About card this replaces could only be read. Everything here is meant to be set, so the
 * tests are about a choice being kept rather than about words being on screen, with one
 * exception: the build lines still have to say what is running, since checking the television
 * against the repository is what they are for.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsTvTest : TvTest() {
    override val outDir = "build/tv-shots/settings"

    @Before
    fun settingsFixture() {
        favorites(myApps, myInputs)
        ShotHooks.build = BuildStamp(
            version = "9.9.9",
            channel = "stable",
            commit = "abcdef123456",
            modified = false,
            tag = "v9.9.9",
            builtOn = "2026-11-02",
        )
    }

    @After
    fun unpinTheBuild() {
        ShotHooks.build = null
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun says(text: String) {
        waitFor("\"$text\" on screen") { onScreen(text) }
        assertTrue("not on screen: $text", onScreen(text))
    }

    @Test
    fun `the three sections are all there`() {
        launch(SettingsActivity::class.java)

        says("Orbit settings")
        says("VISUAL")
        says("PICTURES")
        says("ABOUT")
    }

    @Test
    fun `the about section says what is running, as the card it replaces did`() {
        launch(SettingsActivity::class.java)

        says("9.9.9 stable")
        says("abcdef123456")
        says("released as v9.9.9")
        says("built 2026-11-02")
    }

    @Test
    fun `the colours are offered by name as well as by swatch`() {
        launch(SettingsActivity::class.java)

        ThemeStore.accents.forEach { says(it.label) }
    }

    @Test
    fun `sky is what an install that has never chosen is drawn in`() {
        // So an owner who never opens this screen sees Orbit exactly as it always looked.
        assertEquals("sky", ThemeStore.accent(context).id)
        assertEquals(ThemeStore.default, ThemeStore.accent(context))
    }

    @Test
    fun `choosing a colour keeps it`() {
        ThemeStore.choose(context, "amber")

        assertEquals("amber", ThemeStore.accent(context).id)
        assertNotEquals(ThemeStore.default.color, ThemeStore.accent(context).color)
    }

    @Test
    fun `a colour that is not one of the offered ones is refused rather than stored`() {
        // The name is what is stored, so a value arriving from an older or newer install must
        // not leave Orbit drawing in nothing.
        ThemeStore.choose(context, "chartreuse")

        assertEquals("sky", ThemeStore.accent(context).id)
    }

    @Test
    fun `choosing a colour tells the screens holding one to redraw`() {
        val before = ThemeStore.revision

        ThemeStore.choose(context, "mint")

        assertTrue("the revision should move", ThemeStore.revision > before)
    }

    @Test
    fun `the backdrop picker is reached from here, now that the top bar no longer offers it`() {
        launch(SettingsActivity::class.java)
        says("Backdrop pictures")

        rule.onAllNodes(hasText("Backdrop pictures", substring = true), useUnmergedTree = true)[0]
            .performClick()

        val started = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, started?.component?.className)
        assertTrue(
            "opens the backdrop sources rather than the app list",
            started?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
    }

    @Test
    fun `the television's own settings are not what this screen is`() {
        // Said on the screen, because two gear-like glyphs sat next to each other in the top
        // bar and nothing told the owner which was which.
        launch(SettingsActivity::class.java)

        says("The television's are the gear in the top bar")
        assertFalse("should not claim to be the TV's settings", onScreen("Device Preferences"))
    }
}
