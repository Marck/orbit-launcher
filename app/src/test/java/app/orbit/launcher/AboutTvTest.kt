package app.orbit.launcher

import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The About card, which says what is running on the television.
 *
 * It exists to answer one question: is the set running what the repository has. A version alone
 * cannot answer it, since every build between two releases carries the same one, so the commit
 * is the part that has to be there.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AboutTvTest : TvTest() {
    override val outDir = "build/tv-shots/about"

    @Before
    fun aboutFixture() {
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
        favorites(myApps, myInputs)
    }

    private fun shows(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun opened() = launch(HomeActivity::class.java).also { s ->
        toTopBar(s, "About")
        assertEquals("never reached the About button", "About", focused())
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor("the card to open", diagnose = { "focus: ${focused()}" }) { shows("Orbit") }
    }

    @Test
    fun `the card says the version, the channel and the commit`() {
        opened()
        assertTrue("no version on the card", shows(BuildInfo.version))
        assertTrue("no channel on the card", shows(BuildInfo.channel))
        assertTrue("no commit on the card", shows(BuildInfo.commit))
        shot("about")
    }

    @Test
    fun `the commit is a real one, not a placeholder`() {
        // Built outside a checkout there is nothing to record, and the card would then be
        // claiming to identify a build it cannot. Here it is built in one.
        assertEquals("a short sha is 12 hex characters", 12, BuildInfo.commit.length)
        assertTrue(
            "not a sha: ${BuildInfo.commit}",
            BuildInfo.commit.all { it.isDigit() || it in 'a'..'f' },
        )
    }

    @Test
    fun `a unit test build is a debug build, and says so`() {
        assertEquals("debug", BuildInfo.channel)
        assertTrue(BuildInfo.line().startsWith(BuildInfo.version))
    }

    @Test
    fun `any key closes it`() {
        // Nothing on the card can be acted on, so holding Back for it would be a trap: every
        // other key would appear to do nothing at all.
        val s = opened()
        press(s, KeyEvent.KEYCODE_DPAD_DOWN)
        waitFor("the card to close", diagnose = { "focus: ${focused()}" }) { !shows("Any key to close") }
        assertFalse(shows("Any key to close"))
    }

    @Test
    fun `closing it leaves focus on the button it was opened from`() {
        val s = opened()
        press(s, KeyEvent.KEYCODE_BACK)
        waitFor("focus to come back", diagnose = { "focus: ${focused()}" }) { focused() == "About" }
        assertEquals("About", focused())
    }
}
