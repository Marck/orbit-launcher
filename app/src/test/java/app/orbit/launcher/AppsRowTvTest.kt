package app.orbit.launcher

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen's apps row, driven like a remote. Tests named ok_* pass today and pin down
 * what works; tests named bug_* fail today and are the repros for the reported bugs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppsRowTvTest : TvTest() {
    override val outDir = "build/tv-shots/explore-home"

    private val L = KeyEvent.KEYCODE_DPAD_LEFT
    private val R = KeyEvent.KEYCODE_DPAD_RIGHT
    private val U = KeyEvent.KEYCODE_DPAD_UP
    private val D = KeyEvent.KEYCODE_DPAD_DOWN
    private val OK = KeyEvent.KEYCODE_DPAD_CENTER
    private val MENU = KeyEvent.KEYCODE_MENU
    private val BACK = KeyEvent.KEYCODE_BACK

    private val menuEntries = listOf("Open", "Move", "Remove from favorites", "Uninstall")
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    /** The process-wide app cache would otherwise carry one test's fixture into the next. */
    private fun resetCache() {
        val f = AppRepository::class.java.getDeclaredField("cache")
        f.isAccessible = true
        f.set(null, null)
    }

    @Before fun clean() { resetCache() }
    @After fun close() { scenarios.forEach { runCatching { it.close() } } }

    private fun home(): ActivityScenario<HomeActivity> = launch(HomeActivity::class.java).also { scenarios += it }
    private fun drain() { while (nextStarted() != null) Unit }
    private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun fb(): Rect? = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
    private fun rootWidth() = rule.onAllNodes(isRoot()).fetchSemanticsNodes().first().size.width.toFloat()
    private fun log(m: String) = println("TV $m | focused=${focused()} bounds=${fb()} apps=${stored(FavoriteStore.APPS)}")
    private fun density() = context.resources.displayMetrics.density

    private fun longRow() {
        val ex = (1..14).map { i -> app("com.extra.app$i", "Extra $i", banner("X$i", 0xFF336699.toInt(), 0xFFFFFFFF.toInt())) }
        ShotHooks.apps = ShotHooks.apps!! + ex
        favorites(myApps + ex.map { it.packageName }, myInputs)
    }

    /** Leaving for another app and coming back. */
    private fun <A : android.app.Activity> leaveAndReturn(s: ActivityScenario<A>, settle: Long = 1500) {
        s.moveToState(Lifecycle.State.CREATED); pump(300)
        s.moveToState(Lifecycle.State.RESUMED); pump(settle)
    }

    /**
     * What Home does to a home activity that is already in front (singleTask): the system pauses
     * it, delivers the intent, resumes it. Also what a system overlay activity or the screensaver does.
     */
    private fun <A : android.app.Activity> pauseResume(s: ActivityScenario<A>) {
        s.moveToState(Lifecycle.State.STARTED); pump(100)
        s.moveToState(Lifecycle.State.RESUMED); pump(800)
    }

    // ================= what works =================

    @Test fun ok_coldStartWithFavoritesFocusesFirstTile() {
        favorites(myApps, myInputs)
        home(); log("cold, favorites")
        assertEquals("TiviMate", focused())
    }

    @Test fun ok_coldStartWithoutFavoritesFocusesPlus() {
        favorites(emptyList(), myInputs)
        home(); log("cold, no favorites")
        assertEquals("+", focused())
    }

    @Test fun ok_warmStartFocus() {
        AppRepository.load(context)
        favorites(myApps, myInputs)
        home(); log("warm, favorites")
        assertEquals("TiviMate", focused())
    }

    @Test fun ok_warmStartNoFavoritesPlusOpensPicker() {
        AppRepository.load(context)
        favorites(emptyList(), myInputs)
        val s = home()
        assertEquals("+", focused())
        press(s, L); press(s, R); assertEquals("+", focused())
        drain(); press(s, OK)
        assertEquals(AppPickerActivity::class.java.name, nextStarted()?.component?.className)
    }

    @Test fun ok_okLaunchesTileAndPlusOpensPicker() {
        favorites(myApps, myInputs)
        val s = home(); drain()
        press(s, OK)
        assertEquals("ar.tvplayer.tv", nextStarted()?.`package`)
        press(s, L); assertEquals("+", focused())
        press(s, OK)
        assertEquals(AppPickerActivity::class.java.name, nextStarted()?.component?.className)
    }

    @Test fun ok_wrapBothEndsShortRow() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, L); assertEquals("+", focused())
        press(s, R); assertEquals("TiviMate", focused())
        press(s, L); press(s, L); assertEquals("Help", focused())
        press(s, R); assertEquals("+", focused())
    }

    @Test fun ok_wrapBothEndsLongRow() {
        longRow()
        val s = home()
        val w = rootWidth()
        press(s, L); log("wrap left in long row")
        assertEquals("+", focused()); fb()!!.let { assertTrue("$it", it.left >= 0 && it.right <= w) }
        press(s, R); assertEquals("TiviMate", focused())
        press(s, L); press(s, L); assertEquals("Extra 14", focused())
        // all the way round to the right: Extra 14 -> + -> TiviMate, then a full lap of 21 stops
        press(s, R); press(s, R); assertEquals("TiviMate", focused())
        repeat(21) { press(s, R, settle = 200) }
        pump(400); log("after a full lap right")
        assertEquals("TiviMate", focused())
    }

    @Test fun ok_menuKeys() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU)
        assertEquals("Open", focused())
        press(s, U); assertEquals("Open", focused())
        press(s, L); assertEquals("Open", focused())
        press(s, R); assertEquals("Open", focused())
        repeat(5) { press(s, D) }; assertEquals("Uninstall", focused())
        press(s, L); press(s, R); assertEquals("Uninstall", focused())
        press(s, BACK)
        assertFalse(has("Uninstall")); assertEquals("NPO Start", focused())
    }

    @Test fun ok_holdOkOpensMenuAndOpenLaunches() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); drain()
        holdOk(s)
        assertNull(nextStarted()); assertEquals("Open", focused())
        press(s, OK)
        assertEquals("nl.uitzendinggemist", nextStarted()?.`package`)
        assertEquals("NPO Start", focused())
    }

    @Test fun ok_holdOkAndMenuOnPlusDoNothing() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, L); assertEquals("+", focused()); drain()
        holdOk(s)
        // focused() reads null here: the swallowed release leaves Compose focus on the inner
        // clickable, which has no semantics of its own. The + stays lit and keys still work.
        assertNull(nextStarted()); assertTrue(has(AddAppLabel))
        press(s, MENU)
        assertFalse(has("Open")); assertTrue(has(AddAppLabel))
        press(s, R); assertEquals("TiviMate", focused())
    }

    @Test fun ok_uninstallOnlyOfferedForRemovable() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, MENU)
        assertFalse(has("Uninstall"))
        // Down past the end used to stop on the last entry, which was Remove from favorites.
        // A factory app now ends on Manage, the television's own page for it, since there is
        // nothing to uninstall and a menu of three where one is the row itself said little.
        repeat(4) { press(s, D) }; assertEquals("Manage", focused())
        press(s, BACK); press(s, R); press(s, MENU)
        repeat(3) { press(s, D) }; drain(); press(s, OK)
        val i = nextStarted()
        assertEquals(Intent.ACTION_DELETE, i?.action)
        assertEquals("package:org.jellyfin.androidtv", i?.data.toString())
    }

    @Test fun ok_removeFavoriteAndLastFavorite() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); press(s, D); press(s, D); press(s, OK, settle = 800)
        assertEquals(myApps - "nl.uitzendinggemist", stored(FavoriteStore.APPS))
        assertEquals("YouTube", focused()); assertTrue(has("Removed from favorites"))
        s.close()
        favorites(listOf("ar.tvplayer.tv"), myInputs)
        val s2 = home()
        press(s2, MENU); press(s2, D); press(s2, D); press(s2, OK, settle = 800)
        assertEquals(emptyList<String>(), stored(FavoriteStore.APPS)); assertEquals("+", focused())
    }

    @Test fun ok_moveKeepAndRevert() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); press(s, D); press(s, OK)
        assertTrue(has("FAVORITE APPS - MOVING, OK TO KEEP"))
        press(s, U); press(s, D); press(s, MENU) // swallowed during a move
        assertTrue(focused()!!.startsWith("NPO Start")); assertFalse(has("Open"))
        press(s, R); press(s, R); press(s, OK)
        val kept = listOf("ar.tvplayer.tv", "com.google.android.youtube.tv", "org.jellyfin.androidtv", "nl.uitzendinggemist", "org.xbmc.kodi", "com.tv.help")
        assertEquals(kept, stored(FavoriteStore.APPS)); assertFalse(has("FAVORITE APPS - MOVING, OK TO KEEP"))
        press(s, MENU); press(s, D); press(s, OK)
        repeat(6) { press(s, L) }
        press(s, BACK)
        assertEquals(kept, stored(FavoriteStore.APPS)); assertEquals("NPO Start", focused())
    }

    @Test fun ok_uninstallEndToEnd() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); repeat(3) { press(s, D) }; drain(); press(s, OK)
        assertEquals(Intent.ACTION_DELETE, nextStarted()?.action)
        uninstall("nl.uitzendinggemist")
        leaveAndReturn(s, settle = 600)
        log("back from the uninstaller")
        for (i in 1..10) { if (has("It's gone now")) break; pump(200); log("waiting ${i * 200}ms more, cached=${AppRepository.cached().size}") }
        assertTrue(has("It's gone now"))
        assertEquals(myApps - "nl.uitzendinggemist", stored(FavoriteStore.APPS))
        assertEquals("YouTube", focused())
        pump(3000); assertFalse(has("It's gone now"))
    }

    @Test fun ok_returnFocusesSameTile() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, R); drain(); press(s, OK)
        assertEquals("org.jellyfin.androidtv", nextStarted()?.`package`)
        leaveAndReturn(s)
        assertEquals("Jellyfin", focused())
    }

    // ================= bugs (each fails today) =================

    /** Home pressed (or any pause/resume) while the long-press menu is open. */
    @Test fun bug_menuLosesFocusOnPauseResume() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, MENU); press(s, D)
        assertEquals("Move", focused())
        pauseResume(s)
        log("menu open, after pause/resume")
        shot("bug-menu-after-resume")
        assertTrue(has("Remove from favorites")) // the menu is still drawn
        val f = focused()
        // Today focus sits on the NPO tile under the scrim; Right then OK launches YouTube while
        // the NPO menu stays on screen, and Back no longer closes it.
        assertTrue("focus left the open menu for '$f'", f in menuEntries)
    }

    @Test fun bug_inputMenuLosesFocusToAppsRowOnPauseResume() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, D); press(s, MENU)
        assertEquals("Open", focused())
        pauseResume(s)
        log("input menu open, after pause/resume")
        val f = focused()
        assertTrue("focus left the open input menu for '$f'", f in menuEntries)
    }

    @Test fun bug_inputMoveLosesFocusToAppsRowOnPauseResume() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, D); press(s, MENU); press(s, D); press(s, OK)
        assertTrue(has("INPUTS - MOVING, OK TO KEEP"))
        pauseResume(s)
        log("input move, after pause/resume")
        press(s, R); drain(); press(s, OK)
        val started = nextStarted()
        println("TV started during input move: $started, moving=${has("INPUTS - MOVING, OK TO KEEP")}")
        // Today: R walks the apps row and OK launches NPO Start while the inputs row still says MOVING.
        assertNull("an app was launched in the middle of an input move: $started", started)
    }

    @Test fun bug_returnFromInputPutsFocusInAppsRow() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, D); press(s, R); press(s, R)
        val before = focused(); log("on an input")
        drain(); press(s, OK)
        assertEquals("android.intent.action.VIEW", nextStarted()?.action)
        leaveAndReturn(s)
        log("returned from the input")
        assertEquals(before, focused())
    }

    @Test fun bug_backClosesTheLauncher() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, BACK)
        var finishing = false
        if (s.state != Lifecycle.State.DESTROYED) s.onActivity { finishing = it.isFinishing }
        println("TV after Back: state=${s.state} finishing=$finishing")
        assertFalse("Back on the home screen finishes HomeActivity", finishing || s.state == Lifecycle.State.DESTROYED)
    }

    @Test fun bug_focusedTileLeavesSafeAreaInLongRow() {
        longRow()
        val s = home()
        val w = rootWidth()
        val safe = 48 * density()
        val pop = 66 * density() * 0.1f // the 1.2x pop-out reaches 10% of the height past each edge
        repeat(8) { press(s, R) }
        log("8 rights into a long row"); shot("bug-right-edge")
        val b = fb()!!
        // The focused bounds keep the tile's own left edge and add the grown width, so the tile's
        // own right edge is left + 111dp. It must stay inside the safe area.
        val right = b.left + 111 * density()
        assertTrue("focused tile's own right edge $right, past the safe area end ${w - safe}", right <= w - safe + 1)
    }

    @Test fun bug_focusedTileLeavesSafeAreaOnTheLeft() {
        longRow()
        val s = home()
        val safe = 48 * density()
        val pop = 66 * density() * 0.1f
        press(s, L); press(s, L) // + then Extra 14, scrolled to the end
        repeat(10) { press(s, L) }
        log("10 lefts from the end"); shot("bug-left-edge")
        val b = fb()!!
        assertTrue("focused tile starts at ${b.left}, before the safe area ${safe - pop}", b.left >= safe - pop - 1)
    }

    @Test fun bug_movingFirstVisibleTileScrollsRowInstead() {
        longRow()
        val s = home()
        val start = fb()!!.left
        press(s, MENU); press(s, D); press(s, OK)
        press(s, R)
        log("moved TiviMate one place right"); shot("bug-move-first-visible")
        assertEquals("nl.uitzendinggemist", stored(FavoriteStore.APPS)[0])
        val now = fb()!!.left
        // Today the tile stays at the left edge and the row scrolls under it, pushing NPO off screen.
        assertTrue("moving tile did not move on screen: $start -> $now", now > start + 100)
    }

    @Test fun bug_focusLostOnRecreate() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, R); press(s, R); press(s, R)
        assertEquals("Jellyfin", focused())
        s.recreate(); pump(1500)
        log("after recreate")
        assertEquals("Jellyfin", focused())
    }

    @Test fun bug_upThenDownLandsOnAnotherTile() {
        favorites(myApps, myInputs)
        val s = home()
        press(s, U); log("up from TiviMate")
        press(s, D); log("down again")
        assertEquals("TiviMate", focused())
    }

    @Test fun bug_textFallbackTouchesTileEdges() {
        ShotHooks.apps = ShotHooks.apps!! + app("com.noart", "No Art App With A Rather Long Name Indeed", null)
        favorites(listOf("com.noart", "ar.tvplayer.tv"), myInputs)
        val s = home()
        shot("bug-noart-focused")
        press(s, R)
        shot("bug-noart-unfocused")
        val text = rule.onAllNodes(hasText("No Art App With A Rather Long Name Indeed"), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        val tileLeft = 48 * density()
        val tileRight = tileLeft + 111 * density()
        println("TV fallback text $text, tile $tileLeft..$tileRight")
        val border = 3 * density()
        assertTrue("label text runs to the tile edge ($text vs $tileLeft..$tileRight), under the 3dp border",
            text.left >= tileLeft + border && text.right <= tileRight - border)
    }
}
