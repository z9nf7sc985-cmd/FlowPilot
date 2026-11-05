package com.flowpilot.automation.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowpilot.automation.service.AutomationService

class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ID) ?: return
        AutomationService.start(context, id)
        ScheduleHelper.scheduleOne(context, id)
    }

    companion object {
        const val EXTRA_ID = "automation_id"
    }
}
