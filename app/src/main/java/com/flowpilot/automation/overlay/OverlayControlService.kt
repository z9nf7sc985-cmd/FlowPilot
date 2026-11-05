package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import com.flowpilot.automation.engine.RunLog
import com.flowpilot.automation.service.AutomationService

class OverlayControlService : Service() {
    private var wm: WindowManager? = null
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (view == null) show()
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1A237E.toInt())
            setPadding(16, 12, 16, 12)
        }
        val startBtn = Button(this).apply {
            text = "▶"
            setOnClickListener {
                val id = RunLog.runningAutomationId.value
                    ?: com.flowpilot.automation.FlowPilotApp.instance.repository.automations.value.firstOrNull()?.id
                if (id != null) AutomationService.start(this@OverlayControlService, id)
            }
        }
        val stopBtn = Button(this).apply {
            text = "■"
            setOnClickListener {
                RunLog.globalKill = true
                AutomationService.stop(this@OverlayControlService)
            }
        }
        layout.addView(startBtn)
        layout.addView(stopBtn)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }

        var dragX = 0f
        var dragY = 0f
        layout.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dragX = e.rawX; dragY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - dragX).toInt()
                    val dy = (e.rawY - dragY).toInt()
                    params.x -= dx
                    params.y += dy
                    dragX = e.rawX
                    dragY = e.rawY
                    wm?.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }
        view = layout
        wm?.addView(layout, params)
    }

    override fun onDestroy() {
        view?.let { wm?.removeView(it) }
        view = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, OverlayControlService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayControlService::class.java))
        }
    }
}
