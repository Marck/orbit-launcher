package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The same programme, drawn once.
 *
 * An app publishes a row into each of its own channels that a programme belongs to, so one film
 * in both "recently added" and "favourites" arrives twice. The poster row drew both, side by
 * side, and the copies also took places that something else could have had.
 *
 * The harder half of this is what must not be treated as a duplicate. An app will publish the
 * same artwork for two different things: two episodes of one series carry the series cover, and
 * a row keyed on the picture alone would hide half a series.
 */
class BackdropsDistinctTest {

    private fun picture(
        title: String?,
        art: String,
        way: String? = null,
        pkg: String = "server",
    ) = Picture(
        packageName = pkg,
        uri = art,
        title = title,
        seasons = null,
        description = null,
        intentUri = way,
    )

    @Test
    fun `a programme published into two channels is kept once`() {
        val found = listOf(
            picture("Pinocchio", "pic://server/pinocchio", way = "intent://server/item/1"),
            picture("Pinocchio", "pic://server/pinocchio", way = "intent://server/item/1"),
        )
        assertEquals(listOf("Pinocchio"), Backdrops.distinct(found).map { it.title })
    }

    @Test
    fun `two episodes sharing a series cover both stay`() {
        // The one thing this must never do. The cover is the series', the programmes are not.
        val found = listOf(
            picture("The Odyssey S1 E1", "pic://server/odyssey", way = "intent://server/item/1"),
            picture("The Odyssey S1 E2", "pic://server/odyssey", way = "intent://server/item/2"),
        )
        assertEquals(2, Backdrops.distinct(found).size)
    }

    @Test
    fun `two films of the same name with their own covers both stay`() {
        val found = listOf(
            picture("Pinocchio", "pic://server/pinocchio-1940", way = "intent://server/item/1"),
            picture("Pinocchio", "pic://server/pinocchio-2022", way = "intent://server/item/2"),
        )
        assertEquals(2, Backdrops.distinct(found).size)
    }

    @Test
    fun `a row pointing at the same thing is the same thing, whatever it calls itself`() {
        val found = listOf(
            picture("Pinocchio", "pic://server/a", way = "intent://server/item/1"),
            picture("Pinocchio (1940)", "pic://server/b", way = "intent://server/item/1"),
        )
        assertEquals(listOf("Pinocchio"), Backdrops.distinct(found).map { it.title })
    }

    @Test
    fun `a row with no way in is still only shown once`() {
        val found = listOf(
            picture("Pinocchio", "pic://server/pinocchio"),
            picture("Pinocchio", "pic://server/pinocchio"),
        )
        assertEquals(1, Backdrops.distinct(found).size)
    }

    @Test
    fun `a third copy cannot slip through on the test the second one failed`() {
        // The second copy is caught by its way in, the third only by its face. Both have to be
        // recorded even for a row that is dropped, or the third is let through.
        val found = listOf(
            picture("Pinocchio", "pic://server/pinocchio", way = "intent://server/item/1"),
            picture("Pinocchio", "pic://server/pinocchio", way = "intent://server/item/1"),
            picture("Pinocchio", "pic://server/pinocchio", way = "intent://server/item/9"),
        )
        assertEquals(1, Backdrops.distinct(found).size)
    }

    @Test
    fun `the first copy is the one that is kept`() {
        val found = listOf(
            picture("Kept", "pic://server/same", way = "intent://server/item/1"),
            picture("Dropped", "pic://server/other", way = "intent://server/item/1"),
        )
        assertEquals(listOf("Kept"), Backdrops.distinct(found).map { it.title })
    }

    @Test
    fun `duplicates do not eat places in the row`() {
        // They were counted before the cap, so a library with many copies filled the row with
        // fewer things than it had to show.
        val found = (1..3).flatMap { n ->
            listOf(
                picture("Film $n", "pic://server/$n", way = "intent://server/item/$n"),
                picture("Film $n", "pic://server/$n", way = "intent://server/item/$n"),
            )
        }
        val picked = Backdrops.pick(found, setOf("server"), emptySet())
        assertEquals(3, picked.size)
        assertEquals(3, picked.map { it.title }.toSet().size)
    }
}
