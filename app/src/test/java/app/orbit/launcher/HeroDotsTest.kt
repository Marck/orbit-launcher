package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dots under the hero, apart from the screen: how many, which way and how big. */
class HeroDotsTest {

    @Test
    fun `one dot per hero, up to the window`() {
        for (count in 2..DotsShown) {
            val (left, right) = dotSpan(count)
            assertEquals("dots for $count heroes", count, left + right + 1)
        }
        // A single hero is not a cycle, and has none.
        assertEquals(0 to 0, dotSpan(1))
        assertEquals(0 to 0, dotSpan(0))
    }

    @Test
    fun `a long list is a window with the one on screen in the middle`() {
        assertEquals(2 to 2, dotSpan(5))
        assertEquals(2 to 2, dotSpan(Backdrops.MAX_PICTURES))
    }

    @Test
    fun `an even count puts the spare dot on the side the cycle runs to`() {
        assertEquals(0 to 1, dotSpan(2))
        assertEquals(1 to 2, dotSpan(4))
    }

    @Test
    fun `the strip slides with the step, both ways and round the ends`() {
        assertEquals(1, slideOf(0, 1, 3))
        assertEquals(1, slideOf(2, 0, 3))
        assertEquals(-1, slideOf(1, 0, 3))
        assertEquals(-1, slideOf(0, 2, 3))
        // Not one step: a new list, or a jump. It settles without sliding.
        assertEquals(0, slideOf(0, 2, 5))
        assertEquals(0, slideOf(1, 1, 3))
        assertEquals(0, slideOf(0, 0, 1))
    }

    @Test
    fun `the middle dot is the biggest and they shrink outwards`() {
        val sizes = (0..2).map { dotDiameter(it.toFloat()) }
        assertTrue("not shrinking outwards: $sizes", sizes[0] > sizes[1] && sizes[1] > sizes[2])
        // Symmetric, and no smaller than the outermost further out, for the one fading away.
        assertEquals(dotDiameter(1f), dotDiameter(-1f), 0f)
        assertEquals(dotDiameter(2f), dotDiameter(3f), 0f)
        // Eased between the steps as the strip slides, never outside them.
        val half = dotDiameter(0.5f)
        assertTrue("$half is not between ${sizes[1]} and ${sizes[0]}", half < sizes[0] && half > sizes[1])
    }

    @Test
    fun `only the dot sliding out of the window fades`() {
        assertEquals(1f, dotAlpha(-2f, 2, 2), 0f)
        assertEquals(1f, dotAlpha(2f, 2, 2), 0f)
        assertEquals(0.5f, dotAlpha(2.5f, 2, 2), 0.001f)
        assertEquals(0f, dotAlpha(-3f, 2, 2), 0f)
        assertEquals(0f, dotAlpha(4f, 2, 2), 0f)
    }
}
