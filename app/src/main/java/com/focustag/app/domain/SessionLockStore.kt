package com.focustag.app.domain

import android.content.Context

/**
 * Survives reboot and credential-encrypted storage delay.
 * Written when a class session arms; read from LOCKED_BOOT so we can
 * re-apply setUninstallBlocked before the student unlocks the phone.
 */
object SessionLockStore {
    private const val PREFS = "session_lock_ce"
    private const val KEY_LOCKED = "locked"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setLocked(context: Context, locked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKED, locked).commit()
    }

    fun isLocked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKED, false)
}
