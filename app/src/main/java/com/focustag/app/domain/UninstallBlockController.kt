package com.focustag.app.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.focustag.app.data.receiver.FocusDeviceAdminReceiver

/**
 * OS-level uninstall block. Survives reboot only when this package is Device
 * or Profile Owner. ACS intercept still covers the unlocked session.
 */
class UninstallBlockController(private val context: Context) {

    fun setBlocked(blocked: Boolean) {
        SessionLockStore.setLocked(context, blocked)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val admin = ComponentName(context, FocusDeviceAdminReceiver::class.java)
        val owner = dpm.isDeviceOwnerApp(context.packageName) ||
            dpm.isProfileOwnerApp(context.packageName)
        if (!owner) {
            Log.d(TAG, "skip setUninstallBlocked blocked=$blocked (not device/profile owner)")
            return
        }
        try {
            dpm.setUninstallBlocked(admin, context.packageName, blocked)
            Log.i(TAG, "setUninstallBlocked=$blocked")
        } catch (e: SecurityException) {
            Log.w(TAG, "setUninstallBlocked failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "UninstallBlock"
    }
}
