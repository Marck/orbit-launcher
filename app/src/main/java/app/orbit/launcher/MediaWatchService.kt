package app.orbit.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
        val position = controller.playbackState?.position ?: return
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: return
        PlaybackMemory.record(this, controller.packageName, title, position, duration)
    }
}
