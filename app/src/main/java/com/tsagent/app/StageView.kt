package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Main animated stage: glowing orb, orbit rings, voice ring, circuit lines to menu nodes. */
class StageView(ctx: Context) : View(ctx) {
    var state = 0            // 0 ready, 1 listening, 2 speaking, 3 thinking
    var caption: String = ""
    var onNode: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val nodes = listOf(
        "MEMORY" to 0xFF00FF9C.toInt(), "CHAT" to 0xFF00E5FF.toInt(),
        "SOUL" to 0xFFFF2BD6.toInt()
    )
    private val labels = arrayOf("READY", "LISTENING", "SPEAKING", "THINKING")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val n = 300
    private val pts = (0 until n).map { i ->
        val y = 1f - 2f * (i + 0.5f) / n
        val r = sqrt(1f - y * y)
        val th = i * 2.399963f
        floatArrayOf(cos(th) * r, y, sin(th) * r)
    }
    private val dust = List(36) {
        floatArrayOf(Math.random().toFloat(), Math.random().toFloat(),
            0.0006f + Math.random().toFloat() * 0.0012f, 0.6f + Math.random().toFloat() * 1.4f)
    }
    private val rects = List(4) { RectF() }
    private val paths = List(4) { Path() }
    private val measure = PathMeasure()
    private val pos = FloatArray(2)
    private val ring = Path()
    private var ringLen = 1f
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private var t = 0f
    private var cur = 0xFF00FF9C.toInt()
    private var cx = 0f
    private var cy = 0f
    private var rad = 0f

    private fun lerp(a: Int, b: Int, f: Float) = (a + (b - a) * f).toInt()
    private fun blend(a: Int, b: Int, f: Float) = Color.argb(
        lerp(Color.alpha(a), Color.alpha(b), f), lerp(Color.red(a), Color.red(b), f),
        lerp(Color.green(a), Color.green(b), f), lerp(Color.blue(a), Color.blue(b), f)
    )
    private fun withAlpha(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w * 0.62f
        cy = h * 0.46f
        rad = min(h * 0.27f, w * 0.20f)
        ring.reset()
        ring.addCircle(cx, cy, rad * 1.5f, Path.Direction.CW)
        ringLen = (2.0 * Math.PI * rad * 1.5f).toFloat()
        for (i in nodes.indices) {
            val top = h * 0.10f + i * (h * 0.21f)
            rects[i].set(2 * d, top, 92 * d, top + 32 * d)
            val sx = rects[i].right
            val sy = rects[i].centerY()
            val ang = atan2(sy - cy, sx - cx)
            val tx = cx + rad * 1.02f * cos(ang)
            val ty = cy + rad * 1.02f * sin(ang)
            val mx = (sx + tx) / 2f
            paths[i].reset()
            paths[i].moveTo(sx, sy)
            paths[i].cubicTo(mx, sy, mx, ty, tx, ty)
        }
    }

    override fun onDraw(c: Canvas) {
        // the developer name, drawn under the SOUL button (where the big SETTINGS button used to be)
        rects.getOrNull(2)?.let { r ->
            val d = resources.displayMetrics.density
            val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(210, 255, 176, 46)
                textSize = 11f * d; typeface = android.graphics.Typeface.MONOSPACE
            }
            c.drawText("dev Tarek4x", r.left, r.bottom + 16 * d, tp)
        }
        t += 0.016f
        val target = when (state) {
            1 -> 0xFF00E5FF.toInt(); 2 -> 0xFFFF2BD6.toInt(); 3 -> 0xFFFFB02E.toInt(); else -> 0xFF00FF9C.toInt()
        }
        cur = blend(cur, target, 0.07f)
        val fast = if (state == 0) 1f else 2.2f
        val w = width.toFloat()
        val h = height.toFloat()

        // drifting dust
        paint.style = Paint.Style.FILL
        for (p in dust) {
            p[1] -= p[2]
            if (p[1] < 0f) { p[1] = 1f; p[0] = Math.random().toFloat() }
            paint.color = Color.argb(70, 0, 255, 156)
            c.drawCircle(p[0] * w, p[1] * h, p[3] * d, paint)
        }

        // glow
        paint.shader = RadialGradient(cx, cy, rad * 1.9f,
            intArrayOf(withAlpha(cur, 120), withAlpha(cur, 0)), floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad * 1.9f, paint)
        paint.shader = null

        // circuit lines
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f * d
        for (i in nodes.indices) {
            paint.color = withAlpha(nodes[i].second, 0x66)
            c.drawPath(paths[i], paint)
        }
        paint.style = Paint.Style.FILL
        for (i in nodes.indices) {
            measure.setPath(paths[i], false)
            val len = measure.length
            for (k in 0..1) {
                val f = (t * 0.22f * fast + i * 0.21f + k * 0.5f) % 1f
                measure.getPosTan(len * f, pos, null)
                paint.color = withAlpha(nodes[i].second, 0x40)
                c.drawCircle(pos[0], pos[1], 6f * d, paint)
                paint.color = withAlpha(nodes[i].second, 0xE6)
                c.drawCircle(pos[0], pos[1], 2.6f * d, paint)
            }
        }

