package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import kotlin.math.roundToInt

/**
 * Something the owner could carry on with, exactly as the app that owns it published it.
 *
 * Nothing here is invented. A field the app left empty stays empty, and the hero leaves it out,
 * so there is never a progress bar for a programme whose position nobody reported.
 */
data class Resumable(
    val packageName: String,
    val title: String,
    /** The episode, when the app named one. Never set for a film. */
    val subtitle: String?,
    val description: String?,
    val posterUri: String?,
    val intentUri: String,
    /** Null unless the app reported both where it had got to and how long the thing is. */
    val progress: Progress?,
    /** What the app says this row is for. Decides the heading above the title. */
    val kind: Kind = Kind.CONTINUE,
    /** The row's TvContract type, or null when the app left it off. */
    val programType: Int? = null,
) {
    /** The heading the hero puts above the title, in the words of what this row really is. */
    val heading: String get() = kind.heading(programType)
}

/**
 * Why an app put a row in Watch Next.
 *
 * The contract has four of these and they do not mean the same thing: only [CONTINUE] is a
 * programme that was started and stopped. Treating them all as one is how a series whose next
 * episode has not been touched ends up under "continue watching", which is what the media
 * server publishes on the set this was written for: both of its rows say [NEXT].
 *
 * A row with no kind on it, or one the contract gains later, reads as [CONTINUE], which is
 * what every row was treated as before.
 */
enum class Kind {
    /** Started and not finished. Carry on where it was left. */
    CONTINUE,

    /** The one after the last one finished. Nobody has watched any of it. */
    NEXT,

    /** Out since the last visit. Nobody has watched any of it either. */
    NEW,

    /** Put aside on purpose, and never started. */
    WATCHLIST,
    ;

    /**
     * The heading, in the words of what the row actually is.
     *
     * [programType] is the row's own TvContract type, which decides whether "episode" is a
     * true word for it: an app may mark a film as the next thing to watch, and calling that an
     * episode would be a different lie from the one this replaces.
     */
    fun heading(programType: Int?): String = when (this) {
        CONTINUE -> "CONTINUE WATCHING"
        NEXT -> if (episodic(programType)) "NEXT EPISODE" else "NEXT UP"
        NEW -> if (episodic(programType)) "NEW EPISODE" else "NEW"
        WATCHLIST -> "SAVED FOR LATER"
    }

    private fun episodic(programType: Int?): Boolean = programType in EPISODIC

    companion object {
        private val EPISODIC = setOf(
            TvContract.WatchNextPrograms.TYPE_TV_EPISODE,
            TvContract.WatchNextPrograms.TYPE_TV_SEASON,
            TvContract.WatchNextPrograms.TYPE_TV_SERIES,
        )

        /** Reads the column's value, falling back to [CONTINUE] for anything unrecognised. */
        fun of(watchNextType: Int?): Kind = when (watchNextType) {
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT -> NEXT
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW -> NEW
            TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST -> WATCHLIST
            else -> CONTINUE
        }
    }
}

/**
 * How far in, exactly as the publishing app reported it.
 *
 * [summary] is in minutes for anything that plays. For anything else the unit is unknown, so it
 * says a percentage rather than claiming minutes. Android TV has no book type at all: a reader
 * publishes its page through the Engage SDK, which is written for Google and cannot be read by
 * another launcher, so "Page x of y" is not something Orbit can be told.
 */
data class Progress(val fraction: Float, val summary: String)

/**
 * The TV's own Watch Next list, which apps publish to and Orbit only reads.
 *
 * Reading another app's rows needs READ_TV_LISTINGS, which the owner grants once. Without it
 * this returns nothing and the hero falls back to what Orbit knows by itself.
 */
object ContinueWatching {

    /**
     * The TV provider declares this one, not the platform, so the SDK has no constant for it.
     */
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"

    private const val MAX_ROWS = 12

    /** A poster is a few hundred kilobytes. This is the ceiling, not the expectation. */
    private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024

