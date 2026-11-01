package app.orbit.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log

/**
 * Counts what each app publishes to the TV provider, and nothing else.
 *
 * This exists because the question "does app X expose anything to a launcher" cannot be
 * answered from outside the app: `adb shell content query` returns "No result found" for every
 * `android.media.tv` table, including ones with a thousand rows, because `com.android.shell`
 * does not hold `READ_TV_LISTINGS` and cannot be granted it. The output is indistinguishable
 * from an empty table, so the only honest reading comes from inside a process that holds the
 * permission.
 *
 * It lives in the debug source set, so the release APK does not carry it at all.
 *
 * **It prints counts only.** No titles, no descriptions, no artwork or intent URIs, no row ids.
 * A package name and an integer are the whole vocabulary, which is what makes the output safe
 * to paste into an issue. [summarise] is pure so that guarantee can be tested rather than
 * asserted, see `ProbeTest`.
 */
internal object Probe {

    const val TAG = "OrbitProbe"

    const val ACTION = "app.orbit.launcher.PROBE"

    /** One row, reduced to the only two things worth reporting. */
    internal data class Row(val packageName: String, val type: Int?)

    /**
     * The lines for one table, busiest app first.
     *
     * [kindOf] names a row's type when the table has one worth naming; Watch Next does, the
     * preview and channel tables do not, and pass null.
     */
    internal fun summarise(
        label: String,
        rows: List<Row>,
        kindOf: ((Int?) -> String)? = null,
    ): List<String> {
        if (rows.isEmpty()) return listOf("$label: nothing published")
        val byApp = rows.groupBy { it.packageName }
        val lines = mutableListOf("$label: ${rows.size} rows from ${byApp.size} apps")
        byApp.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Row>>> { it.value.size }
                .thenBy { it.key })
            .forEach { (packageName, ofApp) ->
                val detail = kindOf?.let { name ->
                    ofApp.groupingBy { name(it.type) }.eachCount()
                        .entries.sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key} ${it.value}" }
                        .let { " ($it)" }
                }.orEmpty()
                lines += "  $packageName ${ofApp.size}$detail"
            }
        return lines
    }

    /** Reads all three tables and logs the counts. Call off the main thread. */
    fun run(context: Context) {
        if (!ContinueWatching.granted(context)) {
            Log.i(TAG, "READ_TV_LISTINGS not granted, so every table reads as empty")
            return
        }
        val lines =
            summarise("watch-next", read(context, TvContract.WatchNextPrograms.CONTENT_URI, true)) {
                Kind.of(it).name.lowercase()
            } +
                summarise("preview", read(context, TvContract.PreviewPrograms.CONTENT_URI, false)) +
                summarise("channels", read(context, TvContract.Channels.CONTENT_URI, false))
        lines.forEach { Log.i(TAG, it) }
    }

    /**
     * The package and, for Watch Next, the type of every row.
     *
     * No projection, for the same reason the app's own reads use none: a column an older TV
     * provider does not have throws rather than coming back null.
     */
    private fun read(context: Context, uri: android.net.Uri, withType: Boolean): List<Row> =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor == null) return emptyList()
                val packageColumn = cursor.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME)
                if (packageColumn < 0) return emptyList()
                val typeColumn = if (withType) {
                    cursor.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE)
                } else {
                    -1
                }
                val rows = mutableListOf<Row>()
                while (cursor.moveToNext()) {
                    val packageName = cursor.getString(packageColumn) ?: continue
                    val type = typeColumn
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { cursor.getInt(it) }
                    rows += Row(packageName, type)
                }
                rows
            }
        }.getOrElse {
            // The class name only. A provider's message can quote the query it refused.
            Log.i(TAG, "read failed: ${it.javaClass.simpleName}")
            emptyList()
        }
}

/**
 * Runs [Probe] on `adb shell am broadcast -a app.orbit.launcher.PROBE`.
 *
 * Exported, because a broadcast from the shell arrives as another uid, and guarded by
 * `android.permission.DUMP` so that it is the shell and the system rather than any installed
 * app. The shell holds DUMP, which is the same permission that lets `adb shell dumpsys` work.
 * That it exists in debug builds alone is the second guard, and the worst it can do is write
 * counts to the log.
 */
internal class ProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val finish = goAsync()
        Thread {
            try {
                Probe.run(context)
            } finally {
                finish.finish()
            }
        }.start()
    }
}
