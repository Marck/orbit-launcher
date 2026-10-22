package app.orbit.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The record behind "Back to ...": what it keeps, and when it stops offering it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LastOpenedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Writes the record by hand, so an age can be given without moving the clock. */
    private fun aged(kind: String, id: String, ageMs: Long) {
        context.getSharedPreferences("orbit.last", Context.MODE_PRIVATE).edit()
            .putString("kind", kind)
            .putString("id", id)
            .putLong("at", System.currentTimeMillis() - ageMs)
            .commit()
    }

    @Test
    fun `an app just opened is offered again`() {
        LastOpened.record(context, LastOpened.APP, "org.jellyfin.androidtv")
        assertEquals(
            LastOpened.Entry(LastOpened.APP, "org.jellyfin.androidtv"),
            LastOpened.recent(context),
        )
    }

    @Test
    fun `so is an input`() {
        LastOpened.record(context, LastOpened.INPUT, "hdmi1")
        assertEquals(LastOpened.Entry(LastOpened.INPUT, "hdmi1"), LastOpened.recent(context))
    }

    @Test
    fun `the last one wins`() {
        LastOpened.record(context, LastOpened.APP, "first")
        LastOpened.record(context, LastOpened.INPUT, "second")
        assertEquals(LastOpened.Entry(LastOpened.INPUT, "second"), LastOpened.recent(context))
    }

    @Test
    fun `nothing opened yet is not offered`() {
        assertNull(LastOpened.recent(context))
    }

    @Test
    fun `something opened within the night is still offered`() {
        aged(LastOpened.APP, "org.xbmc.kodi", ageMs = 11L * 60 * 60 * 1000)
        assertEquals(LastOpened.Entry(LastOpened.APP, "org.xbmc.kodi"), LastOpened.recent(context))
    }

    @Test
    fun `yesterday is not offered`() {
        aged(LastOpened.APP, "org.xbmc.kodi", ageMs = 13L * 60 * 60 * 1000)
        assertNull(LastOpened.recent(context))
    }

    @Test
    fun `a clock that has gone backwards does not make it fresh forever`() {
        aged(LastOpened.APP, "org.xbmc.kodi", ageMs = -(60L * 60 * 1000))
        assertNull(LastOpened.recent(context))
    }
}
