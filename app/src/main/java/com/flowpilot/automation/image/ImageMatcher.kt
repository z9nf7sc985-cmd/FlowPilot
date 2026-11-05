package com.flowpilot.automation.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin template matcher using normalized cross-correlation on a
 * downscaled grayscale grid. Feasible for small templates on API 29 devices.
 */
object ImageMatcher {

    data class Region(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun findTemplate(
        screen: Bitmap,
        templateFile: File,
        threshold: Double,
        regionSpec: String = "",
        maxDim: Int = 480
    ): Triple<Int, Int, Double>? {
        val template = BitmapFactory.decodeFile(templateFile.absolutePath) ?: return null
        try {
            val region = parseRegion(regionSpec, screen.width, screen.height)
            val cropped = if (region != null) {
                Bitmap.createBitmap(
                    screen,
                    region.left,
                    region.top,
                    region.right - region.left,
                    region.bottom - region.top
                )
            } else screen

            val scale = min(1.0, maxDim.toDouble() / max(cropped.width, cropped.height))
            val sw = max(1, (cropped.width * scale).toInt())
            val sh = max(1, (cropped.height * scale).toInt())
            val tw = max(1, (template.width * scale).toInt())
            val th = max(1, (template.height * scale).toInt())
            if (tw >= sw || th >= sh) return null

            val sGray = toGray(cropped, sw, sh)
            val tGray = toGray(template, tw, th)

            var best = -1.0
            var bestX = 0
            var bestY = 0
            val step = max(1, min(tw, th) / 8)
            for (y in 0..(sh - th) step step) {
                for (x in 0..(sw - tw) step step) {
                    val score = ncc(sGray, sw, sh, tGray, tw, th, x, y)
                    if (score > best) {
                        best = score
                        bestX = x
                        bestY = y
                    }
                }
            }
            // Refine around best
            val refine = step
            for (y in max(0, bestY - refine)..min(sh - th, bestY + refine)) {
                for (x in max(0, bestX - refine)..min(sw - tw, bestX + refine)) {
                    val score = ncc(sGray, sw, sh, tGray, tw, th, x, y)
                    if (score > best) {
                        best = score
                        bestX = x
                        bestY = y
                    }
                }
            }

            if (best < threshold) return null
            val absX = ((bestX + tw / 2.0) / scale).toInt() + (region?.left ?: 0)
            val absY = ((bestY + th / 2.0) / scale).toInt() + (region?.top ?: 0)
            return Triple(absX, absY, best)
        } finally {
            template.recycle()
        }
    }

    private fun parseRegion(spec: String, w: Int, h: Int): Region? {
        if (spec.isBlank()) return null
        val p = spec.split(",").map { it.trim().toIntOrNull() }
        if (p.size != 4 || p.any { it == null }) return null
        val l = p[0]!!.coerceIn(0, w - 1)
        val t = p[1]!!.coerceIn(0, h - 1)
        val r = p[2]!!.coerceIn(l + 1, w)
        val b = p[3]!!.coerceIn(t + 1, h)
        return Region(l, t, r, b)
    }

    private fun toGray(src: Bitmap, w: Int, h: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()
        val out = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            out[i] = (0.299f * r + 0.587f * g + 0.114f * b)
        }
        return out
    }

    private fun ncc(
        screen: FloatArray, sw: Int, sh: Int,
        templ: FloatArray, tw: Int, th: Int,
        ox: Int, oy: Int
    ): Double {
        var sumS = 0.0
        var sumT = 0.0
        var sumSS = 0.0
        var sumTT = 0.0
        var sumST = 0.0
        val n = (tw * th).toDouble()
        for (y in 0 until th) {
            val sRow = (oy + y) * sw + ox
            val tRow = y * tw
            for (x in 0 until tw) {
                val s = screen[sRow + x].toDouble()
                val t = templ[tRow + x].toDouble()
                sumS += s; sumT += t
                sumSS += s * s; sumTT += t * t
                sumST += s * t
            }
        }
        val meanS = sumS / n
        val meanT = sumT / n
        val num = sumST - n * meanS * meanT
        val den = sqrt((sumSS - n * meanS * meanS) * (sumTT - n * meanT * meanT))
        if (den < 1e-9) return -1.0
        return num / den
    }
}
