package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Records how far into something the TV got, from the playing app's own media session.
 *
 * It is a notification listener because that is the only door: a normal app may call
 * MediaSessionManager.getActiveSessions only if it is one. No notification is ever read. The
 * two callbacks below are left empty on purpose, and nothing here looks at their contents.
 */
class MediaWatchService : NotificationListenerService() {

    private val watched = mutableMapOf<MediaController, MediaController.Callback>()

    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        follow(sessions.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = sessions() ?: return
        val ours = ComponentName(this, MediaWatchService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(onSessions, ours)
            follow(manager.getActiveSessions(ours))
        }
    }

    override fun onListenerDisconnected() {
        runCatching { sessions()?.removeOnActiveSessionsChangedListener(onSessions) }
        follow(emptyList())
        super.onListenerDisconnected()
    }

    /** Nothing is read from notifications. The listener exists only for the sessions. */
    override fun onNotificationPosted(notification: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(notification: StatusBarNotification?) = Unit

    private fun sessions(): MediaSessionManager? =
        getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    /** Follows exactly the sessions that exist now, and lets go of the rest. */
    private fun follow(current: List<MediaController>) {
        watched.keys.filterNot { it in current }.forEach { gone ->
            watched.remove(gone)?.let { runCatching { gone.unregisterCallback(it) } }
        }
        current.filterNot { it in watched }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = mark(controller)
                override fun onMetadataChanged(metadata: MediaMetadata?) = mark(controller)
                override fun onSessionDestroyed() = mark(controller)
            }
            watched[controller] = callback
            runCatching { controller.registerCallback(callback) }
            mark(controller)
        }
    }

    private fun mark(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val state = controller.playbackState ?: return
        // A session that has stopped reports nothing useful: YouTube publishes STOPPED with the
        // position back at zero when the player is closed, which overwrote a real reading with
        // nothing. There is nothing to carry on with from a stopped session anyway, so what was
        // already remembered is left alone.
        if (!worthRecording(state)) return
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: return
        val position = reached(state).let { if (duration > 0) it.coerceAtMost(duration) else it }
        PlaybackMemory.record(this, controller.packageName, title, position, duration)
    }

    companion object {
        /**
         * The states that say nothing about where anything got to.
         *
         * STOPPED and NONE both come with the position reset, and ERROR means the app gave up.
         * Recording any of them replaces a real reading with zero.
         */
        private val FORGETFUL = setOf(
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE,
            PlaybackState.STATE_ERROR,
        )

        /** Whether this state says anything worth remembering about where playback got to. */
        internal fun worthRecording(state: PlaybackState): Boolean = state.state !in FORGETFUL

        /**
         * Where playback has actually got to, rather than where the session last said it was.
         *
         * A session publishes its position once and then leaves it alone: on this set YouTube
         * reports 210ms with the state still PLAYING a minute into a video, and Orbit recorded
         * 256ms for it. The contract is that a reader adds the time elapsed since
         * [PlaybackState.getLastPositionUpdateTime], scaled by the speed. Taking the position on
         * its own means a programme watched for minutes is remembered as barely started, and
         * the hero would draw a bar sitting at nothing.
         *
         * Only while it is playing. A paused session's position is already where it stopped,
         * and winding it forward by the time since would invent watching that did not happen.
         */
        internal fun reached(state: PlaybackState, now: Long = SystemClock.elapsedRealtime()): Long {
            if (state.state != PlaybackState.STATE_PLAYING) return state.position
            val since = now - state.lastPositionUpdateTime
            if (since <= 0) return state.position
            return state.position + (since * state.playbackSpeed).toLong()
        }
    }
}
