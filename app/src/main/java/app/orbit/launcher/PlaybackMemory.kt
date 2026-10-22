package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * How far into something the TV last got, as the playing app's own media session reported it.
 *
 * Needed because an app may publish a Watch Next row with no position on it at all. The two on
 * this set are both "next episode" rows, which never carry one: a position only appears on a
 * "continue" row, written when something is stopped part way through. This fills that gap from
 * what the app told the system while it was playing, and keeps it after the app has gone.
 */
object PlaybackMemory {

    /** Long enough to still be useful tomorrow morning, short enough not to offer last week. */
    private const val FRESH_MS = 24L * 60 * 60 * 1000

    fun record(context: Context, packageName: String, title: String, position: Long, duration: Long) {
        if (duration <= 0 || position < 0 || position > duration) return
        prefs(context).edit()
            .putString("$packageName.title", title)
            .putLong("$packageName.position", position)
            .putLong("$packageName.duration", duration)
            .putLong("$packageName.at", System.currentTimeMillis())
            .apply()
    }

    /**
     * The bar for [packageName], when what was playing is the same thing as [title].
     *
     * Matched on the name as well as the app, because an app plays more than one thing and a
     * position from the wrong one would be a confident lie rather than a missing bar.
     */
    fun progressFor(context: Context, packageName: String, title: String): Progress? {
        val prefs = prefs(context)
        val at = prefs.getLong("$packageName.at", 0L)
        val age = System.currentTimeMillis() - at
        if (at == 0L || age !in 0..FRESH_MS) return null
        val played = prefs.getString("$packageName.title", null) ?: return null
        if (!sameThing(played, title)) return null
        return ContinueWatching.timed(
            prefs.getLong("$packageName.position", -1),
            prefs.getLong("$packageName.duration", -1),
        )
    }

    /** An app names an episode either way round, so one containing the other is close enough. */
    private fun sameThing(played: String, listed: String): Boolean {
        val a = played.trim().lowercase()
        val b = listed.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    /** True when the owner has turned Orbit on in the TV's notification access settings. */
    fun granted(context: Context): Boolean {
        val ours = ComponentName(context, MediaWatchService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("orbit.playback", Context.MODE_PRIVATE)
}
