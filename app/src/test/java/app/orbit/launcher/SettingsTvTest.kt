package app.orbit.launcher

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
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

    /** What the cursor is on, by the words under it, so a route is never counted out. */
    private fun focusedText(): String =
        // The merged tree, so a focused row carries the words of the labels inside it. Read
        // unmerged, the row that takes focus has no text of its own and this came back empty.
        rule.onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
            }
            .joinToString(" ")

    /**
     * Walks down until the words are under the cursor.
     *
     * A count of presses would have to be corrected every time a section is added, and the
     * correction is invisible: the test still passes, having pressed OK on whatever happened
     * to be in the right place. Walking until it is actually there tests reachability, which
     * is the claim.
     */
    private fun walkDownTo(text: String, scenario: androidx.test.core.app.ActivityScenario<SettingsActivity>) {
        repeat(12) {
            if (focusedText().contains(text)) return
            press(scenario, KeyEvent.KEYCODE_DPAD_DOWN)
        }
        throw AssertionError("never reached \"$text\" with the cursor; it stopped on \"${focusedText()}\"")
    }

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
    fun `the remote can reach the colours, which is what a focusable screen means`() {
        // Driven with the keys a remote sends, not with performClick. The screen rendered
        // perfectly and could not be used at all: nothing asked for focus when it opened, so
        // every press went nowhere. A click straight at the node hid that completely.
        val s = launch(SettingsActivity::class.java)

        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)

        // The third swatch, whatever the palette is tuned to. Naming the colour here would
        // make retuning the palette look like a broken remote.
        assertEquals(
            "two to the right of the first swatch is the third",
            ThemeStore.accents[2].id,
            ThemeStore.accent(context).id,
        )
    }

    @Test
    fun `the backdrop picker is reached with the remote, now the top bar no longer offers it`() {
        val s = launch(SettingsActivity::class.java)
        says("Backdrop pictures")

        walkDownTo("Backdrop pictures", s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, started?.component?.className)
        assertTrue(
            "opens the backdrop sources rather than the app list",
            started?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
    }
}
