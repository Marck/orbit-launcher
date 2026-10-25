package app.orbit.launcher

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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

    private fun picture(
        title: String,
        seasons: Int? = null,
        description: String? = null,
        year: Int? = null,
        runtime: Int? = null,
        channel: String? = null,
        intentUri: String? = null,
    ) = Picture(server, "pic://ambient/$title", title, seasons, description, year, runtime, channel, intentUri)

    /** A row's own way in, as an app writes it: its own component, carrying the item's id. */
    private fun itemIntent(packageName: String, itemId: String): String =
        Intent().apply {
            component = ComponentName(packageName, "$packageName.StartupActivity")
            putExtra("ItemId", itemId)
        }.toUri(Intent.URI_INTENT_SCHEME)

    /** Focus on the hero, with nothing left over from getting there. */
    private fun ontoHero(s: androidx.test.core.app.ActivityScenario<HomeActivity>) {
        press(s, KeyEvent.KEYCODE_DPAD_UP)
        while (nextStarted() != null) Unit
    }

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

    /**
     * Waits for the words, rather than asserting on whatever frame the launch happened to
     * leave behind. Every one of these comes from a provider read on another thread.
     */
    private fun says(text: String) {
        waitFor("\"$text\" on screen") { onScreen(text) }
        assertTrue("not on screen: $text", onScreen(text))
    }

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
    fun `an episode nobody has started is not headed continue watching`() {
        // What the media server publishes on the set this was written for: both of its rows
        // are the next episode of a series, with no position on them, and the heading used to
        // say the owner was part way through something they had never opened.
        ShotHooks.watchNext = listOf(episode.copy(
            kind = Kind.NEXT,
            programType = android.media.tv.TvContract.WatchNextPrograms.TYPE_TV_EPISODE,
            progress = null,
        ))
        launch(HomeActivity::class.java)
        says("NEXT EPISODE")
        silentAbout("CONTINUE WATCHING")
        says("A Programme With Episodes")
        shot("next-episode")
    }

    @Test
    fun `something only put aside says so rather than claiming it was watched`() {
        ShotHooks.watchNext = listOf(film.copy(
            kind = Kind.WATCHLIST,
            programType = android.media.tv.TvContract.WatchNextPrograms.TYPE_MOVIE,
            progress = null,
        ))
        launch(HomeActivity::class.java)
        says("SAVED FOR LATER")
        silentAbout("CONTINUE WATCHING")
    }

    @Test
    fun `a long title is given a second line rather than cut`() {
        ShotHooks.watchNext = listOf(
            episode.copy(title = "A Programme With A Name Far Longer Than The Hero Is Wide")
        )
        launch(HomeActivity::class.java)
        // The whole name, not an ellipsis part way through it.
        says("A Programme With A Name Far Longer Than The Hero Is Wide")
        shot("long-title")
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
    fun `OK on a picture opens the thing it shows, not the app's front door`() {
        ShotHooks.pictures = listOf(
            picture("A Film On The Server", description = "About.", intentUri = itemIntent(server, "item-42"))
        )
        val s = launch(HomeActivity::class.java)
        says("A Film On The Server")
        ontoHero(s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        assertEquals(server, started?.component?.packageName)
        assertEquals("item-42", started?.getStringExtra("ItemId"))
    }

    @Test
    fun `a picture with no way in still opens the app that published it`() {
        ShotHooks.pictures = listOf(picture("No Way In", description = "About."))
        val s = launch(HomeActivity::class.java)
        says("No Way In")
        ontoHero(s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        assertEquals(server, started?.`package`)
        assertEquals(Intent.ACTION_MAIN, started?.action)
    }

    @Test
    fun `a row pointing somewhere its own app does not own is not followed`() {
        ShotHooks.pictures = listOf(
            picture("Points Elsewhere", description = "About.", intentUri = itemIntent("com.somewhere.else", "x"))
        )
        val s = launch(HomeActivity::class.java)
        says("Points Elsewhere")
        ontoHero(s)
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        // The app that published the row, not the one the row pointed at.
        val started = nextStarted()
        assertEquals(server, started?.`package`)
        assertNull("must not follow another app's component", started?.component)
    }

    @Test
    fun `an ambient picture says which of the app's own rows it came from`() {
        ShotHooks.pictures = listOf(
            picture("A Row Of Its Own", description = "About.", channel = "Recently added media")
        )
        launch(HomeActivity::class.java)
        says("ON JELLYFIN, RECENTLY ADDED MEDIA")
        says("A Row Of Its Own")
        shot("ambient-row")
    }

    @Test
    fun `an ambient film gives the year and the running length the app published`() {
        ShotHooks.pictures = listOf(
            picture("An Ambient Film", description = "What this one is about.", year = 2014, runtime = 109)
        )
        launch(HomeActivity::class.java)
        says("An Ambient Film")
        says("2014 - 1H 49M")
        silentAbout("SEASON")
        shot("ambient-film")
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
    fun `with nothing at all the clock takes focus and offers the backdrops`() {
        // Nothing to carry on with, no pictures, and nothing opened yet, which is the one
        // state where the hero used to have no focus target and so no way to the picker.
        val s = launch(HomeActivity::class.java)
        // The hint is not there until the clock is focused, which is the point of it.
        silentAbout("Press OK to choose backdrops")
        press(s, KeyEvent.KEYCODE_DPAD_UP)
        says("Press OK to choose backdrops")
        while (nextStarted() != null) Unit
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        assertEquals(AppPickerActivity::class.java.name, started?.component?.className)
        assertTrue(
            "opens the backdrop sources",
            started?.getBooleanExtra(AppPickerActivity.EXTRA_BACKDROPS, false) == true,
        )
    }

    @Test
    fun `a picture that will not load does not hold the hero empty`() {
        // Artwork that cannot be loaded: nothing opens a pic:// link in a test. The wait ends
        // when the fetch comes back with nothing, rather than after a guessed number of
        // seconds, so the way back is offered as soon as it is known there is no picture.
        ShotHooks.artwork = null
        ShotHooks.pictures = listOf(picture("One That Will Not Load", description = "About."))
        LastOpened.record(context, LastOpened.APP, server)
        launch(HomeActivity::class.java)
        // The wait ends when the fetch comes back, and a fetch takes as long as the machine
        // takes; waiting for the words themselves is the only honest way to time this.
        says("CARRY ON")
        says("Back to Jellyfin")
        silentAbout("One That Will Not Load")
        shot("no-picture")
    }

    @Test
    fun `a picture that loads is never pushed aside by the way back`() {
        ShotHooks.pictures = listOf(picture("A Loaded One", description = "About."))
        LastOpened.record(context, LastOpened.APP, server)
        launch(HomeActivity::class.java)
        says("A Loaded One")
        silentAbout("CARRY ON")
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

    /** Which of [titles] the hero is showing, or null while it is showing none of them. */
    private fun heroTitle(titles: List<String>): String? = titles.firstOrNull { onScreen(it) }

    @Test
    fun `right steps the hero on and left steps it back`() {
        // Bounded pumps on purpose. The hero also moves on by itself every CycleMs, so a test
        // that waits for the picture to change gets a pass whether the keys work or not: that
        // is exactly what the first version of this test did.
        val titles = listOf("First Ambient", "Second Ambient", "Third Ambient")
        ShotHooks.pictures = titles.map { picture(it, description = "What $it is about.") }
        val s = launch(HomeActivity::class.java)
        ontoHero(s)
        waitFor("the hero to show one of its pictures") { heroTitle(titles) != null }
        val before = heroTitle(titles)

        // One press, one settle, nowhere near the ten seconds the hero cycles on its own.
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT, settle = 600)
        val middle = heroTitle(titles)
        assertNotNull("the hero stopped showing anything", middle)
        assertTrue("right did not step the hero on", middle != before)

        press(s, KeyEvent.KEYCODE_DPAD_LEFT, settle = 600)
        // The automatic cycle only ever goes forwards, so with three pictures it cannot land
        // back where it started in two steps. Coming back is the step going the other way.
        assertEquals("left did not step the hero back", before, heroTitle(titles))
        // And neither key took focus off the hero.
        assertFalse("a step took focus out of the hero: ${focused()}", focused() in topBarButtons)
    }

    @Test
    fun `a step wraps off either end`() {
        // Checked here rather than through the screen: the cycle starts wherever the shuffle
        // put it, so the step off the front is not reachable by pressing keys on purpose.
        assertEquals(1, stepTo(0, 1, 3))
        assertEquals(2, stepTo(1, 1, 3))
        // Off the end, round to the front.
        assertEquals(0, stepTo(2, 1, 3))
        // Off the front, round to the end. This is the one Kotlin's % gets wrong.
        assertEquals(2, stepTo(0, -1, 3))
        assertEquals(0, stepTo(1, -1, 3))
        // A single entry stays put, whichever way it is stepped.
        assertEquals(0, stepTo(0, 1, 1))
        assertEquals(0, stepTo(0, -1, 1))
        // And an empty cycle has nowhere to be but the start.
        assertEquals(0, stepTo(0, 1, 0))
    }
}
