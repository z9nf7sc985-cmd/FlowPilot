package com.flowpilot.automation.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flowpilot.automation.engine.RunLog
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FlowAccessibilityService : AccessibilityService() {

    @Volatile var lastForegroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        RunLog.log("Accessibility service connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        RunLog.log("Accessibility service disconnected", com.flowpilot.automation.engine.LogEntry.Level.WARN)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastForegroundPackage = event.packageName?.toString()
            foregroundListener?.invoke(lastForegroundPackage)
        }
    }

    fun foregroundPackage(): String? = lastForegroundPackage

    fun tap(x: Float, y: Float, durationMs: Long = 50): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSync(gesture)
    }

    fun longPress(x: Float, y: Float, durationMs: Long = 800): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGestureSync(GestureDescription.Builder().addStroke(stroke).build())
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 400): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGestureSync(GestureDescription.Builder().addStroke(stroke).build())
    }

    private fun dispatchGestureSync(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        val ok = AtomicReference(false)
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok.set(true); latch.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                ok.set(false); latch.countDown()
            }
        }, null)
        if (!accepted) return false
        latch.await(3, TimeUnit.SECONDS)
        return ok.get()
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
        if (focused == null) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun paste(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, clip)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findEditable(c)
            if (f != null) return f
        }
        return null
    }

    fun clickByText(text: String, partial: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) { n ->
            val t = n.text?.toString() ?: ""
            if (partial) t.contains(text, ignoreCase = true) else t.equals(text, ignoreCase = true)
        } ?: return false
        return clickNode(node)
    }

    fun clickByViewId(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        val node = nodes?.firstOrNull() ?: return false
        return clickNode(node)
    }

    fun clickByDesc(desc: String, partial: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) { n ->
            val d = n.contentDescription?.toString() ?: ""
            if (partial) d.contains(desc, ignoreCase = true) else d.equals(desc, ignoreCase = true)
        } ?: return false
        return clickNode(node)
    }

    fun findText(text: String, partial: Boolean): String? {
        val root = rootInActiveWindow ?: return null
        val node = findNode(root) { n ->
            val t = n.text?.toString() ?: ""
            if (partial) t.contains(text, ignoreCase = true) else t.equals(text, ignoreCase = true)
        } ?: return null
        return node.text?.toString()
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        walk(root) { n ->
            val t = n.text?.toString()
            if (!t.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
        }
        return sb.toString()
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        // Fallback: tap center of bounds
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        return tap(rect.exactCenterX(), rect.exactCenterY())
    }

    private fun findNode(node: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findNode(c, pred)
            if (f != null) return f
        }
        return null
    }

    private fun walk(node: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Unit) {
        visitor(node)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            walk(c, visitor)
        }
    }

    fun takeScreenshotToFile(file: File): Boolean {
        val bmp = captureBitmap() ?: return false
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            true
        } catch (_: Exception) {
            false
        } finally {
            bmp.recycle()
        }
    }

    fun captureBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT >= 30) {
            val latch = CountDownLatch(1)
            val ref = AtomicReference<Bitmap?>()
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hw = screenshot.hardwareBuffer
                            val color = screenshot.colorSpace
                            ref.set(Bitmap.wrapHardwareBuffer(hw, color)?.copy(Bitmap.Config.ARGB_8888, false))
                            hw.close()
                        } catch (_: Exception) {
                            ref.set(null)
                        }
                        latch.countDown()
                    }
                    override fun onFailure(errorCode: Int) {
                        ref.set(null)
                        latch.countDown()
                    }
                }
            )
            latch.await(5, TimeUnit.SECONDS)
            return ref.get()
        }
        // API 29 and below: use last MediaProjection frame if available
        return com.flowpilot.automation.service.ScreenCaptureHolder.latestBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }

    companion object {
        @Volatile var instance: FlowAccessibilityService? = null
        @Volatile var foregroundListener: ((String?) -> Unit)? = null
    }
}
