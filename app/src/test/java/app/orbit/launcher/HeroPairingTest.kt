package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which picture the hero names.
 *
 * The hero keeps the last bitmap on screen while the next one is being fetched, so the step the
 * cycle has reached and the artwork actually drawn are different things for as long as a fetch
 * takes. Naming the step put the new title over the old picture, which was visible on the set
 * on 24 October: "Da Vinci's Demons" over the artwork for something else entirely.
 */
class HeroPairingTest {

    private fun picture(name: String) =
        Picture(
            packageName = "server",
            uri = "pic://server/$name",
            title = name,
            seasons = null,
            description = "About $name.",
        )

    private val pool = listOf(picture("First"), picture("Second"), picture("Third"))

    @Test
    fun `the hero names the picture the artwork came back for, not the step the cycle is on`() {
        // The cycle has moved on to the third; the artwork on screen is still the second's.
        assertEquals("Second", pictureFor(pool, pool[1].uri)?.title)
    }

    @Test
    fun `nothing has been fetched yet, so there is nothing to name`() {
        assertNull(pictureFor(pool, null))
    }

    @Test
    fun `artwork from a pool that has since been replaced names nothing`() {
        // A reshuffle or a fresh read leaves the last fetch pointing at a row that is gone.
        // Showing no picture is right; showing one the artwork is not of is the bug.
        assertNull(pictureFor(pool, "pic://server/Gone"))
    }

    @Test
    fun `every picture in the pool can be named, whatever its position`() {
        assertEquals(
            pool.map { it.title },
            pool.map { pictureFor(pool, it.uri)?.title },
        )
    }
}
