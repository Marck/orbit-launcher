package app.orbit.launcher

import android.app.Application
import android.graphics.Bitmap
import android.media.tv.TvContract
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
 * different heights: 216px for an ambient picture with a title alone, 306px once that picture
 * had a year and a running length, and 400px for something to carry on with. Everything below
 * moved with it. The hero steps through pictures by itself, so the apps row walked up and down
 * the screen while nobody was touching the remote. Measured on the set before the fix, the top
 * of the first tile ranged over 61px between one picture and the next.
 *
 * Every case here asserts the same number, so a new kind of hero content that is taller or
 * shorter than the rest fails rather than quietly starting the drift again.
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
        // offer, which is how an earlier version of this test passed while measuring nothing.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
    }

    /** The hero's own box, in root pixels. */
    private fun heroHeight(): Float {
        waitFor("the hero") {
            rule.onAllNodes(hasTestTag(HeroTag), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        return rule.onAllNodes(hasTestTag(HeroTag), useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.height
    }

    /** What [HeroH] is in the pixels this screen is measured in. */
    private fun expected(): Float = with(rule.density) { HeroH.toPx() }

    private fun assertFixed() =
        assertEquals("the hero is not the height it reserves", expected(), heroHeight(), 0.5f)

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

    private fun showWatching(item: Resumable) {
        ShotHooks.watchNext = listOf(item)
        ShotHooks.pictures = emptyList()
        launch(HomeActivity::class.java)
    }

    private fun showAmbient(title: String?, seasons: Int?, description: String?, year: Int?, runtime: Int?) {
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
        showAmbient("Short", null, null, null, null)
        assertFixed()
    }

    @Test
    fun `an ambient picture whose title wraps to a second line`() {
        showAmbient(longTitle, null, null, null, null)
        assertFixed()
    }

    @Test
    fun `an ambient picture with a year and a running length, which used to be taller`() {
        showAmbient(longTitle, 3, longDesc, 2019, 124)
        assertFixed()
    }

    @Test
    fun `something to carry on with, at its shortest`() {
        showWatching(watching("Short", null, null, null))
        assertFixed()
    }

    @Test
    fun `something to carry on with, with an episode line and a progress bar`() {
        showWatching(watching(longTitle, "Season 12 Episode 14", longDesc, Progress(0.4f, "24 MIN WATCHED, 36 LEFT")))
        assertFixed()
    }

    @Test
    fun `the clock, which is what a bare screen shows`() {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        launch(HomeActivity::class.java)
        assertFixed()
    }

    @Test
    fun `the tallest hero is not clipped by the room it is given`() {
        // A fixed height that was too small would hold the rows still by cutting the text off,
        // which is a worse fault than the one being fixed.
        showWatching(watching(longTitle, "Season 12 Episode 14", longDesc, Progress(0.4f, "24 MIN WATCHED, 36 LEFT")))
        for (text in listOf("SEASON 12 EPISODE 14", "24 MIN WATCHED", "A long sentence about")) {
            assertTrue(
                "clipped out of the tallest hero: $text",
                rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
        shot("tallest")
    }
}
