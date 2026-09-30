package com.focustag.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.focustag.app.data.model.AppCategory
import com.focustag.app.data.model.AppInfo

open class AppInventoryRepository(private val context: Context?) {

    private val alwaysRestrictedPackages = setOf(
        "com.android.settings",
        "com.android.vending", // Play Store
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx", // Edge
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.opera.browser",
        "com.brave.browser",
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.twitter.android",
        "com.snapchat.android"
    )

    open fun getInstalledApps(): List<AppInfo> {
        val pm = context!!.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val myPackageName = context.packageName

        return resolveInfos.map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()

            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            val category = when {
                packageName == myPackageName -> AppCategory.CORE
                alwaysRestrictedPackages.contains(packageName) -> AppCategory.RESTRICTED
                appInfo?.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.ALLOWABLE
                else -> AppCategory.RESTRICTED
            }

            AppInfo(packageName, appName, category)
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }
}
