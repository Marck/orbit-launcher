package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which ambient pictures are offered, and in what order.
 *
 * The choosing is separate from the reading precisely so it can be checked here, with no TV
 * provider behind it. The ordering matters on a real set: of its 1359 rows, 1197 carry a name
 * and nothing else, so a plain shuffle would show a bare title seven times in eight.
 */
class BackdropsPickTest {

    private fun picture(pkg: String, title: String, described: Boolean = false) =
        Picture(
            packageName = pkg,
            uri = "pic://$pkg/$title",
            title = title,
            seasons = null,
            description = if (described) "What $title is about." else null,
        )

    private val chosen = setOf("shop", "server")

    @Test
    fun `a picture that says what it is comes before one that does not`() {
        val found = listOf(
            picture("shop", "Bare one"),
            picture("shop", "Bare two"),
            picture("server", "Told", described = true),
            picture("shop", "Bare three"),
        )
        assertEquals("Told", Backdrops.pick(found, chosen, emptySet()).first().title)
    }

    @Test
    fun `the ones with nothing to say are kept on the end, not dropped`() {
        val found = listOf(picture("shop", "Bare"), picture("server", "Told", described = true))
        assertEquals(listOf("Told", "Bare"), Backdrops.pick(found, chosen, emptySet()).map { it.title })
    }

    @Test
    fun `an app chosen on its own still supplies its bare pictures`() {
        val found = listOf(picture("shop", "Bare one"), picture("shop", "Bare two"))
        assertEquals(2, Backdrops.pick(found, setOf("shop"), emptySet()).size)
    }

    @Test
    fun `only the chosen apps`() {
        val found = listOf(picture("shop", "Theirs"), picture("stranger", "Not chosen", described = true))
        assertEquals(listOf("Theirs"), Backdrops.pick(found, setOf("shop"), emptySet()).map { it.title })
    }

    @Test
    fun `nothing already waiting in Watch Next`() {
        val found = listOf(picture("server", "Call The Midwife", described = true), picture("shop", "Other"))
        val left = Backdrops.pick(found, chosen, setOf("  call the midwife "))
        assertEquals(listOf("Other"), left.map { it.title })
    }

    @Test
    fun `a picture with no title at all is still offered, since nothing can clash with it`() {
        val found = listOf(Picture("shop", "pic://shop/x", null, null, null))
        assertEquals(1, Backdrops.pick(found, setOf("shop"), setOf("anything")).size)
    }

    @Test
    fun `the pool is capped rather than read whole into memory`() {
        val found = (1..400).map { picture("shop", "Title $it", described = it % 2 == 0) }
        val pool = Backdrops.pick(found, setOf("shop"), emptySet())
        assertTrue("pool of ${pool.size} is not bounded below the 400 rows", pool.size < found.size)
        assertTrue("pool of ${pool.size} is bigger than the cap", pool.size <= 60)
        // Enough described rows exist to fill the cap, so none of the bare ones should be in it.
        assertFalse(pool.any { it.description.isNullOrBlank() })
    }

    @Test
    fun `the order is not the order they were read in`() {
        val found = (1..60).map { picture("shop", "Title $it", described = true) }
        val titles = Backdrops.pick(found, setOf("shop"), emptySet()).map { it.title }
        assertEquals(60, titles.size)
        assertFalse("read order came back unshuffled", titles == found.map { it.title })
    }
}
