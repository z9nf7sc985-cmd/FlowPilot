package com.flowpilot.automation.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.data.Trigger
import com.flowpilot.automation.service.AutomationService

class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val wifiOn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        @Suppress("DEPRECATION")
        val ssid = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.connectionInfo?.ssid?.trim('"') ?: ""
        } catch (_: Exception) { "" }

        val apps = FlowPilotApp.instance.repository.automations.value
        for (a in apps) {
            if (!a.enabled) continue
            val t = a.trigger as? Trigger.Wifi ?: continue
            var fire = true
            t.connected?.let { if (it != wifiOn) fire = false }
            t.ssidContains?.let { if (!ssid.contains(it, ignoreCase = true)) fire = false }
            if (fire && (t.connected != null || t.ssidContains != null)) {
                AutomationService.start(context, a.id)
            }
        }
    }
}
