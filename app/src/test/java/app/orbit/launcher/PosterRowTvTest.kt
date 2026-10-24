package app.orbit.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
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

    private fun cover(title: String, intentUri: String? = null) =
        Picture(
            packageName = server,
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

    private fun silentAbout(text: String) =
        assertFalse("should not be on screen: $text", onScreen(text))

    /** Focus on the first poster, with nothing left over from getting there. */
    private fun ontoPosters(s: ActivityScenario<HomeActivity>) {
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        while (nextStarted() != null) Unit
    }

    @Test
    fun `a cover gets a row of its own, under a label that does not overclaim`() {
        ShotHooks.pictures = listOf(cover("A Cover On The Server"))
        launch(HomeActivity::class.java)
        says("POSTERS")
        // Not "library": most of these come from a shop front, not from anything the owner has.
        silentAbout("LIBRARY")
        shot("posters")
    }

    @Test
    fun `a set with no covers keeps the screen it had, with no empty row on it`() {
        ShotHooks.pictures = listOf(wide("A Wide Still"))
        launch(HomeActivity::class.java)
        says("APPS")
        silentAbout("POSTERS")
        shot("no-posters")
    }

    @Test
    fun `a cover is never stretched across the hero`() {
        ShotHooks.pictures = listOf(cover("A Cover On The Server"))
        launch(HomeActivity::class.java)
        says("POSTERS")
        // The hero names what it is showing. A cover reaching it would be named there too.
        silentAbout("ON JELLYFIN")
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
        says("POSTERS")
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
        says("POSTERS")
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
        says("POSTERS")
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
