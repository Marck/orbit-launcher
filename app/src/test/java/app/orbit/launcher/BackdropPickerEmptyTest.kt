package app.orbit.launcher

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the backdrop picker says when it has nothing to offer.
 *
 * It used to say nothing at all: the title, a sentence about choosing, and then an empty
 * screen. An owner who declined the TV listings saw exactly that every time they opened it,
 * with no reason given and no way forward, and the hero never offers the permission a second
 * time once it has been answered. The two empty cases are different and have to read
 * differently: nothing may be read, against nothing publishes anything.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BackdropPickerEmptyTest : TvTest() {
    override val outDir = "build/tv-shots/backdrop-empty"

    @Before
    fun pickerFixture() {
        favorites(myApps, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.pictures = emptyList()
    }

    private fun backdrops(): Intent =
        Intent(context, AppPickerActivity::class.java)
            .putExtra(AppPickerActivity.EXTRA_BACKDROPS, true)

    private fun onScreen(text: String): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun says(text: String) {
        waitFor("\"$text\" on screen") { onScreen(text) }
        assertTrue("not on screen: $text", onScreen(text))
    }

    @Test
    fun `without the listings it says so, and offers the way to grant them`() {
        launch(AppPickerActivity::class.java, backdrops())
        says("Backdrop pictures")
        says("Orbit needs access to the TV listings")
        says("Allow access")
    }

    @Test
    fun `with the listings and nothing publishing, it says that instead`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
        launch(AppPickerActivity::class.java, backdrops())
        says("Backdrop pictures")
        says("No app on this TV publishes pictures")
        // Nothing to grant, so nothing to press.
        assertFalse("should not offer a permission that is already held", onScreen("Allow access"))
    }

    @Test
    fun `with pictures to offer it shows the apps, not an explanation`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
        ShotHooks.pictures = listOf(
            Picture(
                packageName = "org.jellyfin.androidtv",
                uri = "pic://server/one",
                title = "Something",
                seasons = null,
                description = "About it.",
            )
        )
        launch(AppPickerActivity::class.java, backdrops())
        says("Jellyfin")
        assertFalse("explanation should be gone", onScreen("No app on this TV publishes pictures"))
        assertFalse("explanation should be gone", onScreen("Orbit needs access to the TV listings"))
    }
}
