package app.orbit.launcher

import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * Test doubles for the two repositories, read by them when set.
 *
 * In the main source set on purpose: Robolectric has no launcher apps and no TvInputManager, so
 * without a seam here every row is empty and nothing can be driven. The cost is six early
 * returns in production code and six fields that nothing but a test ever writes; they are null
 * in a real build and R8 cannot drop them, which is the trade accepted for being able to test
 * the real screens rather than copies of them.
 */
object ShotHooks {
    var apps: List<LaunchableApp>? = null
    var inputs: List<TvInput>? = null
    var store: Intent? = null
    var storeIcon: Drawable? = null
    var allInputs: Intent? = null
    var search: Intent? = null
    var defaultHome: Boolean? = null
}
