package com.focustag.app.data.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bannerView: TextView? = null

    companion object {
        const val TAG = "FocusTagAccessibility"
        val sessionState = AtomicReference(AccessibilitySessionState())

        @Volatile
        private var instance: FocusTagAccessibilityService? = null
        private val LINES = listOf(
            "%s tried to sneak in. The door said no.",
            "Nice try. %s is sitting this class out.",
            "%s is in timeout. You are not.",
            "Plot twist: %s can wait until the bell.",
            "Caught. %s goes back in the bag."
        )

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
            if (reason == "POLICY") {
                performGlobalAction(GLOBAL_ACTION_HOME)
                showCaughtBanner(pkgName)
                openFocusTag(pkgName)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            if (pkgName != lastAnalyticsPackage || (currentTime - lastAnalyticsTime) >= ANALYTICS_DEBOUNCE_MS) {
                SessionHistoryRepository(applicationContext, state.ownerUserId)
                    .emitInterceptionEvent(state.sessionId, pkgName)
                lastAnalyticsTime = currentTime
                lastAnalyticsPackage = pkgName
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
        val line = LINES.random().format(label)
        mainHandler.post {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            bannerView?.let { runCatching { wm.removeView(it) } }
            val view = TextView(this).apply {
                text = "Back to class\n$line\n\nTap to open FocusTag"
                setTextColor(Color.parseColor("#F6F1E8"))
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(72, 72, 72, 72)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E61B2428"))
                }
                setOnClickListener {
                    hideBanner()
                    openFocusTag(pkgName)
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            runCatching { wm.addView(view, params) }
                .onSuccess { bannerView = view }
                .onFailure { Log.e(TAG, "Banner failed: ${it.message}") }
            mainHandler.removeCallbacks(hideBannerRunnable)
            mainHandler.postDelayed(hideBannerRunnable, 6000)
        }
    }

    private fun openFocusTag(pkgName: String) {
        val line = LINES.random().format(
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkgName, 0)).toString()
            }.getOrDefault("That app")
        )
        val intent = android.content.Intent(this, com.focustag.app.CaughtBannerActivity::class.java)
            .putExtra("line", "Back to class\n$line")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                android.app.NotificationChannel("focus_return", "Back to class", android.app.NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val pending = android.app.PendingIntent.getActivity(
            this,
            7,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(this, "focus_return")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Back to class")
            .setContentText("Tap to open FocusTag")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(7, notification) }
    }

    private val hideBannerRunnable = Runnable { hideBanner() }

    private fun hideBanner() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        bannerView?.let { runCatching { wm.removeView(it) } }
        bannerView = null
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
        hideBanner()
        if (instance === this) instance = null
        if (SessionLockStore.isLocked(applicationContext)) {
            FocusWatchdogService.setArmed(applicationContext, true)
        }
        super.onDestroy()
    }
}
