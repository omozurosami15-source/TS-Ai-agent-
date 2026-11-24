package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/** Mic button with rotating reticle ring; pulses while listening. */
class MicView(ctx: Context) : View(ctx) {
    var active: Boolean = false
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var down = false      // finger on the button: shrink a little
    private var scale = 1f        // eased toward the target every frame, so it feels smooth

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> down = false
        }
        invalidate()
        return super.onTouchEvent(e)   // keeps the normal click and long-click
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val s = width.coerceAtMost(height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val u = s / 100f
        scale += ((if (down) 0.92f else 1f) - scale) * 0.25f
        c.save()
        c.scale(scale, scale, cx, cy)
        // soft breathing glow: stronger while listening
        val br = 0.5f + 0.5f * sin(t * 2f)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(if (active) 110 else (28 + 22 * br).toInt(), 0, 255, 156)
        c.drawCircle(cx, cy, s * (if (active) 0.44f else 0.40f + 0.03f * br), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * u
        paint.color = Color.argb(170, 0, 255, 156)
        c.save()
        c.rotate(t * 40f * (if (active) 3f else 1f), cx, cy)
        val rr = RectF(cx - s * 0.43f, cy - s * 0.43f, cx + s * 0.43f, cy + s * 0.43f)
        for (i in 0 until 8) c.drawArc(rr, i * 45f, 22f, false, paint)
        c.restore()
        if (active) {
            paint.strokeWidth = 2f * u
            for (k in 0..1) {
                val f = (t * 0.9f + k * 0.5f) % 1f
                paint.color = Color.argb(((1f - f) * 170).toInt(), 0, 229, 255)
                c.drawCircle(cx, cy, s * (0.30f + 0.16f * f), paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(cx - 30 * u, cy - 30 * u, cx + 30 * u, cy + 30 * u,
            Theme.G, Theme.C, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, s * 0.30f, paint)
        paint.shader = null
        paint.color = 0xFF021008.toInt()
        c.drawRoundRect(RectF(cx - 7 * u, cy - 18 * u, cx + 7 * u, cy + 4 * u), 7 * u, 7 * u, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f * u
        paint.strokeCap = Paint.Cap.ROUND
        c.drawArc(RectF(cx - 14 * u, cy - 12 * u, cx + 14 * u, cy + 14 * u), 0f, 180f, false, paint)
        c.drawLine(cx, cy + 14 * u, cx, cy + 21 * u, paint)
        c.drawLine(cx - 7 * u, cy + 21 * u, cx + 7 * u, cy + 21 * u, paint)
        c.restore()
        postInvalidateOnAnimation()
    }
}
