package app.orbit.launcher

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class VerifyBackHomeTest : TvTest() {
    override val outDir = "build/tv-shots/explore-home"
    private val L = KeyEvent.KEYCODE_DPAD_LEFT
    private val R = KeyEvent.KEYCODE_DPAD_RIGHT
    private val U = KeyEvent.KEYCODE_DPAD_UP
    private val D = KeyEvent.KEYCODE_DPAD_DOWN
    private val OK = KeyEvent.KEYCODE_DPAD_CENTER
    private val MENU = KeyEvent.KEYCODE_MENU
    private val BACK = KeyEvent.KEYCODE_BACK
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before fun clean() {
        val f = AppRepository::class.java.getDeclaredField("cache"); f.isAccessible = true; f.set(null, null)
    }
    @After fun close() { scenarios.forEach { runCatching { it.close() } } }

    private fun home() = launch(HomeActivity::class.java).also { scenarios += it }
    private fun has(t: String) = rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()

    /** Home pressed while Orbit is in front: pause, new intent, resume, never stopped. */
    private fun homePress(s: ActivityScenario<HomeActivity>) {
        s.moveToState(Lifecycle.State.STARTED); pump(50)
        s.onActivity {
            val m = HomeActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
            m.isAccessible = true
            m.invoke(it, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        }
        s.moveToState(Lifecycle.State.RESUMED); pump(800)
    }

    @Test fun backFromLaterTileGoesToFirst() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, R)
        assertEquals("Jellyfin", focused())
        press(s, BACK)
        assertEquals("TiviMate", focused())
        var finishing = false; s.onActivity { finishing = it.isFinishing }
        assertFalse(finishing)
    }

    @Test fun backFromInputsAndTopBarGoesToFirst() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, D); press(s, R)
        assertEquals(true, focused()?.startsWith("Nintendo"))
        press(s, BACK)
        assertEquals("TiviMate", focused())
        press(s, R); toTopBar(s, "All inputs")
        assertEquals("All inputs", focused())
        press(s, R); press(s, BACK)
        assertEquals("TiviMate", focused())
    }

    @Test fun backInMenuOnlyClosesMenu() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, MENU)
        assertEquals("Open", focused())
        press(s, BACK)
        assertFalse(has("Remove from favorites"))
        assertEquals("YouTube", focused())
    }

    @Test fun homeClosesMenuAndGoesToFirst() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, MENU)
        homePress(s)
        assertFalse(has("Remove from favorites"))
        assertEquals("TiviMate", focused())
    }

    @Test fun homeDuringMoveRevertsIt() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, MENU); press(s, D); press(s, OK)
        press(s, R); press(s, R)
        assertEquals("nl.uitzendinggemist", stored(FavoriteStore.APPS)[0])
        homePress(s)
        assertEquals(myApps, stored(FavoriteStore.APPS))
        assertFalse(has("FAVORITE APPS - MOVING, OK TO KEEP"))
        assertEquals("TiviMate", focused())
    }

    @Test fun leavingDuringMoveRevertsAndReturnsToTile() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); press(s, D); press(s, OK)
        press(s, R)
        s.moveToState(Lifecycle.State.CREATED); pump(300)
        s.moveToState(Lifecycle.State.RESUMED); pump(1500)
        assertEquals(myApps, stored(FavoriteStore.APPS))
        assertFalse(has("FAVORITE APPS - MOVING, OK TO KEEP"))
        assertEquals("NPO Start", focused())
    }

    @Test fun homeFromTopBarAfterRecreateStillWorks() {
        favorites(myApps, myInputs)
        val s = home()
        toTopBar(s, "Settings")
        assertEquals("Settings", focused())
        s.recreate(); pump(1500)
        assertEquals("Settings", focused())
        homePress(s)
        assertEquals("TiviMate", focused())
    }
}
