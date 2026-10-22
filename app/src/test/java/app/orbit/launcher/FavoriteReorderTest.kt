package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FavoriteReorderTest {

    private val stored = listOf("a", "b", "c", "d")

    private fun reorder(id: String, delta: Int, shown: Collection<String> = stored) =
        FavoriteStore.reorder(stored, id, delta, shown)

    @Test
    fun movesOneRight() {
        assertEquals(listOf("a", "c", "b", "d"), reorder("b", 1))
    }

    @Test
    fun movesOneLeft() {
        assertEquals(listOf("a", "c", "b", "d"), reorder("c", -1))
    }

    @Test
    fun movesSeveralPlaces() {
        assertEquals(listOf("b", "c", "a", "d"), reorder("a", 2))
        assertEquals(listOf("d", "a", "b", "c"), reorder("d", -3))
    }

    @Test
    fun movesTheFirstAndLastInward() {
        assertEquals(listOf("b", "a", "c", "d"), reorder("a", 1))
        assertEquals(listOf("a", "b", "d", "c"), reorder("d", -1))
    }

    @Test
    fun staysPutAtEitherEnd() {
        assertSame(stored, reorder("a", -1))
        assertSame(stored, reorder("d", 1))
    }

    @Test
    fun stopsAtTheEndWhenMovedPastIt() {
        assertEquals(listOf("a", "b", "d", "c"), reorder("c", 5))
        assertEquals(listOf("b", "a", "c", "d"), reorder("b", -5))
    }

    @Test
    fun stepsOverHiddenFavorites() {
        val shown = setOf("a", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), reorder("a", 1, shown))
        assertEquals(listOf("c", "a", "b", "d"), reorder("c", -1, shown))
    }

    @Test
    fun stepsOverSeveralHiddenFavoritesInARow() {
        assertEquals(listOf("b", "c", "d", "a"), reorder("a", 1, setOf("a", "d")))
    }

    @Test
    fun staysPutWhenOnlyHiddenFavoritesLieAhead() {
        assertSame(stored, reorder("b", 1, setOf("a", "b")))
        assertSame(stored, reorder("c", -1, setOf("c", "d")))
    }

    @Test
    fun leavesTheListAloneForAnUnknownId() {
        assertSame(stored, reorder("x", 1))
        assertSame(stored, reorder("x", -1))
    }

    @Test
    fun leavesTheListAloneForNoMove() {
        assertSame(stored, reorder("b", 0))
    }
}
