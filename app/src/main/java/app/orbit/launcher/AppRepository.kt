package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

/** One launchable app, with the artwork the app itself ships. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val art: Drawable?,
    val isBanner: Boolean,
    /** A factory app cannot be uninstalled, so the option is not offered for one. */
    val removable: Boolean,
    /**
     * A factory app whose update can still come off.
     *
     * Taking it off does not remove the app: it puts back the version the television shipped
     * with and wipes the data. The system's own uninstaller words the two differently, which is
     * how this was confirmed rather than assumed. Asked about Netflix it says "Replace this app
     * with the factory version? All data will be removed."; asked about an app that was never
     * updated it says "Do you want to uninstall this app?" and then cannot do it.
     */
    val factoryUpdated: Boolean = false,
    /** When the package was last installed or updated, or 0 when that could not be read. */
    val updatedAt: Long = 0L,
)

/**
 * Builds the app row from what is installed. Leanback entries first, then any
 * plain launcher entries, so sideloaded phone apps are not invisible.
 *
 * Artwork comes from the app itself: its banner when it has one, otherwise its
 * icon. The launcher ships no third-party artwork.
 */
object AppRepository {

    /**
     * Whether the app came with the television.
     *
     * Read off the flag rather than off where the app lives: an updated factory app moves to
     * /data/app and is then indistinguishable by path from one the owner installed. Measured on
     * the set, Netflix and Prime Video both sit in /data/app and both still carry SYSTEM.
     */
    internal fun removable(flags: Int): Boolean =
        (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0

    /** A factory app that has since been updated, so the update can be taken off again. */
    internal fun factoryUpdated(flags: Int): Boolean =
        !removable(flags) &&
            (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /**
     * Last loaded list, kept for the lifetime of the process.
     *
     * Reading every launcher activity and decoding its artwork takes long enough to see, and the
     * launcher is reopened constantly. Showing the previous list straight away makes reopening
     * instant, and the refresh replaces it when it lands.
     */
    @Volatile
    private var cache: List<LaunchableApp>? = null

    fun cached(): List<LaunchableApp> = cache.orEmpty()

    /**
     * Every launchable app, or only those named in [only].
     *
     * The home screen asks for its favorites first. Reading and decoding a hundred apps takes
     * long enough to watch on a TV chip, and the rows only show the favorites, so the screen is
     * finished as soon as those are in; the rest arrives behind them, for the pickers and for
     * naming the app a picture came from.
     *
     * Artwork is only read again for a package updated since the last read. Decoding every app's
     * banner on every return was most of the cost of a refresh, and handing back the same artwork
     * lets the tiles keep the bitmaps they already made from it. The label is always read again,
     * since it follows the language the TV is set to.
     */
    fun load(context: Context, only: Set<String>? = null): List<LaunchableApp> {
        ShotHooks.apps?.let { hooked ->
            val list = if (only == null) hooked else hooked.filter { it.packageName in only }
            if (only == null) cache = list
            return list
        }
        val pm = context.packageManager
        val leanback = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val plain = query(pm, Intent.CATEGORY_LAUNCHER)
        val previous = cache.orEmpty().associateBy { it.packageName }

        val seen = mutableSetOf(context.packageName)
        val apps = mutableListOf<LaunchableApp>()

        for (info in leanback + plain) {
            val pkg = info.activityInfo.packageName
            if (only != null && pkg !in only) continue
            if (!seen.add(pkg)) continue

            val updatedAt =
                runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            val known = previous[pkg]?.takeIf { updatedAt != 0L && it.updatedAt == updatedAt }
            val art: Drawable?
            val isBanner: Boolean
            if (known != null) {
                art = known.art
                isBanner = known.isBanner
            } else {
                val banner = runCatching { info.activityInfo.loadBanner(pm) }.getOrNull()
                art = banner ?: runCatching { info.loadIcon(pm) }.getOrNull()
                isBanner = banner != null
            }
            apps += LaunchableApp(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                art = art,
                isBanner = isBanner,
                removable = removable(info.activityInfo.applicationInfo.flags),
                factoryUpdated = factoryUpdated(info.activityInfo.applicationInfo.flags),
                updatedAt = updatedAt,
            )
        }
        val sorted = apps.sortedBy { it.label.lowercase() }
        // A list of only some of the apps is not the catalogue, and must not be handed to the
        // next screen that asks for one.
        if (only == null) cache = sorted
        return sorted
    }

    /** Opens [packageName], and says whether the TV had anywhere to send it. */
    /**
     * Starts the app, and says whether it could.
     *
     * Every way of opening an app from Orbit comes through here, so this is where the hero's
     * record of the last one is written. Only once the start has gone through: an app that
     * could not be opened is not something to offer going back to.
     */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = launchIntent(context, packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess.also { started ->
            if (started) LastOpened.record(context, LastOpened.APP, packageName)
        }
    }

    fun launchIntent(context: Context, packageName: String): Intent? {
        ShotHooks.apps?.let { apps ->
            if (apps.any { it.packageName == packageName }) {
                return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName)
            }
        }
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
    }

    private fun query(pm: PackageManager, category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    /**
     * Whether the system still knows the package at all, enabled or not.
     *
     * An app can drop out of the launcher list without being gone: disabled in Settings, in the
     * middle of an update, or on USB storage that has not mounted yet. Only this says it is gone.
     */
    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
        )
    }.isSuccess

    /** True when Orbit is already the system's preferred home app. */
    fun isDefaultHome(context: Context): Boolean {
        ShotHooks.defaultHome?.let { return it }
        val resolved = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
