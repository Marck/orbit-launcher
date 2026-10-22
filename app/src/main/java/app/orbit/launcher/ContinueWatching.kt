package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri

/**
 * Something the owner could carry on with, exactly as the app that owns it published it.
 *
 * Nothing here is invented. A field the app left empty stays empty, and the hero leaves it out,
 * so there is never a progress bar for a programme whose position nobody reported.
 */
data class Resumable(
    val packageName: String,
    val title: String,
    /** The episode, when the app named one. */
    val subtitle: String?,
    val description: String?,
    val posterUri: String?,
    val intentUri: String,
    /** 0 to 1, only when the app gave both a position and a length. */
    val progress: Float?,
    val minutesLeft: Int?,
)

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

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The most recently engaged row, or null. Call off the main thread. */
    fun latest(context: Context): Resumable? {
        if (!granted(context)) return null
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
                if (cursor == null) return@runCatching null
                val c = Columns(cursor)
                while (cursor.moveToNext()) {
                    build(c)?.let { return@runCatching it }
                }
                null
            }
        }.getOrNull()
    }

    /**
     * Decodes a poster, scaled down to [maxWidth]. Call off the main thread.
     *
     * The URI belongs to the app that published the row, so it is opened through the resolver
     * and never logged: an app's own content URI can carry the owner's server in it.
     */
    fun poster(context: Context, uri: String, maxWidth: Int): Bitmap? = runCatching {
        val parsed = Uri.parse(uri)
        val bytes = context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
            ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    /**
     * The intent the app published, pinned to that app.
     *
     * A row is data from another app, so it is not followed wherever it points: a component has
     * to belong to the app that published the row, and anything without one is addressed to that
     * app by name. Any selector is dropped, because a selector overrides all of that.
     */
    fun intentFor(item: Resumable): Intent? = runCatching {
        val intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME)
        intent.selector = null
        val component: ComponentName? = intent.component
        when {
            component == null -> intent.setPackage(item.packageName)
            component.packageName == item.packageName -> intent
            else -> return@runCatching null
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()

    private fun build(c: Columns): Resumable? {
        if (c.int(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0) return null
        val packageName = c.text(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: return null
        val title = c.text(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: return null
        val intentUri = c.text(TvContract.WatchNextPrograms.COLUMN_INTENT_URI) ?: return null

        val position = c.long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        val duration = c.long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
        val started = position != null && duration != null && duration > 0 && position in 0..duration

        return Resumable(
            packageName = packageName,
            title = title,
            subtitle = episode(c),
            description = c.text(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION),
            posterUri = c.text(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
            intentUri = intentUri,
            progress = if (started) position!!.toFloat() / duration!! else null,
            minutesLeft = if (started) ((duration!! - position!!) / 60_000L).toInt() else null,
        )
    }

    /**
     * An app names the episode either way round, so the two are never joined: one app puts
     * "S11 - Christmas Special" in the episode title while the season column says something
     * else again, and the two together read as a contradiction.
     */
    private fun episode(c: Columns): String? {
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
