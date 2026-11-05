package com.flowpilot.automation.triggers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService

class NotificationTriggerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras?.getCharSequence("android.text")?.toString().orEmpty()
        val pkg = sbn.packageName
        val apps = FlowPilotApp.instance.repository.automations.value
        for (a in apps) {
            if (!a.enabled) continue
            val t = a.trigger as? Trigger.NotificationReceived ?: continue
            if (t.packageName != null && t.packageName != pkg) continue
            if (t.titleContains != null && !title.contains(t.titleContains, true)) continue
            if (t.textContains != null && !text.contains(t.textContains, true)) continue
            AutomationService.start(this, a.id)
        }
    }
}
