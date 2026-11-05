package com.flowpilot.automation.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.flowpilot.automation.R
import com.flowpilot.automation.data.Automation
import com.flowpilot.automation.ui.MainActivity

object ShortcutHelper {
    fun pinRunShortcut(context: Context, automation: Automation): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.flowpilot.automation.RUN_AUTOMATION"
            putExtra("automation_id", automation.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val info = ShortcutInfo.Builder(context, "run_${automation.id}")
            .setShortLabel(automation.name.take(20))
            .setLongLabel("Run ${automation.name}")
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        return sm.requestPinShortcut(info, null)
    }
}
