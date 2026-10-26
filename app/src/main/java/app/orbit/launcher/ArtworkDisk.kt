package app.orbit.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The scaled tiles, kept on disk so a cold start does not read the originals again.
 *
 * [ArtworkCache] holds the same pictures in memory and loses them with the process. What it
 * loses is expensive: measured on the set, a cover is 1 to 2.4MB of JPEG at 2000x3000 behind a
 * provider that serves one at a time, and nine of them finished 7.9 to 32.7 seconds after a
 * common start. Decoding is not the cost at all, which the same measurement showed as 0 to 1ms
 * of bounds work per picture, so what a second cold start is waiting for is purely the bytes.
 *
 * So the tile that was scaled down for drawing is written here, and a later start reads tens of
 * kilobytes instead of megabytes. The first fill after an install is unchanged. Every one after
 * it should not need the originals at all.
 *
 * This lives in the cache directory on purpose. The set's /data was 89% full with 730MB free
 * when this was written, and a store the system cannot reclaim under pressure is a liability on
 * a disk that full. Here, Android may drop it and the owner may clear it, and both only cost one
 * slow fill.
 *
 * A file is named by the digest of what it holds, never by the URI. An app's artwork URI carries
 * the owner's server and the item's identity in it, and that is not something to write down.
 *
 * Nothing here has to notice artwork being replaced, which was the one thing a store that
 * outlives the process could get wrong. Measured on the set rather than assumed: the media
 * server's artwork URI carries an image tag, one 32 character hash per URI, and the tag is
 * derived from the picture. New artwork is therefore a new URI, a new key and a fetch, and the
 * tile for the old one is left unread until the age bound takes it.
 */
object ArtworkDisk {

    /**
     * What the whole store is allowed to weigh.
     *
     * Sized from the working set rather than guessed at, because a ceiling under it would make
     * every visit evict what the next visit wants and the store would do nothing but churn.
     * Measured on the set: a 256px cover compresses to 23 to 52KB over eight samples and a
     * 1280px backdrop to 45 to 244KB over seven, none of them with an alpha channel. The counts
     * are the code's own, [Backdrops.MAX_PICTURES] of them, each of which can be asked for as a
     * cover for the row and again as a backdrop for the hero, plus [ContinueWatching.MAX_ROWS]
     * more backdrops from Watch Next. At the sizes actually seen that is around 12MB, and
     * 20.2MB if every single tile came in at the largest measured. This clears both.
     */
    internal const val CEILING_BYTES = 24L * 1024 * 1024

    /**
     * How long a tile nobody has asked for is kept.
     *
     * This is what deletes artwork for media that is gone. Something removed from the library
     * stops being published, so it stops being read, so its file stops being touched and it
     * leaves on its own. No list of what still exists has to be kept, and nothing has to be
     * right for that to work.
     */
    internal const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    /** Artwork is photographic, and the difference from here up is not visible on a tile. */
    internal const val QUALITY = 90

    /** How stale a tile's mark has to be before reading it writes a new one. */
    internal const val TOUCH_AFTER_MS = 60L * 60 * 1000

    private const val DIR = "artwork"
    private const val TEMP_SUFFIX = ".part"

    private val swept = AtomicBoolean(false)

    /** Bytes on disk as far as this process knows, or negative before anything has counted. */
    private val held = AtomicLong(-1L)

