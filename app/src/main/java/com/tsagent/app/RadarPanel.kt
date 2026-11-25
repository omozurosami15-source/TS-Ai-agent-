package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Animated scan panel: a framed box, a glowing rotating sweep and blinking contacts. */
class RadarPanel(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d; color = Color.argb(170, 0, 255, 156) }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * d; color = Color.argb(255, 0, 255, 156) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d; color = Color.argb(90, 0, 255, 156) }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.argb(200, 2, 16, 10) }
    private val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 0, 255, 156); textSize = 11f * d; typeface = android.graphics.Typeface.MONOSPACE; letterSpacing = 0.12f
    }
    private var t = 0f
    private val contacts = List(7) { i -> Triple(((i * 83) % 360) * 0.0174533f, 0.30f + ((i * 41) % 55) / 100f, i) }
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        t += 0.025f
        val w = width.toFloat(); val h = height.toFloat()
        val pad = 4 * d
        // panel background and frame, matching the rest of the screen
        val panel = RectF(pad, pad, w - pad, h - pad)
        c.drawRoundRect(panel, 8 * d, 8 * d, bg)
        c.drawRoundRect(panel, 8 * d, 8 * d, frame)
        // corner brackets like the rest of the interface
        val L = 14 * d
        for ((x, y, sx, sy) in listOf(
            listOf(panel.left, panel.top, 1f, 1f), listOf(panel.right, panel.top, -1f, 1f),
            listOf(panel.left, panel.bottom, 1f, -1f), listOf(panel.right, panel.bottom, -1f, -1f))) {
            c.drawLine(x, y, x + sx * L, y, corner); c.drawLine(x, y, x, y + sy * L, corner)
        }
        val cx = w / 2f; val cy = h / 2f + 4 * d
        val r = minOf(w, h) * 0.30f
        box.set(cx - r, cy - r, cx + r, cy + r)
        for (k in 1..3) c.drawCircle(cx, cy, r * k / 3f, ring)
        // glowing sweep: a bright leading edge with a fading tail
        val angle = (t * 110f) % 360f
        for (s in 0..18) {
            sweep.color = Color.argb(((1f - s / 19f) * 110).toInt(), 0, 255, 156)
            c.drawArc(box, angle - s * 2.6f, 2.6f, true, sweep)
        }
        // contacts that light up as the sweep passes them
        for ((a, rr, i) in contacts) {
            val ang = a + t * 0.25f
            val rad = r * rr
            val x = cx + cos(ang) * rad; val y = cy + sin(ang) * rad
            val diff = ((angle - Math.toDegrees(ang.toDouble()).toFloat()) % 360f + 360f) % 360f
            val lit = if (diff < 60f) 1f - diff / 60f else 0f
            val pulse = (sin(t * 2.5f + i) + 1f) / 2f
            dot.color = Color.argb((70 + 150 * maxOf(lit, pulse * 0.4f)).toInt().coerceIn(0, 255), 0, 255, 156)
            c.drawCircle(x, y, (2.2f + 2.4f * lit) * d, dot)
        }
        label.typeface = android.graphics.Typeface.DEFAULT_BOLD
        label.textSize = 14f * d
        c.drawText("SIGNAL SCAN", pad + 12 * d, pad + 22 * d, label)
        // signal bars (bottom left) and status (bottom right)
        for (i in 0 until 3) {
            val bh = (9 + 7 * i) * d
            val bx = pad + 12 * d + i * 8 * d
            dot.color = Color.argb(235, 0, 255, 156)
            c.drawRect(bx, h - pad - 10 * d - bh, bx + 5.5f * d, h - pad - 10 * d, dot)
        }
        val chx = w - pad - 86 * d; val chy = h - pad - 18 * d
        dot.color = Color.argb(255, 0, 230, 120)
        c.drawCircle(chx, chy, 8.5f * d, dot)
        ring.strokeWidth = 2f * d; ring.color = Color.argb(255, 2, 16, 10)
        c.drawLine(chx - 4f * d, chy, chx - 1f * d, chy + 3.5f * d, ring); c.drawLine(chx - 1f * d, chy + 3.5f * d, chx + 4.5f * d, chy - 3.5f * d, ring)
        ring.strokeWidth = 1f * d; ring.color = Color.argb(90, 0, 255, 156)
        label.textSize = 12f * d; label.color = Color.WHITE
        c.drawText("Status: OK", chx + 13f * d, chy + 4.5f * d, label)
        label.color = Color.argb(230, 0, 255, 156)
        postInvalidateOnAnimation()
    }
}
