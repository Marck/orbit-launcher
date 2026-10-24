package app.orbit.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What [TvTest.waitFor]'s budget is actually worth.
 *
 * Everything it waits for runs on a real thread: a provider read, a file scan, a decode. So
 * the budget has to be real time, or it promises one thing on a laptop and delivers a
 * fraction of it on a loaded build runner, which is how N7 kept coming back.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class WaitBudgetTest : TvTest() {

    @Test
    fun `a budget buys that much real time, not that much pumped time`() {
        val budget = 2_000L
        val start = System.nanoTime()
        // Never holds, so the whole budget is spent and the check at the end fails.
        runCatching { waitFor("something that never happens", budgetMs = budget) { false } }
        val real = (System.nanoTime() - start) / 1_000_000

        // A ceiling would pass on any machine. What matters is the floor: the thing being
        // waited for has to get at least the time the caller asked to give it.
        assertTrue(
            "a ${budget}ms budget gave the background work only ${real}ms of real time",
            real >= budget,
        )
    }

    @Test
    fun `a wait that runs out says what the screen was showing`() {
        val thrown = runCatching {
            waitFor(
                "something that never happens",
                budgetMs = 100,
                diagnose = { "under YOUR PICTURES: [Looking for pictures...]" },
            ) { false }
        }.exceptionOrNull()

        // Without this the only record of a CI timeout is that it timed out, which is what
        // left N7 undiagnosable the first time round.
        assertTrue("the wait should have failed", thrown != null)
        assertTrue(
            "no diagnosis in the failure: ${thrown?.message}",
            thrown?.message?.contains("Looking for pictures") == true,
        )
    }

    @Test
    fun `a diagnosis that throws does not replace the failure with its own`() {
        val thrown = runCatching {
            waitFor("something that never happens", budgetMs = 100, diagnose = { error("boom") }) { false }
        }.exceptionOrNull()

        assertTrue("the wait should still report what it waited for", thrown?.message?.contains("something that never happens") == true)
        assertTrue("and should say the diagnosis failed", thrown?.message?.contains("diagnose threw") == true)
    }
}
