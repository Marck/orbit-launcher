package app.orbit.launcher

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvTestSmoke : TvTest() {
    @Test
    fun okOnSecondTileLaunchesIt() {
        favorites(myApps, myInputs)
        val s = launch(HomeActivity::class.java)
        println("TV focused at start: ${focused()}")
        press(s, KeyEvent.KEYCODE_DPAD_RIGHT)
        println("TV focused after right: ${focused()}")
        while (nextStarted() != null) Unit
        press(s, KeyEvent.KEYCODE_DPAD_CENTER)
        val started = nextStarted()
        println("TV started: $started")
        assertNotNull("OK should start something", started)
        assertEquals(listOf("hdmi1", "ps5", "switch"), stored(FavoriteStore.INPUTS))
    }
}
