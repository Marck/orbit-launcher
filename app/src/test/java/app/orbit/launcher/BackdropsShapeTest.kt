package app.orbit.launcher

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.media.tv.TvContract
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Which shape of artwork goes where.
 *
 * The hero and the poster row read the same table and must not take each other's rows: a cover
 * stretched across the backdrop shows its own printed border down the side, and a 16:9 still
 * squeezed into a poster is the same mistake the other way up. The split used to be made by
 * throwing the covers away while reading, which is what discarded 112 of the media server's
 * 117 rows, so what is checked here is that both shapes survive the read and then separate.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackdropsShapeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun picture(title: String, wide: Boolean) =
        Picture(
            packageName = "server",
            uri = "pic://server/$title",
            title = title,
            seasons = null,
            description = "What $title is about.",
            wide = wide,
        )

    private fun sourcedToServer() {
        // Chooses by hand, so the test does not depend on what is in the favorites.
        Backdrops.toggle(context, "server")
        assertTrue("server" in Backdrops.sources(context))
    }

    @After
    fun tearDown() {
        ShotHooks.pictures = null
        CountingTv.longDescription = null
        CountingTv.rejectLongDescription = false
    }

    @Test
    fun `the hero takes the wide rows and leaves the covers`() {
        sourcedToServer()
        ShotHooks.pictures = listOf(
            picture("Wide one", wide = true),
            picture("Cover one", wide = false),
            picture("Cover two", wide = false),
        )
        assertEquals(listOf("Wide one"), Backdrops.art(context).map { it.title })
    }

    @Test
    fun `the poster row takes the covers and leaves the wide rows`() {
        sourcedToServer()
        ShotHooks.pictures = listOf(
            picture("Wide one", wide = true),
            picture("Cover one", wide = false),
            picture("Cover two", wide = false),
        )
        assertEquals(
            setOf("Cover one", "Cover two"),
            Backdrops.posters(context).map { it.title }.toSet(),
        )
    }

    @Test
    fun `between them the two take every row and neither takes one twice`() {
        sourcedToServer()
        val all = listOf(
            picture("Wide one", wide = true),
            picture("Wide two", wide = true),
            picture("Cover one", wide = false),
            picture("Cover two", wide = false),
            picture("Cover three", wide = false),
        )
        ShotHooks.pictures = all
        val wide = Backdrops.art(context).map { it.title }
        val covers = Backdrops.posters(context).map { it.title }
        assertEquals(all.size, wide.size + covers.size)
        assertEquals(emptySet<String?>(), wide.toSet() intersect covers.toSet())
    }

    @Test
    fun `a row that declares no shape is treated as wide, the way the contract reads it`() {
        sourcedToServer()
        // The default on Picture, which is what a row with a null aspect ratio comes back as.
        val undeclared = Picture(
            packageName = "server",
            uri = "pic://server/undeclared",
            title = "Undeclared",
            seasons = null,
            description = "No ratio on this row.",
        )
        ShotHooks.pictures = listOf(undeclared)
        assertEquals(listOf("Undeclared"), Backdrops.art(context).map { it.title })
        assertEquals(emptyList<String?>(), Backdrops.posters(context).map { it.title })
    }

    @Test
    fun `a cover already in Watch Next is left out of the poster row too`() {
        sourcedToServer()
        ShotHooks.pictures = listOf(
            picture("Carrying on", wide = false),
            picture("Cover one", wide = false),
        )
        assertEquals(
            listOf("Cover one"),
            Backdrops.posters(context, except = setOf("carrying on")).map { it.title },
        )
    }

    /**
     * A stand-in for the TV provider that counts what is asked of it.
     *
     * The only test that goes through the real cursor rather than the [ShotHooks] seam, so it
     * is also the only one that would notice a column read from the wrong index.
     */
    class CountingTv : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val previews = uri.toString().startsWith(TvContract.PreviewPrograms.CONTENT_URI.toString())
            if (previews) previewQueries++ else channelQueries++
            // A provider without the column answers by throwing, not by leaving it null.
            if (rejectLongDescription &&
                projection?.contains(TvContract.PreviewPrograms.COLUMN_LONG_DESCRIPTION) == true
            ) {
                throw IllegalArgumentException("Invalid column long_description")
            }
            val columns = projection ?: emptyArray()
            val cursor = MatrixCursor(columns)
            if (previews) {
                val kinds = rowTypes
                if (kinds != null) {
                    // One row per kind, named after it, so a test can see which survived.
                    kinds.forEach {
                        cursor.addRow(
                            fit(row(TvContract.PreviewPrograms.ASPECT_RATIO_16_9, "Type $it", it), columns)
                        )
                    }
                } else {
                    // One wide row and one cover, from the same app.
                    cursor.addRow(fit(row(TvContract.PreviewPrograms.ASPECT_RATIO_16_9, "A Wide Still"), columns))
                    cursor.addRow(fit(row(coverRatio, "A Cover"), columns))
                }
            }
            return cursor
        }

        /** A row cut to the projection asked for, the way a real provider answers. */
        private fun fit(values: Array<Any?>, columns: Array<out String>): Array<Any?> =
            if (values.size <= columns.size) values else values.copyOf(columns.size)

        private fun row(
            aspect: Int,
            title: String,
            type: Int = TvContract.PreviewPrograms.TYPE_MOVIE,
        ): Array<Any?> = arrayOf(
            "server",
            "pic://server/$title",
            type,
            aspect,
            title,
            null,
            "What $title is about.",
            "2019-06-14",
            90L * 60_000L,
            null,
            null,
            // The long description, which most apps leave empty: the short one is then used.
            longDescription,
        )

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

        companion object {
            var previewQueries = 0
            var channelQueries = 0

            /** What the cover row declares. Set per test, because apps do not agree on it. */
            var coverRatio = TvContract.PreviewPrograms.ASPECT_RATIO_2_3

            /** The long description, which only the test about it fills in. */
            var longDescription: String? = null

            /** A provider that has never heard of the long description column. */
            var rejectLongDescription = false

            /** One row per kind instead of the usual pair, for the test about kinds. */
            var rowTypes: List<Int>? = null
        }
    }

    private fun registerTvProvider(
        coverRatio: Int = TvContract.PreviewPrograms.ASPECT_RATIO_2_3,
        rowTypes: List<Int>? = null,
    ) {
        CountingTv.previewQueries = 0
        CountingTv.channelQueries = 0
        CountingTv.coverRatio = coverRatio
        CountingTv.rowTypes = rowTypes
        val info = android.content.pm.ProviderInfo().apply {
            authority = TvContract.AUTHORITY
            applicationInfo = context.applicationInfo
            name = CountingTv::class.java.name
            packageName = context.packageName
        }
        org.robolectric.Robolectric.buildContentProvider(CountingTv::class.java).create(info)
    }

    @Test
    fun `splitting what was read costs no further reads`() {
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider()

        val found = Backdrops.all(context)
        assertEquals("one walk of the table", 1, CountingTv.previewQueries)
        assertEquals(2, found.size)

        // The whole point of the refactor: the hero and the poster row split one read between
        // them. Splitting it again, any number of times, must not go back to the provider.
        repeat(5) {
            Backdrops.wide(found, context)
            Backdrops.covers(found, context)
        }
        assertEquals("splitting must not re-read", 1, CountingTv.previewQueries)

        assertEquals(listOf("A Wide Still"), Backdrops.wide(found, context).map { it.title })
        assertEquals(listOf("A Cover"), Backdrops.covers(found, context).map { it.title })
    }

    @Test
    fun `anything an app declares that is not 16 by 9 is a cover`() {
        // 5 is TvContractCompat's ASPECT_RATIO_MOVIE_POSTER, which the media server writes and
        // the platform has no constant for. Checked by value on purpose: the rule is "not
        // 16:9" rather than a list of shapes Orbit happens to know the names of.
        val notWide = listOf(
            TvContract.PreviewPrograms.ASPECT_RATIO_2_3,
            TvContract.PreviewPrograms.ASPECT_RATIO_1_1,
            TvContract.PreviewPrograms.ASPECT_RATIO_4_3,
            TvContract.PreviewPrograms.ASPECT_RATIO_3_2,
            5,
        )
        // Outside the loop: choosing a source is a toggle, so a second call would undo it.
        sourcedToServer()
        ShotHooks.pictures = null
        for (ratio in notWide) {
            registerTvProvider(coverRatio = ratio)
            val covers = Backdrops.all(context).filter { !it.wide }
            assertEquals("ratio $ratio should be a cover", listOf("A Cover"), covers.map { it.title })
        }
    }

    @Test
    fun `the real cursor is read column for column`() {
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider()

        // Goes through read(), so a column taken from the wrong index shows up here and
        // nowhere else: every other test hands Picture objects straight to the seam.
        val cover = Backdrops.all(context).single { !it.wide }
        assertEquals("A Cover", cover.title)
        assertEquals("server", cover.packageName)
        assertEquals("What A Cover is about.", cover.description)
        assertEquals(2019, cover.year)
        assertEquals(90, cover.runtime)
    }

    @Test
    fun `the long description is read, and preferred over the capped one`() {
        // Pins the column's position as much as the choice: read from the wrong index this
        // comes back as the intent uri, the channel, or nothing at all.
        CountingTv.longDescription = "The whole sentence, which the short column could not hold."
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider()

        val cover = Backdrops.all(context).single { !it.wide }
        assertEquals("The whole sentence, which the short column could not hold.", cover.description)
    }

    @Test
    fun `a provider without the long description still gives every backdrop`() {
        // The read is wrapped, so a projection the provider rejects would cost the whole
        // screen rather than one field. The column is dropped and everything else survives.
        CountingTv.rejectLongDescription = true
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider()

        val all = Backdrops.all(context)
        assertTrue("the backdrops went with the column", all.isNotEmpty())
        val cover = all.single { !it.wide }
        assertEquals("A Cover", cover.title)
        assertEquals("What A Cover is about.", cover.description)
    }

    @Test
    fun `no chosen source means neither shape is offered`() {
        // Chosen, then unchosen, so the stored set is empty rather than never written.
        Backdrops.toggle(context, "server")
        Backdrops.toggle(context, "server")
        ShotHooks.pictures = listOf(
            picture("Wide one", wide = true),
            picture("Cover one", wide = false),
        )
        assertEquals(emptyList<Picture>(), Backdrops.art(context))
        assertEquals(emptyList<Picture>(), Backdrops.posters(context))
    }

    @Test
    fun `a clip counts, because a shop front calls its whole catalogue one`() {
        // Counted on the set: the shop front that was missing from the picker publishes 21
        // rows and every one of them is a clip. Leaving clips out left that app out whole.
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider(rowTypes = listOf(TvContract.PreviewPrograms.TYPE_CLIP))

        val read = Backdrops.all(context)
        assertEquals("a clip was dropped, so an app that only publishes clips is invisible", 1, read.size)
    }

    @Test
    fun `a channel strip still does not count, since it is not something to watch`() {
        sourcedToServer()
        ShotHooks.pictures = null
        registerTvProvider(rowTypes = listOf(TvContract.PreviewPrograms.TYPE_CHANNEL))

        assertTrue("a row's own banner was taken for a backdrop", Backdrops.all(context).isEmpty())
    }

    @Test
    fun `what counts is programmes and clips, and nothing else the contract allows`() {
        sourcedToServer()
        ShotHooks.pictures = null
        // Every kind the contract defines, so adding one later is a decision rather than a
        // default. Named by the row's own title, which is the kind it was built from.
        val every = (0..12).toList()
        registerTvProvider(rowTypes = every)

        val kept = Backdrops.all(context).mapNotNull { it.title?.removePrefix("Type ")?.toIntOrNull() }
        assertEquals(
            listOf(
                TvContract.PreviewPrograms.TYPE_MOVIE,
                TvContract.PreviewPrograms.TYPE_TV_SERIES,
                TvContract.PreviewPrograms.TYPE_TV_SEASON,
                TvContract.PreviewPrograms.TYPE_TV_EPISODE,
                TvContract.PreviewPrograms.TYPE_CLIP,
            ).sorted(),
            kept.sorted(),
        )
    }
}
