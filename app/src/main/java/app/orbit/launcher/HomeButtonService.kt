package app.orbit.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Brings Orbit forward when the stock launcher appears, so the home key lands
 * on Orbit without the stock launcher having to be disabled.
 *
 * Key filtering is not an option: Android never routes system keys such as
 * HOME to an accessibility service, so onKeyEvent cannot see the home press.
 * Watching for the launcher window is what actually works.
 */
class HomeButtonService : AccessibilityService() {

    private var lastTakeover = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() !in STOCK_LAUNCHERS) return

        // The event itself proves the launcher is in front, so no foreground
        // check is needed; getRunningTasks would only report our own tasks
        // anyway. The debounce stops repeated events stacking launches.
        val now = SystemClock.elapsedRealtime()
        if (now - lastTakeover < DEBOUNCE_MS) return
        lastTakeover = now

        startActivity(
            Intent(this, HomeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val DEBOUNCE_MS = 700L
        val STOCK_LAUNCHERS = setOf("com.google.android.tvlauncher")
    }
}
