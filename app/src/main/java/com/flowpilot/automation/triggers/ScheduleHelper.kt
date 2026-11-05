package com.flowpilot.automation.triggers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import java.util.Calendar

object ScheduleHelper {
    fun rescheduleAll(context: Context) {
        val apps = FlowPilotApp.instance.repository.automations.value
        apps.filter { it.enabled && it.trigger is Trigger.Schedule }.forEach {
            scheduleOne(context, it.id)
        }
    }

    fun scheduleOne(context: Context, automationId: String) {
        val a = FlowPilotApp.instance.repository.get(automationId) ?: return
        val t = a.trigger as? Trigger.Schedule ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .putExtra(ScheduleAlarmReceiver.EXTRA_ID, automationId)
        val pi = PendingIntent.getBroadcast(
            context,
            automationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = if (t.intervalMinutes != null && t.intervalMinutes > 0) {
            System.currentTimeMillis() + t.intervalMinutes * 60_000L
        } else {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, t.hour)
                set(Calendar.MINUTE, t.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, automationId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, automationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
