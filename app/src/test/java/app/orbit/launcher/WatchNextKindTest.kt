package app.orbit.launcher

import android.media.tv.TvContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the heading over the hero is allowed to claim.
 *
 * Watch Next has four kinds of row and only one of them was watched. The set this was written
 * for publishes two rows and both say NEXT, so until this was read every row was headed
 * "continue watching" over a programme nobody had started. The rule being locked in is that
 * the heading repeats what the app said, and that a row saying nothing still reads the way it
 * always did.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WatchNextKindTest {

    private fun row(kind: Kind, type: Int?) = Resumable(
        packageName = "server",
        title = "Something",
        subtitle = null,
        description = null,
        posterUri = null,
        intentUri = "intent://x",
        progress = null,
        kind = kind,
        programType = type,
    )

    @Test
    fun `a started programme is the only one that says continue watching`() {
        assertEquals(
            "CONTINUE WATCHING",
            row(Kind.CONTINUE, TvContract.WatchNextPrograms.TYPE_TV_EPISODE).heading,
        )
    }

    @Test
    fun `the episode after the last one is not something to continue`() {
        assertEquals(
            "NEXT EPISODE",
            row(Kind.NEXT, TvContract.WatchNextPrograms.TYPE_TV_EPISODE).heading,
        )
    }

    @Test
    fun `a film marked as the next thing is not called an episode`() {
        assertEquals("NEXT UP", row(Kind.NEXT, TvContract.WatchNextPrograms.TYPE_MOVIE).heading)
    }

    @Test
    fun `newly out reads as new, and only says episode when it is one`() {
        assertEquals("NEW EPISODE", row(Kind.NEW, TvContract.WatchNextPrograms.TYPE_TV_SERIES).heading)
        assertEquals("NEW", row(Kind.NEW, TvContract.WatchNextPrograms.TYPE_MOVIE).heading)
    }

    @Test
    fun `something only ever put aside says so`() {
        assertEquals(
            "SAVED FOR LATER",
            row(Kind.WATCHLIST, TvContract.WatchNextPrograms.TYPE_MOVIE).heading,
        )
    }

    @Test
    fun `a row with no type on it is never called an episode`() {
        assertEquals("NEXT UP", row(Kind.NEXT, null).heading)
        assertEquals("NEW", row(Kind.NEW, null).heading)
    }

    @Test
    fun `the column maps onto the contract, and anything else stays as it was`() {
        assertEquals(Kind.CONTINUE, Kind.of(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE))
        assertEquals(Kind.NEXT, Kind.of(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT))
        assertEquals(Kind.NEW, Kind.of(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW))
        assertEquals(Kind.WATCHLIST, Kind.of(TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST))
    }

    @Test
    fun `a row that names no kind reads the way every row used to`() {
        // The whole list was headed this way before the column was read, so a provider that
        // does not have it must not change what the owner sees.
        assertEquals(Kind.CONTINUE, Kind.of(null))
        assertEquals(Kind.CONTINUE, Kind.of(99))
        assertEquals("CONTINUE WATCHING", Kind.of(null).heading(null))
    }

    @Test
    fun `a kind is never silently dropped`() {
        // Every kind has to produce something, so adding one to the contract fails here rather
        // than quietly heading a new kind of row as something it is not.
        val headings = Kind.entries.map { it.heading(TvContract.WatchNextPrograms.TYPE_TV_EPISODE) }
        assertEquals(Kind.entries.size, headings.toSet().size)
        assertEquals(emptyList<String>(), headings.filter { it.isBlank() })
    }
}
