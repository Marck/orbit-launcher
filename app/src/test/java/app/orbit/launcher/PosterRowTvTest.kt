package app.orbit.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The row of posters, driven on the real home screen.
 *
 * The rows here are invented; nothing in this file says what is on any set. What is real is the
 * split the screen depends on: a tall cover belongs in this row and must never be stretched
 * across the hero, and a wide still belongs behind the hero and must never be squeezed into a
 * poster. The row is also meant to be absent rather than empty when a set has no covers, which
 * is the case on a TV with no media server on it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class PosterRowTvTest : TvTest() {
    override val outDir = "build/tv-shots/posters"

    private val server = "org.jellyfin.androidtv"

    private fun cover(title: String, intentUri: String? = null, pkg: String = server) =
        Picture(
            packageName = pkg,
            uri = "pic://cover/$title",
            title = title,
            seasons = null,
            description = "What $title is about.",
            intentUri = intentUri,
            wide = false,
        )

    private fun wide(title: String) =
        Picture(
            packageName = server,
            uri = "pic://wide/$title",
            title = title,
            seasons = null,
            description = "What $title is about.",
            wide = true,
        )

    /** A row's own way in, as an app writes it: its own component, carrying the item's id. */
    private fun itemIntent(packageName: String, itemId: String): String =
        Intent().apply {
            component = ComponentName(packageName, "$packageName.StartupActivity")
            putExtra("ItemId", itemId)
        }.toUri(Intent.URI_INTENT_SCHEME)

    private fun poster(): Bitmap =
        Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.DKGRAY)
        }

    @Before
    fun posterFixture() {
        // Rows can only be read with the permission. Without it there is nothing to put in the
        // row, which is a different test.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
        favorites(listOf(server) + myApps.filter { it != server }, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        ShotHooks.artwork = poster()
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun says(text: String) {
        waitFor("\"$text\" on screen") { onScreen(text) }
        assertTrue("not on screen: $text", onScreen(text))
    }

    /** Whether the top bar is on screen, rather than scrolled off the top of the column. */
    private fun topBarShown(): Boolean = runCatching {
        rule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }.isSuccess

    private fun countOnScreen(text: String): Int =
        rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    private fun silentAbout(text: String) =
        assertFalse("should not be on screen: $text", onScreen(text))

    /** Focus on the first poster, with nothing left over from getting there. */
    private fun ontoPosters(s: ActivityScenario<HomeActivity>) {
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        while (nextStarted() != null) Unit
    }

    @Test
    fun `two rows sharing one cover do not take the screen down with them`() {
        // What a media server actually publishes: two episodes of one series, each carrying the
        // series cover. Compose throws "Key ... was already used" out of the row's measure pass,
        // which took Orbit down while the owner was moving along the posters.
        val shared = "pic://cover/one-series"
        ShotHooks.pictures = listOf(
            cover("Episode One").copy(uri = shared),
            cover("Episode Two").copy(uri = shared),
        )
        val scenario = launch(HomeActivity::class.java)
        says("ON JELLYFIN")

        // Walked, not merely drawn: the crash came from the row measuring an item that was not
        // on screen when it was built.
        ontoPosters(scenario)
        repeat(4) { press(scenario, KeyEvent.KEYCODE_DPAD_RIGHT) }
        repeat(4) { press(scenario, KeyEvent.KEYCODE_DPAD_LEFT) }

        says("ON JELLYFIN")
    }

    @Test
    fun `a cover gets a row of its own, named after the app it came from`() {
        ShotHooks.pictures = listOf(cover("A Cover On The Server"))
        launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        // Not "library": that is a claim about the owner, not about where the row came from.
        silentAbout("LIBRARY")
        shot("posters")
    }

    @Test
    fun `covers from more than one app keep the name that claims nothing`() {
        ShotHooks.pictures = listOf(
            cover("A Cover On The Server"),
            // A second chosen app. An app the owner never picked has its covers filtered
            // out long before the label, so it could not make the row mixed.
            cover("A Cover From Somewhere Else", pkg = "com.google.android.youtube.tv"),
        )
        launch(HomeActivity::class.java)
        says("POSTERS")
        silentAbout("ON JELLYFIN")
        silentAbout("ON YOUTUBE")
    }

    @Test
    fun `an app Orbit cannot name does not put its package on the screen`() {
        // Checked on the rule rather than through the screen: a cover from an app the owner
        // never chose is filtered out before any label is worked out, so the only way to reach
        // this is an app that went away between the choosing and the read.
        val orphan = cover("A Cover", pkg = "com.an.app.that.went.away")
        assertEquals("POSTERS", posterRowLabel(listOf(orphan), ShotHooks.apps.orEmpty()))
    }

    @Test
    fun `an app with a blank name is not named either`() {
        val blank = app("com.quiet.app", "   ", null)
        val picture = cover("A Cover", pkg = "com.quiet.app")
        assertEquals("POSTERS", posterRowLabel(listOf(picture), listOf(blank)))
    }

    @Test
    fun `the top bar comes back when focus leaves the posters`() {
        // With the posters in, the column is taller than the screen. Compose scrolls a row
        // into view as it takes focus and never scrolls back, which left the top bar and the
        // date off the top of the screen for as long as focus stayed below the hero.
        ShotHooks.pictures = List(12) { cover("Cover number $it") }
        val s = launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        assertTrue("the top bar should be on screen to begin with", topBarShown())

        // Down past the posters and into the inputs, which is as far as the column goes.
        ontoPosters(s)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        waitFor("the top bar to scroll away", diagnose = { "focus: ${focused()}" }) { !topBarShown() }
        // Proves the rest of this test is testing something: with nothing scrolled away there
        // is nothing to come back from, and the assertions below would hold on their own.
        assertFalse("the rows below never pushed the top bar off screen", topBarShown())

        press(s, KeyEvent.KEYCODE_DPAD_UP)
        press(s, KeyEvent.KEYCODE_DPAD_UP)
        waitFor("the top bar to come back", diagnose = { "focus: ${focused()}" }) { topBarShown() }
        assertTrue("the top bar never came back after the posters", topBarShown())
    }

    /** Which cover has focus, or null when focus is not in the row at all. */
    private fun focusedCover(names: List<String>): String? =
        focused()?.let { f -> names.firstOrNull { f.contains(it) } }

    /**
     * The row comes round, in both directions.
     *
     * Written as a property rather than by naming the first and last cover, because the row's
     * order is shuffled: which cover sits at either end is not knowable from here, and a test
     * that assumed it would pass or fail on the shuffle rather than on the behaviour.
     *
     * The property is that **every** press moves. One more press than there are covers means
     * an end is reached whatever the order, and before this the press at that end did nothing
     * at all. Collecting what was visited also proves it goes round the whole row rather than
     * bouncing between two of them.
     */
    private fun assertComesRound(key: Int) {
        val names = ('A'..'L').map { "Cover $it" }
        ShotHooks.pictures = names.map { cover(it) }
        val s = launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        ontoPosters(s)
        waitFor("a cover to take focus", diagnose = { "focus: ${focused()}" }) {
            focusedCover(names) != null
        }

        val seen = linkedSetOf<String>()
        repeat(names.size + 1) {
            val before = focusedCover(names)!!
            seen += before
            press(s, key)
            waitFor("the press at $before to move", diagnose = { "focus: ${focused()}" }) {
                focusedCover(names).let { it != null && it != before }
            }
        }

        assertEquals("the row did not go round every cover", names.size, seen.size)
    }

    @Test
    fun `the row comes round going left`() {
        assertComesRound(KeyEvent.KEYCODE_DPAD_LEFT)
    }

    @Test
    fun `the row comes round going right`() {
        assertComesRound(KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    @Test
    fun `a set with no covers keeps the screen it had, with no empty row on it`() {
        ShotHooks.pictures = listOf(wide("A Wide Still"))
        launch(HomeActivity::class.java)
        says("APPS")
        silentAbout("POSTERS")
        // The hero names the still it is showing with the same words the row would use, so
        // absence cannot be asserted here. Counting can: one line is the hero's, two would
        // mean a row had been drawn for a set that has no covers.
        assertEquals("a poster row was drawn with nothing in it", 1, countOnScreen("ON JELLYFIN"))
        shot("no-posters")
    }

    @Test
    fun `a cover is never stretched across the hero`() {
        ShotHooks.pictures = listOf(cover("A Cover On The Server"))
        launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        // The hero writes out the title of whatever it is showing, and a tile does not: with
        // artwork on it a cover says its name only to a screen reader. So the title being
        // nowhere in the text on screen is the cover not having reached the hero.
        silentAbout("A Cover On The Server")
    }

    @Test
    fun `a wide still is never squeezed into the poster row`() {
        ShotHooks.pictures = listOf(wide("A Wide Still"))
        launch(HomeActivity::class.java)
        // It belongs to the hero, which does name what it shows.
        says("A Wide Still")
        silentAbout("POSTERS")
    }

    @Test
    fun `OK on a poster opens the thing it shows, not the app's front door`() {
        ShotHooks.pictures = listOf(
            cover("A Cover On The Server", intentUri = itemIntent(server, "item-42"))
        )
        val s = launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        ontoPosters(s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        assertEquals(server, started?.component?.packageName)
        assertEquals("item-42", started?.getStringExtra("ItemId"))
    }

    @Test
    fun `a poster with no way in still opens the app that published it`() {
        ShotHooks.pictures = listOf(cover("No Way In"))
        val s = launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        ontoPosters(s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        assertEquals(server, started?.`package`)
        assertEquals(Intent.ACTION_MAIN, started?.action)
    }

    @Test
    fun `the inputs row is still reachable once the posters have pushed it off the screen`() {
        ShotHooks.pictures = List(8) { cover("Cover ${it + 1}") }
        val s = launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        // Down through the posters and on into the inputs, which now sit below the fold. The
        // screen scrolls to bring them up; without that they would be drawn and unreachable.
        ontoPosters(s)
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        // By label, which is what the tile puts on the screen, not by the id it is stored under.
        val labels = listOf("HDMI 1", "PlayStation 5", "Nintendo Switch OLED")
        fun inInputs() = labels.any { focused().orEmpty().startsWith(it) }
        waitFor("focus down in the inputs row") { inInputs() }
        assertTrue("focus never reached the inputs row: ${focused()}", inInputs())
        shot("posters-and-inputs")
    }
}
