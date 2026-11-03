package app.orbit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * A row on the home screen, as something that can be ordered and switched off.
 *
 * Stored by name, so an order written by one version survives a release that adds a row or
 * drops one. A name that is no longer known is ignored rather than crashing the screen it
 * describes.
 */
enum class HomeRow {
    /** The owner's apps. Always shown: a launcher with nothing to launch is not one. */
    FAVORITES,

    /** Covers from whatever the apps publish, which is empty until something does. */
    POSTERS,

    /** The television's physical inputs. */
    INPUTS,

    /**
     * Reserved for the Home Assistant work, where a row appears only when something is worth
     * saying. Known to the store so an order written now keeps its place for it, and left out
     * of the settings screen until there is a source behind it: offering a row that can never
     * appear would be worse than not offering it.
     */
    NOTIFICATIONS,
}

/** What the home screen is made of, in the order the owner put it. */
object LayoutStore {

    /** The order Orbit shipped with, and what an install that never chooses still gets. */
    internal val default = listOf(
        // Directly under the hero, because it is news and it is only there when there is
        // some. Everything below it keeps its place on the days nothing happens.
        HomeRow.NOTIFICATIONS,
        HomeRow.POSTERS,
        HomeRow.FAVORITES,
        HomeRow.INPUTS,
    )

    /** Cannot be switched off, so the screen always has something to launch from. */
    internal val required = setOf(HomeRow.FAVORITES)

    /**
     * Rows with nothing behind them yet, left out of the settings screen.
     *
     * Empty now that the notifications row has a source. Kept, because the next row to be
     * built will want it and the rule is worth keeping written down: a row that can never
     * appear is worse than one that is not offered.
     */
    internal val unbuilt = emptySet<HomeRow>()

    /** The rows the settings screen lists, in order, whether shown or not. */
    internal fun offered(context: Context): List<HomeRow> =
        ordered(context).filterNot { it in unbuilt }

    /** Bumped on a change, so the home screen rebuilds itself with it. */
    var revision by mutableIntStateOf(0)
        private set

    /** Every known row in the owner's order, including the ones switched off. */
    internal fun ordered(context: Context): List<HomeRow> {
        val stored = prefs(context).getString(KEY_ORDER, null)
            ?.split(SEPARATOR)
            ?.mapNotNull { name -> HomeRow.entries.firstOrNull { it.name == name } }
            .orEmpty()
        // Nothing stored is not the same as an empty order: it means the owner has never
        // said, so the shipped order applies. Reading it off the enum instead, as this did,
        // quietly made the declaration order the default and left [default] as a comment.
        val base = stored.ifEmpty { default }
        // Appended rather than dropped: a row this version knows about and the stored order
        // does not is new, and a new row has to appear somewhere or it is unreachable.
        return base + HomeRow.entries.filterNot { it in base }
    }

    /** Whether a row is drawn at all. Having nothing to put in it is a separate question. */
    internal fun shown(context: Context, row: HomeRow): Boolean =
        row in required || row.name !in hidden(context)

    /** The rows the home screen draws, in order. */
    fun rows(context: Context): List<HomeRow> =
        ordered(context).filter { it !in unbuilt && shown(context, it) }

    /**
     * Moves a row one place, or does nothing at either end.
     *
     * Nothing rather than wrapping: a list this short is read as a picture of the screen, and
     * the top row leaping to the bottom reads as a mistake rather than as an order.
     */
    fun move(context: Context, row: HomeRow, delta: Int) {
        val order = offered(context).toMutableList()
        val from = order.indexOf(row)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.add(to, order.removeAt(from))
        // The rows not offered keep their place at the end, so nothing is lost by moving one.
        val kept = ordered(context).filter { it in unbuilt }
        prefs(context).edit { putString(KEY_ORDER, (order + kept).joinToString(SEPARATOR) { it.name }) }
        revision++
    }

    fun setShown(context: Context, row: HomeRow, shown: Boolean) {
        if (row in required) return
        val next = hidden(context).toMutableSet()
        if (shown) next -= row.name else next += row.name
        prefs(context).edit { putString(KEY_HIDDEN, next.joinToString(SEPARATOR)) }
        revision++
    }

    private fun hidden(context: Context): Set<String> =
        prefs(context).getString(KEY_HIDDEN, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "orbit.layout"
    private const val KEY_ORDER = "order"
    private const val KEY_HIDDEN = "hidden"
    private const val SEPARATOR = "\n"
}
