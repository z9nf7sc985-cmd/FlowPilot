package com.flowpilot.automation.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.flowpilot.automation.FlowPilotApp
import com.flowpilot.automation.a11y.FlowAccessibilityService
import com.flowpilot.automation.image.ImageMatcher
import java.io.File
import java.io.FileOutputStream

class ImageCropPickerService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null
    private var screenshot: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (root == null) {
            Handler(Looper.getMainLooper()).postDelayed({ captureAndShow() }, 400)
        }
        return START_STICKY
    }

    private fun captureAndShow() {
        val a11y = FlowAccessibilityService.instance
        if (a11y == null) {
            Toast.makeText(this, "Enable Accessibility first", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val bmp = a11y.captureBitmap()
        if (bmp == null) {
            Toast.makeText(this, "Screenshot failed — grant screen capture in Setup", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        screenshot = bmp
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val cropView = CropView(this, bmp)
        val status = TextView(this).apply {
            text = "Drag to select region"
            setTextColor(Color.WHITE)
            setPadding(16, 8, 16, 8)
        }
        fun currentCrop(): Bitmap? {
            val (l, t, r, b) = cropView.bmpRect() ?: return null
            if (r - l < 8 || b - t < 8) return null
            return Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                val cropped = currentCrop()
                if (cropped == null) {
                    Toast.makeText(this@ImageCropPickerService, "Drag a larger box", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val name = "pick_${System.currentTimeMillis()}.png"
                val out = File(FlowPilotApp.instance.repository.templatesDir(), name)
                FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                lastSavedPath = out.name
                listener?.invoke(out.name)
                Toast.makeText(this@ImageCropPickerService, "Saved $name", Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
        val testBtn = Button(this).apply {
            text = "Test match"
            setOnClickListener {
                val cropped = currentCrop()
                if (cropped == null) {
                    Toast.makeText(this@ImageCropPickerService, "Drag a box first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val tmp = File(cacheDir, "crop_test.png")
                FileOutputStream(tmp).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                val match = ImageMatcher.findTemplate(bmp, tmp, 0.7)
                tmp.delete()
                status.text = if (match != null) {
                    "Match OK score=${"%.2f".format(match.third)} at (${match.first},${match.second})"
                } else {
                    "No match above 0.70 — try a sharper crop"
                }
            }
        }
        val cancelBtn = Button(this).apply {
            text = "Cancel"
            setOnClickListener { stopSelf() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC1A237E.toInt())
            addView(status, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(testBtn)
            addView(saveBtn)
            addView(cancelBtn)
        }
        val frame = FrameLayout(this).apply {
            addView(cropView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(bar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
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

    override fun onDestroy() {
        root?.let { wm?.removeView(it) }
        root = null
        screenshot?.recycle()
        screenshot = null
        super.onDestroy()
    }

    private class CropView(context: Context, private val bmp: Bitmap) : View(context) {
        private var viewRect = RectF()
        private var bmpLeft = 0f
        private var bmpTop = 0f
        private var bmpRight = 0f
        private var bmpBottom = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private val dim = Paint().apply { color = 0x66000000 }
        private val border = Paint().apply {
            color = Color.parseColor("#80CBC4")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        fun bmpRect(): IntArray? {
            if (bmpRight - bmpLeft < 1 || bmpBottom - bmpTop < 1) return null
            return intArrayOf(
                bmpLeft.toInt().coerceIn(0, bmp.width - 1),
                bmpTop.toInt().coerceIn(0, bmp.height - 1),
                bmpRight.toInt().coerceIn(1, bmp.width),
                bmpBottom.toInt().coerceIn(1, bmp.height)
            )
        }

        private fun layout(): FloatArray {
            val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val ox = (width - dw) / 2f
            val oy = (height - dh) / 2f
            return floatArrayOf(scale, ox, oy)
        }

        override fun onDraw(canvas: Canvas) {
            val (scale, ox, oy) = layout()
            canvas.drawBitmap(bmp, null, RectF(ox, oy, ox + bmp.width * scale, oy + bmp.height * scale), null)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
            if (viewRect.width() > 0) canvas.drawRect(viewRect, border)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val (scale, ox, oy) = layout()
            fun toBmpX(vx: Float) = ((vx - ox) / scale).coerceIn(0f, bmp.width.toFloat())
            fun toBmpY(vy: Float) = ((vy - oy) / scale).coerceIn(0f, bmp.height.toFloat())
            fun toViewX(bx: Float) = ox + bx * scale
            fun toViewY(by: Float) = oy + by * scale
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    startX = toBmpX(event.x)
                    startY = toBmpY(event.y)
                    bmpLeft = startX; bmpTop = startY; bmpRight = startX; bmpBottom = startY
                    viewRect.set(event.x, event.y, event.x, event.y)
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    bmpLeft = minOf(startX, toBmpX(event.x))
                    bmpTop = minOf(startY, toBmpY(event.y))
                    bmpRight = maxOf(startX, toBmpX(event.x))
                    bmpBottom = maxOf(startY, toBmpY(event.y))
                    viewRect.set(toViewX(bmpLeft), toViewY(bmpTop), toViewX(bmpRight), toViewY(bmpBottom))
                    invalidate()
                }
                MotionEvent.ACTION_UP -> dragging = false
            }
            return true
        }
    }

    companion object {
        @Volatile var lastSavedPath: String? = null
        @Volatile var listener: ((String) -> Unit)? = null
        fun start(context: Context) {
            context.startService(Intent(context, ImageCropPickerService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, ImageCropPickerService::class.java))
        }
    }
}
