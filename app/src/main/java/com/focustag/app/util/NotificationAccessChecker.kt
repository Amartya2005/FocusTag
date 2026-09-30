package com.focustag.app.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.focustag.app.data.service.FocusNotificationGuardService

object NotificationAccessChecker {

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val expected = ComponentName(context, FocusNotificationGuardService::class.java)
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry)?.equals(expected) == true ||
                entry.contains(expected.flattenToString(), ignoreCase = true) ||
                entry.contains(expected.flattenToShortString(), ignoreCase = true)
        }
    }
}
