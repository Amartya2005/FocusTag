package com.focustag.app.domain

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Notifications that let a student send a message without opening the app.
 * Used only while a focus session is armed.
 */
object NotificationReplyGuard {

    val messagingPackages: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.instagram.android",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.snapchat.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.discord",
        "com.slack",
        "com.microsoft.teams",
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.google.android.apps.dynamite",
        "com.google.android.talk",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.facebook.katana"
    )

    fun shouldSuppress(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == UninstallGuard.SELF_PACKAGE) return false
        if (messagingPackages.contains(sbn.packageName)) return true
        val notification = sbn.notification ?: return false
        val category = notification.category
        if (category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_SOCIAL) {
            return true
        }
        return hasRemoteInput(notification)
    }

    fun hasRemoteInput(notification: Notification): Boolean {
        val actions = notification.actions ?: return false
        return actions.any { action ->
            val inputs = action.remoteInputs
            inputs != null && inputs.isNotEmpty()
        }
    }

    fun looksLikeReplyShade(packageName: String, className: String?, windowText: String?): Boolean {
        val text = windowText.orEmpty()
        val cls = className.orEmpty()
        val systemUi = packageName == "com.android.systemui" || packageName.startsWith("com.nothing.systemui")
        if (!systemUi) return false
        val replyUi = text.contains("Reply", ignoreCase = true) ||
            text.contains("Send", ignoreCase = true) ||
            cls.contains("RemoteInput", ignoreCase = true) ||
            cls.contains("NotificationReply", ignoreCase = true)
        if (!replyUi) return false
        return messagingPackages.any { pkg ->
            text.contains(pkg, ignoreCase = true)
        } || text.contains("WhatsApp", ignoreCase = true) ||
            text.contains("Instagram", ignoreCase = true) ||
            text.contains("Messages", ignoreCase = true) ||
            text.contains("Telegram", ignoreCase = true) ||
            text.contains("Messenger", ignoreCase = true) ||
            text.contains("Snapchat", ignoreCase = true)
    }
}
