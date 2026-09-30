package com.focustag.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.UserManager
import android.view.WindowManager
import com.focustag.app.data.service.FocusWatchdogService
import com.focustag.app.domain.SchoolOwnedPolicy
import com.focustag.app.domain.SessionLockStore
import com.focustag.app.domain.UninstallBlockController

class FocusTagApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        if (SchoolOwnedPolicy.isOwner(this)) SchoolOwnedPolicy.applyBaseline(this)
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
