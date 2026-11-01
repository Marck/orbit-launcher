package app.orbit.launcher

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An app the television came with is offered in the picker, but supplies nothing until asked.
 *
 * Both halves matter and they pull against each other. Leaving it out of rotation is the point:
 * its rows are the maker's shop front and nobody chose them. Leaving it off the picker as well
 * would be the old bug in a new shape, where an app is excluded with nothing on screen to say
 * so and no way to change it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BackdropShopFrontTest : TvTest() {
    override val outDir = "build/tv-shots/backdrop-shop-front"

    private val mine = "org.jellyfin.androidtv"
    private val shopFront = "com.maker.select"

    @Before
    fun pickerFixture() {
        favorites(myApps, myInputs)
        ShotHooks.watchNext = emptyList()
        ShotHooks.apps = listOf(
            app(mine, "Jellyfin", null),
            app(shopFront, "Maker Select", null, removable = false),
        )
        ShotHooks.pictures = listOf(picture(mine, "Mine"), picture(shopFront, "Theirs"))
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(ContinueWatching.PERMISSION)
    }

    private fun picture(pkg: String, title: String) = Picture(
        packageName = pkg,
        uri = "pic://$pkg/$title",
        title = title,
        seasons = null,
        description = "About $title.",
    )

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
    fun `the picker lists the factory app alongside the owner's`() {
        launch(AppPickerActivity::class.java, backdrops())

        says("Jellyfin")
        says("Maker Select")
    }

    @Test
    fun `only the owner's app is supplying pictures to begin with`() {
        launch(AppPickerActivity::class.java, backdrops())

        // The count is the readable form of the rule: two apps publish, one is in rotation.
        says("Pictures come from 1 app.")
    }

    @Test
    fun `the picker says why its list is shorter than the list of apps`() {
        launch(AppPickerActivity::class.java, backdrops())

        says("publish rows to the TV's own home screen")
    }

    @Test
    fun `a factory app the owner has made a favorite is theirs, so it supplies from the start`() {
        favorites(myApps + shopFront, myInputs)
        launch(AppPickerActivity::class.java, backdrops())

        says("Pictures come from 2 apps.")
    }

    @Test
    fun `the owner can turn the factory app on, which is the half a default must not take away`() {
        Backdrops.choose(context, shopFront, on = true)
        launch(AppPickerActivity::class.java, backdrops())

        says("Pictures come from 2 apps.")
        assertTrue(
            "turning it on should let it through",
            Backdrops.allowed(context)(shopFront),
        )
    }

    @Test
    fun `turning off the owner's own app leaves nothing supplying`() {
        Backdrops.choose(context, mine, on = false)
        launch(AppPickerActivity::class.java, backdrops())

        says("No app is supplying pictures")
    }
}
