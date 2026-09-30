package com.focustag.app

import android.app.Application
import android.os.UserManager
import com.focustag.app.data.service.FocusWatchdogService
import com.focustag.app.domain.SessionLockStore
import com.focustag.app.domain.UninstallBlockController

class FocusTagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!SessionLockStore.isLocked(this)) return
        UninstallBlockController(this).setBlocked(true)
        val um = getSystemService(UserManager::class.java)
        val unlocked = um?.isUserUnlocked != false
        if (unlocked) {
            FocusWatchdogService.setArmed(this, true)
        }
    }
}
