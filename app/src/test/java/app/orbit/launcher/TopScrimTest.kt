package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fade behind the top bar, and the one thing it must not do: end while still dark.
 *
 * It is there because the hero's artwork reaches the top of the screen and a pale frame up
 * there took the clock and the glyphs with it. The first version was a straight line, 0.75 at
 * the top through 0.3 to nothing over 72dp. Measured on the set over a bright picture the
 * luminance climbed from 7 to 43 down the bar and then flattened, at 144px, which is exactly
 * 72dp. The owner saw that flattening as a black panel behind the icons, and only ever on pale
 * artwork, because over a dark picture there is nothing for it to be darker than.
 *
 * A straight fade is the problem. It loses the same darkness in every step, including the last
 * one, so it arrives at nothing still moving and the place it arrives is a line. A fade that
 * tapers, losing less in each step than in the one before, is too faint to find by the time it
 * ends and has no edge to see.
 *
 * So what is held here is the shape, not the numbers. The last test does the real work: it
 * samples the fade this replaced and shows it fails the rule, which is the only thing that
 * makes the other tests worth anything.
 */
class TopScrimTest {

    /** The stops read off a straight fade from 0.75 through 0.3 at 60% to nothing. */
    private val straight: List<Float> =
        List(ScrimStops.size) { i ->
            val at = i / (ScrimStops.size - 1f)
            if (at <= 0.6f) 0.75f + (at / 0.6f) * (0.3f - 0.75f) else 0.3f + ((at - 0.6f) / 0.4f) * -0.3f
        }

    private fun steps(stops: List<Float>) = stops.zipWithNext { above, below -> above - below }

    @Test
    fun `the fade only ever gets lighter`() {
        steps(ScrimStops).forEach {
            assertTrue("the fade got darker further down: $ScrimStops", it > 0f)
        }
    }

    @Test
    fun `the fade ends at nothing, so there is no darkness left to drop`() {
        assertEquals(
            "the fade stops while still dark, which is the line the owner saw",
            0f,
            ScrimStops.last(),
            0f,
        )
    }

    @Test
    fun `the fade tapers, losing less in every step than in the one before`() {
        steps(ScrimStops).zipWithNext { earlier, later ->
            assertTrue(
                "the fade does not taper: a step of $later follows one of $earlier in $ScrimStops",
                later < earlier,
            )
        }
    }

    @Test
    fun `the straight fade this replaced fails that rule`() {
        // Without this, the test above is only a description of whatever is in the file.
        val same = steps(straight).zipWithNext().count { (earlier, later) -> later >= earlier }
        assertTrue(
            "the rule is too loose to have caught the straight fade: ${steps(straight)}",
            same > 0,
        )
    }

    @Test
    fun `the straight fade would have passed the other two rules`() {
        // Which is why neither of them is the one that matters: the fault was never that the
        // fade got darker or stopped above zero. It was the shape of the way down.
        assertTrue("sanity: the straight fade descends", steps(straight).all { it > 0f })
        assertEquals("sanity: the straight fade reaches nothing", 0f, straight.last(), 1e-6f)
    }
}
