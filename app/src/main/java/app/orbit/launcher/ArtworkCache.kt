package app.orbit.launcher

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Decoded artwork, kept for as long as the process lives.
 *
 * A cover and a backdrop are not the same kind of thing, and they no longer share a budget.
 * Measured on the set: a cover drawn 204px wide was held at 500x750, 1.5MB of it, and a hero
 * backdrop asked for at 1280px holds four to nine times that. The hero cycles through its
 * pictures while the screen just sits there, so three or four backdrops emptied a single shared
 * 8MB of every cover, and a return to the screen read all thirteen covers again through a
 * provider that serves one at a time. That is what made the row fill a tile at a time, and it
 * carried on doing it after the cache arrived, because the cache never got to keep anything:
 * thirteen covers asked for, thirteen misses, no hits at all.
 *
 * A picture bigger than its own bound is not kept. An LruCache handed an entry larger than its
 * maximum evicts every other entry and then that one as well, so one oversized backdrop left
 * nothing behind at all, which was the other half of the same fault.
 *
 * Nothing here is written to disk: a cover is the owner's library, and it stays in memory and
 * goes when the process does.
 */
object ArtworkCache {

    /** A row of covers at the width they are drawn, with room to spare. */
    internal const val COVER_BYTES = 6 * 1024 * 1024

    /**
     * The backdrop on screen and the ones either side of it. The hero cycles through up to
     * sixty, so holding more than a few is spending a television's memory on a 1-in-60 chance.
     */
    internal const val BACKDROP_BYTES = 8 * 1024 * 1024

    /** Wider than a cover is ever asked for, and narrower than the hero's 1280. */
    internal const val COVER_MAX_WIDTH = 512

    private class Pictures(max: Int) : LruCache<String, Bitmap>(max) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val covers = Pictures(COVER_BYTES)
    private val backdrops = Pictures(BACKDROP_BYTES)

    private fun tier(maxWidth: Int) = if (maxWidth > COVER_MAX_WIDTH) backdrops else covers

    /** What a picture asked for at [maxWidth] is allowed to weigh. */
    internal fun bound(maxWidth: Int): Int =
        if (maxWidth > COVER_MAX_WIDTH) BACKDROP_BYTES else COVER_BYTES

    /**
     * The key a picture is held under.
     *
     * The width is part of it: the hero and the poster row ask for the same artwork at
     * different sizes, and handing the row the hero's copy would hold a backdrop in memory for
     * every tile.
     */
    fun key(uri: String, maxWidth: Int): String = "$maxWidth@$uri"

    fun get(uri: String, maxWidth: Int): Bitmap? = tier(maxWidth).get(key(uri, maxWidth))

    fun put(uri: String, maxWidth: Int, bitmap: Bitmap) {
        // A recycled bitmap would be handed out and then fail to draw.
        if (bitmap.isRecycled) return
        // Keeping this would cost every other picture in the tier and then itself.
        if (bitmap.byteCount > bound(maxWidth)) return
        tier(maxWidth).put(key(uri, maxWidth), bitmap)
    }

    /** Bytes currently held, across both kinds. */
    fun size(): Int = covers.size() + backdrops.size()

    /** Bytes held in the tier a picture of this width belongs to. */
    internal fun size(maxWidth: Int): Int = tier(maxWidth).size()

    /** Used by tests, which share one process and must not inherit each other's pictures. */
    fun clear() {
        covers.evictAll()
        backdrops.evictAll()
    }
}
