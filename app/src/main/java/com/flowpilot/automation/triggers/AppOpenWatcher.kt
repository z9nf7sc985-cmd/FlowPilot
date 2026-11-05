package com.flowpilot.automation.triggers

import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService

object AppOpenWatcher {
    private var lastPkg: String? = null

    fun install() {
        FlowAccessibilityService.foregroundListener = { pkg ->
            if (pkg != null && pkg != lastPkg) {
                lastPkg = pkg
                val apps = FlowPilotApp.instance.repository.automations.value
                for (a in apps) {
                    if (!a.enabled) continue
                    val t = a.trigger as? Trigger.AppOpened ?: continue
                    if (t.packageName == pkg) {
                        AutomationService.start(FlowPilotApp.instance, a.id)
                    }
                }
            }
        }
    }
}
