package app.orbit.launcher

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Decoded artwork, kept for as long as the process lives.
 *
 * The poster row is read again on every return to the screen, and the order it picks is
 * shuffled, so the covers on the left are rarely the ones that were there last time. Without
 * this, every one of them was fetched and decoded again from nothing each time the owner
 * pressed Home, which is why the row filled in slowly and one tile at a time.
 *
 * Bounded by the bytes the bitmaps actually hold rather than by a count, because a backdrop is
 * worth a dozen covers and a TV has little to spare. The oldest go first when the bound is hit.
 * Nothing here is written to disk: a cover is the owner's library, and it stays in memory and
 * goes when the process does.
 */
object ArtworkCache {

    /** Enough for a screenful of covers and the backdrop behind them, several times over. */
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * The key a picture is held under.
     *
     * The width is part of it: the hero and the poster row ask for the same artwork at
     * different sizes, and handing the row the hero's copy would hold a backdrop in memory for
     * every tile.
     */
    fun key(uri: String, maxWidth: Int): String = "$maxWidth@$uri"

    fun get(uri: String, maxWidth: Int): Bitmap? = cache.get(key(uri, maxWidth))

    fun put(uri: String, maxWidth: Int, bitmap: Bitmap) {
        // A recycled bitmap would be handed out and then fail to draw.
        if (!bitmap.isRecycled) cache.put(key(uri, maxWidth), bitmap)
    }

    /** Bytes currently held. */
    fun size(): Int = cache.size()

    /** Used by tests, which share one process and must not inherit each other's pictures. */
    fun clear() = cache.evictAll()
}
