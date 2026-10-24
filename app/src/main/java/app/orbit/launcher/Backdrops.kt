package app.orbit.launcher

import android.content.Context
import android.media.tv.TvContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Ambient artwork, for when the app in focus has nothing to carry on with.
 *
 * It comes from the rows apps publish to the TV's own preview list. Those rows carry a title, a
 * type, the publishing app and a picture, and on this set nothing else at all: not one of the
 * 1359 of them has a genre, and not one has an age rating. So there is no way to ask for action
 * or comedy, and no way to leave out anything frightening. What can be chosen is which apps
 * supply the pictures, which is what the picker offers, and that is the honest version of the
 * same control: a media server shows what the owner already has, a shop front shows its shop.
 */
/**
 * One ambient picture, with what the publishing app said about it.
 *
 * [seasons] is the app's own item count on a series row, which is the number of seasons. It is
 * left out when the app did not report one, rather than guessed at.
 *
 * [description] is the row's own short description, the same field the Continue watching hero
 * shows, so a picture reads the same whichever of the two put it on the screen. The long one is
 * empty on every row on this set, so there is nothing to fall back to.
 *
 * [channel] is the name of the publishing app's own row, its "channel" in the TV provider's
 * words: the server on this set publishes five of them, and every preview row says which one
 * it belongs to. It is what the app calls that row, so it is left exactly as the app wrote it.
 *
 * [year] and [runtime] come from the two fields every row on this set already fills in:
 * a release date on 113 of 117, and a duration on all 117. Both are left out when the app
 * reported something that cannot be one, rather than shown as whatever it said.
 */
data class Picture(
    val packageName: String,
    val uri: String,
    val title: String?,
    val seasons: Int?,
    val description: String?,
    val year: Int? = null,
    /** Minutes, and only on something with a single running length. Never on a series row. */
    val runtime: Int? = null,
    val channel: String? = null,
    /**
     * The row's own way in, pointing at this one thing rather than the app's front door.
     *
     * Never logged. An app's intent carries its own addressing, and a media server's carries
     * the id of something in the owner's library.
     */
    val intentUri: String? = null,
    /**
     * Whether the artwork is wide enough to sit behind the hero.
     *
     * The publishing app declares a ratio per row, and on this set the two shapes are a real
     * split in the data rather than a matter of taste: the server sends 16:9 for its episode
     * and folder rows and a tall cover for everything else, 5 wide against 112 covers. A row
     * that declares no ratio is taken as wide, which is what the contract's own default says.
     */
    val wide: Boolean = true,
)

object Backdrops {

    /** A film or a programme. A clip or a channel strip is not a backdrop. */
    private val TYPES = setOf(
        TvContract.PreviewPrograms.TYPE_MOVIE,
        TvContract.PreviewPrograms.TYPE_TV_SERIES,
        TvContract.PreviewPrograms.TYPE_TV_SEASON,
        TvContract.PreviewPrograms.TYPE_TV_EPISODE,
    )

    /** Enough for a long sitting without reading the whole list into memory. */
    private const val MAX = 60

