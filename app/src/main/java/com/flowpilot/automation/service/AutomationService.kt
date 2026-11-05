package com.flowpilot.automation.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.R
import com.flowpilot.automation.engine.FlowEngine
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.engine.VariableStore
import com.flowpilot.automation.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutomationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                RunLog.globalKill = true
                job?.cancel()
                RunLog.log("Stopped by user")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RUN -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
                startForeground(NOTIF_ID, buildNotification("Running…"))
                RunLog.globalKill = false
                job?.cancel()
                job = scope.launch { runAutomation(id) }
            }
        }
        return START_STICKY
    }

    private suspend fun runAutomation(id: String) {
        val repo = FlowPilotApp.instance.repository
        val automation = repo.get(id)
        if (automation == null) {
            RunLog.log("Automation not found: $id", com.flowpilot.automation.engine.LogEntry.Level.ERROR)
            stopSelf()
            return
        }
        updateNotification("Running: ${automation.name}")
        val engine = FlowEngine(
            context = this,
            vars = VariableStore(automation.variables),
            automation = automation,
            onSubroutine = { subId ->
                val sub = repo.get(subId) ?: return@FlowEngine
                FlowEngine(this, VariableStore(sub.variables), sub, null).run()
            }
        )
        engine.run()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra("route", "log"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FlowPilotApp.CHANNEL_RUNNER)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("FlowPilot")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "STOP", stop)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "com.flowpilot.automation.RUN"
        const val ACTION_STOP = "com.flowpilot.automation.STOP"
        const val EXTRA_ID = "automation_id"
        private const val NOTIF_ID = 42

        fun start(context: Context, automationId: String) {
            val i = Intent(context, AutomationService::class.java)
                .setAction(ACTION_RUN)
                .putExtra(EXTRA_ID, automationId)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, AutomationService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
