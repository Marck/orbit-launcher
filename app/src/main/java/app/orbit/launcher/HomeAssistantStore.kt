package app.orbit.launcher

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Where something from Home Assistant is shown. */
enum class HaSlot {
    /** The quiet line in the hero, used only when there is nothing to watch. */
    HERO,

    /** The notifications row, which exists only while it has something to say. */
    NOTIFICATIONS,
}

/**
 * The owner's Home Assistant, as configured on the television.
 *
 * Both the address and the token are credentials. They are written here and read here, and
 * they go nowhere else: not to a log, not into a screenshot, not into this repository. The
 * screen that sets them never reads the token back out, which is why there is no accessor
 * that returns it for display.
 */
object HomeAssistantStore {

    /** Bumped on a change, so the screens reading any of this redraw. */
    var revision by mutableIntStateOf(0)
        private set

    /** The address, without its trailing slash so one can be added safely. */
    fun baseUrl(context: Context): String? =
        prefs(context).getString(KEY_URL, null)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    internal fun token(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /** Whether there is enough to try a connection with. Says nothing about it working. */
    fun configured(context: Context): Boolean =
        baseUrl(context) != null && token(context) != null

    /**
     * Whether an address is one worth trying, said before anything is sent.
     *
     * Only the shape, not whether anything answers. A bare host is the usual slip, and
     * failing on it with "cannot connect" would send the owner looking at the wrong thing.
     */
    internal fun looksLikeUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            trimmed.removePrefix("http://").removePrefix("https://").isNotBlank()
    }

    fun save(context: Context, url: String, token: String) {
        prefs(context).edit {
            putString(KEY_URL, url.trim().trimEnd('/'))
            putString(KEY_TOKEN, token.trim())
        }
        revision++
    }

    /** Forgets the connection and everything chosen from it. */
    fun clear(context: Context) {
        prefs(context).edit { clear() }
        revision++
    }

    /** The entities chosen for a slot, in the order they were chosen. */
    fun chosen(context: Context, slot: HaSlot): List<String> =
        prefs(context).getString(key(slot), null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Adds an entity to a slot, or takes it out if it is already there. */
    fun toggle(context: Context, slot: HaSlot, entityId: String) {
        val next = chosen(context, slot).toMutableList()
        if (!next.remove(entityId)) next += entityId
        prefs(context).edit { putString(key(slot), next.joinToString(SEPARATOR)) }
        revision++
    }

    private fun key(slot: HaSlot) = "slot_${slot.name}"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "orbit.hass"
    private const val KEY_URL = "url"
    private const val KEY_TOKEN = "token"
    private const val SEPARATOR = "\n"
}
