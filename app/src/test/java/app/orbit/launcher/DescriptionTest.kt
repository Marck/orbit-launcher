package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of a row's two descriptions the hero shows.
 *
 * The words were being lost before Orbit ever laid them out. The TV provider caps a short
 * description at 150 characters and cuts mid word to get there, and that is the only column
 * Orbit read, so every attempt to fix the hero's own trimming was aimed at the wrong thing.
 */
class DescriptionTest {

    /** Netflix's row, exactly as it arrives: 150 characters, stopped part way through "out". */
    private val capped =
        "After inheriting a hotel from her late brother, a single mom moves in with his " +
            "good-natured ghost — and high-maintenance guests who will never check o"

    @Test
    fun `the row that arrives cut is a real one, and really is at the cap`() {
        // The fixture is the evidence for the rule, so it is checked rather than trusted.
        assertEquals(ShortDescriptionCap, capped.length)
        assertTrue("the fixture does not end part way through a word", capped.endsWith(" check o"))
    }

    @Test
    fun `a description stopped at the cap loses the half word, not the meaning`() {
        val shown = bestDescription(long = null, short = capped)!!

        assertFalse("the half word is still there: $shown", shown.endsWith("check o"))
        assertTrue("it does not say it was cut: $shown", shown.endsWith("…"))
        assertTrue("it lost more than the half word: $shown", shown.startsWith("After inheriting a hotel"))
        // Cut back to the last whole word, which is "check", and no further.
        assertTrue("it did not stop at a whole word: $shown", shown.endsWith("check…"))
        // What is left is the app's own words, unchanged up to where they were cut.
        assertTrue("the text was altered, not just shortened", capped.startsWith(shown.removeSuffix("…")))
    }

    @Test
    fun `the long description wins, because it is the one nothing truncated`() {
        val full = capped.dropLast(1) + "out, which is the whole sentence the short one could not hold."
        assertEquals(full, bestDescription(long = full, short = capped))
    }

    @Test
    fun `a short description is kept when the app published no long one`() {
        val short = "A short line that finished on its own."
        assertEquals(short, bestDescription(long = null, short = short))
        assertEquals(short, bestDescription(long = "   ", short = short))
    }

    @Test
    fun `a description that stopped on its own is left alone`() {
        // At the cap, but the app ended the sentence itself, so nothing was taken from it.
        val ended = "x".repeat(ShortDescriptionCap - 1) + "."
        assertEquals(ShortDescriptionCap, ended.length)
        assertEquals(ended, bestDescription(long = null, short = ended))
    }

    @Test
    fun `a description under the cap is never touched`() {
        val under = "y".repeat(ShortDescriptionCap - 1)
        assertEquals(under, bestDescription(long = null, short = under))
    }

    @Test
    fun `a row with no description at all stays without one`() {
        assertNull(bestDescription(long = null, short = null))
        assertNull(bestDescription(long = "", short = "   "))
    }

    @Test
    fun `a shorter long description does not replace a fuller short one`() {
        // Some apps put a one line tagline in the long column. The fuller text wins either way.
        val short = "The full two sentence summary that the app put in the short column instead."
        assertEquals(short, bestDescription(long = "A tagline.", short = short))
    }

    @Test
    fun `an app cannot hand the hero a novel`() {
        // Nothing caps the long column, and Compose measures the whole string on every layout
        // pass whatever is drawn of it. The hero's own row is measured more than once a frame.
        val novel = (1..400).joinToString(" ") { "word$it" }
        val shown = bestDescription(long = novel, short = null)!!

        assertTrue("it was not held at all: ${shown.length}", shown.length <= DescriptionCeiling + 1)
        assertTrue("it does not say it was cut", shown.endsWith("…"))
        assertTrue("it was cut part way through a word: ...${shown.takeLast(12)}", novel.startsWith(shown.removeSuffix("…")))
    }

    @Test
    fun `the ceiling is clear of anything real`() {
        // 348 characters is the longest description measured on this set. A ceiling below what
        // apps actually publish would be a new truncation bug rather than a guard against one.
        val longest = 348
        assertTrue("the ceiling would cut real rows", DescriptionCeiling > longest)
        assertTrue("the title ceiling is below a real title", TitleCeiling > 64)

        val real = "x".repeat(longest)
        assertEquals("a real length description was held back", real, bestDescription(long = real, short = null))
    }

    @Test
    fun `a title an app made absurd is held too`() {
        val absurd = (1..100).joinToString(" ") { "part$it" }
        val held = bounded(absurd, TitleCeiling)

        assertTrue("the title was not held: ${held.length}", held.length <= TitleCeiling + 1)
        assertTrue(held.endsWith("…"))
        // An ordinary title is passed through as it was written.
        assertEquals("Monster: The Lizzie Borden Story", bounded("Monster: The Lizzie Borden Story", TitleCeiling))
    }

    @Test
    fun `one very long word is still held, even with nowhere to cut`() {
        // trimToWord gives up when there is no earlier word boundary, so the ceiling has to
        // hold on its own or the guard would pass the whole string through.
        val oneWord = "z".repeat(DescriptionCeiling * 3)
        val held = bounded(oneWord, DescriptionCeiling)

        assertTrue("a single word walked past the ceiling: ${held.length}", held.length <= DescriptionCeiling + 1)
        assertTrue(held.endsWith("…"))
    }
}
