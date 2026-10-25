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

    private val takeover = Takeover()

    /**
     * On some sets the only All inputs panel belongs to the stock launcher, so
     * its window arrives here looking like the launcher itself. Which activity
     * that is only changes when apps are installed or updated, so it is
     * resolved once rather than on every launcher window.
     */
    private val inputsPanel: String? by lazy {
        val component = InputRepository.allInputsIntent(this)?.component ?: return@lazy null
        // A window reports the class that really runs, so an alias is looked
        // through to the activity it stands for.
        val info = runCatching { packageManager.getActivityInfo(component, 0) }.getOrNull()
        info?.targetActivity ?: component.className
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() !in STOCK_LAUNCHERS) return
        // Covering the inputs panel would make it impossible to use, whether
        // Orbit opened it or the remote's input key did.
        val className = event.className?.toString()
        if (className != null && className == inputsPanel) return

        // The event itself proves the launcher is in front, so no foreground check is
        // needed; getRunningTasks would only report our own tasks anyway.
        if (!takeover.claim(SystemClock.elapsedRealtime())) return

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
        // Keep in sync with android:packageNames in res/xml/home_button_service.xml,
        // which stops the system waking the service for any other app's windows.
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.tvlauncher", // Android TV Home
            "com.google.android.apps.tv.launcherx", // Google TV
            "com.google.android.leanbacklauncher", // Android TV before 8.0
        )
    }
}
