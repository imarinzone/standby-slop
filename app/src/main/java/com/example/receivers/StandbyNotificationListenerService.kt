package com.example.receivers

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.ui.standby.MediaStateHolder

class StandbyNotificationListenerService : NotificationListenerService() {
    private val TAG = "StandbyNotificationListener"
    private var mediaSessionManager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var isListeningToSessions = false

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener connected successfully. Ready to sync music playback state.")
        registerSessionListener()
    }

    override fun onListenerDisconnected() {
        unregisterSessionListener()
        super.onListenerDisconnected()
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handler.post {
            syncControllers(controllers)
        }
    }

    private fun registerSessionListener() {
        if (isListeningToSessions) return
        try {
            val component = ComponentName(this, StandbyNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, component)
            val initial = mediaSessionManager?.getActiveSessions(component)
            syncControllers(initial)
            isListeningToSessions = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering sessions change listener", e)
        }
    }

    private fun unregisterSessionListener() {
        if (!isListeningToSessions) return
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
            clearCallbacks()
            isListeningToSessions = false
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering session listener", e)
        }
    }

    private fun clearCallbacks() {
        activeCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
        activeCallbacks.clear()
    }

    private fun syncControllers(controllers: List<MediaController>?) {
        clearCallbacks()
        if (controllers.isNullOrEmpty()) {
            MediaStateHolder.trackTitle.value = "No Track"
            MediaStateHolder.artistName.value = "Unknown Artist"
            MediaStateHolder.isPlaying.value = false
            MediaStateHolder.albumArt.value = null
            MediaStateHolder.activeAppPackage.value = null
            return
        }

        val activeController = controllers.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING 
        } ?: controllers.first()

        updateMediaState(activeController)

        for (controller in controllers) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handler.post {
                        val refreshed = mediaSessionManager?.getActiveSessions(
                            ComponentName(this@StandbyNotificationListenerService, StandbyNotificationListenerService::class.java)
                        )
                        var target = refreshed?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                        if (target == null) {
                            target = controller
                        }
                        updateMediaState(target)
                    }
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handler.post {
                        updateMediaState(controller)
                    }
                }
            }
            try {
                controller.registerCallback(cb)
                activeCallbacks[controller] = cb
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register controller callback", e)
            }
        }
    }

    private fun updateMediaState(controller: MediaController) {
        try {
            val metadata = controller.metadata
            val playbackState = controller.playbackState
            
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

            var title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            var artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (title.isNullOrBlank()) {
                title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            }
            if (artist.isNullOrBlank()) {
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            }

            if (title.isNullOrBlank()) {
                title = "Unknown Track"
            }
            if (artist.isNullOrBlank()) {
                artist = "Unknown Artist"
            }

            var art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            if (art == null) {
                art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            }

            MediaStateHolder.trackTitle.value = title
            MediaStateHolder.artistName.value = artist
            MediaStateHolder.isPlaying.value = isPlaying
            MediaStateHolder.albumArt.value = art
            MediaStateHolder.activeAppPackage.value = controller.packageName
            
            Log.d(TAG, "Updated media state: $title by $artist (playing=$isPlaying)")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating media state", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            val component = ComponentName(this, StandbyNotificationListenerService::class.java)
            val initial = mediaSessionManager?.getActiveSessions(component)
            syncControllers(initial)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            val component = ComponentName(this, StandbyNotificationListenerService::class.java)
            val initial = mediaSessionManager?.getActiveSessions(component)
            syncControllers(initial)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
