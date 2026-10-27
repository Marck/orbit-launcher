package app.orbit.launcher

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hold menu on the two app grids, Favorite apps and All apps.
 *
 * Both list every app on the television with the favorites ticked, so they are where an app
 * that is not a favorite yet can be held. A press already does each screen's own thing, adding
 * or removing on one and opening on the other, so the menu is there for what no press reaches:
 * taking the app off the set. Open and Move are deliberately absent, Move because the order of
 * the row is set in the row itself.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PickerMenuTvTest : TvTest() {
    override val outDir = "build/tv-shots/picker-menu"

    private val mine = "org.jellyfin.androidtv"
    private val notYet = "com.plexapp.android"
    private val updated = "com.netflix.ninja"
    private val factory = "com.sony.dtv.smarthelp"

    @Before
    fun pickerFixture() {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        ShotHooks.apps = listOf(
            app(mine, "Jellyfin", banner("Jellyfin", 0xFF101828.toInt(), 0xFFFFFFFF.toInt())),
            app(notYet, "Plex", banner("Plex", 0xFF1F1F1F.toInt(), 0xFFE5A00D.toInt())),
            app(
                updated, "Netflix", banner("Netflix", 0xFF000000.toInt(), 0xFFE50914.toInt()),
                removable = false, factoryUpdated = true,
            ),
            app(
                factory, "Help", banner("Help", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()),
                removable = false,
            ),
        )
        // Only Jellyfin is a favorite, so the same screen holds both wordings of the entry.
        favorites(listOf(mine), emptyList())
    }

    /** Exact, because "Uninstall" is a prefix of "Uninstall updates". */
    private fun shows(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun picker(opening: Boolean = false): ActivityScenario<AppPickerActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AppPickerActivity::class.java)
            .putExtra(AppPickerActivity.EXTRA_OPEN, opening)
        return launch(AppPickerActivity::class.java, intent)
    }

    /** Walks right to [label], by name rather than by a count of presses. */
    private fun onto(s: ActivityScenario<AppPickerActivity>, label: String) {
        waitFor("a tile to take focus", diagnose = { "focus: ${focused()}" }) { focused() != null }
        var guard = 0
        while (focused()?.contains(label) != true && guard++ < 8) {
            press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        assertTrue("never reached $label, focus is ${focused()}", focused()?.contains(label) == true)
    }

    private fun menuOn(label: String, opening: Boolean = false): ActivityScenario<AppPickerActivity> {
        val s = picker(opening)
        onto(s, label)
        holdOk(s)
        return s
    }

    /** Moves down to the named entry and presses it. */
    private fun choose(s: ActivityScenario<AppPickerActivity>, entry: String) {
        var guard = 0
        while (focused()?.contains(entry) != true && guard++ < 6) {
            press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        }
        assertEquals("never reached the $entry entry", true, focused()?.contains(entry))
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        pump(600)
    }

    @Test
    fun `holding OK on a favorite offers to remove it and to uninstall it`() {
        menuOn("Jellyfin")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) {
            shows("Remove from favorites")
        }
        assertTrue("an installed app can come off the television", shows("Uninstall"))
        shot("menu-on-a-favorite")
    }

    @Test
    fun `holding OK on an app that is not a favorite offers to add it`() {
        menuOn("Plex")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertFalse(
            "it is not a favorite, so there is nothing to remove it from",
            shows("Remove from favorites"),
        )
        shot("menu-on-a-stranger")
    }

    @Test
    fun `the menu here offers no Open and no Move`() {
        // A press on the tile already adds or removes, and the order of the row is set in the
        // row itself, where the tile can be seen moving.
        menuOn("Jellyfin")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) {
            shows("Remove from favorites")
        }
        assertFalse(shows("Open"))
        assertFalse(shows("Move"))
    }

    @Test
    fun `adding from the menu puts the app in the row`() {
        val s = menuOn("Plex")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertFalse("Plex should not be a favorite yet", notYet in stored(FavoriteStore.APPS))
        choose(s, "Add to favorites")
        assertTrue("the menu should close behind the choice", !shows("Add to favorites"))
        assertEquals(listOf(mine, notYet), stored(FavoriteStore.APPS))
    }

    @Test
    fun `removing from the menu takes it out of the row`() {
        val s = menuOn("Jellyfin")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) {
            shows("Remove from favorites")
        }
        choose(s, "Remove from favorites")
        assertEquals(emptyList<String>(), stored(FavoriteStore.APPS))
    }

    @Test
    fun `an updated factory app is offered its update, a plain one nothing`() {
        // Most of the apps on the set are factory apps that have since updated themselves.
        // Netflix is one; the update can come off and the app itself cannot.
        menuOn("Netflix")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertTrue(shows("Uninstall updates"))
        assertFalse(
            "calling it Uninstall promises to remove an app that stays on the television",
            shows("Uninstall"),
        )
    }

    @Test
    fun `a factory app that never updated is offered the television's own page`() {
        val s = menuOn("Help")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertFalse(shows("Uninstall"))
        assertFalse(shows("Uninstall updates"))
        assertTrue("nothing offered at all on a factory app", shows("Manage"))
        choose(s, "Manage")
        val started = started(s)
        assertEquals(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            started?.action,
        )
        assertEquals("package:$factory", started?.data.toString())
    }

    @Test
    fun `Back closes the menu and hands focus back to the tile`() {
        val s = menuOn("Jellyfin")
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) {
            shows("Remove from favorites")
        }
        press(s, KeyEvent.KEYCODE_BACK)
        waitFor("the menu to close", diagnose = { "focus: ${focused()}" }) {
            !shows("Remove from favorites")
        }
        assertTrue(
            "the remote is left with nothing focused, so it cannot reach the grid again",
            focused()?.contains("Jellyfin") == true,
        )
        assertEquals("Back out of the menu changes nothing", listOf(mine), stored(FavoriteStore.APPS))
    }

    @Test
    fun `All apps raises the same menu`() {
        // A hold used to favorite an app outright there. It now opens the menu, so a hold means
        // one thing on both grids and uninstall is reachable from either.
        val s = menuOn("Plex", opening = true)
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertTrue(shows("Uninstall"))
        assertFalse("the order of the row is set in the row itself", shows("Move"))
        assertFalse("a press on this screen already opens the app", shows("Open"))
        assertEquals(
            "a hold alone should change nothing now that it opens a menu",
            listOf(mine),
            stored(FavoriteStore.APPS),
        )
        choose(s, "Add to favorites")
        assertEquals(listOf(mine, notYet), stored(FavoriteStore.APPS))
    }

    /** Whatever was asked to start, read from the activity as well as the application. */
    private fun started(s: ActivityScenario<AppPickerActivity>): Intent? {
        var mine: Intent? = null
        s.onActivity { mine = shadowOf(it).peekNextStartedActivity() }
        return mine ?: nextStarted()
    }

    @Test
    fun `a hold on All apps does not open the app`() {
        // The release of the hold used to fall through to the tile's press. On this screen that
        // press starts the app, which would leave the menu up behind whatever just opened.
        val s = menuOn("Plex", opening = true)
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) { shows("Add to favorites") }
        assertEquals("a hold should start nothing", null, started(s))
        // The same reading, proving it can see a launch at all: Back out and press OK instead.
        press(s, KeyEvent.KEYCODE_BACK)
        waitFor("the menu to close", diagnose = { "focus: ${focused()}" }) {
            !shows("Add to favorites")
        }
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        assertNotNull("a press on All apps opens the app", started(s))
    }
}
