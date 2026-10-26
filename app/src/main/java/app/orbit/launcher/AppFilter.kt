package app.orbit.launcher

/**
 * Which apps the picker is showing.
 *
 * A set has far more factory apps than installed ones: on the TV this was written for, 18 of
 * the 28 with a launcher came with the television. Those are the ones nobody is looking for,
 * and they were sorted in among the rest by name, so finding something you installed yourself
 * meant reading past ten Sony apps to get to it.
 */
internal enum class AppFilter(val label: String) {
    ALL("All apps"),
    INSTALLED("Installed"),
    FACTORY("Factory"),
    ;

    fun keeps(app: LaunchableApp): Boolean = when (this) {
        ALL -> true
        // The same flag the hold menu uses to decide whether to offer Uninstall, so the two
        // cannot disagree about what came with the television.
        INSTALLED -> app.removable
        FACTORY -> !app.removable
    }
}

/**
 * The order the picker shows apps in: the owner's favorites, then what the owner installed,
 * then what came with the television.
 *
 * Favorites keep the owner's own order and come first whichever kind they are: a favorite is
 * already a thing the owner picked out, and moving it because the TV happens to ship it would
 * be sorting by a fact nobody cares about at that moment.
 *
 * Within the other two groups the alphabetical order the apps arrive in is kept, which is what
 * a stable sort on the group alone gives.
 */
internal fun inPickerOrder(
    apps: List<LaunchableApp>,
    favorites: List<String>,
): List<LaunchableApp> {
    val favoured = favorites.toSet()
    return FavoriteStore.arrange(apps, favorites) { it.packageName }
        .sortedBy { app ->
            when {
                app.packageName in favoured -> 0
                app.removable -> 1
                else -> 2
            }
        }
}
