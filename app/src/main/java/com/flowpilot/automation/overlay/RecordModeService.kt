package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.data.Block
import com.flowpilot.automation.data.BlockType

/**
 * Partial record mode: captures taps/swipes on a transparent overlay as TAP_XY / SWIPE steps,
 * and app-open events from Accessibility. Text entry is not captured reliably (Android limitation).
 */
class RecordModeService : Service() {
    private var wm: WindowManager? = null
    private var panel: View? = null
    private var captureLayer: View? = null
    private val recorded = mutableListOf<Block>()
    private var startX = 0f
    private var startY = 0f
    private var startT = 0L
    private var lastPkg: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishRecording()
                return START_NOT_STICKY
            }
            else -> if (panel == null) startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        recorded.clear()
        lastPkg = FlowAccessibilityService.instance?.foregroundPackage()
        FlowAccessibilityService.foregroundListener = { pkg ->
            if (pkg != null && pkg != lastPkg && pkg != packageName) {
                lastPkg = pkg
                recorded += Block(type = BlockType.OPEN_APP, params = mapOf("package" to pkg))
                updateCount()
            }
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        captureLayer = object : View(this) {}.apply {
            setBackgroundColor(0x22000000)
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.rawX; startY = e.rawY; startT = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = e.rawX - startX
                        val dy = e.rawY - startY
                        val dur = (System.currentTimeMillis() - startT).coerceIn(50, 3000)
                        if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > 40) {
                            recorded += Block(
                                type = BlockType.SWIPE,
                                params = mapOf(
                                    "x1" to startX.toInt().toString(),
                                    "y1" to startY.toInt().toString(),
                                    "x2" to e.rawX.toInt().toString(),
                                    "y2" to e.rawY.toInt().toString(),
                                    "durationMs" to dur.toString()
                                )
                            )
                        } else {
                            recorded += Block(
                                type = BlockType.TAP_XY,
                                params = mapOf(
                                    "x" to e.rawX.toInt().toString(),
                                    "y" to e.rawY.toInt().toString()
                                )
                            )
                        }
                        updateCount()
                        true
                    }
                    else -> true
                }
            }
        }
        val countTv = TextView(this).apply {
            id = android.view.View.generateViewId()
            text = "Recording · 0 steps"
            setTextColor(Color.WHITE)
            setPadding(16, 8, 16, 8)
        }
        val stopBtn = Button(this).apply {
            text = "Stop & save"
            setOnClickListener { finishRecording() }
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCCB71C1C.toInt())
            addView(countTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(stopBtn)
        }
        val fullParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        wm?.addView(captureLayer, fullParams)
        wm?.addView(panel, panelParams)
        Toast.makeText(
            this,
            "Record mode: taps/swipes + app opens. Text typing is not captured.",
            Toast.LENGTH_LONG
        ).show()
        isRecording = true
    }

    private fun updateCount() {
        val tv = (panel as? LinearLayout)?.getChildAt(0) as? TextView
        tv?.text = "Recording · ${recorded.size} steps"
    }

    private fun finishRecording() {
        FlowAccessibilityService.foregroundListener = null
        lastBlocks = recorded.toList()
        listener?.invoke(lastBlocks)
        isRecording = false
        Toast.makeText(this, "Recorded ${lastBlocks.size} steps", Toast.LENGTH_LONG).show()
        stopSelf()
    }

    override fun onDestroy() {
        FlowAccessibilityService.foregroundListener = null
        captureLayer?.let { runCatching { wm?.removeView(it) } }
        panel?.let { runCatching { wm?.removeView(it) } }
        captureLayer = null
        panel = null
        isRecording = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.flowpilot.automation.RECORD_STOP"
        @Volatile var isRecording: Boolean = false
        @Volatile var lastBlocks: List<Block> = emptyList()
        @Volatile var listener: ((List<Block>) -> Unit)? = null

        fun start(context: Context) {
            context.startService(Intent(context, RecordModeService::class.java))
        }
        fun stop(context: Context) {
            context.startService(Intent(context, RecordModeService::class.java).setAction(ACTION_STOP))
        }
    }
}