    /**
     * The file a picture is held in.
     *
     * The width is in the digest for the same reason it is in the memory key: the hero and the
     * poster row ask for the same artwork at different sizes and must not be handed each
     * other's copy.
     */
    internal fun name(uri: String, maxWidth: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ArtworkCache.key(uri, maxWidth).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    internal fun dir(context: Context): File = File(context.cacheDir, DIR)

    /**
     * Marks a tile as still wanted, which is what [sweep] reads to tell a cover in use from one
     * whose media is gone.
     *
     * Not done on every read. A visit to the home screen reads every cover in the row and the
     * hero's backdrop on top, and writing the same metadata back dozens of times a visit is
     * wear on the set's flash for no gain: the bound this feeds is measured in days, so an
     * hour's resolution is as good as a millisecond's.
     */
    private fun touch(file: File) {
        val now = System.currentTimeMillis()
        if (now - file.lastModified() < TOUCH_AFTER_MS) return
        file.setLastModified(now)
    }

    /**
     * The tile, if it was kept. Call off the main thread.
     *
     * Reading is also what marks the file as still wanted, so [sweep] can tell a cover in use
     * from one whose media is gone. A file that will not decode is corrupt, most likely a write
     * that the process did not outlive, and is removed rather than tried again on every start.
     */
    fun read(context: Context, uri: String, maxWidth: Int): Bitmap? = runCatching {
        val file = File(dir(context), name(uri, maxWidth))
        if (!file.isFile) return@runCatching null
        val bitmap = BitmapFactory.decodeFile(file.path)
        if (bitmap == null) {
            file.delete()
            return@runCatching null
        }
        touch(file)
        bitmap
    }.getOrNull()

    /**
     * Keeps the tile. Call off the main thread.
     *
     * Written beside its final name and then renamed, because the posters are fetched in
     * parallel and a reader must never be handed a file that is still being written. The rename
     * is within one directory, so a reader sees either nothing or the whole picture.
     *
     * A picture bigger than the whole store is not kept, for the reason the memory cache learned
     * the hard way: an entry past the bound evicts everything else and then itself.
     */
    fun write(
        context: Context,
        uri: String,
        maxWidth: Int,
        bitmap: Bitmap,
        /** Overridden only by tests, so the bound can be proven without writing 16MB. */
        ceiling: Long = CEILING_BYTES,
    ) {
        if (bitmap.isRecycled) return
        runCatching {
            val dir = dir(context)
            if (!dir.isDirectory && !dir.mkdirs()) return
            val temp = File.createTempFile(name(uri, maxWidth), TEMP_SUFFIX, dir)
            val written = runCatching {
                temp.outputStream().use { out ->
                    // Alpha survives only in PNG, and artwork that reports it would otherwise
                    // come back with a black background instead of a transparent one.
                    val format =
                        if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG
                        else Bitmap.CompressFormat.JPEG
                    bitmap.compress(format, QUALITY, out)
                }
            }.getOrDefault(false)
            if (!written || temp.length() == 0L || temp.length() > ceiling) {
                temp.delete()
                return
            }
            val target = File(dir, name(uri, maxWidth))
            if (!temp.renameTo(target)) {
                temp.delete()
                return
            }
            // The sweep at the start of a run can only bound what the previous run left. Without
            // this, a single long session could write past the ceiling and not be held to it
            // until the next start, which on a disk this full is not a bound at all.
            val counted = held.get()
            val total =
                if (counted < 0) size(context).also { held.set(it) }
                else held.addAndGet(target.length())
            if (total > ceiling) sweepNow(context, ceiling = ceiling)
        }
    }

    /**
     * Brings the store back inside its bounds. Safe to call from any thread, does nothing after
     * the first time in a process.
     *
     * Age goes first, since that is the rule that exists to forget deleted media, and then the
     * oldest of what is left goes until the total fits. Oldest means least recently read, not
     * first written, which is what makes a cover that is still on the home screen outlast one
     * that is only still on disk.
     */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()) {
        if (!swept.compareAndSet(false, true)) return
        sweepNow(context, now)
    }

    /** The sweep itself, without the once-per-process guard. Used by tests. */
    internal fun sweepNow(
        context: Context,
        now: Long = System.currentTimeMillis(),
        ceiling: Long = CEILING_BYTES,
    ) {
        synchronized(this) {
            runCatching {
                val files = dir(context).listFiles()?.toMutableList() ?: return
                // Anything unread for long enough, which includes a part file left behind by a
                // write the process did not outlive.
                files.removeAll { it.lastModified() < now - MAX_AGE_MS && it.delete() }
                var total = files.sumOf { it.length() }
                files.sortBy { it.lastModified() }
                for (file in files) {
                    if (total <= ceiling) break
                    val size = file.length()
                    if (file.delete()) total -= size
                }
                held.set(total)
            }
        }
    }

    /** Bytes held on disk. Used by tests and by the measurement of the real working set. */
    internal fun size(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** Used by tests, which share one process and must not inherit each other's files. */
    internal fun reset(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        swept.set(false)
        held.set(-1L)
    }
}
