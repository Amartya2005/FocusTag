package com.focustag.app.util

/**
 * Stops intent-flooded tap_focus calls from a malicious local app.
 */
object LaunchGuard {
    private const val MIN_INTERVAL_MS = 900L
    private var lastAcceptAt = 0L

    @Synchronized
    fun tryAccept(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAcceptAt < MIN_INTERVAL_MS) return false
        lastAcceptAt = now
        return true
    }
}
