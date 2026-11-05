package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

data class PickedPoint(
    val x: Int,
    val y: Int,
    val xPercent: Float,
    val yPercent: Float,
    val screenWidth: Int,
    val screenHeight: Int
)

class CoordPickerService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) show()
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm!!.defaultDisplay.getRealMetrics(metrics)
        val sw = metrics.widthPixels
        val sh = metrics.heightPixels

        val cross = CrosshairView(this)
        val banner = TextView(this).apply {
            text = "Tap to pick a point · Long-press to cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1A237E.toInt())
            setPadding(24, 24, 24, 24)
            textSize = 14f
            gravity = Gravity.CENTER
            setOnLongClickListener { stopSelf(); true }
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            addView(cross, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(banner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
        }
        frame.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                cross.setCross(e.rawX, e.rawY)
            }
            if (e.action == MotionEvent.ACTION_UP) {
                val x = e.rawX.toInt()
                val y = e.rawY.toInt()
                val picked = PickedPoint(
                    x = x,
                    y = y,
                    xPercent = if (sw > 0) x * 100f / sw else 0f,
                    yPercent = if (sh > 0) y * 100f / sh else 0f,
                    screenWidth = sw,
                    screenHeight = sh
                )
                lastX = x
                lastY = y
                lastPoint = picked
                listener?.invoke(picked)
                Toast.makeText(
                    this,
                    "Point: $x, $y (${"%.1f".format(picked.xPercent)}%, ${"%.1f".format(picked.yPercent)}%)",
                    Toast.LENGTH_LONG
                ).show()
                stopSelf()
            }
            true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        root = frame
        wm?.addView(frame, params)
    }

    override fun onDestroy() {
        root?.let { wm?.removeView(it) }
        root = null
        super.onDestroy()
    }

    private class CrosshairView(context: Context) : View(context) {
        private var cx = -1f
        private var cy = -1f
        private val paint = Paint().apply {
            color = Color.parseColor("#80CBC4")
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        private val ring = Paint().apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        fun setCross(x: Float, y: Float) {
            cx = x; cy = y; invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (cx < 0) return
            canvas.drawLine(0f, cy, width.toFloat(), cy, paint)
            canvas.drawLine(cx, 0f, cx, height.toFloat(), paint)
            canvas.drawCircle(cx, cy, 28f, ring)
            canvas.drawCircle(cx, cy, 6f, paint)
        }
    }

    companion object {
        @Volatile var lastX: Int = 0
        @Volatile var lastY: Int = 0
        @Volatile var lastPoint: PickedPoint? = null
        @Volatile var listener: ((PickedPoint) -> Unit)? = null

        fun start(context: Context) {
            context.startService(Intent(context, CoordPickerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CoordPickerService::class.java))
        }
    }
}
