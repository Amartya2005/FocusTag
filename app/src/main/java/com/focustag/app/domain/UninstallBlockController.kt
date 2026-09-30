package com.focustag.app.domain

import android.content.Context

class UninstallBlockController(private val context: Context) {
    fun setBlocked(blocked: Boolean) {
        SchoolOwnedPolicy.setSessionLocked(context, blocked)
    }
}
