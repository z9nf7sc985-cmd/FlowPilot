package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

data class RecordedSwipe(
    val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long
)

class SwipeRecorderService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) show()
        return START_STICKY
    }

    private fun show() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val trail = TrailView(this)
        var startX = 0f; var startY = 0f; var startT = 0L
        val banner = TextView(this).apply {
            text = "Draw a swipe · Long-press to cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1A237E.toInt())
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER
            setOnLongClickListener { stopSelf(); true }
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0x33000000)
            addView(trail, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(banner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
        }
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY; startT = System.currentTimeMillis()
                    trail.reset(e.rawX, e.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    trail.lineTo(e.rawX, e.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dur = (System.currentTimeMillis() - startT).coerceIn(100, 3000)
                    val swipe = RecordedSwipe(
                        startX.toInt(), startY.toInt(),
                        e.rawX.toInt(), e.rawY.toInt(), dur
                    )
                    lastSwipe = swipe
                    listener?.invoke(swipe)
                    Toast.makeText(
                        this,
                        "Swipe (${swipe.x1},${swipe.y1})→(${swipe.x2},${swipe.y2}) ${dur}ms",
                        Toast.LENGTH_LONG
                    ).show()
                    stopSelf()
                    true
                }
                else -> false
            }
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

    private class TrailView(context: Context) : View(context) {
        private val path = Path()
        private val paint = Paint().apply {
            color = Color.parseColor("#80CBC4")
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        fun reset(x: Float, y: Float) { path.reset(); path.moveTo(x, y); invalidate() }
        fun lineTo(x: Float, y: Float) { path.lineTo(x, y); invalidate() }
        override fun onDraw(canvas: Canvas) { canvas.drawPath(path, paint) }
    }

    companion object {
        @Volatile var lastSwipe: RecordedSwipe? = null
        @Volatile var listener: ((RecordedSwipe) -> Unit)? = null
        fun start(context: Context) {
            context.startService(Intent(context, SwipeRecorderService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, SwipeRecorderService::class.java))
        }
    }
}
