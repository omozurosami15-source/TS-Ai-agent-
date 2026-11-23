package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Animated glow behind the opening-screen logo: a soft breathing halo, expanding rings and small dots orbiting the logo. */
class SplashGlow(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val t0 = System.nanoTime()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val purple = 0xFFB04CFF.toInt()
    private val cyan = 0xFF00E5FF.toInt()

    private fun withA(col: Int, a: Int) = Color.argb(a.coerceIn(0, 255), Color.red(col), Color.green(col), Color.blue(col))

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1_000_000_000f
        val cx = width / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f
        val fadeIn = min(1f, t / 0.3f)

        // soft halo that breathes
        val breathe = 0.5f + 0.5f * sin(t * 2.2f)
        val hr = base * (0.66f + 0.06f * breathe)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, hr,
            intArrayOf(withA(purple, (120 * fadeIn).toInt()), withA(purple, 0)),
            floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, hr, p)
        p.shader = null

        // rings that spread outwards and fade
        p.style = Paint.Style.STROKE
        for (k in 0..2) {
            val ph = (t * 0.55f + k / 3f) % 1f
            val r = base * (0.62f + 0.38f * ph)
            p.strokeWidth = (2.2f - 1.4f * ph) * d
            p.color = withA(if (k % 2 == 0) purple else cyan, ((1f - ph) * 160 * fadeIn).toInt())
            c.drawCircle(cx, cy, r, p)
        }

        // dots orbiting the logo
        p.style = Paint.Style.FILL
        for (k in 0..2) {
            val ang = t * (1.4f + 0.3f * k) + k * 2.094f
            val rr = base * (0.84f + 0.03f * sin(t * 2f + k))
            p.color = withA(if (k % 2 == 0) cyan else purple, (230 * fadeIn).toInt())
            c.drawCircle(cx + cos(ang) * rr, cy + sin(ang) * rr, (3.4f - 0.6f * k) * d, p)
        }
        postInvalidateOnAnimation()
    }
}
