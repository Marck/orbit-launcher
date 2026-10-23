package app.orbit.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The year and the running length an app puts on a preview row, and the line they make.
 *
 * Every row on this set carries a duration and nearly every one carries a release date, so this
 * is detail that is already there rather than anything asked of an app. What is not already
 * there is any promise about the shape of it, which is what these check: the three shapes the
 * TV contract allows for a date all have to work, and anything else has to be dropped rather
 * than put on the screen half parsed.
 */
class BackdropsDetailTest {

    @Test
    fun `the three date shapes the contract allows all give the year`() {
        assertEquals(2019, Backdrops.yearOf("2019"))
        assertEquals(2019, Backdrops.yearOf("2019-06-14"))
        assertEquals(2019, Backdrops.yearOf("2019-06-14T09:30:00Z"))
    }

    @Test
    fun `a date with nothing in it gives no year`() {
        assertNull(Backdrops.yearOf(null))
        assertNull(Backdrops.yearOf(""))
        assertNull(Backdrops.yearOf("   "))
    }

    @Test
    fun `something that is not a year is left out rather than shown`() {
        assertNull(Backdrops.yearOf("soon"))
        assertNull(Backdrops.yearOf("14-06-2019"))
        assertNull(Backdrops.yearOf("20"))
        // In range as digits, but no programme carries it.
        assertNull(Backdrops.yearOf("0007"))
    }

    @Test
    fun `a duration becomes whole minutes, rounded the way a listing reads it`() {
        assertEquals(109, Backdrops.minutesOf(109 * 60_000L))
        // 108 and a half, which a listing calls 109 rather than 108.
        assertEquals(109, Backdrops.minutesOf(108 * 60_000L + 31_000L))
        assertEquals(108, Backdrops.minutesOf(108 * 60_000L + 29_000L))
    }

    @Test
    fun `a duration that cannot be a running length is dropped`() {
        assertNull(Backdrops.minutesOf(null))
        assertNull(Backdrops.minutesOf(0L))
        // Seconds where milliseconds were meant: three seconds is not a programme.
        assertNull(Backdrops.minutesOf(3_000L))
        // Longer than half a day.
        assertNull(Backdrops.minutesOf(13 * 60 * 60_000L))
    }

    private fun picture(year: Int? = null, seasons: Int? = null, runtime: Int? = null) =
        Picture("server", "pic://x", "A Title", seasons, null, year, runtime)

    @Test
    fun `a film reads as its year and its running length`() {
        assertEquals("2014 - 1H 49M", detailOf(picture(year = 2014, runtime = 109)))
    }

    @Test
    fun `a running length under an hour drops the hours, and a whole one drops the minutes`() {
        assertEquals("44M", detailOf(picture(runtime = 44)))
        assertEquals("2H", detailOf(picture(runtime = 120)))
    }

    @Test
    fun `a series counts in seasons, and one of them is not said as 1 seasons`() {
        assertEquals("2019 - 3 SEASONS", detailOf(picture(year = 2019, seasons = 3)))
        assertEquals("1 SEASON", detailOf(picture(seasons = 1)))
    }

    @Test
    fun `a row with nothing to add gets no line at all`() {
        assertNull(detailOf(picture()))
    }
}
