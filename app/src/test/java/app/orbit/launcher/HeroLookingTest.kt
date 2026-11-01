package app.orbit.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the hero is waiting on a picture, as against having none to wait for.
 *
 * The difference decides whether the screen keeps quiet for a moment or says there is nothing
 * at all, and getting it wrong is what put the clock between two backdrops.
 */
class HeroLookingTest {

    @Test
    fun `before the rows have been read, anything could still be coming`() {
        assertTrue(stillLooking(rowsRead = false, wanted = null, fetched = null))
    }

    @Test
    fun `with no link wanted there is nothing to wait for`() {
        assertFalse(stillLooking(rowsRead = true, wanted = null, fetched = null))
    }

    @Test
    fun `a link that has not come back yet is still being waited for`() {
        assertTrue(stillLooking(rowsRead = true, wanted = "pic://wanted", fetched = null))
    }

    @Test
    fun `the answer for the link before this one does not count as this one`() {
        // The regression. A bitmap was in hand, so the old test read it as loaded, and the
        // hero fell through to the clock with the previous picture still behind it.
        assertTrue(stillLooking(rowsRead = true, wanted = "pic://wanted", fetched = "pic://before"))
    }

    @Test
    fun `once the link wanted is the one that came back, the waiting is over`() {
        assertFalse(stillLooking(rowsRead = true, wanted = "pic://wanted", fetched = "pic://wanted"))
    }

    @Test
    fun `a link that came back with nothing has still come back`() {
        // Nothing to show, and that is an answer: the hero offers the way back rather than
        // waiting out the clock on a picture that will never arrive.
        assertFalse(stillLooking(rowsRead = true, wanted = "pic://gone", fetched = "pic://gone"))
    }
}
