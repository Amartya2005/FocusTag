package com.focustag.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focustag.app.MainActivity
import com.focustag.app.R

/**
 * Foreground watchdog while a class session is armed.
 * Keeps the process off the OEM idle/kill list on Nothing OS without MDM.
 */
class FocusWatchdogService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Classroom session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FocusTag alive while class is in session"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Focus on")
            .setContentText("Classroom lock is active")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(launch)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focus_watchdog"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "com.focustag.app.WATCHDOG_STOP"

        fun setArmed(context: Context, armed: Boolean) {
            val app = context.applicationContext
            val intent = Intent(app, FocusWatchdogService::class.java)
            if (armed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(intent)
                } else {
                    app.startService(intent)
                }
            } else {
                app.startService(intent.setAction(ACTION_STOP))
            }
        }
    }
}
