package com.focustag.app.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focustag.app.data.service.FocusWatchdogService
import com.focustag.app.domain.EnforcementCoordinatorHub
import com.focustag.app.domain.SessionLockStore
import com.focustag.app.domain.UninstallBlockController

class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext
        if (SessionLockStore.isLocked(app)) {
            UninstallBlockController(app).setBlocked(true)
            FocusWatchdogService.setArmed(app, true)
        }
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            EnforcementCoordinatorHub.requestHeadlessReconcile(app)
        }
    }
}
