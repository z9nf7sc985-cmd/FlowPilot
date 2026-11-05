package com.flowpilot.automation.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return
        val automation = runCatching { FlowPilotApp.instance.repository.get(id) }.getOrNull()
        if (automation == null || !automation.enabled || automation.trigger !is Trigger.Schedule) {
            return
        }
        AutomationService.start(context, id)
        ScheduleHelper.scheduleOne(context, id)
    }

    companion object {
        const val EXTRA_ID = "automation_id"
    }
}
