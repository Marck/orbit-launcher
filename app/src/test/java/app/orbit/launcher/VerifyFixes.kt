package app.orbit.launcher

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class VerifyFixes : TvTest() {

    private fun install(pkg: String) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply { packageName = pkg }
        })
    }

    @Test
    fun a1_installedButMissingKeepsFavorite() {
        favorites(myApps, myInputs)
        install("ar.tvplayer.tv")
        ShotHooks.apps = ShotHooks.apps!!.filter { it.packageName != "ar.tvplayer.tv" }
        launch(HomeActivity::class.java)
        pump(500)
        println("TV stored a1: ${stored(FavoriteStore.APPS)}")
        assertTrue("TiviMate must be kept", "ar.tvplayer.tv" in stored(FavoriteStore.APPS))
    }

    @Test
    fun a2_uninstalledIsPrunedAndAnnounced() {
        favorites(myApps, myInputs)
        val s = launch(HomeActivity::class.java)
        ShotHooks.apps = ShotHooks.apps!!.filter { it.packageName != "nl.uitzendinggemist" }
        s.moveToState(Lifecycle.State.CREATED)
        s.moveToState(Lifecycle.State.RESUMED)
        waitFor("NPO to be pruned") { "nl.uitzendinggemist" !in stored(FavoriteStore.APPS) }
        println("TV stored a2: ${stored(FavoriteStore.APPS)}")
        assertFalse("NPO must be pruned", "nl.uitzendinggemist" in stored(FavoriteStore.APPS))
        assertEquals(5, stored(FavoriteStore.APPS).size)
        shot("verify-a2-gone")
    }
}
