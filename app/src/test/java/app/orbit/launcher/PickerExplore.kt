package app.orbit.launcher

import android.graphics.Color as AColor
import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch

/**
 * Exploration of the two picker screens (AppPickerActivity, InputPickerActivity) and their
 * hand-off to HomeActivity. Tests named bugN_* FAIL on the current app code (they assert the
 * expected behaviour); ok_* tests pass and document what works.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PickerExplore : TvTest() {

    override val outDir = "build/tv-shots/explore-pickers"

    // ---------- helpers ----------

    private fun label(n: SemanticsNode): String? {
        val cd = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        val tx = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        return listOfNotNull(cd, tx).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { null }
    }

    private fun nodes(): List<SemanticsNode> =
        rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()

    /** Bounds of the first node whose content description or text is exactly [text]. */
    private fun boundsOf(text: String): Rect =
        nodes().first { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true ||
                n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true
        }.boundsInRoot

    private fun allText(): List<String> = nodes().mapNotNull { label(it) }

    private fun chosenLine(): String = allText().first { it.startsWith("Select") }

    private fun focusedBounds(): Rect? =
        rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private var shotNo = 0

    /** Reads a pixel from a Roborazzi capture (captureToImage never settles with marquees running). */
    private fun pixel(x: Int, y: Int): Int {
        val path = "$outDir/px-${javaClass.simpleName}-${shotNo++}.png"
        rule.onRoot().captureRoboImage(path)
        return android.graphics.BitmapFactory.decodeFile(path).getPixel(x, y)
    }

    private fun brightness(c: Int) = (AColor.red(c) + AColor.green(c) + AColor.blue(c)) / 3

    /** App tiles show their banner Image, whose bounds follow the tile's 1.06x focus scale. */
    private fun appTileLit(label: String): Boolean = boundsOf(label).width > 222f * 1.03f

    /**
     * Input grid geometry at this TV size: 5 cells of 326px with 24px gaps from x=96, first row
     * 201..361px. A focused tile is scaled 1.06x, so its 6px ring spans ~10px outside the cell.
     */
    private fun cellLeft(col: Int) = 96 + col * 350.5f

    /** Brightness on the left focus ring of first-row input tile [col], mid-height. Black when not lit. */
    private fun inputRingBrightness(col: Int): Int = brightness(pixel((cellLeft(col) - 7).toInt(), 281))

    /**
     * How much bigger the focused tile is than an unfocused one.
     *
     * The focus ring is gone, so the pop-out is the whole signal and this is what the ring
     * brightness used to measure. A clipped tile grows by less than the scale.
     */
    private fun popOut(vertical: Boolean = false): Float {
        val focused = focusedBounds() ?: return 1f
        val grown = if (vertical) focused.height else focused.width
        val plain = nodes()
            .filter {
                it.config.getOrNull(SemanticsProperties.ContentDescription) != null ||
                    it.config.getOrNull(SemanticsProperties.Text) != null
            }
            .map { if (vertical) it.boundsInRoot.height else it.boundsInRoot.width }
            .filter { it > 40f && it < grown - 1f }
            .minOrNull() ?: return 1f
        return grown / plain
    }

    private fun order(): List<String> = nodes()
        .filter { it.config.getOrNull(SemanticsProperties.ContentDescription) != null && it.boundsInRoot.width > 100 }
        .sortedWith(compareBy({ it.boundsInRoot.top.toInt() / 50 }, { it.boundsInRoot.left }))
        .map { label(it)!! }

    private fun manyApps(n: Int) = (1..n).map { i ->
        app("pkg.app%02d".format(i), "App %02d".format(i), banner("A%02d".format(i), 0xFF223344.toInt(), 0xFFFFFFFF.toInt()))
    }

    // ---------- bugs (each FAILS today) ----------

    /** After OK toggles a tile, the focused tile is no longer highlighted (no border, no scale). */
    @Test
    fun bug1_appPicker_highlightLostAfterOk() {
        favorites(emptyList(), emptyList())
        val s = launch(AppPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue("NPO Start should be lit before OK", appTileLit("NPO Start"))
        press(s, KeyEvent.KEYCODE_DPAD_CENTER, settle = 2000)
        shot("bug1-app-after-ok")
        assertEquals(listOf("nl.uitzendinggemist"), stored(FavoriteStore.APPS))
        val lit = listOf("TiviMate", "NPO Start", "YouTube", "Jellyfin", "Kodi", "Help", "Netflix").filter { appTileLit(it) }
        println("TV lit tiles after OK: $lit")
        assertEquals("the tile just toggled should still show the focus highlight", listOf("NPO Start"), lit)
    }

    @Test
    fun bug1b_inputPicker_highlightLostAfterOk() {
        favorites(emptyList(), emptyList())
        val s = launch(InputPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        val before = popOut()
        press(s, KeyEvent.KEYCODE_DPAD_CENTER, settle = 2000)
        shot("bug1b-input-after-ok")
        assertEquals(listOf("ps5"), stored(FavoriteStore.INPUTS))
        val after = popOut()
        println("TV PS5 pop-out before OK=$before after OK=$after")
        assertTrue("focused tile does not pop out before OK, was $before", before > 1.05f)
        assertTrue("focused tile should still pop out after OK, was $after", after > 1.05f)
    }

    /** The input picker does not wrap at its ends, unlike the app picker and both home rows. */
    @Test
    fun bug2_inputPicker_noWrapLeftAtFirst() {
        favorites(emptyList(), emptyList())
        val s = launch(InputPickerActivity::class.java)
        assertEquals("HDMI 1", focused())
        press(s, KeyEvent.KEYCODE_DPAD_LEFT)
        println("TV left at first -> ${focused()}")
        assertEquals("Left on the first input should wrap to the last", "HDMI 4, eARC/ARC", focused())
    }

    @Test
    fun bug2b_inputPicker_noWrapRightAtLast() {
        favorites(emptyList(), emptyList())
        val s = launch(InputPickerActivity::class.java)
        repeat(3) { press(s, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("HDMI 4, eARC/ARC", focused())
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        println("TV right at last -> ${focused()}")
        assertEquals("Right on the last input should wrap to the first", "HDMI 1", focused())
    }

    /** The focus ring on a disconnected (dimmed) input is drawn at 45% alpha as well. */
    @Test
    fun bug3_inputPicker_focusRingDimmedOnDisconnected() {
        favorites(emptyList(), emptyList())
        val s = launch(InputPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        val connected = popOut()
        repeat(2) { press(s, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("HDMI 4, eARC/ARC", focused())
        val disconnected = popOut()
        shot("bug3-disconnected-focused")
        println("TV pop-out: connected focused=$connected disconnected focused=$disconnected")
        assertTrue("connected tile does not pop out, was $connected", connected > 1.05f)
        // The dimming is inside the tile, so a disconnected input in focus stands out exactly
        // as much as any other. That is what the ring used to prove.
        assertTrue("focus on a disconnected input should be as plain to see, was $disconnected", disconnected > 1.05f)
    }

    /**
     * The 1.06x pop-out of a first-row tile is clipped at the top by the grid's viewport
     * (lazy grids clip along their scroll axis), so the top of the focus ring is cut off.
     */
    @Test
    fun bug7_inputPicker_firstRowFocusRingClippedAtTop() {
        favorites(emptyList(), emptyList())
        launch(InputPickerActivity::class.java)
        assertEquals("HDMI 1", focused())
        val wide = popOut()
        val tall = popOut(vertical = true)
        println("TV HDMI 1 focused: pop-out $wide wide, $tall tall")
        assertTrue("focused tile does not pop out, was $wide", wide > 1.05f)
        // A first-row tile grows upward as much as it grows down. Clipped at the top of the
        // grid it would grow by half as much, which is what this used to catch as a cut ring.
        assertTrue("focused first-row tile is clipped at the top, grew $tall", tall > 1.10f)
    }

    @Test
    fun bug7b_appPicker_firstRowPopOutClippedAtTop() {
        favorites(emptyList(), emptyList())
        val s = launch(AppPickerActivity::class.java)
        assertEquals("TiviMate", focused())
        // The same tile, focused and then at rest. Compared against itself rather than against
        // a pixel: a threshold here is really a record of where the grid happened to start, and
        // it failed the moment anything was added above the grid.
        val lit = boundsOf("TiviMate")
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        val resting = boundsOf("TiviMate")
        assertTrue(
            "a focused first-row tile should sit higher than the same tile at rest, " +
                "lit $lit resting $resting",
            lit.top < resting.top,
        )
    }

    /** App picker counts stored favorites that are not on the list; the input picker does not. */
    @Test
    fun bug4_appPicker_countsAbsentFavorites() {
        favorites(listOf("gone.app", "org.xbmc.kodi"), listOf("ghost", "ps5"))
        val a = launch(AppPickerActivity::class.java)
        val appLine = chosenLine()
        a.close()
        launch(InputPickerActivity::class.java)
        val inputLine = chosenLine()
        println("TV app picker: '$appLine'  input picker: '$inputLine'")
        assertEquals("Select to add or remove. 1 chosen.", inputLine)
        assertEquals(
            "only Kodi is ticked",
            "Select to add or remove. 1 chosen. Hold OK to uninstall.",
            appLine,
        )
    }

    @Test
    fun bug4b_appPicker_emptyListStillSaysChosen() {
        ShotHooks.apps = emptyList()
        favorites(listOf("ar.tvplayer.tv"), emptyList())
        launch(AppPickerActivity::class.java)
        shot("bug4b-empty-apps")
        println("TV empty app picker line: '${chosenLine()}'")
        assertEquals("nothing is shown, so nothing should read as chosen",
            "Select an app to add it to the row. Hold OK to uninstall.", chosenLine())
    }

    /**
     * The grid reshuffles under the cursor when the app list read lands after a toggle.
     * The picker first shows AppRepository.cached() and then the fresh load. On a device the
     * fresh list always has new Drawable objects, so `remember(apps)` re-arranges with the
     * favorites as they are by then. Dispatchers.IO is held busy here to stand in for a slow
     * PackageManager read; the app code is unchanged.
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun bug5_appPicker_reshufflesWhenFreshListLands() {
        // Home has run before: the cache holds a list.
        AppRepository.load(context)
        // The fresh read returns the same apps with new artwork objects, as PackageManager does.
        val fixtureApps = ShotHooks.apps!!
        ShotHooks.apps = fixtureApps.map { it.copy(art = banner(it.label, 0xFF333333.toInt(), 0xFFFFFFFF.toInt())) }
        favorites(listOf("org.xbmc.kodi", "ar.tvplayer.tv"), emptyList())

        val latch = CountDownLatch(1)
        repeat(256) { GlobalScope.launch(Dispatchers.IO) { latch.await() } }
        try {
            val s = launch(AppPickerActivity::class.java)
            assertEquals("Kodi", focused())
            // Untick Kodi (first) while the fresh read is still running.
            press(s, KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(listOf("ar.tvplayer.tv"), stored(FavoriteStore.APPS))
            val kodiBefore = boundsOf("Kodi")
            println("TV order before release: ${order()} cacheIsFresh=${AppRepository.cached() === ShotHooks.apps}")
            latch.countDown()
            // Let the IO thread finish the read in real time, then let frames run.
            repeat(10) { Thread.sleep(50); pump(200) }
            shot("bug5-after-fresh-list")
            val kodiAfter = boundsOf("Kodi")
            println("TV order after release: ${order()} cacheIsFresh=${AppRepository.cached() === ShotHooks.apps}")
            println("TV Kodi before=$kodiBefore after=$kodiAfter focused=${focused()}")
            assertEquals("Kodi tile should stay where it was", kodiBefore.left, kodiAfter.left, 1f)
            assertEquals("Kodi tile should stay where it was", kodiBefore.top, kodiAfter.top, 1f)
        } finally {
            latch.countDown()
        }
    }

    // ---------- what works (pass today) ----------

    @Test
    fun ok_appPicker_navToggleWrapBack() {
        favorites(listOf("org.xbmc.kodi", "ar.tvplayer.tv"), emptyList())
        val s = launch(AppPickerActivity::class.java)
        assertEquals("Kodi", focused())
        assertEquals("Select to add or remove. 2 chosen. Hold OK to uninstall.", chosenLine())
        press(s, KeyEvent.KEYCODE_DPAD_LEFT)
        // The last tile is now the last factory app by name rather than the last app by name,
        // since what the owner installed is offered before what came with the television.
        assertEquals("YouTube", focused()) // wrap to last
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("Kodi", focused()) // wrap to first
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        // Also moved by the new order: the second row starts somewhere else now.
        assertEquals("Plex: Stream Movies & TV", focused())
        // One press, not two. The second used to be a no-op against the top of the grid; it
        // now reaches the filter buttons, which is the only thing that leads to them.
        press(s, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("Kodi", focused())
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        val tiviBefore = boundsOf("TiviMate")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER) // untick TiviMate
        assertEquals(listOf("org.xbmc.kodi"), stored(FavoriteStore.APPS))
        assertEquals("Select to add or remove. 1 chosen. Hold OK to uninstall.", chosenLine())
        press(s, KeyEvent.KEYCODE_DPAD_CENTER) // tick again: goes to the end
        assertEquals(listOf("org.xbmc.kodi", "ar.tvplayer.tv"), stored(FavoriteStore.APPS))
        assertEquals(tiviBefore.top, boundsOf("TiviMate").top, 1f) // no reshuffle
        // A held OK raises the menu here rather than falling through to a tap on the way up,
        // so nothing is toggled until an entry in it is chosen. What the menu offers, and what
        // choosing from it does, is PickerMenuTvTest's.
        holdOk(s)
        assertEquals(listOf("org.xbmc.kodi", "ar.tvplayer.tv"), stored(FavoriteStore.APPS))
        press(s, KeyEvent.KEYCODE_BACK)
        shot("ok-app-picker")
    }

    @Test
    fun ok_backWithoutTogglingLeaves() {
        val s = launch(AppPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(s, KeyEvent.KEYCODE_BACK)
        var f1 = false
        s.onActivity { f1 = it.isFinishing }
        s.close()
        val t = launch(InputPickerActivity::class.java)
        press(t, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(t, KeyEvent.KEYCODE_BACK)
        var f2 = false
        t.onActivity { f2 = it.isFinishing }
        assertTrue(f1 && f2)
    }

    /** After a toggle, the first Back press is swallowed; it takes a second Back to leave. */
    @Test
    fun bug6_appPicker_backAfterToggleNeedsTwoPresses() {
        favorites(emptyList(), emptyList())
        val s = launch(AppPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("ar.tvplayer.tv"), stored(FavoriteStore.APPS))
        press(s, KeyEvent.KEYCODE_BACK)
        var first = false
        s.onActivity { first = it.isFinishing }
        println("TV after OK, first Back: finishing=$first litTiviMate=${appTileLit("TiviMate")}")
        press(s, KeyEvent.KEYCODE_BACK)
        var second = false
        s.onActivity { second = it.isFinishing }
        println("TV after OK, second Back: finishing=$second")
        assertTrue("one Back after choosing should leave the picker", first)
    }

    @Test
    fun bug6b_inputPicker_backAfterToggleNeedsTwoPresses() {
        favorites(emptyList(), emptyList())
        val s = launch(InputPickerActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("hdmi1"), stored(FavoriteStore.INPUTS))
        press(s, KeyEvent.KEYCODE_BACK)
        var first = false
        s.onActivity { first = it.isFinishing }
        press(s, KeyEvent.KEYCODE_BACK)
        var second = false
        s.onActivity { second = it.isFinishing }
        println("TV input picker after OK: first Back finishing=$first, second Back finishing=$second")
        assertTrue("one Back after choosing should leave the picker", first)
    }

    @Test
    fun ok_appPicker_longListKeepsFocusOnScreen() {
        ShotHooks.apps = manyApps(37)
        favorites(emptyList(), emptyList())
        val s = launch(AppPickerActivity::class.java)
        val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
        repeat(9) {
            press(s, KeyEvent.KEYCODE_DPAD_DOWN)
            val b = focusedBounds()!!
            assertTrue("focus on screen: $b", b.top >= 0 && b.bottom <= screen.bottom)
        }
        assertEquals("App 36", focused())
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("App 37", focused())
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("App 01", focused())
        press(s, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("App 37", focused())
        assertTrue(focusedBounds()!!.bottom <= screen.bottom)
        shot("ok-long-list-wrapped")
    }

    @Test
    fun ok_inputPicker_disconnectedSelectable_absentNotCounted_back() {
        favorites(emptyList(), listOf("switch", "ghost"))
        val s = launch(InputPickerActivity::class.java)
        assertEquals("Nintendo Switch OLED, HDMI 3", focused())
        assertEquals("Select to add or remove. 1 chosen.", chosenLine())
        repeat(3) { press(s, KeyEvent.KEYCODE_DPAD_RIGHT) }
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("switch", "ghost", "hdmi4"), stored(FavoriteStore.INPUTS))
        assertEquals("Select to add or remove. 2 chosen.", chosenLine())
        shot("ok-input-picker")
    }

    @Test
    fun ok_handOffFromPlusTile() {
        favorites(listOf("ar.tvplayer.tv", "org.xbmc.kodi"), listOf("hdmi1"))
        val home = launch(HomeActivity::class.java)
        press(home, KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("+", focused())
        while (nextStarted() != null) Unit
        press(home, KeyEvent.KEYCODE_DPAD_CENTER)
        val intent = nextStarted()!!
        home.moveToState(Lifecycle.State.CREATED)
        val p = launch(AppPickerActivity::class.java, intent)
        repeat(2) { press(p, KeyEvent.KEYCODE_DPAD_RIGHT) }
        assertEquals("NPO Start", focused())
        press(p, KeyEvent.KEYCODE_DPAD_CENTER)
        repeat(2) { press(p, KeyEvent.KEYCODE_DPAD_LEFT) }
        press(p, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("org.xbmc.kodi", "nl.uitzendinggemist"), stored(FavoriteStore.APPS))
        press(p, KeyEvent.KEYCODE_BACK)
        p.close()
        home.moveToState(Lifecycle.State.RESUMED)
        pump(1500)
        val kodi = boundsOf("Kodi").left
        val npo = boundsOf("NPO Start").left
        assertTrue("Kodi before NPO Start", kodi < npo)
        assertTrue("TiviMate gone from the row", nodes().none { label(it) == "TiviMate" && it.boundsInRoot.top > 600 })
        assertEquals("+", focused())
        shot("ok-home-after-picker")
    }

    @Test
    fun ok_inputHandOff() {
        favorites(myApps, listOf("ps5", "ghost"))
        val p = launch(InputPickerActivity::class.java)
        press(p, KeyEvent.KEYCODE_DPAD_RIGHT)
        press(p, KeyEvent.KEYCODE_DPAD_CENTER)
        repeat(2) { press(p, KeyEvent.KEYCODE_DPAD_RIGHT) }
        press(p, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("ps5", "ghost", "hdmi1", "hdmi4"), stored(FavoriteStore.INPUTS))
        p.close()
        launch(HomeActivity::class.java)
        val xs = listOf("PlayStation 5", "HDMI 1", "HDMI 4").map { boundsOf(it).left }
        assertEquals(xs.sorted(), xs)
        shot("ok-home-inputs")
    }

    @Test
    fun ok_emptyListsBackStillWorks() {
        ShotHooks.apps = emptyList()
        ShotHooks.inputs = emptyList()
        val s = launch(AppPickerActivity::class.java)
        assertEquals(null, focused())
        press(s, KeyEvent.KEYCODE_BACK)
        var f1 = false
        s.onActivity { f1 = it.isFinishing }
        s.close()
        val t = launch(InputPickerActivity::class.java)
        shot("ok-empty-inputs")
        assertEquals("Select an input to add it to the row.", chosenLine())
        press(t, KeyEvent.KEYCODE_BACK)
        var f2 = false
        t.onActivity { f2 = it.isFinishing }
        assertTrue(f1 && f2)
    }
}
