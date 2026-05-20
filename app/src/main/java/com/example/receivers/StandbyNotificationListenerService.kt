package com.example.receivers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class StandbyNotificationListenerService : NotificationListenerService() {
    private val TAG = "StandbyNotificationListenerService"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification Listener connected successfully. Ready to sync music playback state.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // This service can observe media controller changes or active notifications
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
