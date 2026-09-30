package com.focustag.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focustag.app.domain.EnforcementCoordinatorHub

class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        EnforcementCoordinatorHub.requestHeadlessReconcile(context.applicationContext)
    }
}
