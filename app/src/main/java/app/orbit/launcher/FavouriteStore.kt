package app.orbit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The owner's favourites, in the order they chose.
 *
 * Stored as package names rather than anything derived from the installed app, so a favourite
 * survives an update, a reinstall, or the app changing its label. Order matters, so this is one
 * delimited string rather than a string set.
 */
object FavouriteStore {

    // Keeps the name it was first written under. Renaming the file would drop every favourite
    // already saved on the TV.
    private const val PREFS = "orbit.pins"
    private const val SEPARATOR = "\n"

    const val APPS = "apps"
    const val INPUTS = "inputs"

    /** Bumped on every change, so the home screen reorders without rescanning the device. */
    var revision by mutableIntStateOf(0)
        private set

    fun favourites(context: Context, key: String): List<String> =
        prefs(context).getString(key, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun toggle(context: Context, key: String, id: String) {
        val current = favourites(context, key).toMutableList()
        if (!current.remove(id)) current += id
        write(context, key, current)
    }

    fun remove(context: Context, key: String, id: String) {
        val current = favourites(context, key).toMutableList()
        if (current.remove(id)) write(context, key, current)
    }

    /** Moves a favourite one place. Anything that is not a favourite has no place to move from. */
    fun move(context: Context, key: String, id: String, delta: Int) {
        val current = favourites(context, key).toMutableList()
        val from = current.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, current.lastIndex)
        if (to == from) return
        current.removeAt(from)
        current.add(to, id)
        write(context, key, current)
    }

    /** Drops favourites whose target is gone, so a removed app leaves no hole. */
    fun prune(context: Context, key: String, present: Set<String>) {
        val current = favourites(context, key)
        val kept = current.filter { it in present }
        if (kept.size != current.size) write(context, key, kept)
    }

    /** Replaces the whole order, used to undo a cancelled move. */
    fun replace(context: Context, key: String, values: List<String>) = write(context, key, values)

    /** Only the favourites, in the owner's order. A favourite that is not in [items] is skipped. */
    fun <T> pick(items: List<T>, favourites: List<String>, id: (T) -> String): List<T> {
        val byId = items.associateBy(id)
        return favourites.mapNotNull { byId[it] }
    }

    /** Favourites in the owner's order, then everything else in its existing order. */
    fun <T> arrange(items: List<T>, favourites: List<String>, id: (T) -> String): List<T> {
        if (favourites.isEmpty()) return items
        val rank = favourites.withIndex().associate { (index, value) -> value to index }
        val (chosen, rest) = items.partition { rank.containsKey(id(it)) }
        return chosen.sortedBy { rank[id(it)] } + rest
    }

    private fun write(context: Context, key: String, values: List<String>) {
        prefs(context).edit().putString(key, values.joinToString(SEPARATOR)).apply()
        revision++
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
