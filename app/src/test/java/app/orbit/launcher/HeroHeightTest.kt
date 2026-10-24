package app.orbit.launcher

import android.app.Application
import android.graphics.Bitmap
import android.media.tv.TvContract
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero takes the same room whatever it is showing, so nothing below it moves.
 *
 * It used to be as tall as its own text with only a floor under it, and so stood at three
 * heights: 216px for an ambient picture with a title alone, 306px once that picture had a year
 * and a running length on it, and 400px for something to carry on with. Everything below moved
 * with it, and the hero steps through pictures by itself, so the apps row walked up and down
 * the screen while nobody was touching the remote. Measured on the set before the fix, the top
 * of the first tile ranged over 61px between one picture and the next.
 *
 * Every case asserts the same box, top and bottom, so a new kind of hero content that is
 * taller or shorter than the rest fails here rather than quietly starting the drift again.
 *
 * Every case also asserts that what it meant to show is actually on the screen. Without that
 * this file passed while measuring nothing at all: the hero only shows a row published by the
 * app under the cursor, so with the wrong app focused it fell back to its clock, and a clock
 * in a fixed box is the right height for the wrong reason.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class HeroHeightTest : TvTest() {
    override val outDir = "build/tv-shots/hero-height"

    private val server = "org.jellyfin.androidtv"
    private val source = "com.netflix.ninja"

    private val longTitle =
        "A Programme With A Name Far Longer Than The Hero Is Wide So It Has To Wrap"
    private val longDesc =
        "A long sentence about the episode, of the kind the publishing app supplies, which " +
            "runs to more than one line across the width the hero is given and has to be cut " +
            "short rather than run on across the whole screen for ever and ever."

    @Before
    fun grantListings() {
        // Without it the rows are never read and every case would be the same permission
        // offer. ContinueWatching.all checks the permission before it reads the test hook.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
    }

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun heroBox(): Rect {
        waitFor("the hero") {
            rule.onAllNodes(hasTestTag(HeroTag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return rule.onAllNodes(hasTestTag(HeroTag), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
    }

    /**
     * The hero fills exactly the room it reserves, and starts where it always starts.
     *
     * [showing] is a piece of text that proves the case rendered what it meant to: a box of
     * the right size around the wrong contents would otherwise pass.
     */
    private fun assertFixed(showing: String) {
        waitFor("\"$showing\" in the hero") { onScreen(showing) }
        assertTrue("the hero never showed $showing, so nothing was measured", onScreen(showing))
        val box = heroBox()
        assertEquals("the hero is not the height it reserves", with(rule.density) { HeroH.toPx() }, box.height, 0.5f)
        assertEquals("the hero does not start where it always starts", HeroTop, box.top, 0.5f)
    }

    private fun watching(title: String, subtitle: String?, description: String?, progress: Progress?) =
        Resumable(
            packageName = server,
            title = title,
            subtitle = subtitle,
            description = description,
            posterUri = null,
            intentUri = "orbit://play",
            progress = progress,
            kind = Kind.CONTINUE,
            programType = TvContract.WatchNextPrograms.TYPE_TV_EPISODE,
        )

    /** The hero only shows a row published by the app under the cursor. */
    private fun showWatching(item: Resumable) {
        ShotHooks.watchNext = listOf(item)
        ShotHooks.pictures = emptyList()
        favorites(listOf(server) + myApps.filter { it != server }, myInputs)
        launch(HomeActivity::class.java)
    }

    private fun showAmbient(title: String, seasons: Int?, description: String?, year: Int?, runtime: Int?) {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = listOf(
            Picture(source, "pic://ambient", title, seasons, description, year, runtime)
        )
        ShotHooks.artwork = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        Backdrops.toggle(ApplicationProvider.getApplicationContext(), source)
        launch(HomeActivity::class.java)
    }

    @Test
    fun `an ambient picture with a title alone`() {
        showAmbient("A Short One", null, null, null, null)
        assertFixed("A Short One")
    }

    @Test
    fun `an ambient picture whose title wraps to a second line`() {
        showAmbient(longTitle, null, null, null, null)
        assertFixed("A Programme With A Name Far Longer")
    }

    @Test
    fun `an ambient picture with a year and a running length, which used to be taller`() {
        showAmbient(longTitle, 3, longDesc, 2019, 124)
        assertFixed("A long sentence about the episode")
    }

    @Test
    fun `something to carry on with, at its shortest`() {
        showWatching(watching("A Short One", null, null, null))
        assertFixed("A Short One")
    }

    @Test
    fun `something to carry on with, with an episode line and a progress bar`() {
        showWatching(
            watching(longTitle, "Season 12 Episode 14", longDesc, Progress(0.4f, "24 MIN WATCHED, 36 LEFT"))
        )
        assertFixed("SEASON 12 EPISODE 14")
    }

    @Test
    fun `the clock, which is what a bare screen shows`() {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        launch(HomeActivity::class.java)
        // The pinned screenshot clock, so this is the clock hero and not something else.
        assertFixed("03:04")
    }

    @Test
    fun `the tallest hero is not clipped by the room it is given`() {
        // A fixed height that was too small would hold the rows still by cutting the text off,
        // which is a worse fault than the one being fixed. Measured: the tallest content runs
        // from the label at the top of the box to the bar under the detail line, and ends
        // about 36dp clear of the bottom.
        showWatching(
            watching(longTitle, "Season 12 Episode 14", longDesc, Progress(0.4f, "24 MIN WATCHED, 36 LEFT"))
        )
        val box = heroBox()
        for (text in listOf("CONTINUE WATCHING", "A Programme With A Name Far Longer", "A long sentence about the episode", "SEASON 12 EPISODE 14")) {
            val nodes = rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue("missing from the tallest hero: $text", nodes.isNotEmpty())
            val bottom = nodes.first().boundsInRoot.bottom
            assertTrue(
                "\"$text\" runs past the bottom of the hero: $bottom > ${box.bottom}",
                bottom <= box.bottom,
            )
        }
        shot("tallest")
    }

    private companion object {
        /**
         * Where the hero starts, in root pixels.
         *
         * Set by the safe area, the top bar and the gap under it, none of which depend on what
         * the hero has to say. Written down so a change to any of them is noticed here too.
         */
        const val HeroTop = 116f
    }
}
