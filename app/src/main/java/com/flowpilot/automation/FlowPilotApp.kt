package com.flowpilot.automation

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.flowpilot.automation.data.AutomationRepository
import com.flowpilot.automation.data.ExampleAutomations
import com.flowpilot.automation.engine.RunLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowPilotApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repository: AutomationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        repository = AutomationRepository(this)
        appScope.launch {
            repository.ensureExamples(ExampleAutomations.all())
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RUNNER,
                    getString(R.string.channel_runner),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    getString(R.string.channel_alerts),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    companion object {
        const val CHANNEL_RUNNER = "flowpilot_runner"
        const val CHANNEL_ALERTS = "flowpilot_alerts"
        lateinit var instance: FlowPilotApp
            private set
    }
}
