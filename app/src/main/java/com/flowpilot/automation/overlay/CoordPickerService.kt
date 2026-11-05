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
import android.widget.TextView
import android.widget.Toast

class CoordPickerService : Service() {
    private var wm: WindowManager? = null
    private var view: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (view == null) show()
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            text = "Tap anywhere to capture x,y\n(Long-press to close)"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x66000000)
            textSize = 16f
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        tv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                lastX = e.rawX.toInt()
                lastY = e.rawY.toInt()
                Toast.makeText(this, "Captured: $lastX, $lastY", Toast.LENGTH_LONG).show()
                listener?.invoke(lastX, lastY)
                true
            } else false
        }
        tv.setOnLongClickListener {
            stopSelf()
            true
        }
        view = tv
        wm?.addView(tv, params)
    }

    override fun onDestroy() {
        view?.let { wm?.removeView(it) }
        view = null
        super.onDestroy()
    }

    companion object {
        @Volatile var lastX: Int = 0
        @Volatile var lastY: Int = 0
        @Volatile var listener: ((Int, Int) -> Unit)? = null

        fun start(context: Context) {
            context.startService(Intent(context, CoordPickerService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, CoordPickerService::class.java))
        }
    }
}
