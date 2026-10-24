package app.orbit.launcher

import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.getOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Why every screenshot used to differ from the last one.
 *
 * Two recordings of the same unchanged code disagreed on 47 of 79 shots. The pixels that moved
 * were a twelve by nineteen box in the top right corner: the minute the run happened to start
 * in. The date underneath it rolls over nightly on top of that. Neither says anything about
 * the layout, and between them they were almost the whole of the difference.
 *
 * So both clocks read [TvTest.PINNED_CLOCK] under test. What is locked in here is that they
 * honour it, because a clock that goes back to reading the real time would put the noise back
 * without failing anything else.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SteadyClockTest : TvTest() {
    override val outDir = "build/tv-shots/steady"

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    /**
     * How the pinned instant reads, in both shapes a clock here can take.
     *
     * Computed rather than written out: the top bar follows the TV's twelve or twenty four
     * hour setting, and a literal would be asserting what this machine happens to be set to.
     * Either way it is 03:04 or 3:04 and never the time the test actually ran at.
     */
    private fun pinnedTimes(): List<String> = listOf("HH:mm", "h:mm").map {
        SimpleDateFormat(it, Locale.getDefault()).format(Date(PINNED_CLOCK))
    }

    /** Everything the screen says, for when a wait times out. */
    private fun shown(): String =
        rule.onAllNodes(androidx.compose.ui.test.isRoot().not(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                    ?.joinToString(" ")
            }
            .joinToString(" | ")
            .take(400)

    private fun saysThePinnedTime(where: String) {
        val wanted = pinnedTimes()
        waitFor("the pinned time in the $where", diagnose = { "on screen: ${shown()}" }) {
            wanted.any { onScreen(it) }
        }
        assertTrue("none of $wanted on screen: ${shown()}", wanted.any { onScreen(it) })
    }

    @Test
    fun `the top bar clock reads the pinned time, not the real one`() {
        launch(HomeActivity::class.java)
        saysThePinnedTime("top bar")
    }

    @Test
    fun `the hero clock reads it too, which is the one a bare screen shows`() {
        // The default fixture already leaves the hero with nothing to carry on with and no
        // pictures, which is the state where it falls back to its own clock.
        launch(HomeActivity::class.java)
        saysThePinnedTime("hero")
    }

    @Test
    fun `the date is pinned too, since it rolls over nightly when it is not`() {
        launch(HomeActivity::class.java)
        val day = SimpleDateFormat("d", Locale.getDefault()).format(Date(PINNED_CLOCK))
        val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(PINNED_CLOCK))
        waitFor("the pinned date", diagnose = { "on screen: ${shown()}" }) {
            onScreen(month) && onScreen(day)
        }
        assertTrue("month $month missing: ${shown()}", onScreen(month))
    }

    @Test
    fun `without the hook the clocks follow the real time`() {
        // The hook is a test seam, not a behaviour: a real build must never show 2024.
        ShotHooks.now = null
        val before = System.currentTimeMillis()
        val reading = reading().time
        val after = System.currentTimeMillis()
        assertTrue("reading $reading outside [$before, $after]", reading in before..after)
    }

    @Test
    fun `the pinned instant is the one the comment claims`() {
        // Asserts the relationship rather than a formatted string, so a machine in another
        // zone does not fail here while the shots stay correct.
        val utc = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        assertTrue(
            "pinned instant is not 2024-01-02 03:04:00 UTC",
            utc.format(java.util.Date(PINNED_CLOCK)) == "2024-01-02 03:04:00",
        )
    }
}
