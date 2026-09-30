package com.focustag.app.util

import android.content.Context
import java.util.UUID

/** Stable install UUID for tap_focus binding (per-app install). */
object InstallIdStore {
    private const val PREFS = "focustag_install"
    private const val KEY = "install_uuid"

    fun getOrCreate(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }
}
