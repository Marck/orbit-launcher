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
 */
data class Picture(
    val packageName: String,
    val uri: String,
    val title: String?,
    val seasons: Int?,
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
        read(context) { picture -> counts.merge(picture.packageName, 1, Int::plus) }
        counts.toList().sortedByDescending { it.second }.map { it.first }
    }.getOrDefault(emptyList())

    /**
     * Pictures from the chosen apps, shuffled, leaving out anything named in [except].
     *
     * What is in Watch Next belongs to the app that serves it, and the hero shows it there. The
     * same programme turning up as an ambient picture behind another app reads exactly like the
     * picture leaking across, which is what it was supposed to stop doing.
     */
    fun art(context: Context, except: Set<String> = emptySet()): List<Picture> {
        val chosen = sources(context)
        if (chosen.isEmpty()) return emptyList()
        val skip = except.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return runCatching {
            val found = mutableListOf<Picture>()
            read(context) { picture ->
                val named = picture.title?.trim()?.lowercase()
                if (picture.packageName in chosen && (named == null || named !in skip)) {
                    found += picture
                }
            }
            found.shuffled().take(MAX)
        }.getOrDefault(emptyList())
    }

    /** Walks the preview rows that are a film or a programme and have a picture. */
    private inline fun read(context: Context, onRow: (Picture) -> Unit) {
        context.contentResolver.query(
            TvContract.PreviewPrograms.CONTENT_URI,
            arrayOf(
                TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_TYPE,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_ITEM_COUNT,
            ),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor == null) return
            while (cursor.moveToNext()) {
                if (cursor.isNull(2) || cursor.getInt(2) !in TYPES) continue
                // Wide artwork only. A tall cover cropped to fill the backdrop shows its own
                // printed border down the side, which reads as a seam rather than a picture.
                if (!cursor.isNull(3) &&
                    cursor.getInt(3) != TvContract.PreviewPrograms.ASPECT_RATIO_16_9
                ) {
                    continue
                }
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
