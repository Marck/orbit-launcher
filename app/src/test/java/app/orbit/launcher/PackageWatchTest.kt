package app.orbit.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What makes the home screen read the installed apps again.
 *
 * It used to be every return to the screen, which on a cold start after a boot is the slowest
 * thing it does and almost always finds nothing changed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PackageWatchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun send(action: String, pkg: String = "com.example.app") {
        context.sendBroadcast(Intent(action, Uri.parse("package:$pkg")))
        // Robolectric holds the main looper still, and that is where a registered receiver
        // is called back.
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `an app being installed, removed or replaced is a reason to read again`() {
        val receiver = PackageWatch.watch(context)
        val before = PackageWatch.revision
        send(Intent.ACTION_PACKAGE_ADDED)
        send(Intent.ACTION_PACKAGE_REMOVED)
        send(Intent.ACTION_PACKAGE_REPLACED)
        // An app can be turned off rather than uninstalled, which takes its entry away just
        // the same.
        send(Intent.ACTION_PACKAGE_CHANGED)
        assertEquals(before + 4, PackageWatch.revision)
        PackageWatch.stop(context, receiver)
    }

    @Test
    fun `nothing is read again once the screen has gone`() {
        val receiver = PackageWatch.watch(context)
        PackageWatch.stop(context, receiver)
        val after = PackageWatch.revision
        send(Intent.ACTION_PACKAGE_ADDED)
        assertEquals(after, PackageWatch.revision)
    }

    @Test
    fun `stopping twice is not an error, since a screen can be disposed more than once`() {
        val receiver = PackageWatch.watch(context)
        PackageWatch.stop(context, receiver)
        PackageWatch.stop(context, receiver)
    }

    @Test
    fun `asking for some apps does not leave a partial list behind as the catalogue`() {
        val all = listOf(
            LaunchableApp("a.one", "One", null, isBanner = false, removable = true),
            LaunchableApp("b.two", "Two", null, isBanner = false, removable = true),
            LaunchableApp("c.three", "Three", null, isBanner = false, removable = true),
        )
        ShotHooks.apps = all
        val whole = AppRepository.load(context)
        assertEquals(3, whole.size)
        val favorites = AppRepository.load(context, only = setOf("b.two"))
        assertEquals(listOf("b.two"), favorites.map { it.packageName })
        // The pickers read the cache. A list of only the favorites is not the catalogue.
        assertEquals(3, AppRepository.cached().size)
        ShotHooks.apps = null
    }

    @Test
    fun `an app that is not installed is not invented by asking for it`() {
        ShotHooks.apps = listOf(LaunchableApp("a.one", "One", null, isBanner = false, removable = true))
        val found = AppRepository.load(context, only = setOf("a.one", "gone.away"))
        assertTrue(found.none { it.packageName == "gone.away" })
        ShotHooks.apps = null
    }
}
