package com.focustag.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallGuardTest {

    @Test
    fun installerWithEmptyWindowIsFailClosed() {
        assertTrue(
            UninstallGuard.shouldIntercept(
                packageName = "com.google.android.packageinstaller",
                className = null,
                windowText = null
            )
        )
    }

    @Test
    fun installerMentioningFocusTagIsBlocked() {
        assertTrue(
            UninstallGuard.shouldIntercept(
                packageName = "com.android.packageinstaller",
                className = "com.android.packageinstaller.UninstallerActivity",
                windowText = "Do you want to uninstall FocusTag?"
            )
        )
    }

    @Test
    fun settingsAppInfoIsBlocked() {
        assertTrue(
            UninstallGuard.shouldIntercept(
                packageName = "com.android.settings",
                className = "com.android.settings.applications.InstalledAppDetails",
                windowText = "FocusTag  Uninstall  Force stop"
            )
        )
    }

    @Test
    fun accessibilitySettingsAreBlocked() {
        assertTrue(
            UninstallGuard.shouldIntercept(
                packageName = "com.android.settings",
                className = "com.android.settings.accessibility.AccessibilitySettings",
                windowText = "Installed apps"
            )
        )
    }

    @Test
    fun launcherUninstallChipForFocusTagIsBlocked() {
        assertTrue(
            UninstallGuard.shouldIntercept(
                packageName = "com.nothing.launcher",
                className = "com.android.launcher3.Launcher",
                windowText = "FocusTag Uninstall"
            )
        )
    }

    @Test
    fun ownPackageNeverIntercepted() {
        assertFalse(
            UninstallGuard.shouldIntercept(
                packageName = UninstallGuard.SELF_PACKAGE,
                className = "com.focustag.app.MainActivity",
                windowText = "Uninstall"
            )
        )
    }

    @Test
    fun unrelatedAppIsNotBlockedByGuard() {
        assertFalse(
            UninstallGuard.shouldIntercept(
                packageName = "com.google.android.calculator",
                className = "com.android.calculator2.Calculator",
                windowText = "Calculator"
            )
        )
    }
}
