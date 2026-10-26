package app.orbit.launcher

import android.content.pm.ApplicationInfo
import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the hold menu offers, for the three kinds of app a television has on it.
 *
 * An app the owner installed can be removed. A factory app cannot. A factory app that has since
 * been updated is the one in between, and it was being treated as the second: the entry was
 * hidden, so there was no way to take the update off from here at all. On the set this was
 * written for that is most of the apps, Netflix and Prime among them, because a factory app
 * that has ever updated itself stays a factory app and moves to /data/app at the same time.
 *
 * Confirmed against the system's own uninstaller rather than reasoned about: asked about
 * Netflix it says "Replace this app with the factory version? All data will be removed.", and
 * asked about an app that was never updated it says "Do you want to uninstall this app?" and
 * then cannot do it. So the entry has to say which of the two it is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class UninstallEntryTvTest : TvTest() {
    override val outDir = "build/tv-shots/uninstall-entry"

    private val mine = "org.jellyfin.androidtv"
    private val updated = "com.netflix.ninja"
    private val factory = "com.sony.dtv.smarthelp"

    @Before
    fun appsFixture() {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        ShotHooks.apps = listOf(
            app(mine, "Jellyfin", banner("Jellyfin", 0xFF101828.toInt(), 0xFFFFFFFF.toInt())),
            app(
                updated, "Netflix", banner("Netflix", 0xFF000000.toInt(), 0xFFE50914.toInt()),
                removable = false, factoryUpdated = true,
            ),
            app(
                factory, "Help", banner("Help", 0xFF333333.toInt(), 0xFFFFFFFF.toInt()),
                removable = false,
            ),
        )
        favorites(listOf(mine, updated, factory), emptyList())
    }

    /** Exact, because "Uninstall" is a prefix of "Uninstall updates". */
    private fun shows(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun menuOn(label: String): Any {
        val s = launch(HomeActivity::class.java)
        waitFor("the apps row", diagnose = { "focus: ${focused()}" }) { focused() != null }
        repeat(4) {
            if (focused() == label) return@repeat
            press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        assertEquals("never reached $label", label, focused())
        holdOk(s)
        waitFor("the menu to open", diagnose = { "focus: ${focused()}" }) {
            shows("Remove from favorites")
        }
        return s
    }

    @Test
    fun `an app the owner installed offers to uninstall it`() {
        menuOn("Jellyfin")
        assertTrue("no uninstall offered on an installed app", shows("Uninstall"))
        assertFalse("an installed app does not have updates to take off", shows("Uninstall updates"))
    }

    @Test
    fun `an updated factory app offers to take the update off`() {
        // This is the one that offered nothing at all before.
        menuOn("Netflix")
        assertTrue("no entry offered on an updated factory app", shows("Uninstall updates"))
        assertFalse(
            "calling it Uninstall promises to remove an app that stays on the television",
            shows("Uninstall"),
        )
    }

    @Test
    fun `a factory app that was never updated offers neither`() {
        // The system would raise its dialog for this one too and then fail, so offering it
        // would be offering a button that cannot work.
        menuOn("Help")
        assertTrue("the menu should still be open", shows("Remove from favorites"))
        assertFalse(shows("Uninstall"))
        assertFalse(shows("Uninstall updates"))
    }

    @Test
    fun `the two questions are read off the flags the system itself uses`() {
        val system = ApplicationInfo.FLAG_SYSTEM
        val updatedSystem = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

        assertTrue("an app with no system flag is the owner's", AppRepository.removable(0))
        assertFalse("a factory app is not removable", AppRepository.removable(system))
        assertFalse(
            "an updated factory app is still a factory app, whatever path it now lives at",
            AppRepository.removable(system or updatedSystem),
        )

        assertFalse("nothing to take off an app the owner installed", AppRepository.factoryUpdated(0))
        assertFalse(
            "a factory app that never updated has no update to take off",
            AppRepository.factoryUpdated(system),
        )
        assertTrue(AppRepository.factoryUpdated(system or updatedSystem))
        assertFalse(
            "the updated flag alone, without system, is not a factory app",
            AppRepository.factoryUpdated(updatedSystem),
        )
    }
}
