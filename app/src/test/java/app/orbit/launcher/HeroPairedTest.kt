package app.orbit.launcher

import android.media.tv.TvContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The hero's words and its picture belong to the same thing.
 *
 * The backdrop keeps the last bitmap it decoded while the next one is fetched. Taking the title
 * from the step instead of from what came back put the new title over the previous picture for
 * as long as the fetch took, which on a cold row is long enough to read. The ambient pictures
 * have been paired this way since they were added; the rows were not.
 */
class HeroPairedTest {

    private fun row(title: String, poster: String?) = Resumable(
        packageName = "org.jellyfin.androidtv",
        title = title,
        subtitle = null,
        description = null,
        posterUri = poster,
        intentUri = "orbit://play/$title",
        progress = null,
        kind = Kind.CONTINUE,
        programType = TvContract.WatchNextPrograms.TYPE_TV_EPISODE,
    )

    private val one = row("Episode One", "pic://one")
    private val two = row("Episode Two", "pic://two")
    private val rows = listOf(one, two)

    @Test
    fun `while the next picture is still coming, the previous pair stays up`() {
        // The step has moved to two; the artwork on screen is still one's.
        assertSame(one, pairedWith(rows, stepped = two, fetched = "pic://one"))
    }

    @Test
    fun `once the picture lands, the step is what is shown`() {
        // Also the case for a row whose picture will not load: a fetch records what it was for
        // even when it comes back with nothing, so that row shows its own title over an empty
        // backdrop rather than the previous row's forever.
        assertSame(two, pairedWith(rows, stepped = two, fetched = "pic://two"))
    }

    @Test
    fun `a first load is not held back waiting for artwork`() {
        // Nothing has been fetched and there is no previous pair to keep showing, so holding
        // the title back would leave the hero blank rather than merely out of step.
        assertSame(one, pairedWith(rows, stepped = one, fetched = null))
    }

    @Test
    fun `artwork from the ambient pool leaves the row alone`() {
        // The hero falls back to an ambient picture's link when a row carries no poster, so
        // what came back is not always one of these rows.
        assertSame(two, pairedWith(rows, stepped = two, fetched = "pic://ambient/something"))
    }

    @Test
    fun `a row that has gone does not hold the hero`() {
        // The cursor moved to another app, so the list no longer holds what is on screen.
        assertSame(two, pairedWith(listOf(two), stepped = two, fetched = "pic://one"))
    }

    @Test
    fun `two episodes sharing one cover show the one the step reached`() {
        val first = row("Episode One", "pic://series")
        val second = row("Episode Two", "pic://series")
        assertSame(
            second,
            pairedWith(listOf(first, second), stepped = second, fetched = "pic://series"),
        )
    }

    @Test
    fun `a row with no poster at all is still shown`() {
        val bare = row("No Picture", null)
        assertSame(bare, pairedWith(listOf(bare), stepped = bare, fetched = null))
        assertSame(bare, pairedWith(listOf(bare), stepped = bare, fetched = "pic://elsewhere"))
    }

    @Test
    fun `no step means no row, which is what the hero checks to know it is free`() {
        // Everything downstream reads "item != null" as "the hero is busy with a row", so this
        // must stay null exactly when there is no step.
        assertNull(pairedWith(rows, stepped = null, fetched = "pic://one"))
        assertNull(pairedWith(emptyList(), stepped = null, fetched = null))
    }

    @Test
    fun `the pair never names a row that is not in the list`() {
        val paired = pairedWith(rows, stepped = two, fetched = "pic://one")
        assertEquals("the hero showed something that is not one of its rows", true, paired in rows)
    }
}
