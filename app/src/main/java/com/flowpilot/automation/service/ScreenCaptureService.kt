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
import com.flowpilot.automation.ui.MainActivity

/**
 * Lightweight foreground service that must be running before/while MediaProjection
 * is active on Android 10. Keeps the capture session alive when FlowPilot is backgrounded.
 */
class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                ScreenCaptureHolder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, FlowPilotApp.CHANNEL_RUNNER)
            .setSmallIcon(R.drawable.ic_stop)
            .setContentTitle("FlowPilot screen capture")
            .setContentText("Screen capture is active for Find image")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Stop capture", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.flowpilot.automation.STOP_CAPTURE"
        private const val NOTIF_ID = 43

        fun start(context: Context) {
            val i = Intent(context, ScreenCaptureService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(i)
            } catch (_: Exception) {
                context.stopService(Intent(context, ScreenCaptureService::class.java))
                ScreenCaptureHolder.stop()
            }
        }
    }
}
