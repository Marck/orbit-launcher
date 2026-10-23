package app.orbit.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * What the TV says about apps coming, going and being updated.
 *
 * The home screen used to read every launcher activity again on every return, because an app
 * could have been installed or removed while it was away. That is the slowest thing it does on
 * a cold start after a boot, and almost every return finds nothing changed. The system already
 * says when something changes, so Orbit listens for that instead and reads again only then.
 *
 * Watched only while the home screen is on the screen. An app installed while Orbit is not
 * running is picked up by the read it does when it starts.
 */
object PackageWatch {

    /** Bumped whenever an app is installed, removed or replaced. */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Listens until the returned receiver is unregistered.
     *
     * A replace arrives as a remove and an add with EXTRA_REPLACING set on the remove; both are
     * counted, since either way the list has to be read again.
     */
    fun watch(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                revision++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            // An app can also be turned off rather than uninstalled, which takes its launcher
            // entry away just the same.
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        return receiver
    }

    fun stop(context: Context, receiver: BroadcastReceiver) {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
