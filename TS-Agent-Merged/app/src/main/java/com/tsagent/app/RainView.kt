package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Matrix-style falling code behind the UI. */
class RainView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val cell = 14f * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = cell }
    private val glyphs = "01{}<>/\\|=+*#\$%&ABCDEF789:;".toCharArray()
    private var drops = FloatArray(0)
    private var speeds = FloatArray(0)
    private var frame = 0
    private val trail = 16

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cols = (w / cell).toInt() + 1
        drops = FloatArray(cols) { -(Math.random().toFloat() * 30f) }
        speeds = FloatArray(cols) { 0.12f + Math.random().toFloat() * 0.3f }
    }

    override fun onDraw(c: Canvas) {
        frame++
        val rows = height / cell
        for (i in drops.indices) {
            val head = drops[i]
            for (k in 0 until trail) {
                val row = head.toInt() - k
                if (row < 0 || row > rows + 1) continue
                val ch = glyphs[(i * 131 + row * 17 + (frame / 8) * (row % 3 + 1)).mod(glyphs.size)]
                val a = if (k == 0) 170 else (110 * (1f - k / trail.toFloat())).toInt()
                paint.color = if (k == 0) Color.argb(a, 212, 255, 232) else Color.argb(a, 0, 255, 156)
                c.drawText(ch.toString(), i * cell, (row + 1) * cell, paint)
            }
            drops[i] += speeds[i]
            if (head - trail > rows && Math.random() > 0.97) {
                drops[i] = 0f
                speeds[i] = 0.12f + Math.random().toFloat() * 0.3f
            }
        }
        postInvalidateOnAnimation()
    }
}
