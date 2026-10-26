package app.orbit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The scaled tiles survive the process, and the store stays bounded while they do.
 *
 * The memory cache only ever helped within one run. Measured on the set, a cold start reads the
 * originals again: 1 to 2.4MB per cover through a provider that serves one at a time, nine of
 * them finishing 7.9 to 32.7 seconds after a common start, with decode measured at 0 to 1ms and
 * therefore not the thing being waited for. These pin that a second cold start reads the small
 * copy instead, and that the store cannot grow without limit on a set whose disk was 89% full.
 *
 * The ceiling tests pass their own bound rather than writing 16MB, which keeps them quick while
 * still exercising the real sweep. One test asserts the hot path uses the real constant, so a
 * seam for the tests cannot quietly become the behaviour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ArtworkDiskTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun empty() {
        ArtworkCache.clear()
        ArtworkDisk.reset(context)
        ShotHooks.artwork = null
    }

    @After
    fun tearDown() {
        ArtworkCache.clear()
        ArtworkDisk.reset(context)
        ShotHooks.artwork = null
    }

    private fun tile(width: Int, height: Int, color: Int = Color.DKGRAY): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            .also { Canvas(it).drawColor(color) }

    private fun png(width: Int, height: Int): ByteArray =
        ByteArrayOutputStream()
            .also { tile(width, height).compress(Bitmap.CompressFormat.PNG, 100, it) }
            .toByteArray()

    /** Serves [bytes] at [uri] exactly once, the way a provider that has been read would. */
    private fun serveOnce(uri: String, bytes: ByteArray) {
        shadowOf(context.contentResolver)
            .registerInputStream(Uri.parse(uri), ByteArrayInputStream(bytes))
    }

    private fun fileFor(uri: String, width: Int) =
        File(ArtworkDisk.dir(context), ArtworkDisk.name(uri, width))

    @Test
    fun `a tile written comes back at the size it was written`() {
        val written = tile(256, 384)
        ArtworkDisk.write(context, "content://server/cover/1", PosterFetchPx, written)

        val read = ArtworkDisk.read(context, "content://server/cover/1", PosterFetchPx)
        assertNotNull("the tile was not kept", read)
        assertEquals(written.width, read!!.width)
        assertEquals(written.height, read.height)
    }

    @Test
    fun `nothing is returned for artwork that was never kept`() {
        assertNull(ArtworkDisk.read(context, "content://server/never/seen", PosterFetchPx))
    }

    @Test
    fun `a cold start reads the small copy instead of the original`() {
        // The whole point of the store. The stream is spent after the first read, so a picture
        // coming back once memory is empty can only have come off the disk.
        val uri = "content://server/cover/cold"
        serveOnce(uri, png(600, 900))
        val first = ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx)
        assertNotNull("the first read should decode the original", first)

        ArtworkCache.clear()
        val second = ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx)
        assertNotNull("a new process would have read the original all over again", second)
        assertNotSame("this would be the memory cache, not the disk", first, second)
        assertEquals(first!!.width, second!!.width)
    }

    @Test
    fun `the uri is never written down`() {
        // An artwork uri carries the owner's server and the item's identity. The file name is a
        // digest, so neither is on disk in anything readable.
        val uri = "content://media.example.invalid/Items/abc123/Images/Primary"
        val name = ArtworkDisk.name(uri, PosterFetchPx)
        assertEquals("a sha-256 in hex is 64 characters", 64, name.length)
        assertTrue("the name is not hex", name.all { it in "0123456789abcdef" })
        for (part in listOf("media.example.invalid", "Items", "abc123", "Primary", uri)) {
            assertFalse("the name contains '$part'", name.contains(part))
        }
    }

    @Test
    fun `the same artwork at two widths is two files`() {
        val uri = "content://server/cover/2"
        ArtworkDisk.write(context, uri, PosterFetchPx, tile(256, 384))
        ArtworkDisk.write(context, uri, 1280, tile(1280, 720))

        assertEquals(2, ArtworkDisk.dir(context).listFiles()?.size)
        assertEquals(256, ArtworkDisk.read(context, uri, PosterFetchPx)?.width)
        assertEquals(1280, ArtworkDisk.read(context, uri, 1280)?.width)
    }

    @Test
    fun `the store is held to its ceiling`() {
        repeat(8) { i ->
            ArtworkDisk.write(context, "content://server/cover/$i", PosterFetchPx, tile(256, 384))
        }
        val one = ArtworkDisk.size(context) / 8
        val ceiling = one * 4

        ArtworkDisk.sweepNow(context, ceiling = ceiling)
        assertTrue(
            "the store kept ${ArtworkDisk.size(context)} bytes against a ceiling of $ceiling",
            ArtworkDisk.size(context) <= ceiling,
        )
    }

    @Test
    fun `the least recently read go first`() {
        // Not the first written. A cover still on the home screen is read on every visit, and
        // has to outlast one that is only still on disk.
        val kept = "content://server/cover/kept"
        val dropped = "content://server/cover/dropped"
        ArtworkDisk.write(context, kept, PosterFetchPx, tile(256, 384))
        ArtworkDisk.write(context, dropped, PosterFetchPx, tile(256, 384))
        // Both were written together, so age them by hand and then read one of them, which is
        // what a visit to the home screen does. Past the touch threshold, since a read only
        // writes a new mark when the old one is stale enough to be worth the write.
        val old = System.currentTimeMillis() - 2 * ArtworkDisk.TOUCH_AFTER_MS
        fileFor(kept, PosterFetchPx).setLastModified(old)
        fileFor(dropped, PosterFetchPx).setLastModified(old)
        assertNotNull(ArtworkDisk.read(context, kept, PosterFetchPx))

        ArtworkDisk.sweepNow(context, ceiling = fileFor(kept, PosterFetchPx).length())

        assertNotNull("the cover in use was dropped", ArtworkDisk.read(context, kept, PosterFetchPx))
        assertNull("the unread cover was kept", ArtworkDisk.read(context, dropped, PosterFetchPx))
    }

    @Test
    fun `reading the same tile twice in an hour does not rewrite its mark`() {
        // A visit reads every cover in the row and the hero's backdrop on top. Writing the mark
        // back each time is wear on the set's flash for a bound measured in days.
        val uri = "content://server/cover/touched"
        ArtworkDisk.write(context, uri, PosterFetchPx, tile(256, 384))
        val file = fileFor(uri, PosterFetchPx)
        val fresh = System.currentTimeMillis() - 60 * 1000
        file.setLastModified(fresh)

        assertNotNull(ArtworkDisk.read(context, uri, PosterFetchPx))
        assertEquals("a recent mark was written again", fresh, file.lastModified())
    }

    @Test
    fun `artwork nobody has asked for in a month goes`() {
        // This is what forgets media that was deleted from the library. It stops being
        // published, so it stops being read, so it leaves on its own.
        val gone = "content://server/cover/deleted"
        val here = "content://server/cover/current"
        ArtworkDisk.write(context, gone, PosterFetchPx, tile(256, 384))
        ArtworkDisk.write(context, here, PosterFetchPx, tile(256, 384))
        val now = System.currentTimeMillis()
        fileFor(gone, PosterFetchPx).setLastModified(now - ArtworkDisk.MAX_AGE_MS - 1000)

        ArtworkDisk.sweepNow(context, now)

        assertNull("artwork for deleted media was kept", ArtworkDisk.read(context, gone, PosterFetchPx))
        assertNotNull("artwork still in use was dropped", ArtworkDisk.read(context, here, PosterFetchPx))
    }

    @Test
    fun `a tile still being written is never served`() {
        // The posters are fetched in parallel, so a half written file must not be readable.
        // It is written under another name and renamed, and only the exact name is ever read.
        val uri = "content://server/cover/partial"
        ArtworkDisk.dir(context).mkdirs()
        File(ArtworkDisk.dir(context), ArtworkDisk.name(uri, PosterFetchPx) + "123.part")
            .writeBytes(png(256, 384).copyOfRange(0, 64))

        assertNull(ArtworkDisk.read(context, uri, PosterFetchPx))
    }

    @Test
    fun `a tile that will not decode is removed rather than tried again`() {
        val uri = "content://server/cover/corrupt"
        ArtworkDisk.dir(context).mkdirs()
        val file = fileFor(uri, PosterFetchPx)
        file.writeBytes(ByteArray(512) { 0x7f })

        assertNull(ArtworkDisk.read(context, uri, PosterFetchPx))
        assertFalse("a file that cannot be decoded was left to fail on every start", file.isFile)
    }

    @Test
    fun `a picture past the whole store's bound is not kept`() {
        // An entry larger than the bound would cost everything else and then itself, which is
        // the fault the memory cache already had.
        val uri = "content://server/cover/huge"
        ArtworkDisk.write(context, uri, PosterFetchPx, tile(256, 384), ceiling = 1)
        assertNull(ArtworkDisk.read(context, uri, PosterFetchPx))
    }

    @Test
    fun `the sweep happens once in a process`() {
        ArtworkDisk.sweep(context)
        val stale = "content://server/cover/stale"
        ArtworkDisk.write(context, stale, PosterFetchPx, tile(256, 384))
        fileFor(stale, PosterFetchPx)
            .setLastModified(System.currentTimeMillis() - ArtworkDisk.MAX_AGE_MS - 1000)

        ArtworkDisk.sweep(context)
        assertNotNull("the sweep ran again on the same process", ArtworkDisk.read(context, stale, PosterFetchPx))
    }

    @Test
    fun `the whole working set fits without recycling straight away`() {
        // A ceiling under the working set would make the store churn: every visit evicting what
        // the next one wants, for the cost of the writes and none of the benefit.
        //
        // The per-tile figures are measured on the set rather than assumed, and are the largest
        // of each kind that was seen: 52KB for a 256px cover over eight samples, 244KB for a
        // 1280px backdrop over seven. The counts come from the code, so raising either cap
        // without raising the ceiling fails here rather than on the television.
        val cover = 52L * 1024
        val backdrop = 244L * 1024
        val covers = Backdrops.MAX_PICTURES * cover
        // Every picture can be asked for as a backdrop as well as a cover, and Watch Next adds
        // its own rows on top.
        val backdrops = (Backdrops.MAX_PICTURES + ContinueWatching.MAX_ROWS) * backdrop
        assertTrue(
            "the working set is ${covers + backdrops} bytes against a ceiling of " +
                "${ArtworkDisk.CEILING_BYTES}, so the store would evict what it just wrote",
            covers + backdrops <= ArtworkDisk.CEILING_BYTES,
        )
    }

    @Test
    fun `the hot path is bounded by the real ceiling, not a test's`() {
        // The ceiling is a parameter so the tests can stay quick. This is what stops that seam
        // from being the behaviour as well.
        val uri = "content://server/cover/default"
        ArtworkDisk.write(context, uri, PosterFetchPx, tile(256, 384))
        assertNotNull("the default bound rejected an ordinary tile", ArtworkDisk.read(context, uri, PosterFetchPx))
        assertEquals(24L * 1024 * 1024, ArtworkDisk.CEILING_BYTES)
        assertEquals(30L * 24 * 60 * 60 * 1000, ArtworkDisk.MAX_AGE_MS)
    }
}
