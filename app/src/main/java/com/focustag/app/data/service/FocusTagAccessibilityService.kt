package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.domain.EnforcementCoordinatorHub
import com.focustag.app.domain.NotificationReplyGuard
import com.focustag.app.domain.UninstallBlockController
import com.focustag.app.domain.UninstallGuard
import java.util.concurrent.atomic.AtomicReference

data class AccessibilitySessionState(
    val ownerUserId: String? = null,
    val sessionId: String? = null,
    val blockedPackages: Set<String> = emptySet(),
    val isArmed: Boolean = false
)

class FocusTagAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "FocusTagAccessibility"
        val sessionState = AtomicReference(AccessibilitySessionState())

        @Volatile
        private var instance: FocusTagAccessibilityService? = null

        fun updateSessionState(newState: AccessibilitySessionState) {
            val previous = sessionState.getAndSet(newState)
            Log.d(
                TAG,
                "Session state updated: isArmed=${newState.isArmed}, owner=${newState.ownerUserId}, blockedCount=${newState.blockedPackages.size}"
            )
            if (previous.isArmed != newState.isArmed) {
                instance?.let { UninstallBlockController(it).setBlocked(newState.isArmed) }
            }
            if (newState.isArmed) {
                FocusNotificationGuardService.sweepArmedSession()
            }
        }
    }

    fun updateSessionStateBridge(newState: AccessibilitySessionState) = updateSessionState(newState)

    object StateManager {
        fun update(newState: AccessibilitySessionState) = updateSessionState(newState)
    }

    private var lastInterceptionTime = 0L
    private var lastInteractedPackage: String? = null
    private val ENFORCEMENT_DEBOUNCE_MS = 350L

    private var lastAnalyticsTime = 0L
    private var lastAnalyticsPackage: String? = null
    private val ANALYTICS_DEBOUNCE_MS = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "FocusTag AccessibilityService successfully connected")
        UninstallBlockController(this).setBlocked(sessionState.get().isArmed)
        EnforcementCoordinatorHub.requestHeadlessReconcile(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Unit
            else -> return
        }
        val pkgName = event.packageName?.toString() ?: return
        if (pkgName.isEmpty() || pkgName == packageName) return

        val state = sessionState.get()
        if (!state.isArmed || state.ownerUserId == null || state.sessionId == null) return

        try {
            val className = event.className?.toString()
            val windowText = collectWindowText(event)
            val policyHit = state.blockedPackages.contains(pkgName)
            val uninstallHit = UninstallGuard.shouldIntercept(pkgName, className, windowText)
            val replyHit = NotificationReplyGuard.looksLikeReplyShade(pkgName, className, windowText)
            if (!policyHit && !uninstallHit && !replyHit) return

            val currentTime = System.currentTimeMillis()
            if (pkgName == lastInteractedPackage && (currentTime - lastInterceptionTime) < ENFORCEMENT_DEBOUNCE_MS) {
                return
            }

            val reason = when {
                UninstallGuard.isFileManagerPackage(pkgName) -> "FILES_GUARD"
                uninstallHit -> "UNINSTALL_GUARD"
                replyHit -> "NOTIF_REPLY"
                else -> "POLICY"
            }
            Log.i(TAG, "INTERCEPTED ($reason): $pkgName / $className. Redirecting to HOME.")
            if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                if (pkgName != lastAnalyticsPackage || (currentTime - lastAnalyticsTime) >= ANALYTICS_DEBOUNCE_MS) {
                    SessionHistoryRepository(applicationContext, state.ownerUserId)
                        .emitInterceptionEvent(state.sessionId, pkgName)
                    lastAnalyticsTime = currentTime
                    lastAnalyticsPackage = pkgName
                }
            }

            lastInterceptionTime = currentTime
            lastInteractedPackage = pkgName
        } catch (e: Exception) {
            Log.e(TAG, "Error processing event: ${e.message}")
        }
    }

    private fun collectWindowText(event: AccessibilityEvent): String {
        val fromEvent = event.text.joinToString(" ") { it.toString() }
        val sourceText = try { event.source?.text?.toString().orEmpty() } catch (_: Exception) { "" }
        val root = rootInActiveWindow ?: return "$fromEvent $sourceText"
        val sb = StringBuilder(fromEvent).append(' ').append(sourceText)
        try {
            walkText(root, sb, 0)
        } catch (_: Exception) {
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
        return sb.toString()
    }

    private fun walkText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 6) return
        node.text?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        node.contentDescription?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                walkText(child, out, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "FocusTag AccessibilityService was interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
        Log.d(TAG, "FocusTag AccessibilityService destroyed")
    }
}
