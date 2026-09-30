package com.focustag.app

import android.app.Application
import android.content.Context
import android.os.UserManager
import com.focustag.app.data.service.FocusWatchdogService
import com.focustag.app.domain.SessionLockStore
import com.focustag.app.domain.UninstallBlockController

class FocusTagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        if (!SessionLockStore.isLocked(this)) return
        UninstallBlockController(this).setBlocked(true)
        val unlocked = getSystemService(UserManager::class.java)?.isUserUnlocked != false
        if (unlocked) FocusWatchdogService.setArmed(this, true)
    }

    companion object {
        @Volatile
        var appContext: Context? = null
            private set
    }
}
