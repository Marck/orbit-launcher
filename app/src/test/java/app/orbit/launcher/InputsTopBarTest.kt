package app.orbit.launcher

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Inputs row, top bar, Notice and Confirmation on the home screen, driven like a remote.
 * Tests named bugN_* fail on the current app code; ok_* tests document what works.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class InputsTopBarTest : TvTest() {
    override val outDir = "build/tv-shots/explore-inputs"

    private var tag = ""
    private fun log(m: String) = println("TV[$tag] $m")
    private fun drain() { while (nextStarted() != null) Unit }
    private fun where(): String {
        val n = rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return "null"
        val b = n.boundsInRoot
        return "${focused()} @(${b.left.toInt()},${b.top.toInt()})"
    }
    private fun p(s: ActivityScenario<HomeActivity>, code: Int, settle: Long = 400) {
        press(s, code, settle)
        log("${KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")} -> ${where()}")
    }
    private fun home() = launch(HomeActivity::class.java)

    /** Leave for another app and come back, as after switching input or opening Settings. */
    private fun leaveAndReturn(s: ActivityScenario<HomeActivity>) {
        // A real TvInputManager hands back new (equal) objects on every read.
        ShotHooks.inputs = ShotHooks.inputs?.map { it.copy() }
        s.moveToState(Lifecycle.State.CREATED); pump(200)
        s.moveToState(Lifecycle.State.RESUMED); pump(1500)
        log("after return -> ${where()}")
    }

    private val U = KeyEvent.KEYCODE_DPAD_UP
    private val D = KeyEvent.KEYCODE_DPAD_DOWN
    private val L = KeyEvent.KEYCODE_DPAD_LEFT
    private val R = KeyEvent.KEYCODE_DPAD_RIGHT
    private val OK = KeyEvent.KEYCODE_DPAD_CENTER
    private val MENU = KeyEvent.KEYCODE_MENU

    private val lastInputColor = 0xFF1AA3C4.toInt()

    /** Background colour of an input tile, sampled just inside its left edge, half way down. */
    private fun tileColor(label: String): Int {
        val path = "$outDir/tile-$tag.png"
        rule.onNode(hasText(label, substring = true)).captureRoboImage(path)
        val bmp = android.graphics.BitmapFactory.decodeFile(path)
        return bmp.getPixel(10, bmp.height / 2)
    }

    private fun close(a: Int, b: Int) =
        listOf(16, 8, 0).all { sh -> kotlin.math.abs(((a shr sh) and 0xFF) - ((b shr sh) and 0xFF)) < 24 }

    // ---------------------------------------------------------------- bugs

    @Test
    fun bug1_lastUsedInputNotHighlightedAfterReturn() {
        tag = "bug1"
        favorites(myApps, myInputs)
        val s = home()
        p(s, D); p(s, R)                     // PlayStation 5
        drain()
        p(s, OK)
        val started = nextStarted()
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("content://android.media.tv/passthrough/ps5", started?.data.toString())
        assertEquals("ps5", InputRepository.lastUsedInput(context))
        leaveAndReturn(s)
        shot("bug1-after-return")
        val control = tileColor("HDMI 1")
        log("HDMI 1 (not last used) colour: ${Integer.toHexString(control)}")
        val color = tileColor("PlayStation 5")
        log("PS5 tile colour after return: ${Integer.toHexString(color)}")
        assertTrue(
            "PS5 was just switched to, so it should carry the last-input colour after returning; " +
                "got #${Integer.toHexString(color)}",
            close(color, lastInputColor),
        )
    }

    @Test
    fun bug2_returnFromInputSwitchPutsFocusInAppsRow() {
        tag = "bug2"
        favorites(myApps, myInputs)
        val s = home()
        p(s, D); p(s, R)                     // PlayStation 5
        drain()
        p(s, OK)
        assertNotNull(nextStarted())
        leaveAndReturn(s)
        assertTrue(
            "after switching to PS5 and coming back, focus should be on PS5, was ${focused()}",
            focused().orEmpty().startsWith("PlayStation 5"),
        )
    }

    @Test
    fun bug3_returnFromTopBarSettingsPutsFocusInAppsRow() {
        tag = "bug3"
        favorites(myApps, myInputs)
        val s = home()
        p(s, R)                              // NPO Start
        toTopBar(s, "Settings")
        assertEquals("Settings", focused())
        drain()
        p(s, OK)
        assertEquals(Settings.ACTION_SETTINGS, nextStarted()?.action)
        leaveAndReturn(s)
        assertEquals("focus after coming back from Settings", "Settings", focused())
    }

    @Test
    fun bug4_upToTopBarAndDownDoesNotReturnToTile() {
        tag = "bug4"
        favorites(myApps, myInputs)
        val s = home()
        p(s, R)                              // NPO Start
        toTopBar(s, "All inputs")
        assertEquals("All inputs", focused())
        // Down passes back through the hero, which sits between the top bar and the rows.
        p(s, D)
        if (focused() !in topBarButtons && focused() != "NPO Start") p(s, D)
        assertEquals("Down from the top bar should go back to the tile Up left", "NPO Start", focused())
    }

    @Test
    fun bug5_downToInputsAndUpDoesNotReturnToTile() {
        tag = "bug5"
        favorites(myApps, myInputs)
        val s = home()
        repeat(4) { p(s, R) }                // Kodi
        assertEquals("Kodi", focused())
        p(s, D)                              // inputs-row +
        p(s, U)
        assertEquals("Up from the inputs row should go back to the tile Down left", "Kodi", focused())
    }

    @Test
    fun bug6_noticeDismissThrowsFocusToTopBar() {
        tag = "bug6"
        ShotHooks.defaultHome = false
        favorites(myApps, myInputs)
        val s = home()
        assertEquals("TiviMate", focused())
        p(s, U)
        assertEquals("Details", focused())
        p(s, R)
        assertEquals("Dismiss", focused())
        p(s, OK)
        shot("bug6-after-dismiss")
        assertEquals(0, rule.onAllNodes(hasText("Dismiss")).fetchSemanticsNodes().size)
        assertEquals(
            "after Dismiss, focus should drop to the apps row the notice sat on, not jump to the top bar",
            "TiviMate", focused(),
        )
    }

    @Test
    fun bug7_dismissedNoticeReturnsAfterRecreate() {
        tag = "bug7"
        ShotHooks.defaultHome = false
        favorites(myApps, myInputs)
        val s = home()
        p(s, U); p(s, R); p(s, OK)           // Dismiss
        assertEquals(0, rule.onAllNodes(hasText("Dismiss")).fetchSemanticsNodes().size)
        leaveAndReturn(s)
        assertEquals("still dismissed after a return", 0, rule.onAllNodes(hasText("Dismiss")).fetchSemanticsNodes().size)
        s.recreate(); pump(1500)
        assertEquals(
            "dismissed notice came back after the activity was recreated",
            0, rule.onAllNodes(hasText("Dismiss")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun bug8_secondRemoveDoesNotRestartConfirmation() {
        tag = "bug8"
        favorites(myApps, myInputs)
        val s = home()
        p(s, D)
        p(s, MENU, 100); p(s, D, 100); p(s, D, 100); p(s, OK, 100)   // remove HDMI 1
        pump(1000)
        p(s, MENU, 100); p(s, D, 100); p(s, D, 100); p(s, OK, 16)   // remove PS5 ~1.4s later
        assertEquals(listOf("switch"), stored(FavoriteStore.INPUTS))
        var t = 0L
        while (t < 3000 && rule.onAllNodes(hasText("Removed from favorites")).fetchSemanticsNodes().isNotEmpty()) {
            pump(16); t += 16
        }
        log("pill gone ${t}ms after the 2nd remove")
        assertTrue("confirmation for the 2nd remove lasted only ${t}ms", t >= 2400)
    }

    @Test
    fun bug9_inputsPlusLabelOutsideSafeArea() {
        tag = "bug9"
        favorites(myApps, myInputs)
        val s = home()
        p(s, D); p(s, L)                     // inputs-row +
        val label = rule.onAllNodes(hasText(AddInputLabel), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        val bottomDp = label.boundsInRoot.bottom / 2f   // xhdpi
        log("label bottom ${bottomDp}dp, safe limit ${540 - 27}dp")
        assertTrue("label bottom ${bottomDp}dp lies in the bottom overscan margin", bottomDp <= 540 - 27)
    }

    // ---------------------------------------------------------------- working features

    @Test
    fun ok_downFromEachAppAndInputOkHdmi4() {
        tag = "ok1"
        favorites(myApps, listOf("hdmi1", "ps5", "switch", "hdmi4"))
        val s = home()
        val seen = mutableListOf<String?>()
        for (i in 0 until 6) {
            p(s, D); seen += focused(); p(s, U)
            // Up may land elsewhere (bug5); walk to tile i+1 from the start.
            p(s, L); repeat(i + 2) { p(s, R) }   // L wraps to +, R wraps to first
        }
        log("down targets: $seen")
        // The first Down lands below; after that the row keeps its place, as a Leanback row does.
        assertEquals(List(6) { "HDMI 1" }, seen)
        // hdmi4 is disconnected but listed and switchable
        p(s, D); p(s, R); p(s, R); p(s, R)
        assertTrue(focused().orEmpty().startsWith("HDMI 4"))
        drain(); p(s, OK)
        assertEquals("content://android.media.tv/passthrough/hdmi4", nextStarted()?.data.toString())
        shot("ok1-hdmi4")
    }

    /** Control for bug1: the same pixel probe does see the highlight once the activity is rebuilt. */
    @Test
    fun ok_highlightShowsAfterRecreate() {
        tag = "ok6"
        favorites(myApps, myInputs)
        val s = home()
        p(s, D); p(s, R)
        drain(); p(s, OK)
        s.recreate(); pump(1500)
        val color = tileColor("PlayStation 5")
        log("PS5 colour after recreate: ${Integer.toHexString(color)}")
        assertTrue(close(color, lastInputColor))
    }

    @Test
    fun ok_topBarButtons() {
        tag = "ok2"
        favorites(myApps, myInputs)
        val s = home()
        toTopBar(s, "Backdrops")
        assertEquals("Backdrops", focused())
        drain()
        p(s, OK)
        val picker = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, picker?.component?.className)
        // The same screen the apps button opens, but asked for the backdrop sources.
        assertTrue(
            "opens the backdrop picker",
            picker?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
        p(s, R); assertEquals("All apps", focused())
        drain()
        p(s, OK)
        val apps = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, apps?.component?.className)
        assertFalse(
            "the apps button is not the backdrop picker",
            apps?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
        p(s, R); assertEquals("All inputs", focused())
        drain()
        p(s, OK); assertEquals("test.allinputs", nextStarted()?.action)
        p(s, R); assertEquals("Play Store", focused()); p(s, OK); assertEquals("test.store", nextStarted()?.action)
        p(s, R); assertEquals("Settings", focused()); p(s, OK); assertEquals(Settings.ACTION_SETTINGS, nextStarted()?.action)
        p(s, R); assertEquals("Profile", focused()); p(s, OK)
        assertEquals(AvatarPickerActivity::class.java.name, nextStarted()?.component?.className)
    }

    @Test
    fun ok_searchIsLeftmostAndOpens() {
        tag = "search"
        favorites(myApps, myInputs)
        val s = home()
        toTopBar(s, "Search")
        assertEquals("Search", focused())
        // Leftmost: left stays put rather than falling into the hero below it.
        p(s, L); assertEquals("Search", focused())
        drain()
        p(s, OK); assertEquals("test.search", nextStarted()?.action)
        p(s, R); assertEquals("Backdrops", focused())
        p(s, R); assertEquals("All apps", focused())
        // And back along the row: nothing cancels left between them.
        p(s, L); assertEquals("Backdrops", focused())
        p(s, L); assertEquals("Search", focused())
    }

    @Test
    fun ok_noSearchLeavesBackdropsHoldingTheEdge() {
        tag = "nosearch"
        ShotHooks.search = null
        favorites(myApps, myInputs)
        val s = home()
        assertEquals(0, rule.onAllNodes(hasContentDescription("Search"), true).fetchSemanticsNodes().size)
        toTopBar(s, "Backdrops")
        assertEquals("Backdrops", focused())
        p(s, L); assertEquals("Backdrops", focused())
    }

    @Test
    fun ok_topBarHiddenButtons() {
        tag = "ok3"
        ShotHooks.allInputs = null
        ShotHooks.store = null
        favorites(myApps, myInputs)
        val s = home()
        assertEquals(0, rule.onAllNodes(hasContentDescription("All inputs"), true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasContentDescription("Play Store"), true).fetchSemanticsNodes().size)
        toTopBar(s, "Settings")
        assertEquals("Settings", focused())
        p(s, L); assertEquals("All apps", focused())
        p(s, R); assertEquals("Settings", focused())
        shot("ok3-hidden")
    }

    @Test
    fun ok_noticeDetailsAndInputMenuAndNoInputs() {
        tag = "ok4"
        ShotHooks.defaultHome = false
        favorites(myApps, myInputs)
        val s = home()
        p(s, U); assertEquals("Details", focused())
        drain(); p(s, OK)
        assertEquals(Settings.ACTION_HOME_SETTINGS, nextStarted()?.action)
        p(s, D)                              // back to TiviMate, the tile Up left
        assertEquals("TiviMate", focused())
        // input menu: Open, Move, Remove, no Uninstall
        p(s, D); p(s, MENU)
        assertEquals(0, rule.onAllNodes(hasText("Uninstall")).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodes(hasText("Remove from favorites")).fetchSemanticsNodes().size)
        p(s, KeyEvent.KEYCODE_BACK)
        assertTrue(focused().orEmpty().startsWith("HDMI 1"))
        // + asks for the input picker
        p(s, L); drain(); p(s, OK)
        assertEquals(InputPickerActivity::class.java.name, nextStarted()?.component?.className)
    }

    @Test
    fun ok_noInputsOnTv() {
        tag = "ok5"
        ShotHooks.inputs = emptyList()
        favorites(myApps, myInputs)
        val s = home()
        p(s, D)
        assertEquals("+", focused())
        shot("ok5-no-inputs")
        leaveAndReturn(s)
        assertEquals(myInputs, stored(FavoriteStore.INPUTS))
    }
}
