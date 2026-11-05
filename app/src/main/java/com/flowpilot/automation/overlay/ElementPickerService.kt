package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.flowpilot.automation.a11y.FlowAccessibilityService

data class PickedElement(
    val text: String,
    val viewId: String,
    val contentDesc: String,
    val className: String,
    val bounds: Rect
)

class ElementPickerService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) show()
        return START_STICKY
    }

    private fun show() {
        val a11y = FlowAccessibilityService.instance
        if (a11y == null) {
            Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val nodes = collectNodes(a11y.rootInActiveWindow)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlay = ElementOverlayView(this, nodes) { picked ->
            lastPicked = picked
            listener?.invoke(picked)
            Toast.makeText(
                this,
                "Picked: ${picked.text.ifBlank { picked.contentDesc.ifBlank { picked.viewId.ifBlank { "element" } } }}",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
        val banner = TextView(this).apply {
            text = "Tap a highlighted box · Long-press to cancel"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC1A237E.toInt())
            setPadding(24, 24, 24, 24)
            textSize = 14f
            gravity = Gravity.CENTER
            setOnLongClickListener { stopSelf(); true }
        }
        val frame = FrameLayout(this).apply {
            addView(overlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(banner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ))
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

    private fun collectNodes(root: AccessibilityNodeInfo?): List<Pair<Rect, AccessibilityNodeInfo>> {
        if (root == null) return emptyList()
        val out = mutableListOf<Pair<Rect, AccessibilityNodeInfo>>()
        fun walk(n: AccessibilityNodeInfo) {
            val r = Rect()
            n.getBoundsInScreen(r)
            val useful = !n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank() ||
                !n.viewIdResourceName.isNullOrBlank() || n.isClickable || n.isEditable
            if (useful && r.width() > 8 && r.height() > 8) {
                out += r to n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(root)
        return out
    }

    override fun onDestroy() {
        root?.let { wm?.removeView(it) }
        root = null
        super.onDestroy()
    }

    private class ElementOverlayView(
        context: Context,
        private val nodes: List<Pair<Rect, AccessibilityNodeInfo>>,
        private val onPick: (PickedElement) -> Unit
    ) : View(context) {
        private val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#80CBC4")
        }
        private val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            color = 0x3380CBC4
        }

        override fun onDraw(canvas: Canvas) {
            nodes.forEach { (r, _) ->
                canvas.drawRect(r, fillPaint)
                canvas.drawRect(r, boxPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val hit = nodes
                    .filter { it.first.contains(x, y) }
                    .minByOrNull { it.first.width() * it.first.height() }
                if (hit != null) {
                    val n = hit.second
                    onPick(
                        PickedElement(
                            text = n.text?.toString().orEmpty(),
                            viewId = n.viewIdResourceName.orEmpty(),
                            contentDesc = n.contentDescription?.toString().orEmpty(),
                            className = n.className?.toString().orEmpty(),
                            bounds = hit.first
                        )
                    )
                }
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    companion object {
        @Volatile var lastPicked: PickedElement? = null
        @Volatile var listener: ((PickedElement) -> Unit)? = null

        fun start(context: Context) {
            context.startService(Intent(context, ElementPickerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ElementPickerService::class.java))
        }
    }
}
