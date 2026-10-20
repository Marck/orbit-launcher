package app.orbit.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Brings Orbit forward when the stock launcher appears, so the home key lands
 * on Orbit without the stock launcher having to be disabled.
 *
 * Key filtering is not an option: Android never routes system keys such as
 * HOME to an accessibility service, so onKeyEvent cannot see the home press.
 * Watching for the launcher window instead is what actually works.
 */
class HomeButtonService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        val STOCK_LAUNCHERS = setOf("com.google.android.tvlauncher")
    }
}
