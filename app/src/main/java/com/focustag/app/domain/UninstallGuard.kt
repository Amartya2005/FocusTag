package com.focustag.app.domain

/**
 * Session-armed uninstall lock.
 *
 * Device Owner setUninstallBlocked is the only OS-true block and stays optional.
 * While ACS is armed we bounce every known uninstall surface: package installer,
 * Settings app-info, Files by Google / DocumentsUI / OEM file managers, and any
 * window that pairs the FocusTag label with Uninstall / Remove / Delete app.
 */
object UninstallGuard {

    const val SELF_PACKAGE = "com.focustag.app"
    const val SELF_LABEL = "FocusTag"

    val installerPackages: Set<String> = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.coloros.packageinstaller",
        "com.oplus.packageinstaller",
        "com.vivo.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    val settingsPackages: Set<String> = setOf(
        "com.android.settings",
        "com.nothing.settings",
        "com.android.settings.intelligence",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.oplus.securitypermission",
        "com.coloros.securitypermission",
        "com.oppo.launcher",
        "com.oplus.launcher",
        "com.android.launcher",
        "com.android.launcher3",
        "com.oplus.appdetail",
        "com.coloros.appdetail",
        "com.vivo.permissionmanager",
        "com.iqoo.secure"
    )

    /** Nothing 3a ships Files by Google. Block the whole app during class. */
    val fileManagerPackages: Set<String> = setOf(
        "com.google.android.apps.nbu.files",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.providers.downloads.ui",
        "com.nothing.files",
        "com.nothing.documentsui",
        "com.sec.android.app.myfiles",
        "com.mi.android.globalFileexplorer",
        "com.android.fileexplorer",
        "com.coloros.filemanager",
        "com.oneplus.filemanager",
        "com.asus.filemanager",
        "com.huawei.hidisk",
        "com.mediatek.filemanager",
        "nextapp.fx",
        "com.lonelycatgames.Xplore",
        "com.mixplorer",
        "org.openintents.filemanager"
    )

    val alwaysBlockedPackages: Set<String> = installerPackages + fileManagerPackages

    private val uninstallClassHints = listOf(
        "UninstallerActivity",
        "UninstallAppProgress",
        "UninstallUninstalling",
        "InstalledAppDetails",
        "ManageApplications",
        "AppInfoDashboard",
        "AppInfoSettings",
        "ApplicationDetails",
        "InstalledAppDetailsTop",
        "PackageInstallerActivity",
        "DeleteStagedFile",
        "UninstallAction",
        "AppInfoActivity"
    )

    private val accessibilityClassHints = listOf(
        "AccessibilitySettings",
        "AccessibilityService",
        "ToggleAccessibilityServicePreferenceFragment",
        "AccessibilityDetailsSettingsFragment",
        "NotificationAccessSettings"
    )

    private val uninstallPhrases = listOf(
        "uninstall",
        "un-install",
        "remove app",
        "delete app",
        "delete this app",
        "remove this app",
        "do you want to uninstall",
        "uninstall this app",
        "want to uninstall",
        "uninstall app",
        "force stop",
        "battery optimization",
        "autostart",
        "accessibility"
    )

    fun isInstallerPackage(packageName: String): Boolean =
        installerPackages.contains(packageName) || packageName.endsWith(".packageinstaller")

    fun isSettingsPackage(packageName: String): Boolean =
        settingsPackages.contains(packageName) || packageName.endsWith(".settings")

    fun isFileManagerPackage(packageName: String): Boolean =
        fileManagerPackages.contains(packageName)

    fun shouldIntercept(
        packageName: String,
        className: String?,
        windowText: String?,
        selfPackage: String = SELF_PACKAGE,
        selfLabel: String = SELF_LABEL
    ): Boolean {
        if (packageName == selfPackage) return false

        val cls = className.orEmpty()
        val text = windowText.orEmpty()
        val mentionsSelf = text.contains(selfPackage, ignoreCase = true) ||
            text.contains(selfLabel, ignoreCase = true)
        val looksLikeUninstall = uninstallPhrases.any { text.contains(it, ignoreCase = true) } ||
            cls.contains("Uninstall", ignoreCase = true)

        if (packageName.contains("appdetail", ignoreCase = true)) return true
        if (packageName.contains("launcher", ignoreCase = true) && looksLikeUninstall) return true
        if (packageName == "com.android.systemui" && looksLikeUninstall) return true
        if (isFileManagerPackage(packageName)) return true
        if (isInstallerPackage(packageName)) return true

        if (isSettingsPackage(packageName)) {
            if (uninstallClassHints.any { cls.contains(it, ignoreCase = true) }) return true
            if (accessibilityClassHints.any { cls.contains(it, ignoreCase = true) }) return true
            if (text.contains("accessibility", ignoreCase = true) && text.contains("FocusTag", ignoreCase = true)) return true
            if (mentionsSelf && (
                    looksLikeUninstall ||
                        text.contains("disable", ignoreCase = true) ||
                        text.contains("force stop", ignoreCase = true) ||
                        text.contains("accessibility", ignoreCase = true) ||
                        text.contains("battery", ignoreCase = true)
                    )
            ) {
                return true
            }
        }

        if (mentionsSelf && looksLikeUninstall) return true
        return false
    }
}
