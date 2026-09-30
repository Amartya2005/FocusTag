package com.focustag.app.domain

/**
 * Pilot uninstall lock while FOCUS_ACTIVE / ACS armed.
 *
 * Device Owner [DevicePolicyManager.setUninstallBlocked] is the only OS-true block.
 * That path stays parked unless the device is already DO. This guard is the ACS
 * fail-closed intercept for Settings app-info and the system package installer.
 */
object UninstallGuard {

    const val SELF_PACKAGE = "com.focustag.app"
    const val SELF_LABEL = "FocusTag"

    val installerPackages: Set<String> = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    val settingsPackages: Set<String> = setOf(
        "com.android.settings",
        "com.nothing.settings",
        "com.android.settings.intelligence"
    )

    val alwaysBlockedPackages: Set<String> = installerPackages

    private val uninstallClassHints = listOf(
        "UninstallerActivity",
        "UninstallAppProgress",
        "UninstallUninstalling",
        "InstalledAppDetails",
        "AppInfoDashboard",
        "AppInfoSettings",
        "ApplicationDetails",
        "InstalledAppDetailsTop",
        "PackageInstallerActivity",
        "DeleteStagedFile"
    )

    private val accessibilityClassHints = listOf(
        "AccessibilitySettings",
        "AccessibilityService",
        "ToggleAccessibilityServicePreferenceFragment",
        "AccessibilityDetailsSettingsFragment"
    )

    fun isInstallerPackage(packageName: String): Boolean =
        installerPackages.contains(packageName)

    fun isSettingsPackage(packageName: String): Boolean =
        settingsPackages.contains(packageName)

    /**
     * @param windowText concatenated visible text / contentDescription from the active window.
     */
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

        if (isInstallerPackage(packageName)) {
            if (text.isBlank()) return true
            if (mentionsSelf) return true
            return text.contains("uninstall", ignoreCase = true) ||
                cls.contains("Uninstall", ignoreCase = true)
        }

        if (isSettingsPackage(packageName)) {
            if (uninstallClassHints.any { cls.contains(it, ignoreCase = true) }) return true
            if (accessibilityClassHints.any { cls.contains(it, ignoreCase = true) }) return true
            if (mentionsSelf && (
                    text.contains("uninstall", ignoreCase = true) ||
                        text.contains("disable", ignoreCase = true) ||
                        text.contains("force stop", ignoreCase = true) ||
                        text.contains("accessibility", ignoreCase = true)
                    )
            ) {
                return true
            }
        }

        return false
    }
}
