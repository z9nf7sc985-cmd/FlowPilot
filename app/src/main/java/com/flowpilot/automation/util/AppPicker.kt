package com.flowpilot.automation.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object AppPicker {
    fun listLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveFlags = if (Build.VERSION.SDK_INT >= 23) {
            PackageManager.MATCH_ALL
        } else {
            0
        }
        @Suppress("DEPRECATION")
        val infos = pm.queryIntentActivities(intent, resolveFlags)
        return infos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            val label = try {
                ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            } catch (_: Exception) {
                pkg
            }
            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            LaunchableApp(pkg, label, icon)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun labelFor(context: Context, packageName: String): String {
        if (packageName.isBlank()) return "(no app)"
        return try {
            val ai: ApplicationInfo = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
