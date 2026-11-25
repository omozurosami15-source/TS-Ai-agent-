package com.tsagent.app

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

private val DENSITY: Float get() = Resources.getSystem().displayMetrics.density

private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

/** A dark panel with cut corners and a glowing neon border (the look of every box on the Home screen). */
class NeonBg(private val color: Int, private val fill: Int = 0xCC03100C.toInt(), private val cutDp: Float = 10f,
             private val strokeDp: Float = 1.6f) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(c: Canvas) {
        val d = DENSITY
        val b = bounds
        val inset = 2f * d
        val l = b.left + inset; val t = b.top + inset; val r = b.right - inset; val bt = b.bottom - inset
        val k = cutDp * d
        path.reset()
        path.moveTo(l + k, t); path.lineTo(r - k, t); path.lineTo(r, t + k); path.lineTo(r, bt - k)
        path.lineTo(r - k, bt); path.lineTo(l + k, bt); path.lineTo(l, bt - k); path.lineTo(l, t + k); path.close()
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.color = withA(color, 26); p.strokeWidth = strokeDp * d * 4.5f; c.drawPath(path, p)
        p.color = withA(color, 70); p.strokeWidth = strokeDp * d * 2.4f; c.drawPath(path, p)
        p.color = color; p.strokeWidth = strokeDp * d; c.drawPath(path, p)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Miss Sara's logo (rounded square) with a slowly turning neon ring around it. */
class AvatarView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isDither = true }
    private var bmp: Bitmap? = null
    private var t = 0f
    private val clip = Path()
    private val rect = RectF()
    private val inner = RectF()
    private val src = Rect()
    private val mtx = Matrix()
    private var sweep: SweepGradient? = null

    init {
        for (name in listOf("ms_sara_logo.png", "miss_sara_avatar.png")) {
            try { ctx.assets.open(name).use { bmp = BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
            if (bmp != null) break
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val s = minOf(w, h) - 4f * d
        val cx = w / 2f; val cy = h / 2f
        rect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        val inset = 3.5f * d
        inner.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        sweep = SweepGradient(cx, cy, intArrayOf(Theme.G, Theme.C, 0xFF1B8F52.toInt(), Theme.G), null)
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val rad = 16f * d
        val cx = width / 2f; val cy = height / 2f
        // soft glow
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = withA(Theme.G, 50); paint.strokeWidth = 7f * d
        c.drawRoundRect(rect, rad, rad, paint)
        // logo, fitted exactly inside the ring
        val b = bmp
        if (b != null) {
            val ir = rad - 3.5f * d
            clip.reset(); clip.addRoundRect(inner, ir, ir, Path.Direction.CW)
            src.set(0, 0, b.width, b.height)
            paint.style = Paint.Style.FILL
            c.save(); c.clipPath(clip)
            c.drawBitmap(b, src, inner, paint)
            c.restore()
        } else {
            paint.style = Paint.Style.FILL; paint.color = 0xFF0A2A1C.toInt()
            c.drawRoundRect(inner, rad, rad, paint)
        }
        // turning ring (the gradient turns, the frame stays still)
        val sg = sweep
        if (sg != null) {
            mtx.setRotate(t * 40f, cx, cy); sg.setLocalMatrix(mtx)
            paint.shader = sg
        } else paint.color = Theme.G
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d
        c.drawRoundRect(rect, rad, rad, paint)
        paint.shader = null
        postInvalidateOnAnimation()
    }
}

/** A small hand-drawn neon rocket with a flickering flame (used on the SETUP button). */
class RocketIconView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val u = minOf(width, height) / 26f
        c.save()
        c.translate(width / 2f, height / 2f)
        c.rotate(45f)
        c.scale(u, u)
        c.translate(0f, -1f)
        // flame (flickers)
        val fl = 5f + 2.2f * sin(t * 22f) + 1.2f * sin(t * 37f)
        val outer = Path().apply {
            moveTo(-2.4f, 7f); quadTo(-1.6f, 7f + fl * 0.7f, 0f, 7f + fl)
            quadTo(1.6f, 7f + fl * 0.7f, 2.4f, 7f); close()
        }
        paint.style = Paint.Style.FILL; paint.color = 0xFFFF7A00.toInt(); c.drawPath(outer, paint)
        val innerF = Path().apply {
            moveTo(-1.2f, 7f); quadTo(-0.8f, 7f + fl * 0.45f, 0f, 7f + fl * 0.62f)
            quadTo(0.8f, 7f + fl * 0.45f, 1.2f, 7f); close()
        }
        paint.color = 0xFFFFE066.toInt(); c.drawPath(innerF, paint)
        // fins
        paint.color = Theme.M
        val finL = Path().apply { moveTo(-3.4f, 1.5f); lineTo(-7.2f, 8.2f); lineTo(-3.2f, 6.8f); close() }
        val finR = Path().apply { moveTo(3.4f, 1.5f); lineTo(7.2f, 8.2f); lineTo(3.2f, 6.8f); close() }
        c.drawPath(finL, paint); c.drawPath(finR, paint)
        // body
        val body = Path().apply {
            moveTo(0f, -11.5f); cubicTo(5.4f, -6.5f, 5.2f, 2f, 3.6f, 7.2f)
            lineTo(-3.6f, 7.2f); cubicTo(-5.2f, 2f, -5.4f, -6.5f, 0f, -11.5f); close()
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.2f; paint.color = withA(Theme.M, 70)
        c.drawPath(body, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFFF4F7FF.toInt(); c.drawPath(body, paint)
        // nose cap
        val nose = Path().apply {
            moveTo(0f, -11.5f); cubicTo(2.4f, -9.3f, 3.6f, -7.4f, 4.1f, -5.6f)
            lineTo(-4.1f, -5.6f); cubicTo(-3.6f, -7.4f, -2.4f, -9.3f, 0f, -11.5f); close()
        }
        paint.color = Theme.M; c.drawPath(nose, paint)
        // window
        paint.color = 0xFF0A2A3A.toInt(); c.drawCircle(0f, -0.8f, 2.7f, paint)
        paint.color = Theme.C; c.drawCircle(0f, -0.8f, 2f, paint)
        paint.color = 0xAAFFFFFF.toInt(); c.drawCircle(-0.7f, -1.5f, 0.6f, paint)
        c.restore()
        postInvalidateOnAnimation()
    }
}

/** The round "AI CORE" badge: turning tick ring and a breathing centre. */
class AiCoreBadge(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private var t = 0f

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 3f * d
        paint.style = Paint.Style.STROKE
        paint.color = withA(Theme.C, 40); paint.strokeWidth = 8f * d
        c.drawCircle(cx, cy, r * 0.8f, paint)
        paint.color = Theme.C; paint.strokeWidth = 2f * d
        c.drawCircle(cx, cy, r * 0.78f, paint)
        // ticks, turning
        c.save(); c.rotate(t * 25f, cx, cy)
        paint.strokeWidth = 2.4f * d
        for (i in 0 until 36) {
            val a = i * 10f
            val on = i % 3 != 0
            paint.color = withA(Theme.C, if (on) 190 else 90)
            c.save(); c.rotate(a, cx, cy)
            c.drawLine(cx, cy - r, cx, cy - r + (if (i % 9 == 0) 9f else 5f) * d, paint)
            c.restore()
        }
        c.restore()
        paint.strokeWidth = 1.4f * d
        paint.color = withA(Theme.C, 120)
        c.drawCircle(cx, cy, r * 0.58f, paint)
        // centre text, breathing
        val br = 0.5f + 0.5f * sin(t * 2.2f)
        txt.color = withA(Theme.C, (170 + 85 * br).toInt())
        txt.textSize = r * 0.62f
        c.drawText("AI", cx, cy + r * 0.12f, txt)
        txt.textSize = r * 0.27f
        c.drawText("CORE", cx, cy + r * 0.44f, txt)
        postInvalidateOnAnimation()
    }
}

/** The two-row system strip: UPTIME, ACCESSIBILITY, OWNER / PLAN, COINS, COMPANION. */
class StatusPanel(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = 0xFFF2FFF8.toInt() }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private var uptime = "00:00:00"
    private var accOn = false
    private var owner = "-"
    private var plan = "FREE"
    private var coins = "-"
    private var companion = "OFF"
    var onCell: ((Int) -> Unit)? = null

    init { background = NeonBg(Theme.G, 0xCC03120C.toInt(), 12f) }

    fun set(uptime: String, accOn: Boolean, owner: String, plan: String, coins: String, companion: String) {
        this.uptime = uptime; this.accOn = accOn; this.owner = owner
        this.plan = plan; this.coins = coins; this.companion = companion
        invalidate()
    }

    private fun fit(p: Paint, text: String, maxW: Float, size: Float) {
        p.textSize = size
        while (p.measureText(text) > maxW && p.textSize > 8f * d) p.textSize -= 0.5f * d
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val padX = 12f * d
        val colW = (w - 2 * padX) / 3f
        val rowH = (h - 12f * d) / 2f
        val y0 = 6f * d
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d; paint.color = withA(Theme.G, 90)
        c.drawLine(padX, y0 + rowH, w - padX, y0 + rowH, paint)
        for (i in 1..2) c.drawLine(padX + colW * i, y0 + 6f * d, padX + colW * i, h - 6f * d, paint)

        // row 1: icon + label over value
        val r1 = listOf(Triple("UPTIME", uptime, 0xFFFFFFFF.toInt()), Triple("ACCESSIBILITY", if (accOn) "ON" else "OFF", if (accOn) Theme.G else Theme.C),
            Triple("OWNER", owner, when (owner) { "ON" -> Theme.G; "LOCKED" -> Theme.A; else -> 0xFFFFFFFF.toInt() }))
        for (i in 0..2) {
            val x = padX + colW * i + 6f * d
            icon(c, i, x + 13f * d, y0 + rowH / 2f)
            val tx = x + 32f * d
            val room = colW - 40f * d
            fit(label, r1[i].first, room, 12.5f * d)
            c.drawText(r1[i].first, tx, y0 + rowH / 2f - 2f * d, label)
            fit(value, r1[i].second, room, 13.5f * d)
            value.color = r1[i].third
            c.drawText(r1[i].second, tx, y0 + rowH / 2f + 15f * d, value)
        }
        // row 2: icon + "LABEL: value"
        val r2 = listOf("PLAN: $plan", "COINS: $coins", "COMPANION: $companion")
        for (i in 0..2) {
            val x = padX + colW * i + 6f * d
            icon(c, 3 + i, x + 13f * d, y0 + rowH + rowH / 2f)
            fit(label, r2[i], colW - 40f * d, 13f * d)
            c.drawText(r2[i], x + 32f * d, y0 + rowH + rowH / 2f + 5f * d, label)
        }
    }

    private fun icon(c: Canvas, kind: Int, cx: Float, cy: Float) {
        val u = d
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f * u
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (kind) {
            0 -> { paint.color = 0xFFC8F03C.toInt(); c.drawCircle(cx, cy, 11f * u, paint)
                c.drawLine(cx, cy, cx, cy - 7f * u, paint); c.drawLine(cx, cy, cx + 5f * u, cy + 3f * u, paint) }
            1 -> { paint.color = Theme.G
                val p = Path(); p.moveTo(cx, cy - 12f * u); p.lineTo(cx + 10f * u, cy - 8f * u); p.lineTo(cx + 9f * u, cy + 3f * u)
                p.quadTo(cx + 6f * u, cy + 9f * u, cx, cy + 12f * u); p.quadTo(cx - 6f * u, cy + 9f * u, cx - 9f * u, cy + 3f * u)
                p.lineTo(cx - 10f * u, cy - 8f * u); p.close()
                paint.style = Paint.Style.FILL; paint.color = withA(Theme.G, 60); c.drawPath(p, paint)
                paint.style = Paint.Style.STROKE; paint.color = Theme.G; c.drawPath(p, paint)
                paint.strokeWidth = 2.6f * u; c.drawLine(cx - 5f * u, cy, cx - 1.5f * u, cy + 4f * u, paint); c.drawLine(cx - 1.5f * u, cy + 4f * u, cx + 5f * u, cy - 4f * u, paint) }
            2 -> { paint.style = Paint.Style.FILL; paint.color = Theme.C
                c.drawCircle(cx, cy - 5f * u, 5f * u, paint)
                c.drawArc(RectF(cx - 10f * u, cy + 1f * u, cx + 10f * u, cy + 20f * u), 180f, 180f, true, paint) }
            3 -> { paint.style = Paint.Style.FILL; paint.color = 0xFFFFC21F.toInt()
                val p = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) 12f * u else 5f * u
                    val x = cx + (rr * cos(a)).toFloat(); val y = cy + (rr * sin(a)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); c.drawPath(p, paint) }
            4 -> { paint.style = Paint.Style.FILL; paint.color = 0xFFFFC21F.toInt()
                c.drawOval(RectF(cx - 11f * u, cy - 1f * u, cx + 6f * u, cy + 6f * u), paint)
                c.drawOval(RectF(cx - 11f * u, cy - 8f * u, cx + 6f * u, cy - 1f * u), paint)
                paint.color = 0xFFFFE27A.toInt()
                c.drawOval(RectF(cx - 2f * u, cy - 5f * u, cx + 12f * u, cy + 2f * u), paint)
                c.drawOval(RectF(cx - 2f * u, cy + 2f * u, cx + 12f * u, cy + 10f * u), paint) }
            else -> { paint.style = Paint.Style.FILL; paint.color = 0xFFFFC21F.toInt()
                val p = Path(); p.moveTo(cx - 11f * u, cy + 8f * u); p.lineTo(cx - 11f * u, cy - 6f * u); p.lineTo(cx - 5f * u, cy + 1f * u)
                p.lineTo(cx, cy - 9f * u); p.lineTo(cx + 5f * u, cy + 1f * u); p.lineTo(cx + 11f * u, cy - 6f * u); p.lineTo(cx + 11f * u, cy + 8f * u); p.close()
                c.drawPath(p, paint) }
        }
        paint.style = Paint.Style.STROKE
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) return onCell != null
        if (e.actionMasked == MotionEvent.ACTION_UP && onCell != null) {
            val col = ((e.x - 12f * d) / ((width - 24f * d) / 3f)).toInt().coerceIn(0, 2)
            val row = if (e.y < height / 2f) 0 else 1
            onCell?.invoke(row * 3 + col)
            performClick()
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/** TODAY.TASKS card: first open task, percent done and a live bar graph. */
class TasksPanel(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private var first = "Tap to add tasks"
    private var pct = 0
    private var t = 0f

    init { background = NeonBg(Theme.C, 0xCC03121A.toInt(), 12f) }

    fun setTasks(all: List<String>) {
        val done = all.count { it.trim().startsWith("✓") }
        pct = if (all.isEmpty()) 0 else done * 100 / all.size
        val open = all.firstOrNull { !it.trim().startsWith("✓") }
        first = when {
            all.isEmpty() -> "Tap to add tasks"
            open == null -> "All done"
            else -> open.trim()
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val w = width.toFloat(); val h = height.toFloat()
        val pad = 16f * d
        // title row
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f * d; paint.color = Theme.C
        val cal = RectF(pad, pad - 1f * d, pad + 20f * d, pad + 19f * d)
        c.drawRoundRect(cal, 3f * d, 3f * d, paint)
        c.drawLine(cal.left, cal.top + 6f * d, cal.right, cal.top + 6f * d, paint)
        c.drawLine(cal.left + 5f * d, cal.top - 3f * d, cal.left + 5f * d, cal.top + 2f * d, paint)
        c.drawLine(cal.right - 5f * d, cal.top - 3f * d, cal.right - 5f * d, cal.top + 2f * d, paint)
        txt.color = 0xFFFFFFFF.toInt(); txt.textSize = 15f * d
        c.drawText("TODAY.TASKS", pad + 28f * d, pad + 15f * d, txt)
        c.drawCircle(w - pad - 10f * d, pad + 9f * d, 10f * d, paint)
        c.drawLine(w - pad - 15f * d, pad + 9f * d, w - pad - 5f * d, pad + 9f * d, paint)
        c.drawLine(w - pad - 10f * d, pad + 4f * d, w - pad - 10f * d, pad + 14f * d, paint)
        paint.strokeWidth = 1f * d; paint.color = withA(Theme.C, 140)
        c.drawLine(pad, pad + 27f * d, w - pad, pad + 27f * d, paint)
        // first task
        val y2 = pad + 52f * d
        paint.strokeWidth = 1.6f * d; paint.color = Theme.C
        c.drawCircle(pad + 11f * d, y2 - 5f * d, 11f * d, paint)
        c.drawLine(pad + 11f * d, y2 - 5f * d, pad + 11f * d, y2 - 11f * d, paint)
        c.drawLine(pad + 11f * d, y2 - 5f * d, pad + 16f * d, y2 - 2f * d, paint)
        txt.textSize = 14f * d; txt.color = 0xFFEAFBFF.toInt()
        var s = first
        while (s.length > 2 && txt.measureText(s) > w - pad * 2 - 34f * d) s = s.dropLast(2)
        if (s.length < first.length) s += "…"
        c.drawText(s, pad + 32f * d, y2, txt)
        paint.strokeWidth = 1f * d; paint.color = withA(Theme.C, 140)
        c.drawLine(pad, h - 52f * d, w - pad, h - 52f * d, paint)
        // percent + bars
        txt.textSize = 15f * d; txt.color = 0xFFFFFFFF.toInt()
        c.drawText("$pct%", pad, h - 28f * d, txt)
        txt.textSize = 14f * d
        c.drawText("Completed", pad, h - 11f * d, txt)
        val n = 7
        val bw = 7f * d; val gap = 5f * d
        val x0 = w - pad - n * (bw + gap)
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val base = (0.25f + 0.75f * (i + 1) / n)
            val wave = 0.88f + 0.12f * sin(t * 3f + i * 0.7f)
            val bh = 44f * d * base * wave
            paint.color = withA(if (i < n - 2) Theme.C else Theme.G, 235)
            c.drawRoundRect(RectF(x0 + i * (bw + gap), h - 14f * d - bh, x0 + i * (bw + gap) + bw, h - 14f * d), 2f * d, 2f * d, paint)
        }
        postInvalidateOnAnimation()
    }
}

/** Simple line icons for the bottom bar: 0 home, 1 tools, 2 history, 3 profile. */
class NavIconView(ctx: Context, private val kind: Int, private val color: Int) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val u = minOf(width, height) / 40f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.FILL; paint.color = color
        when (kind) {
            0 -> { val p = Path(); p.moveTo(cx, cy - 16f * u); p.lineTo(cx + 17f * u, cy); p.lineTo(cx + 12f * u, cy); p.lineTo(cx + 12f * u, cy + 15f * u)
                p.lineTo(cx + 4f * u, cy + 15f * u); p.lineTo(cx + 4f * u, cy + 5f * u); p.lineTo(cx - 4f * u, cy + 5f * u); p.lineTo(cx - 4f * u, cy + 15f * u)
                p.lineTo(cx - 12f * u, cy + 15f * u); p.lineTo(cx - 12f * u, cy); p.lineTo(cx - 17f * u, cy); p.close(); c.drawPath(p, paint) }
            1 -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = 4.4f * u
                c.drawLine(cx - 12f * u, cy + 12f * u, cx + 5f * u, cy - 5f * u, paint)
                c.drawCircle(cx + 9f * u, cy - 9f * u, 7f * u, paint)
                c.drawLine(cx + 12f * u, cy + 12f * u, cx - 5f * u, cy - 5f * u, paint)
                c.drawLine(cx - 5f * u, cy - 5f * u, cx - 11f * u, cy - 12f * u, paint) }
            2 -> { paint.style = Paint.Style.STROKE; paint.strokeWidth = 3.6f * u
                c.drawArc(RectF(cx - 14f * u, cy - 14f * u, cx + 14f * u, cy + 14f * u), 30f, 300f, false, paint)
                c.drawLine(cx, cy - 7f * u, cx, cy + 1f * u, paint); c.drawLine(cx, cy + 1f * u, cx + 6f * u, cy + 5f * u, paint)
                paint.style = Paint.Style.FILL
                val p = Path(); p.moveTo(cx - 17f * u, cy - 11f * u); p.lineTo(cx - 8f * u, cy - 9f * u); p.lineTo(cx - 14f * u, cy - 2f * u); p.close(); c.drawPath(p, paint) }
            else -> { c.drawCircle(cx, cy - 8f * u, 8f * u, paint)
                c.drawArc(RectF(cx - 16f * u, cy + 2f * u, cx + 16f * u, cy + 34f * u), 180f, 180f, true, paint) }
        }
    }
}
