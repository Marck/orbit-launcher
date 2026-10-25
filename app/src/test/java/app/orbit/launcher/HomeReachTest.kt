package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Whether the home button reaches Orbit, which is not the same as Orbit being the home app.
 *
 * On the set this was written for, the preferred home activity is Orbit and the remote still
 * opens the TV maker's launcher: the key never reaches that resolution. Orbit was treating the
 * recorded preference as proof the button worked, so it said nothing while the button quietly
 * went somewhere else.
 *
 * The one thing no setting can fake is a home press actually arriving. The system puts
 * CATEGORY_HOME on the intent it starts the home app with, and the home button service starts
 * Orbit by class with no category, so an arrival means the TV itself resolved the key here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeReachTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clean() {
        ShotHooks.defaultHome = null
        context.getSharedPreferences("orbit.home", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "",
        )
    }

    private fun serviceOn() {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, HomeButtonService::class.java).flattenToString(),
        )
    }

    @Test
    fun `the service answers for the button whatever the TV resolves`() {
        serviceOn()
        ShotHooks.defaultHome = false
        assertEquals(HomeReach.SERVICE, homeReach(context))
    }

    @Test
    fun `not the home app and no service is the old notice`() {
        ShotHooks.defaultHome = false
        assertEquals(HomeReach.NONE, homeReach(context))
    }

    @Test
    fun `the home app with a press on record is honoured`() {
        ShotHooks.defaultHome = true
        HomePrefs.rememberHomePress(context)
        assertEquals(HomeReach.HONOURED, homeReach(context))
    }

    @Test
    fun `the home app with no press ever is being ignored by the remote`() {
        // Exactly the state of the set: the preference is recorded, nothing has ever arrived.
        ShotHooks.defaultHome = true
        assertEquals(HomeReach.IGNORED, homeReach(context))
    }

    @Test
    fun `a home press is only recorded when the system sends one`() {
        assertFalse("nothing has happened yet", HomePrefs.homePressSeen(context))

        // What the home button service does: by class, no category. Not proof of anything.
        val fromService = Intent(context, HomeActivity::class.java)
        assertFalse(
            "the service's own launch was taken for a home press",
            fromService.categories?.contains(Intent.CATEGORY_HOME) == true,
        )

        // What the system does when it resolves the home key to Orbit.
        val fromSystem = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        assertTrue(fromSystem.categories!!.contains(Intent.CATEGORY_HOME))
    }

    @Test
    fun `once a press has arrived it is remembered`() {
        ShotHooks.defaultHome = true
        assertEquals(HomeReach.IGNORED, homeReach(context))
        HomePrefs.rememberHomePress(context)
        assertEquals(HomeReach.HONOURED, homeReach(context))
    }
}
