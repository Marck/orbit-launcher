package app.orbit.launcher

import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Where a playing session has actually got to.
 *
 * A session publishes its position once and leaves it. Measured on the set: YouTube reported
 * `position=210` with the state still PLAYING a minute into a video, and Orbit wrote 256ms into
 * its memory for it. Taking the position on its own means something watched for minutes is
 * remembered as barely started, and the bar the hero draws from it sits at nothing.
 *
 * The contract is that a reader adds the time since lastPositionUpdateTime, scaled by the
 * speed. What is locked in here is that it does, and that it does not do so while paused.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SessionPositionTest {

    private fun state(
        playback: Int,
        position: Long,
        updatedAt: Long,
        speed: Float = 1f,
    ): PlaybackState = PlaybackState.Builder()
        .setState(playback, position, speed, updatedAt)
        .build()

    @Test
    fun `a playing session is wound forward by the time since it last said anything`() {
        // Published 30s ago at a quarter of a second in, which is what YouTube does.
        val s = state(PlaybackState.STATE_PLAYING, position = 256, updatedAt = 10_000)
        assertEquals(30_256, MediaWatchService.reached(s, now = 40_000))
    }

    @Test
    fun `the speed it is playing at is taken into account`() {
        val s = state(PlaybackState.STATE_PLAYING, position = 0, updatedAt = 0, speed = 2f)
        assertEquals(20_000, MediaWatchService.reached(s, now = 10_000))
    }

    @Test
    fun `a paused session is left exactly where it stopped`() {
        // Winding a paused session forward would invent watching that never happened.
        val s = state(PlaybackState.STATE_PAUSED, position = 120_000, updatedAt = 10_000)
        assertEquals(120_000, MediaWatchService.reached(s, now = 999_000))
    }

    @Test
    fun `a stopped or idle session is left alone too`() {
        for (kind in listOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_BUFFERING)) {
            val s = state(kind, position = 5_000, updatedAt = 0)
            assertEquals("state $kind", 5_000, MediaWatchService.reached(s, now = 60_000))
        }
    }

    @Test
    fun `a clock that has not moved does not wind it backwards`() {
        // lastPositionUpdateTime can read later than now across a boot or a clock change.
        val s = state(PlaybackState.STATE_PLAYING, position = 4_000, updatedAt = 90_000)
        assertEquals(4_000, MediaWatchService.reached(s, now = 10_000))
    }

    @Test
    fun `the reading is the position itself when no time has passed`() {
        val s = state(PlaybackState.STATE_PLAYING, position = 7_777, updatedAt = 50_000)
        assertEquals(7_777, MediaWatchService.reached(s, now = 50_000))
    }

    @Test
    fun `a stopped session is not allowed to wipe what was remembered`() {
        // What the set does: closing the YouTube player publishes STOPPED with the position
        // back at zero, which overwrote a real reading with nothing.
        for (kind in listOf(PlaybackState.STATE_STOPPED, PlaybackState.STATE_NONE, PlaybackState.STATE_ERROR)) {
            assertEquals(
                "state $kind should not be recorded",
                false,
                MediaWatchService.worthRecording(state(kind, position = 0, updatedAt = 0)),
            )
        }
    }

    @Test
    fun `playing and paused are both worth remembering`() {
        // Paused is the clearest resume point there is, so it must not be caught by the guard.
        for (kind in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING)) {
            assertEquals(
                "state $kind should be recorded",
                true,
                MediaWatchService.worthRecording(state(kind, position = 1_000, updatedAt = 0)),
            )
        }
    }

    @Test
    fun `what was recorded on the set would now be a real position`() {
        // The exact numbers read off the TV: 256ms published, duration 9m25s, and the video
        // genuinely about a minute in. Before this it was remembered as 256ms.
        val s = state(PlaybackState.STATE_PLAYING, position = 256, updatedAt = 38_435_159)
        val reached = MediaWatchService.reached(s, now = 38_495_159)
        assertEquals(60_256, reached)
        // And that is a bar worth drawing rather than one sitting at nothing.
        val progress = ContinueWatching.timed(reached, 564_621)
        assertEquals("1 MIN WATCHED, 9 LEFT", progress?.summary)
    }
}
