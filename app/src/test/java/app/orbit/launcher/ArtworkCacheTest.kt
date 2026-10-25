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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Artwork is fetched and decoded once, not once per visit.
 *
 * The poster row is read again on every return and the order it picks is shuffled, so the
 * covers at the left are rarely the ones that were there before. Every one of them used to be
 * fetched and decoded from nothing each time, which is what made the row fill in slowly and a
 * tile at a time.
 *
 * The decoding tests work by registering a stream that can only be read once. A second call
 * that still returns a picture can only have come from the cache, which is a stronger check
 * than counting calls into a double.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ArtworkCacheTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun emptyCache() {
        ArtworkCache.clear()
        ShotHooks.artwork = null
    }

    @After
    fun tearDown() {
        ArtworkCache.clear()
        ShotHooks.artwork = null
    }

    private fun png(width: Int, height: Int, color: Int = Color.DKGRAY): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
    }

    /** Serves [bytes] at [uri] exactly once, the way a provider that has been read would. */
    private fun serveOnce(uri: String, bytes: ByteArray) {
        shadowOf(context.contentResolver)
            .registerInputStream(Uri.parse(uri), ByteArrayInputStream(bytes))
    }

    @Test
    fun `a picture asked for twice is only read once`() {
        val uri = "content://server/cover/1"
        serveOnce(uri, png(200, 300))

        val first = ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx)
        assertNotNull("the first read should decode the picture", first)

        // The stream behind the uri is spent. Anything that comes back now is the cache.
        val second = ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx)
        assertSame("the second ask re-read the picture instead of reusing it", first, second)
    }

    @Test
    fun `without the cache the second read would come back empty`() {
        // Proves the test above is actually testing something: the same sequence with the
        // cache emptied in between gets nothing, because the stream really is spent.
        val uri = "content://server/cover/2"
        serveOnce(uri, png(200, 300))
        assertNotNull(ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx))
        ArtworkCache.clear()
        assertNull(
            "the stream was still readable, so the first test proves nothing",
            ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx),
        )
    }

    @Test
    fun `the hero's copy is not handed to the poster row`() {
        // Same picture, two sizes. Serving the backdrop's copy to a tile would hold a full
        // size bitmap in memory for every cover in the row.
        val uri = "content://server/cover/3"
        serveOnce(uri, png(600, 900))
        val big = ContinueWatching.poster(context, uri, maxWidth = 512)
        assertNotNull(big)
        // Nothing is registered for a second read, so a wrongly shared entry would show up as
        // a picture coming back under a size that was never decoded.
        assertNull(
            "a picture decoded for one size was handed out for another",
            ContinueWatching.poster(context, uri, maxWidth = 64),
        )
    }

    @Test
    fun `the oldest go first once the bound is reached`() {
        val one = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        ArtworkCache.put("content://a", 256, one)
        assertNotNull(ArtworkCache.get("content://a", 256))
        val before = ArtworkCache.size()
        assertTrue("the cache should count the bytes it holds", before >= one.byteCount)

        // Enough large pictures to pass the bound several times over.
        repeat(20) { i ->
            ArtworkCache.put("content://filler/$i", 256, Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888))
        }
        assertNull("the oldest picture should have been dropped", ArtworkCache.get("content://a", 256))
        assertTrue("the cache grew past its bound", ArtworkCache.size() <= 8 * 1024 * 1024)
    }

    @Test
    fun `a recycled picture is never kept`() {
        val dead = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { recycle() }
        ArtworkCache.put("content://dead", 256, dead)
        assertNull("a recycled picture would fail to draw", ArtworkCache.get("content://dead", 256))
    }

    @Test
    fun `the screenshot hook still wins, so tests are not served real pictures`() {
        val stub = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        ShotHooks.artwork = stub
        val uri = "content://server/cover/4"
        serveOnce(uri, png(200, 300))
        assertSame(stub, ContinueWatching.poster(context, uri, maxWidth = PosterFetchPx))
        // And the hook's picture must not be left behind in the cache for a later test.
        ShotHooks.artwork = null
        assertNull(ArtworkCache.get(uri, PosterFetchPx))
    }

    @Test
    fun `the key tells two pictures apart`() {
        assertEquals(ArtworkCache.key("content://a", 256), ArtworkCache.key("content://a", 256))
        assertTrue(ArtworkCache.key("content://a", 256) != ArtworkCache.key("content://b", 256))
        assertTrue(ArtworkCache.key("content://a", 256) != ArtworkCache.key("content://a", 512))
    }
}
