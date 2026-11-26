package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
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
    /** False while a plan's real 3D object is shown in the middle instead of the dotted globe. */
    var showOrb = true
    val orbX: Float get() = cx
    val orbY: Float get() = cy
    val orbR: Float get() = rad
    private val nodeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }

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
        cx = w * 0.64f
        cy = h * 0.47f
        rad = min(h * 0.27f, w * 0.20f)
        ring.reset()
        ring.addCircle(cx, cy, rad * 1.5f, Path.Direction.CW)
        ringLen = (2.0 * Math.PI * rad * 1.5f).toFloat()
        val bh = (h * 0.165f).coerceIn(34f * d, 58f * d)
        val gap = bh * 0.22f
        val top0 = h * 0.03f
        val bw = w * 0.31f
        rects[3].set(2 * d, top0 + 3 * (bh + gap), 2 * d + bw, top0 + 3 * (bh + gap) + bh * 0.78f)
        for (i in nodes.indices) {
            val top = top0 + i * (bh + gap)
            rects[i].set(2 * d, top, 2 * d + bw, top + bh)
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
        // developer pill under the SOUL button
        rects[3].let { r ->
            paint.style = Paint.Style.FILL; paint.shader = null
            paint.color = 0xDD100A00.toInt()
            c.drawRoundRect(r, 10 * d, 10 * d, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * d; paint.color = withAlpha(0xFFFFB02E.toInt(), 40)
            c.drawRoundRect(r, 10 * d, 10 * d, paint)
            paint.strokeWidth = 1.5f * d; paint.color = 0xFFFFB02E.toInt()
            c.drawRoundRect(r, 10 * d, 10 * d, paint)
            val cwid = 22f * d; val ccx = r.left + 24f * d; val ccy = r.centerY()
            val crown = Path()
            crown.moveTo(ccx - cwid / 2, ccy + 7 * d); crown.lineTo(ccx - cwid / 2, ccy - 6 * d); crown.lineTo(ccx - cwid / 4, ccy + 1 * d)
            crown.lineTo(ccx, ccy - 8 * d); crown.lineTo(ccx + cwid / 4, ccy + 1 * d); crown.lineTo(ccx + cwid / 2, ccy - 6 * d)
            crown.lineTo(ccx + cwid / 2, ccy + 7 * d); crown.close()
            paint.style = Paint.Style.FILL; paint.color = 0xFFFFC21F.toInt()
            c.drawPath(crown, paint)
            nodeText.textSize = (r.height() * 0.42f).coerceAtMost(15f * d)
            val devX = r.left + 42f * d
            val devRoom = r.right - devX - 10f * d
            while (nodeText.measureText("dev Tarek4x") > devRoom && nodeText.textSize > 8f * d) nodeText.textSize -= 0.5f * d
            nodeText.color = 0xFFFFE2A0.toInt()
            c.drawText("dev Tarek4x", devX, r.centerY() + nodeText.textSize * 0.35f, nodeText)
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

        // menu nodes: neon buttons with an icon, a name and an arrow
        for (i in nodes.indices) {
            val r = rects[i]
            val col = nodes[i].second
            val cut = 9 * d
            val bp = Path()
            bp.moveTo(r.left + cut, r.top); bp.lineTo(r.right - cut, r.top); bp.lineTo(r.right, r.top + cut); bp.lineTo(r.right, r.bottom - cut)
            bp.lineTo(r.right - cut, r.bottom); bp.lineTo(r.left + cut, r.bottom); bp.lineTo(r.left, r.bottom - cut); bp.lineTo(r.left, r.top + cut); bp.close()
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(r.left, r.top, r.right, r.bottom, withAlpha(col, 95), withAlpha(col, 22), Shader.TileMode.CLAMP)
            c.drawPath(bp, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f * d; paint.color = withAlpha(col, 40); c.drawPath(bp, paint)
            paint.strokeWidth = 1.8f * d; paint.color = col; c.drawPath(bp, paint)
            drawNodeIcon(c, i, r.left + 28f * d, r.centerY(), r.height() * 0.5f, col)
            nodeText.textSize = (r.height() * 0.36f).coerceAtMost(15f * d)
            // keep the label inside the box: leave room for the arrow on the right and shrink the text if needed
            val labelX = r.left + 52f * d
            val labelRoom = r.right - labelX - 34f * d
            while (nodeText.measureText(nodes[i].first) > labelRoom && nodeText.textSize > 7f * d) nodeText.textSize -= 0.5f * d
            nodeText.color = blend(col, 0xFFFFFFFF.toInt(), 0.55f)
            // clip to the box, so no letter can ever be drawn outside it
            c.save(); c.clipRect(r.left + 2f * d, r.top, r.right - 2f * d, r.bottom)
            c.drawText(nodes[i].first, labelX, r.centerY() + nodeText.textSize * 0.35f, nodeText)
            c.restore()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f * d; paint.color = nodeText.color
            paint.strokeCap = Paint.Cap.ROUND
            val ax = r.right - 18f * d; val ay = r.centerY()
            c.drawLine(ax - 3f * d, ay - 6f * d, ax + 3f * d, ay, paint); c.drawLine(ax + 3f * d, ay, ax - 3f * d, ay + 6f * d, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }

        // glowing platform under the globe
        paint.style = Paint.Style.STROKE
        for (k in 0..2) {
            val ow = rad * (1.55f - k * 0.3f); val oh = ow * 0.2f
            val py = cy + rad * 1.18f
            paint.strokeWidth = (2.2f - k * 0.4f) * d
            paint.color = withAlpha(cur, (150 - k * 30 + (25 * sin(t * 2f + k)).toInt()).coerceIn(30, 200))
            c.drawOval(RectF(cx - ow, py - oh, cx + ow, py + oh), paint)
        }
        // curved name over the globe
        val arc = Path()
        val arcR = rad * 1.68f
        arc.addArc(RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR), 205f, 130f)
        ringPaint.textSize = 9.5f * d
        ringPaint.color = withAlpha(cur, 200)
        val title = "TS AGENT  •  ARTIFICIAL INTELLIGENCE"
        val arcLen = (arcR * 130f * Math.PI / 180.0).toFloat()
        c.drawTextOnPath(title, arc, ((arcLen - ringPaint.measureText(title)) / 2f).coerceAtLeast(0f), 0f, ringPaint)

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
        if (showOrb) for (p in pts) {
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
            c.drawTextOnPath("MISS SARA // SECURE LINK // VOICE ENGINE ONLINE // ", ring,
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
        val baseY = min(cy + rad * 1.62f, h - 22 * d)
        text.textSize = 16f * d
        text.letterSpacing = 0.12f
        text.color = cur
        c.drawText(labels[state], cx, baseY, text)
        val lw = text.measureText(labels[state])
        val amp = if (state == 0) 0.3f else 1f
        paint.style = Paint.Style.FILL
        paint.color = cur
        for (side in 0..1) for (k in 0 until 4) {
            val bhh = d * (5f + 15f * amp * abs(sin(t * 7f + k * 1.3f + side * 2f))) 
            val bx = if (side == 0) cx - lw / 2f - 12f * d - k * 6f * d else cx + lw / 2f + 8f * d + k * 6f * d
            c.drawRoundRect(RectF(bx, baseY - bhh, bx + 3.4f * d, baseY), 1.5f * d, 1.5f * d, paint)
        }
        if (caption.isNotBlank()) {
            text.textSize = 11f * d
            text.letterSpacing = 0f
            text.color = 0xFF6FBF9A.toInt()
            c.drawText(caption.take(34), cx, baseY + 16 * d, text)
        }
        text.letterSpacing = 0f

        postInvalidateOnAnimation()
    }

    private fun drawNodeIcon(c: Canvas, kind: Int, cx: Float, cy: Float, size: Float, col: Int) {
        val u = size / 30f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f * u; paint.color = col
        paint.strokeCap = Paint.Cap.ROUND; paint.shader = null
        when (kind) {
            0 -> { for (k in 0..2) {
                    val y = cy - 11f * u + k * 9f * u
                    c.drawOval(RectF(cx - 13f * u, y - 4f * u, cx + 13f * u, y + 4f * u), paint)
                }
                paint.style = Paint.Style.FILL; paint.color = withAlpha(col, 140)
                c.drawOval(RectF(cx - 13f * u, cy - 15f * u, cx + 13f * u, cy - 7f * u), paint) }
            1 -> { paint.style = Paint.Style.FILL; paint.color = col
                c.drawRoundRect(RectF(cx - 15f * u, cy - 12f * u, cx + 15f * u, cy + 8f * u), 8f * u, 8f * u, paint)
                val p = Path(); p.moveTo(cx - 6f * u, cy + 7f * u); p.lineTo(cx - 10f * u, cy + 15f * u); p.lineTo(cx + 2f * u, cy + 7f * u); p.close()
                c.drawPath(p, paint)
                paint.color = 0xFF021008.toInt()
                for (k in -1..1) c.drawCircle(cx + k * 7f * u, cy - 2f * u, 2f * u, paint) }
            else -> { paint.style = Paint.Style.FILL; paint.color = col
                val p = Path(); p.moveTo(cx, cy - 15f * u)
                p.cubicTo(cx + 3f * u, cy - 7f * u, cx + 13f * u, cy - 3f * u, cx + 11f * u, cy + 7f * u)
                p.cubicTo(cx + 10f * u, cy + 13f * u, cx + 4f * u, cy + 16f * u, cx, cy + 16f * u)
                p.cubicTo(cx - 5f * u, cy + 16f * u, cx - 11f * u, cy + 12f * u, cx - 11f * u, cy + 5f * u)
                p.cubicTo(cx - 11f * u, cy - 1f * u, cx - 6f * u, cy - 4f * u, cx - 4f * u, cy - 9f * u)
                p.cubicTo(cx - 2f * u, cy - 6f * u, cx - 1f * u, cy - 9f * u, cx, cy - 15f * u); p.close()
                c.drawPath(p, paint)
                paint.color = 0xFFFFE0F8.toInt()
                c.drawCircle(cx, cy + 8f * u, 4f * u, paint) }
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val hit = rects.indexOfFirst { RectF(it.left - 6 * d, it.top - 6 * d, it.right + 6 * d, it.bottom + 6 * d).contains(e.x, e.y) }
        if (hit < 0) return false
        if (e.action == MotionEvent.ACTION_UP) { performClick(); onNode?.invoke(hit) }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
