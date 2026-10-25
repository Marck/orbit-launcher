package app.orbit.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home button service relaunches Orbit whenever the stock launcher appears, and has to stop
 * doing that if Orbit cannot come up. These pin which of the two look-alike bursts it stands
 * down for.
 */
class TakeoverTest {

    /** Orbit reached the foreground after every takeover, as it does when it works. */
    private class Landing {
        var at = 0L
        val takeover = Takeover { since -> at > since }

        fun press(now: Long): Boolean = takeover.claim(now).also { if (it) at = now + 1 }
    }

    @Test
    fun `presses that land never stand down, however many there are`() {
        val landing = Landing()
        val spacing = Takeover.DEBOUNCE_MS + 1
        // Comfortably more presses, and faster, than the burst window would ever tolerate if it
        // counted presses rather than failures.
        val presses = Takeover.MAX_FAILURES * 3
        assertTrue(presses * spacing < Takeover.BURST_WINDOW_MS)

        repeat(presses) { i ->
            assertTrue("press ${i + 1} was refused", landing.press((i + 1) * spacing))
        }
    }

    @Test
    fun `a launcher that keeps returning is left alone after the allowed failures`() {
        // Nothing ever reaches the foreground, so no takeover lands.
        val takeover = Takeover { false }
        val spacing = Takeover.DEBOUNCE_MS + 1

        var performed = 0
        var standDown = -1L
        for (i in 1..Takeover.MAX_FAILURES + 1) {
            val now = i * spacing
            if (takeover.claim(now)) performed++ else standDown = now
        }

        assertTrue("never stood down", standDown >= 0)
        // Whether a takeover landed is only known from what follows it, so the first claim has
        // no predecessor to judge and each later one books the one before: the allowed failures
        // are reached on the claim after them.
        assertTrue("stood down after $performed takeovers", performed == Takeover.MAX_FAILURES)
    }

    @Test
    fun `the launcher is answered again once the stand down has passed`() {
        val takeover = Takeover { false }
        val spacing = Takeover.DEBOUNCE_MS + 1
        var standDown = 0L
        for (i in 1..Takeover.MAX_FAILURES + 1) {
            val now = i * spacing
            if (!takeover.claim(now)) standDown = now
        }

        assertFalse(takeover.claim(standDown + Takeover.STAND_DOWN_MS - 1))
        assertTrue(takeover.claim(standDown + Takeover.STAND_DOWN_MS))
    }

    @Test
    fun `several windows from one press count as one`() {
        val landing = Landing()
        assertTrue(landing.press(1_000))
        assertFalse(landing.press(1_000 + Takeover.DEBOUNCE_MS - 1))
        assertTrue(landing.press(1_000 + Takeover.DEBOUNCE_MS))
    }

    @Test
    fun `failures stop counting once they are older than the window`() {
        // Nothing lands, so every takeover fails, but they are spread far enough apart that no
        // two are ever counted together.
        val takeover = Takeover { false }
        var now = 1_000L
        repeat(Takeover.MAX_FAILURES + 2) { i ->
            assertTrue("claim ${i + 1} was refused", takeover.claim(now))
            now += Takeover.BURST_WINDOW_MS + 1
        }
    }
}