        // menu nodes
        text.textSize = 10.5f * d
        for (i in nodes.indices) {
            val r = rects[i]
            paint.style = Paint.Style.FILL
            paint.color = 0xDD02100A.toInt()
            c.drawRoundRect(r, 6 * d, 6 * d, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * d
            paint.color = nodes[i].second
            c.drawRoundRect(r, 6 * d, 6 * d, paint)
            text.color = nodes[i].second
            c.drawText(nodes[i].first, r.centerX(), r.centerY() + text.textSize * 0.35f, text)
        }

        // orbit rings
        for (k in 0..2) {
            c.save()
            c.rotate(t * (18f + 9f * k) * fast + k * 60f, cx, cy)
            val ow = rad * 1.28f
            val oh = rad * (0.42f + 0.07f * k)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * d
            paint.color = withAlpha(cur, 95)
            c.drawOval(RectF(cx - ow, cy - oh, cx + ow, cy + oh), paint)
            val a = t * (1.5f + 0.5f * k) * fast
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(cur, 60)
            c.drawCircle(cx + ow * cos(a), cy + oh * sin(a), 7f * d, paint)
            paint.color = Color.WHITE
            c.drawCircle(cx + ow * cos(a), cy + oh * sin(a), 2.8f * d, paint)
            c.restore()
        }

        // dotted sphere
        val pulse = when (state) {
            1 -> 1f + 0.04f * sin(t * 6f)
            2 -> 1f + 0.07f * sin(t * 10f)
            3 -> 1f + 0.03f * sin(t * 13f)
            else -> 1f
        }
        val a = t * 0.55f * fast
        val ca = cos(a); val sa = sin(a)
        val tilt = 0.4f
        val ct = cos(tilt); val st = sin(tilt)
        paint.style = Paint.Style.FILL
        for (p in pts) {
            val x1 = p[0] * ca + p[2] * sa
            val z1 = -p[0] * sa + p[2] * ca
            val y2 = p[1] * ct - z1 * st
            val z2 = p[1] * st + z1 * ct
            val depth = (z2 + 1f) / 2f
            val col = blend(cur, 0xFFCFFFE8.toInt(), (p[1] + 1f) / 2f * 0.55f)
            paint.color = withAlpha(col, (60 + 195 * depth).toInt())
            c.drawCircle(cx + x1 * rad * pulse, cy + y2 * rad * pulse, (1.0f + 2.3f * depth) * d, paint)
        }

        // rotating data ring
        ringPaint.textSize = 8.5f * d
        ringPaint.color = withAlpha(cur, 140)
        for (k in 0..1) {
            c.drawTextOnPath("TS-AGENT // SECURE LINK // VOICE ENGINE ONLINE // ", ring,
                (t * 38f * d * fast + k * ringLen / 2f) % ringLen, 0f, ringPaint)
        }

        // state effects
        paint.style = Paint.Style.STROKE
        when (state) {
            1 -> for (k in 0..2) {
                val f = (t * 0.6f + k / 3f) % 1f
                paint.strokeWidth = 1.6f * d
                paint.color = withAlpha(cur, ((1f - f) * 150).toInt())
                c.drawCircle(cx, cy, rad * (1.1f + 0.6f * f), paint)
            }
            2 -> {
                paint.strokeWidth = 2f * d
                paint.color = withAlpha(cur, 210)
                for (i in 0 until 72) {
                    val ang = i * (Math.PI.toFloat() * 2f / 72f)
                    val len = d * (3f + 11f * abs(sin(t * 8f + i * 0.7f) * cos(t * 3f + i * 0.3f)))
                    val r0 = rad * 1.12f
                    c.drawLine(cx + r0 * cos(ang), cy + r0 * sin(ang),
                        cx + (r0 + len) * cos(ang), cy + (r0 + len) * sin(ang), paint)
                }
            }
            3 -> {
                paint.strokeWidth = 3f * d
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = withAlpha(cur, 230)
                val rc = RectF(cx - rad * 1.16f, cy - rad * 1.16f, cx + rad * 1.16f, cy + rad * 1.16f)
                c.drawArc(rc, t * 260f, 90f, false, paint)
                c.drawArc(rc, t * 260f + 180f, 90f, false, paint)
                paint.strokeCap = Paint.Cap.BUTT
            }
        }

        // labels
        paint.style = Paint.Style.FILL
        val baseY = min(cy + rad * 1.72f, h - 24 * d)
        text.textSize = 12f * d
        text.letterSpacing = 0.2f
        text.color = cur
        c.drawText(labels[state], cx, baseY, text)
        if (caption.isNotBlank()) {
            text.textSize = 11f * d
            text.letterSpacing = 0f
            text.color = 0xFF6FBF9A.toInt()
            c.drawText(caption.take(34), cx, baseY + 16 * d, text)
        }
        text.letterSpacing = 0f

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val hit = rects.indexOfFirst { RectF(it.left - 6 * d, it.top - 6 * d, it.right + 6 * d, it.bottom + 6 * d).contains(e.x, e.y) }
        if (hit < 0) return false
        if (e.action == MotionEvent.ACTION_UP) { performClick(); onNode?.invoke(hit) }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
