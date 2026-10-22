package app.orbit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * The owner's favorites, in the order they chose.
 *
 * Stored as package names rather than anything derived from the installed app, so a favorite
 * survives an update, a reinstall, or the app changing its label. Order matters, so this is one
 * delimited string rather than a string set.
 */
object FavoriteStore {

    private const val PREFS = "orbit.favorites"
    private const val SEPARATOR = "\n"

    const val APPS = "apps"
    const val INPUTS = "inputs"

    /** Bumped on every change, so the home screen reorders without rescanning the device. */
    var revision by mutableIntStateOf(0)
        private set

    fun favorites(context: Context, key: String): List<String> =
        prefs(context).getString(key, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun toggle(context: Context, key: String, id: String) {
        val current = favorites(context, key).toMutableList()
        if (!current.remove(id)) current += id
        write(context, key, current)
    }

    fun remove(context: Context, key: String, id: String) {
        val current = favorites(context, key).toMutableList()
        if (current.remove(id)) write(context, key, current)
    }

    /**
     * Moves a favorite past the next one on screen in the direction of [delta]. Favorites that
     * are not on screen, such as an input that is switched off, are stepped over, so every press
     * visibly moves the tile.
     */
    fun move(context: Context, key: String, id: String, delta: Int, shown: Collection<String>) {
        val current = favorites(context, key)
        val moved = reorder(current, id, delta, shown)
        if (moved !== current) write(context, key, moved)
    }

    /**
     * The order [move] stores: [id] moved [delta] places along [stored], counting only the
     * favorites in [shown]. Gives back [stored] itself when there is nowhere to go, so a press at
     * the end of the row stores nothing.
     */
    fun reorder(stored: List<String>, id: String, delta: Int, shown: Collection<String>): List<String> {
        val from = stored.indexOf(id)
        if (from < 0 || delta == 0) return stored
        val step = if (delta > 0) 1 else -1
        var to = from
        var i = from
        var left = abs(delta)
        while (left > 0) {
            i += step
            if (i !in stored.indices) break
            if (stored[i] in shown) {
                to = i
                left--
            }
        }
        if (to == from) return stored
        return stored.toMutableList().apply {
            removeAt(from)
            add(to, id)
        }
    }

    /** Drops favorites whose target is gone, so a removed app leaves no hole. */
    fun prune(context: Context, key: String, present: Set<String>) {
        val current = favorites(context, key)
        val kept = current.filter { it in present }
        if (kept.size != current.size) write(context, key, kept)
    }

    /** Replaces the whole order, used to undo a cancelled move. */
    fun replace(context: Context, key: String, values: List<String>) = write(context, key, values)

    /** Only the favorites, in the owner's order. A favorite that is not in [items] is skipped. */
    fun <T> pick(items: List<T>, favorites: List<String>, id: (T) -> String): List<T> {
        val byId = items.associateBy(id)
        return favorites.mapNotNull { byId[it] }
    }

    /** Favorites in the owner's order, then everything else in its existing order. */
    fun <T> arrange(items: List<T>, favorites: List<String>, id: (T) -> String): List<T> {
        if (favorites.isEmpty()) return items
        val rank = favorites.withIndex().associate { (index, value) -> value to index }
        val (chosen, rest) = items.partition { rank.containsKey(id(it)) }
        return chosen.sortedBy { rank[id(it)] } + rest
    }

    private fun write(context: Context, key: String, values: List<String>) {
        prefs(context).edit().putString(key, values.joinToString(SEPARATOR)).apply()
        revision++
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
