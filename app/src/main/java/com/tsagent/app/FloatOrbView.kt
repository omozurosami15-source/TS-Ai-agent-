package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Compact version of the main orb for the floating assistant. state: 0 ready, 1 listening, 2 speaking, 3 thinking */
class FloatOrbView(ctx: Context) : View(ctx) {
    var state = 0
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pts = (0 until 150).map { i ->
        val y = 1f - 2f * (i + 0.5f) / 150
        val r = sqrt(1f - y * y)
        val th = i * 2.399963f
        floatArrayOf(cos(th) * r, y, sin(th) * r)
    }
    private var t = 0f
    private var cur = Theme.G

    private fun lerp(a: Int, b: Int, f: Float) = (a + (b - a) * f).toInt()
    private fun blend(a: Int, b: Int, f: Float) = Color.argb(
        lerp(Color.alpha(a), Color.alpha(b), f), lerp(Color.red(a), Color.red(b), f),
        lerp(Color.green(a), Color.green(b), f), lerp(Color.blue(a), Color.blue(b), f))
    private fun withAlpha(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val target = when (state) { 1 -> Theme.C; 2 -> Theme.M; 3 -> Theme.A; else -> Theme.G }
        cur = blend(cur, target, 0.08f)
        val fast = if (state == 0) 1f else 2.2f
        val cx = width / 2f
        val cy = height / 2f
        val rad = min(width, height) * 0.25f

        paint.style = Paint.Style.FILL
        paint.color = 0xB3020A06.toInt()
        c.drawCircle(cx, cy, rad * 1.55f, paint)
        paint.shader = RadialGradient(cx, cy, rad * 1.6f, intArrayOf(withAlpha(cur, 110), withAlpha(cur, 0)), floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad * 1.6f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * d
        paint.color = withAlpha(cur, 200)
        c.drawCircle(cx, cy, rad * 1.55f, paint)

        c.save()
        c.rotate(t * 30f * fast, cx, cy)
        val ow = rad * 1.3f
        val oh = rad * 0.45f
        paint.strokeWidth = 1f * d
        paint.color = withAlpha(cur, 120)
        c.drawOval(RectF(cx - ow, cy - oh, cx + ow, cy + oh), paint)
        val a = t * 2f * fast
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        c.drawCircle(cx + ow * cos(a), cy + oh * sin(a), 2.2f * d, paint)
        c.restore()

        val pulse = when (state) { 1 -> 1f + 0.05f * sin(t * 6f); 2 -> 1f + 0.08f * sin(t * 10f); 3 -> 1f + 0.03f * sin(t * 13f); else -> 1f }
        val ra = t * 0.55f * fast
        val ca = cos(ra); val sa = sin(ra)
        val ct = cos(0.4f); val st = sin(0.4f)
        paint.style = Paint.Style.FILL
        for (p in pts) {
            val x1 = p[0] * ca + p[2] * sa
            val z1 = -p[0] * sa + p[2] * ca
            val y2 = p[1] * ct - z1 * st
            val z2 = p[1] * st + z1 * ct
            val depth = (z2 + 1f) / 2f
            paint.color = withAlpha(blend(cur, 0xFFCFFFE8.toInt(), (p[1] + 1f) / 2f * 0.55f), (60 + 195 * depth).toInt())
            c.drawCircle(cx + x1 * rad * pulse, cy + y2 * rad * pulse, (0.8f + 1.8f * depth) * d, paint)
        }

        paint.style = Paint.Style.STROKE
        when (state) {
            1 -> for (k in 0..1) {
                val f = (t * 0.7f + k * 0.5f) % 1f
                paint.strokeWidth = 1.5f * d
                paint.color = withAlpha(cur, ((1f - f) * 160).toInt())
                c.drawCircle(cx, cy, rad * (1.6f + 0.55f * f), paint)
            }
            2 -> {
                paint.strokeWidth = 1.8f * d
                paint.color = withAlpha(cur, 220)
                for (i in 0 until 36) {
                    val ang = i * (Math.PI.toFloat() * 2f / 36f)
                    val len = d * (2f + 7f * abs(sin(t * 8f + i * 0.9f) * cos(t * 3f + i * 0.4f)))
                    val r0 = rad * 1.62f
                    c.drawLine(cx + r0 * cos(ang), cy + r0 * sin(ang), cx + (r0 + len) * cos(ang), cy + (r0 + len) * sin(ang), paint)
                }
            }
            3 -> {
                paint.strokeWidth = 2.6f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = withAlpha(cur, 230)
                val rc = RectF(cx - rad * 1.7f, cy - rad * 1.7f, cx + rad * 1.7f, cy + rad * 1.7f)
                c.drawArc(rc, t * 260f, 90f, false, paint)
                c.drawArc(rc, t * 260f + 180f, 90f, false, paint)
                paint.strokeCap = Paint.Cap.BUTT
            }
        }
        postInvalidateOnAnimation()
    }
}
