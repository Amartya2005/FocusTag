package com.focustag.app.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.focustag.app.data.receiver.FocusDeviceAdminReceiver

/**
 * OS-level uninstall block. Only succeeds when this app is already Device or
 * Profile Owner. Pilot devices are not enrolled; callers must tolerate a no-op.
 */
class UninstallBlockController(private val context: Context) {

    fun setBlocked(blocked: Boolean) {
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
