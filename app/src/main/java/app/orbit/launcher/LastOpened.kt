package app.orbit.launcher

import android.content.Context

/**
 * The last app or input Orbit opened, so the hero can offer it again.
 *
 * Orbit's own record, so it needs no permission. It only knows what was opened from Orbit: an
 * app started with the remote's own keys, or an input switched to with the input key, is not
 * in here and is not claimed to be.
 */
object LastOpened {

    const val APP = "app"
    const val INPUT = "input"

    /** Long enough to cover a film and a night's sleep, short enough not to offer yesterday. */
    private const val FRESH_MS = 12L * 60 * 60 * 1000

    data class Entry(val kind: String, val id: String)

    fun record(context: Context, kind: String, id: String) {
        prefs(context).edit()
            .putString(KEY_KIND, kind)
            .putString(KEY_ID, id)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    fun recent(context: Context): Entry? {
        val prefs = prefs(context)
        val at = prefs.getLong(KEY_AT, 0L)
        // A clock that has gone backwards, after a reboot without the network, would otherwise
        // make everything look fresh forever.
        val age = System.currentTimeMillis() - at
        if (at == 0L || age !in 0..FRESH_MS) return null
        val kind = prefs.getString(KEY_KIND, null) ?: return null
        val id = prefs.getString(KEY_ID, null) ?: return null
        return Entry(kind, id)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("orbit.last", Context.MODE_PRIVATE)

    private const val KEY_KIND = "kind"
    private const val KEY_ID = "id"
    private const val KEY_AT = "at"
}
