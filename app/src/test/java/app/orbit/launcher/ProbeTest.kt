package app.orbit.launcher

import android.media.tv.TvContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe's whole value is that its output is safe to paste somewhere public, so the
 * vocabulary of that output is the thing under test, not just the arithmetic.
 */
class ProbeTest {

    private fun row(packageName: String, type: Int? = null) = Probe.Row(packageName, type)

    private fun kind(type: Int?) = Kind.of(type).name.lowercase()

    @Test
    fun `an empty table says so rather than printing a heading with nothing under it`() {
        assertEquals(listOf("preview: nothing published"), Probe.summarise("preview", emptyList()))
    }

    @Test
    fun `apps are counted and the busiest comes first`() {
        val rows = listOf(row("app.few"), row("app.many"), row("app.many"), row("app.many"))

        assertEquals(
            listOf(
                "preview: 4 rows from 2 apps",
                "  app.many 3",
                "  app.few 1",
            ),
            Probe.summarise("preview", rows),
        )
    }

    @Test
    fun `apps with the same count are ordered by name, so two runs can be compared`() {
        val rows = listOf(row("app.b"), row("app.a"))

        assertEquals(listOf("  app.a 1", "  app.b 1"), Probe.summarise("preview", rows).drop(1))
    }

    @Test
    fun `watch next rows say which kind they are, which is the whole question for an app`() {
        val rows = listOf(
            row("app.one", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE),
            row("app.one", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE),
            row("app.one", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT),
        )

        assertEquals(
            listOf("watch-next: 3 rows from 1 apps", "  app.one 3 (continue 2, next 1)"),
            Probe.summarise("watch-next", rows, ::kind),
        )
    }

    @Test
    fun `a row with no type is reported, not dropped, so the totals still add up`() {
        val rows = listOf(row("app.one", null), row("app.one", null))

        val lines = Probe.summarise("watch-next", rows, ::kind)

        assertEquals("watch-next: 2 rows from 1 apps", lines.first())
        assertTrue(lines[1], lines[1].endsWith(" 2 (continue 2)"))
    }

    @Test
    fun `every line is a package name, an integer and fixed words, and nothing else`() {
        // The guarantee the probe is allowed to make. A title, a description or a URI reaching
        // the log would have to break this shape first.
        val safe = Regex("""^(\w[\w-]*: (\d+ rows from \d+ apps|nothing published)|""" +
            """ {2}[a-zA-Z][\w.]* \d+( \((\w+ \d+)(, \w+ \d+)*\))?)$""")
        val rows = listOf(
            row("com.example.one", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST),
            row("com.example.two", TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW),
        )

        val lines = Probe.summarise("watch-next", rows, ::kind) +
            Probe.summarise("preview", rows) +
            Probe.summarise("channels", emptyList())

        lines.forEach { assertTrue("unsafe line: $it", safe.matches(it)) }
    }

    @Test
    fun `a row carries only a package and a type, so there is nothing else to leak`() {
        // Reflection rather than reading the class, so adding a title to Row fails here
        // instead of quietly widening what the probe can print.
        assertEquals(
            listOf("packageName", "type"),
            Probe.Row::class.java.declaredFields
                .map { it.name }
                .filterNot { it.startsWith("$") }
                .sorted(),
        )
    }
}