    /** Bumped on every change, so the home screen picks new sources up without rescanning. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * The apps chosen to supply pictures.
     *
     * Until the owner chooses, their own favourite apps are used. Those are the apps they put
     * on the screen themselves, so a picture from one is far likelier to be something of theirs
     * than a streaming service's promotion wall, which is the only other thing on offer.
     */
    fun sources(context: Context): Set<String> {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_CHOSEN, false)) {
            return FavoriteStore.favorites(context, FavoriteStore.APPS).toSet()
        }
        return prefs.getString(KEY_SOURCES, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
    }

    fun toggle(context: Context, packageName: String) {
        val next = sources(context).toMutableSet()
        if (!next.remove(packageName)) next += packageName
        prefs(context).edit()
            .putBoolean(KEY_CHOSEN, true)
            .putString(KEY_SOURCES, next.joinToString(SEPARATOR))
            .apply()
        revision++
    }

    /** Every app that publishes a picture, the one with the most first. Call off the main thread. */
    fun publishers(context: Context): List<String> = runCatching {
        val counts = mutableMapOf<String, Int>()
        val tally = { picture: Picture -> counts.merge(picture.packageName, 1, Int::plus); Unit }
        ShotHooks.pictures?.forEach(tally) ?: read(context, tally)
        counts.toList().sortedByDescending { it.second }.map { it.first }
    }.getOrDefault(emptyList())

    /**
     * Wide pictures from the chosen apps, shuffled, leaving out anything named in [except].
     *
     * Wide only: this is what goes behind the hero, and a cover stretched across it is a seam.
     * The covers are read too, and [posters] is where they go.
     *
     * What is in Watch Next belongs to the app that serves it, and the hero shows it there. The
     * same programme turning up as an ambient picture behind another app reads exactly like the
     * picture leaking across, which is what it was supposed to stop doing.
     *
     * A picture that says what it is comes first. Of the 1359 rows on this set, 162 carry a
     * description and 1197 carry nothing but a name, almost all of them from one shop front. Left
     * shuffled together, seven backdrops in eight would be a title over a picture and no more.
     * The ones with nothing to say are kept on the end rather than dropped, so an app chosen on
     * its own still supplies something.
     */
    fun art(context: Context, except: Set<String> = emptySet()): List<Picture> =
        wide(all(context), context, except)

    /**
     * The tall covers from the chosen apps, for the row of posters.
     *
     * The same rows the hero cannot use. A cover is the shape the publishing app drew it in,
     * so shown at that shape it needs no cropping and carries its own printed title, which is
     * why the row under it says nothing the artwork already says.
     */
    fun posters(context: Context, except: Set<String> = emptySet()): List<Picture> =
        covers(all(context), context, except)

    /**
     * Everything the chosen apps publish, both shapes, in the order the provider gave them.
     *
     * The one walk of the table. The hero and the poster row want different shapes out of the
     * same rows, and walking it once each meant 1359 rows read twice on this set for no reason.
     * The screen reads this once and hands both of them what came back; [art] and [posters] are
     * the same thing for a caller that wants only one shape and has no list already.
     *
     * Call off the main thread.
     */
    fun all(context: Context): List<Picture> {
        val chosen = sources(context)
        if (chosen.isEmpty()) return emptyList()
        ShotHooks.pictures?.let { shot -> return shot.filter { it.packageName in chosen } }
        return runCatching {
            val found = mutableListOf<Picture>()
            read(context) { picture -> if (picture.packageName in chosen) found += picture }
            found
        }.getOrDefault(emptyList())
    }

    /**
     * The two shapes, out of rows already read. Cheap enough for the main thread: the reading
     * is what costs, and on this set these are picking over 117 rows rather than 1359.
     */
    fun wide(found: List<Picture>, context: Context, except: Set<String> = emptySet()): List<Picture> =
        pick(found.filter { it.wide }, sources(context), except)

    fun covers(found: List<Picture>, context: Context, except: Set<String> = emptySet()): List<Picture> =
        pick(found.filter { !it.wide }, sources(context), except)

    /**
     * The rows that may be shown, in the order they should be: from a chosen app, not already
     * in Watch Next, the ones that say what they are first.
     *
     * Separate from the reading so it can be checked without a TV provider behind it.
     */
    fun pick(found: List<Picture>, chosen: Set<String>, except: Set<String>): List<Picture> {
        val skip = except.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val mine = found.filter { picture ->
            val named = picture.title?.trim()?.lowercase()
            picture.packageName in chosen && (named == null || named !in skip)
        }
        val (told, bare) = mine.partition { !it.description.isNullOrBlank() }
        return (told.shuffled() + bare.shuffled()).take(MAX)
    }

    /**
     * The year out of a release date, or nothing.
     *
     * The TV contract allows a release date in three shapes, `yyyy`, `yyyy-MM-dd` and
     * `yyyy-MM-ddTHH:mm:ssZ`, and every one of them starts with the year, so the leading four
     * digits are all that is needed. Anything else an app writes there, and anything outside
     * the years a programme could plausibly carry, is left out rather than put on the screen.
     */
    fun yearOf(released: String?): Int? {
        val digits = released?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        return digits?.toInt()?.takeIf { it in 1880..2200 }
    }

    /**
     * A running length in whole minutes, or nothing.
     *
     * Rounded to the nearest minute, the way a listing reads it. Anything under a minute or
     * over half a day is not a running length, whatever the app meant by it, so it is dropped:
     * a row that says it is three seconds long is worse than a row that says nothing.
     */
    fun minutesOf(durationMillis: Long?): Int? {
        val minutes = durationMillis?.plus(30_000L)?.div(60_000L)?.toInt() ?: return null
        return minutes.takeIf { it in 1..(12 * 60) }
    }

    /**
     * What each of the publishing apps calls its own rows, by the id the preview rows refer to.
     *
     * A second table, read once per walk. There are 30 of these against 1359 preview rows, so
     * it is a small read that saves a lookup per row. An app that publishes rows but no channel
     * for them, or a provider that will not hand the table over, simply leaves the names out:
     * a backdrop without one reads as it did before.
     */
    private fun rowNames(context: Context): Map<Long, String> = runCatching {
        val names = mutableMapOf<Long, String>()
        context.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor == null) return@runCatching emptyMap()
            while (cursor.moveToNext()) {
                if (cursor.isNull(0)) continue
                val name = cursor.getString(1)?.trim()?.takeIf { it.isNotBlank() } ?: continue
                names[cursor.getLong(0)] = name
            }
        }
        names
    }.getOrDefault(emptyMap())

    /** Walks the preview rows that are a film or a programme and have a picture. */
    private inline fun read(context: Context, onRow: (Picture) -> Unit) {
        val rows = rowNames(context)
        context.contentResolver.query(
            TvContract.PreviewPrograms.CONTENT_URI,
            arrayOf(
                TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_TYPE,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_ITEM_COUNT,
                TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.PreviewPrograms.COLUMN_RELEASE_DATE,
                TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
                TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            ),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor == null) return
            while (cursor.moveToNext()) {
                if (cursor.isNull(2) || cursor.getInt(2) !in TYPES) continue
                // Both shapes are read. The hero still takes wide artwork only, because a tall
                // cover cropped to fill the backdrop shows its own printed border down the
                // side, which reads as a seam. The covers are what the poster row is made of,
                // and dropping them here is what used to throw away 112 of the server's 117.
                val wide = cursor.isNull(3) ||
                    cursor.getInt(3) == TvContract.PreviewPrograms.ASPECT_RATIO_16_9
                val packageName = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val uri = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                // An item count only means seasons on a series row; on a film it means nothing.
                val series = cursor.getInt(2) == TvContract.PreviewPrograms.TYPE_TV_SERIES
                val seasons = if (series && !cursor.isNull(5)) {
                    cursor.getInt(5).takeIf { it > 0 }
                } else {
                    null
                }
                onRow(
                    Picture(
                        packageName = packageName,
                        uri = uri,
                        title = cursor.getString(4)?.takeIf { it.isNotBlank() },
                        seasons = seasons,
                        description = cursor.getString(6)?.takeIf { it.isNotBlank() },
                        year = yearOf(cursor.getString(7)),
                        // A series row's duration is one episode's, not the series', and under
                        // a series name that reads as the whole thing being that short.
                        runtime = if (series || cursor.isNull(8)) null else minutesOf(cursor.getLong(8)),
                        channel = if (cursor.isNull(9)) null else rows[cursor.getLong(9)],
                        intentUri = cursor.getString(10)?.takeIf { it.isNotBlank() },
                        wide = wide,
                    )
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "orbit.backdrops"
    private const val KEY_SOURCES = "sources"
    private const val KEY_CHOSEN = "chosen"
    private const val SEPARATOR = "\n"
}
