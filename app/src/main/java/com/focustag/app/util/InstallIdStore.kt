package com.focustag.app.util

import android.content.Context
import java.util.UUID

/** Stable install UUID bound with auth.uid for Pack 2 (not raw tag UID alone). */
object InstallIdStore {
    private const val PREFS = "focus_install"
    private const val KEY = "install_uuid"

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }
}
