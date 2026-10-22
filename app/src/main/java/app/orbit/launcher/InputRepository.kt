package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings

/**
 * One tile in the inputs row.
 *
 * [sub] carries the physical port when the input is a device reported over CEC, so a
 * console shows up under its own name with the port underneath, the way the TV's own
 * picker presents it.
 */
data class TvInput(
    val id: String,
    val label: String,
    val sub: String?,
    val connected: Boolean,
)

object InputRepository {

    private const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"
    private const val ACTION_GLOBAL_SEARCH = "android.search.action.GLOBAL_SEARCH"

    /** The launcher we are replacing also answers [ACTION_VIEW_INPUTS]; its panel is not the one we want. */
    private const val STOCK_LAUNCHER = "com.google.android.tvlauncher"

    /**
     * Physical inputs, as the TV itself would list them.
     *
     * Tuners are left out: they are not passthrough, and live TV is handled by a player app.
     * Inputs the user switched off in the system settings are left out too, which is what keeps
     * unused ports off the row.
     */
    fun load(context: Context): List<TvInput> {
        ShotHooks.inputs?.let { return it }
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()
        val visible = runCatching { manager.tvInputList }.getOrNull().orEmpty()
            .filter { it.isPassthroughInput && !it.isHidden(context) }

        val byId = visible.associateBy { it.id }
        // A CEC device hangs off the port it is plugged into. Show the device, not the empty port.
        val supersededPorts = visible.mapNotNull { it.parentId }.toSet()

        val tiles = visible
            .filter { it.id !in supersededPorts }
            .map { info -> info to info.parentId?.let { byId[it] } }

        return tiles
            .sortedWith(
                compareBy(
                    { (info, port) -> (port ?: info).type != TvInputInfo.TYPE_HDMI },
                    { (info, port) -> sortKey(port?.genericLabel(context) ?: info.displayLabel(context)) },
                )
            )
            .map { (info, port) ->
                val (name, qualifier) = split(
                    if (port != null) info.genericLabel(context) else info.displayLabel(context)
                )
                TvInput(
                    id = info.id,
                    label = name,
                    // A CEC device supplies its own name, so the port goes underneath as identity.
                    // Otherwise a port's own qualifier goes there, since it does not fit on one line.
                    sub = port?.let { split(it.genericLabel(context)).first } ?: qualifier,
                    connected = runCatching {
                        manager.getInputState(info.id) != TvInputManager.INPUT_STATE_DISCONNECTED
                    }.getOrDefault(true),
                )
            }
    }

    /**
     * Splits a trailing qualifier off a port name.
     *
     * Names such as "HDMI 3 (eARC/ARC)" do not fit a tile on one line, and the bracketed part is
     * a property of the port rather than its name, so it reads better underneath.
     */
    private fun split(label: String): Pair<String, String?> {
        val match = Regex("^(.*?)\\s*\\(([^()]*)\\)\\s*$").find(label) ?: return label to null
        val (name, qualifier) = match.destructured
        return if (name.isBlank()) label to null else name to qualifier
    }

    /** "HDMI 10" has to sort after "HDMI 2", which plain alphabetical ordering gets wrong. */
    private fun sortKey(label: String): String =
        Regex("\\d+").replace(label) { it.value.padStart(4, '0') }

    /** A user-assigned name wins over the manufacturer's, matching what the TV displays elsewhere. */
    private fun TvInputInfo.displayLabel(context: Context): String =
        (loadCustomLabel(context) ?: loadLabel(context))?.toString().orEmpty()

    /** The port's own name. Used underneath a CEC device, where a renamed port would read oddly. */
    private fun TvInputInfo.genericLabel(context: Context): String =
        loadLabel(context)?.toString().orEmpty()

    fun switchIntent(inputId: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))

    /**
     * The TV's own input window.
     *
     * More than one activity claims the action, so resolving it leaves Android to raise a chooser,
     * which is no use on a remote. Pick the component instead, preferring the set's own panel over
     * the stock launcher's.
     */
    fun allInputsIntent(context: Context): Intent? {
        ShotHooks.allInputs?.let { return it }
        val action = Intent(ACTION_VIEW_INPUTS)
        val handlers = runCatching {
            context.packageManager.queryIntentActivities(action, 0)
        }.getOrNull().orEmpty()
        if (handlers.isEmpty()) return null
        val chosen = handlers.firstOrNull { it.activityInfo.packageName != STOCK_LAUNCHER }
            ?: handlers.first()
        return Intent(action).setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name)
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_SETTINGS)

    /**
     * The TV's own search, resolved to one activity so the system raises no chooser.
     *
     * Null when the set has no search at all, and then the button is simply not offered.
     */
    fun searchIntent(context: Context): Intent? {
        ShotHooks.search?.let { return it }
        val action = Intent(ACTION_GLOBAL_SEARCH)
        val handlers = runCatching {
            context.packageManager.queryIntentActivities(action, 0)
        }.getOrNull().orEmpty()
        val chosen = handlers.firstOrNull() ?: return null
        return Intent(action).setClassName(chosen.activityInfo.packageName, chosen.activityInfo.name)
    }

    /** The icon the system itself shows for a destination, so the tile matches the rest of the TV. */
    fun iconFor(context: Context, intent: Intent?): Drawable? {
        if (intent != null && intent === ShotHooks.store) ShotHooks.storeIcon?.let { return it }
        val pm = context.packageManager
        return intent?.let {
            runCatching { pm.resolveActivity(it, 0)?.activityInfo?.loadIcon(pm) }.getOrNull()
        }
    }

    /**
     * The input last switched to from Orbit.
     *
     * The launcher is in the foreground, so no passthrough input is live; highlighting the most
     * recent one is the honest version of the native picker's accent.
     */
    fun lastUsedInput(context: Context): String? = prefs(context).getString(KEY_LAST_INPUT, null)

    fun rememberUse(context: Context, inputId: String) {
        prefs(context).edit().putString(KEY_LAST_INPUT, inputId).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("orbit.inputs", Context.MODE_PRIVATE)

    private const val KEY_LAST_INPUT = "last_input"

    /** The store ships under the same id on every Android TV. */
    fun storeIntent(context: Context): Intent? =
        ShotHooks.store ?: AppRepository.launchIntent(context, "com.android.vending")
}
