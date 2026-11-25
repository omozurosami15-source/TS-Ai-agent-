package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.max

/** CRT overlay: scanlines, moving sweep, vignette, flicker, corner brackets. Touches pass through. */
class ScanView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var vignette: RadialGradient? = null
    private var t = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        vignette = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0xA0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val w = width.toFloat()
        val h = height.toFloat()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = 0x22000000
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, paint); y += 3 * d }

        paint.style = Paint.Style.FILL
        val y0 = ((t * 0.18f) % 1.3f - 0.15f) * h
        paint.shader = LinearGradient(0f, y0, 0f, y0 + 120 * d, intArrayOf(0x0000FF9C, 0x2200FF9C, 0x0000FF9C), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, y0, w, y0 + 120 * d, paint)
        paint.shader = vignette
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        if ((t * 60).toInt() % 197 < 2) { paint.color = 0x0A00FF9C; c.drawRect(0f, 0f, w, h, paint) }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = 0x9900FF9C.toInt()
        val m = 8 * d
        val l = 22 * d
        c.drawLine(m, m, m + l, m, paint); c.drawLine(m, m, m, m + l, paint)
        c.drawLine(w - m, m, w - m - l, m, paint); c.drawLine(w - m, m, w - m, m + l, paint)
        c.drawLine(m, h - m, m + l, h - m, paint); c.drawLine(m, h - m, m, h - m - l, paint)
        c.drawLine(w - m, h - m, w - m - l, h - m, paint); c.drawLine(w - m, h - m, w - m, h - m - l, paint)
        postInvalidateOnAnimation()
    }
}
