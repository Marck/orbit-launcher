package app.orbit.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/**
 * Test doubles for the two repositories, read by them when set.
 *
 * In the main source set on purpose: Robolectric has no launcher apps, no TvInputManager and no
 * TV provider, so without a seam here every row is empty, the hero only ever shows the clock and
 * nothing can be driven. The cost is nine early returns in production code and nine fields that
 * nothing but a test ever writes; they are null in a real build and R8 cannot drop them, which
 * is the trade accepted for being able to test the real screens rather than copies of them.
 *
 * [watchNext] and [pictures] stand in for the two provider tables. The rows still go through the
 * real filtering and ordering, so what a test sees is what the device logic does with them.
 * [artwork] stands in for a decoded poster, which no test can fetch or open.
 */
object ShotHooks {
    var apps: List<LaunchableApp>? = null
    var inputs: List<TvInput>? = null
    var store: Intent? = null
    var storeIcon: Drawable? = null
    var allInputs: Intent? = null
    var search: Intent? = null
    var defaultHome: Boolean? = null
    var watchNext: List<Resumable>? = null
    var pictures: List<Picture>? = null
    var artwork: Bitmap? = null
}
