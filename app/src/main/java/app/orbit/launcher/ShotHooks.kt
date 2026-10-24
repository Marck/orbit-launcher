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
 * [now] pins the clock. It is the only hook that is not a repository: a screenshot of a
 * screen with a clock on it is different every minute it is taken, which is enough to make
 * every shot of the home screen differ from the last one for no reason anybody changed.
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

    /** Epoch millis the clocks should read, or null to follow the real one. */
    var now: Long? = null
}
