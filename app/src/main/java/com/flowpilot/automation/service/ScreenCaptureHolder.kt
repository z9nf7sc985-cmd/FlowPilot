package com.flowpilot.automation.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Holds a MediaProjection session for API 29 screenshot capture.
 * User must grant capture permission once via [requestPermission].
 */
object ScreenCaptureHolder {
    @Volatile var latestBitmap: Bitmap? = null
        private set

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    fun hasProjection(): Boolean = projection != null

    fun start(context: Context, resultCode: Int, data: Intent) {
        // Android 10: start a foreground service first so MediaProjection stays valid
        // when the activity goes to the background.
        ScreenCaptureService.start(context)
        stopProjectionOnly()
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "FlowPilotCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler
        )
        reader?.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bmp = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                if (cropped !== bmp) bmp.recycle()
                latestBitmap?.recycle()
                latestBitmap = cropped
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, handler)
    }

    fun stop() {
        stopProjectionOnly()
    }

    /** Release projection resources without touching the capture FGS. */
    fun stopProjectionOnly() {
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        latestBitmap?.recycle()
        latestBitmap = null
    }

    fun createPermissionIntent(context: Context): Intent {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    const val REQUEST_MEDIA_PROJECTION = 9001
}
