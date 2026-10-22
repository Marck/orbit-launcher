package app.orbit.launcher

import android.view.KeyEvent
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Verifier probes for the AppsRowTvTest reports. Prints, does not assert. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class VerifyAppsRowTvTest : TvTest() {
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
    private fun drain() { while (nextStarted() != null) Unit }
    private fun has(t: String) = rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()
    private fun fb() = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    private fun log(m: String) = println("TV V $m | focused=${focused()} bounds=${fb()}")
    private fun <A : android.app.Activity> pauseResume(s: ActivityScenario<A>) {
        s.moveToState(Lifecycle.State.STARTED); pump(100)
        s.moveToState(Lifecycle.State.RESUMED); pump(800)
    }

    @Test fun v_menuAfterResume_consequences() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); press(s, D); log("menu before")
        pauseResume(s); log("menu after pause/resume")
        pauseResume(s); log("menu after a second Home")
        press(s, R); log("right"); drain(); press(s, OK)
        println("TV V started while menu shown: ${nextStarted()} menuShown=${has("Remove from favorites")}")
        press(s, U); log("up from the tile under the scrim")
        press(s, BACK)
        var fin = false; if (s.state != Lifecycle.State.DESTROYED) s.onActivity { fin = it.isFinishing }
        println("TV V after Back: menuShown=${has("Remove from favorites")} finishing=$fin")
    }

    @Test fun v_resumeWithoutMenu_keepsTile_andInputSteal() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, R); log("on Jellyfin")
        pauseResume(s); log("jellyfin after pause/resume")
        press(s, D); press(s, R); log("on input")
        pauseResume(s); log("input after pause/resume (no leaving)")
        press(s, U); press(s, U); log("top bar")
        pauseResume(s); log("top bar after pause/resume")
    }

    @Test fun v_safeAreaAfterSettle() {
        val ex = (1..14).map { i -> app("com.extra.app$i", "Extra $i", banner("X$i", 0xFF336699.toInt(), 0xFFFFFFFF.toInt())) }
        ShotHooks.apps = ShotHooks.apps!! + ex
        favorites(myApps + ex.map { it.packageName }, myInputs)
        val s = home()
        repeat(8) { press(s, R, settle = 800) }
        pump(2000); log("8 slow rights, settled")
        press(s, R, settle = 2000); log("one more right, settled")
    }

    @Test fun v_backOnHome() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, BACK)
        var fin = false; if (s.state != Lifecycle.State.DESTROYED) s.onActivity { fin = it.isFinishing }
        println("TV V back on home: state=${s.state} finishing=$fin")
    }
}
