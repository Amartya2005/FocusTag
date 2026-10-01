package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focustag.app.FocusTagApp
import com.focustag.app.data.repository.SessionHistoryRepository
import com.focustag.app.domain.EnforcementCoordinatorHub
import com.focustag.app.domain.NotificationReplyGuard
import com.focustag.app.domain.SessionLockStore
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
            val ctx = instance?.applicationContext ?: FocusTagApp.appContext
            if (ctx != null && previous.isArmed != newState.isArmed) {
                SessionLockStore.setLocked(ctx, newState.isArmed)
                UninstallBlockController(ctx).setBlocked(newState.isArmed)
                FocusWatchdogService.setArmed(ctx, newState.isArmed)
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
        val armed = sessionState.get().isArmed
        UninstallBlockController(this).setBlocked(armed)
        if (armed) FocusWatchdogService.setArmed(applicationContext, true)
        EnforcementCoordinatorHub.requestHeadlessReconcile(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Unit
            else -> return
        }
        val pkgName = event.packageName?.toString() ?: return
        if (pkgName.isEmpty() || pkgName == packageName) return

        val state = sessionState.get()
        if (!state.isArmed || state.ownerUserId == null || state.sessionId == null) return

        val knownHit = state.blockedPackages.contains(pkgName) ||
            UninstallGuard.isFileManagerPackage(pkgName) ||
            UninstallGuard.isInstallerPackage(pkgName)

        val needsTree = !knownHit && (
            UninstallGuard.isSettingsPackage(pkgName) ||
                pkgName == "com.android.systemui" ||
                pkgName.contains("launcher", ignoreCase = true)
            )

        if (!knownHit && !needsTree && event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        try {
            val className = event.className?.toString()
            val windowText = if (needsTree) collectWindowText(event) else event.text.joinToString(" ") { it.toString() }
            val policyHit = state.blockedPackages.contains(pkgName)
            val uninstallHit = knownHit || UninstallGuard.shouldIntercept(pkgName, className, windowText)
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
            Log.i(TAG, "INTERCEPTED ($reason): $pkgName / $className")
            if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                if (reason == "POLICY") showCaughtBanner(pkgName)
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

    private fun showCaughtBanner(pkgName: String) {
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkgName, 0)).toString()
        }.getOrDefault("That app")
        val intent = android.content.Intent(this, com.focustag.app.ui.banner.CaughtBannerActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(com.focustag.app.ui.banner.CaughtBannerActivity.EXTRA_APP, label)
        startActivity(intent)
    }

    private fun collectWindowText(event: AccessibilityEvent): String {
        val fromEvent = event.text.joinToString(" ") { it.toString() }
        val root = rootInActiveWindow ?: return fromEvent
        val sb = StringBuilder(fromEvent)
        try {
            walkText(root, sb, 0)
        } catch (_: Exception) {
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return sb.toString()
    }

    private fun walkText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 5) return
        node.text?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        node.contentDescription?.let { if (it.isNotBlank()) out.append(' ').append(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                walkText(child, out, depth + 1)
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "FocusTag AccessibilityService was interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
