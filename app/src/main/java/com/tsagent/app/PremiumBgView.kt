package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Animated hero background for a premium design. Redraws about 60 times a second while visible. */
class PremiumBgView(ctx: Context, private val spec: PremiumDesigns.Spec) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var t = 0f
    private var last = 0L
    private val tick = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            if (last != 0L) t += (now - last) / 1_000_000_000f
            last = now
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); last = 0L
        val reduce = context.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("reduce_motion", false)
        if (reduce) invalidate() else post(tick)
    }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }

    private fun a(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        c.drawColor(0xFF03090C.toInt())
        when (spec.style) {
            PremiumDesigns.Style.WAVES -> waves(c, w, h)
            PremiumDesigns.Style.NODES -> nodes(c, w, h)
            PremiumDesigns.Style.GRID -> grid(c, w, h)
            PremiumDesigns.Style.ORB -> orb(c, w, h)
            PremiumDesigns.Style.BARS -> bars(c, w, h)
            PremiumDesigns.Style.RINGS -> rings(c, w, h)
            PremiumDesigns.Style.SCAN -> scan(c, w, h)
        }
    }

    private fun waves(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.STROKE
        for (k in 0 until 6) {
            path.reset()
            val base = h * (0.35f + 0.05f * k)
            var x = 0f
            var first = true
            while (x <= w) {
                val env = (1f - abs(x / w - 0.5f) * 1.2f).coerceAtLeast(0.1f)
                val y = base + sin(x * 0.02f + t * 2.2f + k * 0.7f) * h * 0.09f * env
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 6f
            }
            p.strokeWidth = if (k == 2) 3.2f else 1.4f
            p.color = a(spec.accent, if (k == 2) 230 else 90)
            c.drawPath(path, p)
        }
    }

    private fun nodes(c: Canvas, w: Float, h: Float) {
        val n = 14
        val xs = FloatArray(n); val ys = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = w * (0.5f + 0.42f * sin(t * 0.35f + i * 1.7f))
            ys[i] = h * (0.5f + 0.36f * cos(t * 0.45f + i * 2.3f))
        }
        val reach = w * 0.45f
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = xs[i] - xs[j]; val dy = ys[i] - ys[j]
                val d = sqrt(dx * dx + dy * dy)
                if (d < reach) {
                    p.color = a(spec.accent, (120 * (1f - d / reach)).toInt())
                    c.drawLine(xs[i], ys[i], xs[j], ys[j], p)
                }
            }
        }
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            p.color = a(spec.accent, 220)
            c.drawCircle(xs[i], ys[i], 3.5f + sin(t * 2f + i) * 1.2f, p)
        }
    }

    private fun grid(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.55f
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f
        for (i in -8..8) {
            p.color = a(spec.accent, 70)
            c.drawLine(w * 0.5f + i * w * 0.02f, horizon, w * 0.5f + i * w * 0.14f, h, p)
        }
        for (k in 0 until 10) {
            val f = (k + (t * 0.6f) % 1f) / 10f
            val y = horizon + (h - horizon) * f * f
            p.color = a(spec.accent, (40 + 120 * f).toInt())
            c.drawLine(0f, y, w, y, p)
        }
        for (r in 0 until 3) {
            val rad = w * 0.12f * (1f + r * 0.7f + (t * 0.3f + r * 0.33f) % 1f)
            p.color = a(spec.accent, 140 - r * 40)
            c.drawCircle(w * 0.5f, horizon, rad, p)
        }
    }

    private fun orb(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) * (0.22f + 0.02f * sin(t * 1.6f))
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 1.6f,
            intArrayOf(a(spec.accent, 170), a(spec.accent, 40), 0x00000000),
            floatArrayOf(0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.6f, p)
        p.shader = null
        p.color = a(spec.accent, 210); c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        for (k in 0 until 3) {
            c.save()
            c.rotate(t * (20f + k * 12f) + k * 60f, cx, cy)
            p.color = a(spec.accent, 170 - k * 40)
            val rx = r * (1.5f + k * 0.2f)
            c.drawOval(cx - rx, cy - r * 0.45f, cx + rx, cy + r * 0.45f, p)
            c.restore()
        }
    }

    private fun bars(c: Canvas, w: Float, h: Float) {
        val px = w * 0.08f; val py = h * 0.14f
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = a(spec.accent, 120)
        c.drawRoundRect(px, py, w - px, h - py, 18f, 18f, p)
        p.style = Paint.Style.FILL
        val n = 14
        val slot = (w - 2 * px) / n
        val bw = slot * 0.6f
        for (i in 0 until n) {
            val f = abs(sin(t * 2f + i * 0.6f))
            val bh = (h - 2 * py) * (0.2f + 0.7f * f)
            val x = px + slot * i + (slot - bw) / 2f
            p.color = a(spec.accent, 90 + (150 * f).toInt())
            c.drawRoundRect(x, h - py - bh, x + bw, h - py, 4f, 4f, p)
        }
    }

    private fun rings(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f
        val base = min(w, h) * 0.42f
        p.style = Paint.Style.STROKE
        for (k in 0 until 4) {
            val r = base * (0.45f + k * 0.18f)
            p.strokeWidth = if (k == 0) 3f else 1.5f
            p.color = a(spec.accent, 200 - k * 35)
            val start = t * (30f + k * 22f) * (if (k % 2 == 0) 1f else -1f)
            c.drawArc(cx - r, cy - r, cx + r, cy + r, start, 110f, false, p)
            c.drawArc(cx - r, cy - r, cx + r, cy + r, start + 180f, 40f, false, p)
        }
        p.style = Paint.Style.FILL; p.color = a(spec.accent, 230)
        c.drawCircle(cx, cy, 6f + 2f * sin(t * 3f), p)
    }

    private fun scan(c: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) * 0.36f
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        for (k in 1..4) { p.color = a(spec.accent, 60 + k * 20); c.drawCircle(cx, cy, r * k / 4f, p) }
        val rad = Math.toRadians(((t * 80f) % 360f).toDouble())
        p.strokeWidth = 2.4f; p.color = a(spec.accent, 230)
        c.drawLine(cx, cy, cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat(), p)
        p.style = Paint.Style.FILL; p.color = a(spec.accent, 50)
        val sy = (t * h * 0.5f) % h
        c.drawRect(0f, sy, w, sy + 6f, p)
    }
}
