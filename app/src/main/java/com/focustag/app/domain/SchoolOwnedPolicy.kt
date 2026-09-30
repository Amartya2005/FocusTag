package com.focustag.app.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import com.focustag.app.data.receiver.FocusDeviceAdminReceiver

/**
 * Applies when FocusTag is Device Owner or Profile Owner on *any* Android
 * device (Pixel, Samsung, Nothing, Motorola, …). Personal / BYOD phones
 * stay on ACS intercept + fail-closed only — the OS will not let a normal
 * app freeze the Accessibility toggle.
 */
object SchoolOwnedPolicy {

    fun isOwner(context: Context): Boolean {
        val dpm = dpm(context) ?: return false
        return dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
    }

    fun applyBaseline(context: Context) {
        if (!isOwner(context)) return
        val dpm = dpm(context) ?: return
        val admin = admin(context)
        runCatching { dpm.setPermittedAccessibilityServices(admin, listOf(context.packageName)) }
            .onFailure { Log.w(TAG, "permitted ACS: ${it.message}") }
        addRestriction(dpm, admin, UserManager.DISALLOW_UNINSTALL_APPS)
        addRestriction(dpm, admin, UserManager.DISALLOW_APPS_CONTROL)
        addRestriction(dpm, admin, UserManager.DISALLOW_SAFE_BOOT)
        addRestriction(dpm, admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, true) }
    }

    fun setSessionLocked(context: Context, locked: Boolean) {
        SessionLockStore.setLocked(context, locked)
        if (!isOwner(context)) return
        val dpm = dpm(context) ?: return
        val admin = admin(context)
        runCatching { dpm.setUninstallBlocked(admin, context.packageName, locked || isOwner(context)) }
        if (locked) applyBaseline(context)
    }

    private fun addRestriction(dpm: DevicePolicyManager, admin: ComponentName, key: String) {
        runCatching { dpm.addUserRestriction(admin, key) }
            .onFailure { Log.w(TAG, "restriction $key: ${it.message}") }
    }

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun admin(context: Context) =
        ComponentName(context, FocusDeviceAdminReceiver::class.java)

    private const val TAG = "SchoolOwnedPolicy"
}