    /** The kinds whose position and length are really milliseconds. */
    private val TIMED = setOf(
        TvContract.WatchNextPrograms.TYPE_MOVIE,
        TvContract.WatchNextPrograms.TYPE_TV_SERIES,
        TvContract.WatchNextPrograms.TYPE_TV_SEASON,
        TvContract.WatchNextPrograms.TYPE_TV_EPISODE,
        TvContract.WatchNextPrograms.TYPE_CLIP,
        TvContract.WatchNextPrograms.TYPE_EVENT,
        TvContract.WatchNextPrograms.TYPE_CHANNEL,
    )

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Every readable row, most recently engaged first. Call off the main thread. */
    fun all(context: Context): List<Resumable> {
        if (!granted(context)) return emptyList()
        ShotHooks.watchNext?.let { return it.take(MAX_ROWS) }
        return runCatching {
            // No projection: a column an older TV provider does not have would throw, and every
            // one of these is optional to the apps anyway.
            context.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                null,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            ).use { cursor ->
                if (cursor == null) return@runCatching emptyList()
                val c = Columns(cursor)
                val found = mutableListOf<Resumable>()
                // The hero cycles through these, so a long list is only ever partly seen.
                while (cursor.moveToNext() && found.size < MAX_ROWS) {
                    build(c)?.let { found += it }
                }
                found
            }
        }.getOrNull().orEmpty()
    }

    /**
     * Decodes a poster, scaled down to [maxWidth]. Call off the main thread.
     *
     * The URI belongs to the app that published the row, so it is opened through the resolver
     * and never logged: an app's own content URI can carry the owner's server in it.
     */
    fun poster(context: Context, uri: String, maxWidth: Int): Bitmap? = runCatching {
        ShotHooks.artwork?.let { return@runCatching it }
        // Already decoded once. The row is re-read and reshuffled on every return, so without
        // this the same covers are fetched and decoded again each time the screen is opened.
        ArtworkCache.get(uri, maxWidth)?.let { return@runCatching it }
        val parsed = Uri.parse(uri)
        // Only the media server publishes a content URI its own provider can serve. Every other
        // app on this set publishes an https link to its own artwork, so that is fetched. Only
        // the picture is asked for, nothing is sent, and the link is never logged.
        val bytes = when (parsed.scheme?.lowercase()) {
            "http", "https" -> fetch(uri)
            else -> context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )?.also { ArtworkCache.put(uri, maxWidth, it) }
    }.getOrNull()

    /** Reads a picture off the network. Call off the main thread. */
    private fun fetch(url: String): ByteArray? = runCatching {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            // A picture, not a page. Anything bigger is not artwork and is not worth decoding,
            // and a stream that never ends must not be allowed to fill the heap.
            connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() < MAX_ARTWORK_BYTES) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
                out.toByteArray().takeIf { it.isNotEmpty() }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /**
     * The intent the app published, pinned to that app.
     *
     * A row is data from another app, so it is not followed wherever it points: a component has
     * to belong to the app that published the row, and anything without one is addressed to that
     * app by name. Any selector is dropped, because a selector overrides all of that.
     */
    fun intentFor(item: Resumable): Intent? = intentFor(item.intentUri, item.packageName)

    /**
     * The same rule for any row an app published, not only a Watch Next one. A preview row
     * carries the same field, pointing at the one thing it shows rather than the app's front
     * door, which is what makes a picture on the home screen worth pressing OK on.
     */
    fun intentFor(intentUri: String?, packageName: String): Intent? = runCatching {
        val intent = Intent.parseUri(intentUri ?: return@runCatching null, Intent.URI_INTENT_SCHEME)
        intent.selector = null
        val component: ComponentName? = intent.component
        when {
            component == null -> intent.setPackage(packageName)
            component.packageName == packageName -> intent
            else -> return@runCatching null
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    private fun build(c: Columns): Resumable? {
        if (c.int(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0) return null
        val packageName = c.text(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: return null
        val title = c.text(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: return null
        val intentUri = c.text(TvContract.WatchNextPrograms.COLUMN_INTENT_URI) ?: return null

        return Resumable(
            packageName = packageName,
            title = title,
            subtitle = episode(c),
            description = c.text(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION),
            posterUri = c.text(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
            intentUri = intentUri,
            progress = progress(c),
            kind = Kind.of(c.int(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)),
            programType = c.int(TvContract.WatchNextPrograms.COLUMN_TYPE),
        )
    }

    /**
     * The bar and its two numbers from a position and a length in milliseconds.
     *
     * Shared with the media session reader, which fills in for an app that publishes a row
     * without a position on it.
     */
    fun timed(position: Long, duration: Long): Progress? {
        if (duration <= 0 || position < 0 || position > duration) return null
        val watched = position / 60_000
        // Rounded up, so a part minute still reads as a minute left rather than none.
        val left = (duration - position + 59_999) / 60_000
        return Progress(position.toFloat() / duration, "$watched MIN WATCHED, $left LEFT")
    }

    /** Only drawn when the app reported both numbers, so there is never an invented bar. */
    private fun progress(c: Columns): Progress? {
        val position = c.long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
            ?: return null
        val duration = c.long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS) ?: return null
        if (duration <= 0 || position < 0 || position > duration) return null

        if (c.int(TvContract.WatchNextPrograms.COLUMN_TYPE) in TIMED) {
            return timed(position, duration)
        }
        // Not a timed kind, so the unit is anyone's guess and a percentage says the same thing
        // without claiming minutes.
        val fraction = position.toFloat() / duration
        return Progress(fraction, "${(fraction * 100).roundToInt()}% DONE")
    }

    /**
     * An app names the episode either way round, so the two are never joined: one app puts
     * "S11 - Christmas Special" in the episode title while the season column says something
     * else again, and the two together read as a contradiction.
     */
    private fun episode(c: Columns): String? {
        // A film has no episode, and some apps leave a season number on one anyway.
        if (c.int(TvContract.WatchNextPrograms.COLUMN_TYPE) == TvContract.WatchNextPrograms.TYPE_MOVIE) {
            return null
        }
        c.text(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)?.let { return it }
        val season = c.text(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
        val number = c.text(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
        return when {
            season != null && number != null -> "S$season E$number"
            number != null -> "E$number"
            else -> null
        }
    }

    /** Columns by name, because which ones a TV provider has is not fixed. */
    private class Columns(private val cursor: android.database.Cursor) {
        private val index = cursor.columnNames.withIndex().associate { it.value to it.index }

        fun text(name: String): String? = index[name]
            ?.takeIf { !cursor.isNull(it) }
            ?.let { cursor.getString(it) }
            ?.takeIf { it.isNotBlank() }

        fun long(name: String): Long? = index[name]
            ?.takeIf { !cursor.isNull(it) }
            ?.let { cursor.getLong(it) }

        fun int(name: String): Int? = long(name)?.toInt()
    }
}
