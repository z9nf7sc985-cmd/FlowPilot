package com.flowpilot.automation.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = action == Intent.ACTION_POWER_CONNECTED
        val apps = FlowPilotApp.instance.repository.automations.value
        for (a in apps) {
            if (!a.enabled) continue
            val t = a.trigger as? Trigger.Battery ?: continue
            var fire = false
            t.belowPercent?.let { if (level <= it) fire = true }
            t.abovePercent?.let { if (level >= it) fire = true }
            t.onCharging?.let { if (it == charging || (it && action == Intent.ACTION_POWER_CONNECTED) || (!it && action == Intent.ACTION_POWER_DISCONNECTED)) fire = true }
            if (action == Intent.ACTION_BATTERY_LOW && (t.belowPercent != null)) fire = true
            if (fire) AutomationService.start(context, a.id)
        }
    }
}
