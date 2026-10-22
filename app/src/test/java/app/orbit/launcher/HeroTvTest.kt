package app.orbit.launcher

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero, in each of the states it can be in, driven on the real home screen.
 *
 * Both provider tables are stood in for, so the rows here are invented: nothing in this file
 * says what is on any set. What is real is the order the states come in and the words used.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HeroTvTest : TvTest() {
    override val outDir = "build/tv-shots/hero"

    private val server = "org.jellyfin.androidtv"
    private val player = "org.xbmc.kodi"

    private val episode = Resumable(
        packageName = server,
        title = "A Programme With Episodes",
        subtitle = "Season 2 Episode 4",
        description = "A long sentence about the episode, of the kind the publishing app " +
            "supplies, which has to be cut short rather than run across the whole screen.",
        posterUri = "pic://episode",
        intentUri = "orbit://play/episode",
        progress = Progress(0.4f, "24 MIN WATCHED, 36 LEFT"),
    )

    private val film = Resumable(
        packageName = server,
        title = "A Film",
        subtitle = null,
        description = "What the film is about.",
        posterUri = "pic://film",
        intentUri = "orbit://play/film",
        progress = Progress(0.75f, "90 MIN WATCHED, 30 LEFT"),
    )

    private fun picture(title: String, seasons: Int? = null, description: String? = null) =
        Picture(server, "pic://ambient/$title", title, seasons, description)

    /** A decoded poster. No test can fetch one, and the hero only shows with its picture up. */
    private fun poster(): Bitmap =
        Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(Color.DKGRAY)
        }

    @Before
    fun heroFixture() {
        // The owner granted it once, in the hero. Without it the hero only ever offers to ask.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
        // The media server first, so the tile in focus on arrival is the one that serves rows.
        favorites(listOf(server) + myApps.filter { it != server }, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        ShotHooks.artwork = poster()
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun says(text: String) =
        assertTrue("not on screen: $text", onScreen(text))

    private fun silentAbout(text: String) =
        assertFalse("should not be on screen: $text", onScreen(text))

    @Test
    fun `a part watched episode gives its name, what it is about, the episode and the numbers`() {
        ShotHooks.watchNext = listOf(episode)
        launch(HomeActivity::class.java)
        says("CONTINUE WATCHING")
        says("A Programme With Episodes")
        says("A long sentence about the episode")
        says("SEASON 2 EPISODE 4 - 24 MIN WATCHED, 36 LEFT")
        shot("episode")
    }

    @Test
    fun `a film gives no episode line, only the numbers`() {
        ShotHooks.watchNext = listOf(film)
        launch(HomeActivity::class.java)
        says("A Film")
        says("What the film is about.")
        says("90 MIN WATCHED, 30 LEFT")
        silentAbout("SEASON")
        shot("film")
    }

    @Test
    fun `the hero belongs to the app in focus, and goes quiet on one that serves nothing`() {
        ShotHooks.watchNext = listOf(episode)
        val s = launch(HomeActivity::class.java)
        says("A Programme With Episodes")
        // Along the row to an app with nothing of its own. The picture must not follow it.
        var guard = 0
        while (focused()?.contains("Kodi") != true && guard++ < 8) {
            press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        assertTrue("never reached the Kodi tile", focused()?.contains("Kodi") == true)
        silentAbout("A Programme With Episodes")
        shot("other-app")
    }

    @Test
    fun `an ambient picture is named after the app that published it, and says what it is`() {
        ShotHooks.pictures = listOf(
            picture("An Ambient Programme", seasons = 3, description = "What this one is about.")
        )
        launch(HomeActivity::class.java)
        says("ON JELLYFIN")
        says("An Ambient Programme")
        says("What this one is about.")
        says("3 SEASONS")
        shot("ambient")
    }

    @Test
    fun `one season is not said as 1 seasons`() {
        ShotHooks.pictures = listOf(picture("A Single Season", seasons = 1, description = "About."))
        launch(HomeActivity::class.java)
        says("1 SEASON")
        silentAbout("1 SEASONS")
    }

    @Test
    fun `a picture from an app that is not installed is not claimed to be on anything named`() {
        ShotHooks.pictures = listOf(Picture("com.gone.away", "pic://gone", "A Title", null, "About."))
        // Chosen by hand, since the default sources are the owner's own favourite apps.
        Backdrops.toggle(context, "com.gone.away")
        launch(HomeActivity::class.java)
        says("ALSO ON THIS TV")
        says("A Title")
    }

    @Test
    fun `with nothing to carry on with and no picture, the hero offers the way back`() {
        val s = launch(HomeActivity::class.java)
        // Nothing has been opened yet, so there is nothing to go back to: the clock stands in.
        silentAbout("CARRY ON")
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        leaveAndReturn(s)
        says("CARRY ON")
        says("Back to Jellyfin")
        shot("back-to")
    }

    @Test
    fun `nothing is claimed while the hero is still looking for a picture`() {
        // Artwork that never arrives: nothing opens a pic:// link in a test.
        ShotHooks.artwork = null
        ShotHooks.pictures = listOf(picture("A Slow One", description = "About."))
        LastOpened.record(context, LastOpened.APP, server)
        launch(HomeActivity::class.java, settle = 300)
        silentAbout("CARRY ON")
        silentAbout("A Slow One")
        shot("settling")
    }

    @Test
    fun `a picture that never arrives does not hold the hero empty for good`() {
        ShotHooks.artwork = null
        ShotHooks.pictures = listOf(picture("A Slow One", description = "About."))
        LastOpened.record(context, LastOpened.APP, server)
        launch(HomeActivity::class.java, settle = 2500)
        says("CARRY ON")
        says("Back to Jellyfin")
    }

    @Test
    fun `with no permission there is no picture coming, so the hero does not wait for one`() {
        // The clock must not be held back on a set that can show nothing else.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(ContinueWatching.PERMISSION)
        // Asked once already, so the hero is past offering to ask and on to what it can show.
        context.getSharedPreferences("orbit.home", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("listings_asked", true).commit()
        launch(HomeActivity::class.java, settle = 300)
        silentAbout("CARRY ON")
        assertTrue("no clock on arrival", onScreen(":"))
    }

    @Test
    fun `the way back comes after a picture, not before it`() {
        ShotHooks.pictures = listOf(picture("An Ambient Programme", description = "About."))
        val s = launch(HomeActivity::class.java)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        leaveAndReturn(s)
        says("An Ambient Programme")
        silentAbout("CARRY ON")
    }

    /** Leaves for another app and comes back, as opening one and pressing Home does. */
    private fun leaveAndReturn(s: androidx.test.core.app.ActivityScenario<HomeActivity>) {
        s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        pump(200)
        s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        pump(1500)
    }
}
