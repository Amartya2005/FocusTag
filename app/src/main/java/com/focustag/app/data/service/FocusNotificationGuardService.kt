package com.focustag.app.data.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.focustag.app.domain.NotificationReplyGuard

class FocusNotificationGuardService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Notification listener connected")
        sweepIfArmed()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!FocusTagAccessibilityService.sessionState.get().isArmed) return
        if (NotificationReplyGuard.shouldSuppress(sbn)) {
            Log.i(TAG, "Cancel message notification from ${sbn.packageName}")
            cancelNotification(sbn.key)
        }
    }

    private fun sweepIfArmed() {
        if (!FocusTagAccessibilityService.sessionState.get().isArmed) return
        try {
            activeNotifications.orEmpty().forEach { sbn ->
                if (NotificationReplyGuard.shouldSuppress(sbn)) cancelNotification(sbn.key)
            }
        } catch (e: Exception) {
            Log.w(TAG, "sweep failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FocusNotifGuard"

        @Volatile
        private var instance: FocusNotificationGuardService? = null

        fun sweepArmedSession() {
            instance?.sweepIfArmed()
        }
    }
}
