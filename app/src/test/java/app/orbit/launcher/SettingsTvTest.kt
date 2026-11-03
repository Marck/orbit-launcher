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

    /** More presses than the screen has stops, so an unreachable row fails rather than spins. */
    private val WalkLimit = 24

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

    /** The anchor the cursor is standing on, or null when it is on something unnamed. */
    private fun focusedAnchor(): String? =
        rule.onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    /**
     * Walks down until the cursor is standing on [anchor].
     *
     * Three things this is not. Not a count of presses: adding a section would quietly make
     * the count press OK on the wrong row, and the test would still pass. Not a match on the
     * words under the cursor, which was the first attempt: rewording a sentence then broke a
     * test about whether a row can be reached. And not unbounded, so a row that genuinely
     * cannot be reached fails here rather than spinning.
     *
     * What it asserts is the only thing worth asserting about a route: that the remote can
     * get there at all.
     */
    private fun walkTo(
        anchor: String,
        scenario: androidx.test.core.app.ActivityScenario<SettingsActivity>,
    ) {
        val visited = mutableListOf<String?>()
        repeat(WalkLimit) {
            if (focusedAnchor() == anchor) return
            visited += focusedAnchor()
            press(scenario, KeyEvent.KEYCODE_DPAD_DOWN)
        }
        throw AssertionError(
            "never reached \"$anchor\". The cursor went through $visited and stopped on " +
                "\"${focusedAnchor()}\""
        )
    }

    /** The same walk sideways, for a row of things that sit beside each other. */
    private fun walkRightTo(
        anchor: String,
        scenario: androidx.test.core.app.ActivityScenario<SettingsActivity>,
    ) {
        repeat(WalkLimit) {
            if (focusedAnchor() == anchor) return
            press(scenario, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        throw AssertionError("never reached \"$anchor\"; it stopped on \"${focusedAnchor()}\"")
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

        // Aimed at a colour by name rather than counted to. Reordering the palette, or
        // adding one, then changes nothing here.
        val wanted = ThemeStore.accents.last()
        walkRightTo(Anchors.accent(wanted.id), s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(wanted.id, ThemeStore.accent(context).id)
    }

    @Test
    fun `a route that changes shape does not break the walk to a row`() {
        // The point of anchoring. Switching a layout row off takes a stop out of the way
        // down the screen, which a counted route would walk straight past. This is the thing
        // that used to break every time a section was added.
        LayoutStore.setShown(context, HomeRow.INPUTS, false)
        LayoutStore.setShown(context, HomeRow.POSTERS, false)

        val s = launch(SettingsActivity::class.java)
        walkTo(Anchors.BACKDROPS, s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals(
            AppPickerActivity::class.java.name,
            nextStarted()?.component?.className,
        )
    }

    @Test
    fun `the backdrop picker is reached with the remote, now the top bar no longer offers it`() {
        val s = launch(SettingsActivity::class.java)
        says("Backdrop pictures")

        walkTo(Anchors.BACKDROPS, s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)

        val started = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, started?.component?.className)
        assertTrue(
            "opens the backdrop sources rather than the app list",
            started?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
    }
}
