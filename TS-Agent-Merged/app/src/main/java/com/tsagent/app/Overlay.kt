package com.tsagent.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/** Neon frame + scanning line + corner brackets while the agent works. */
class EdgeGlowView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    override fun onDraw(c: Canvas) {
        t += 0.016f
        val w = width.toFloat()
        val h = height.toFloat()
        val pulse = 0.55f + 0.45f * sin(t * 2.4f)
        val mix = 0.5f + 0.5f * sin(t * 1.1f)
        val r = (0 + 0 * mix).toInt()
        val g = (255 + (229 - 255) * mix).toInt()
        val b = (156 + (255 - 156) * mix).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        for (i in 0..6) {
            val inset = (1.5f + i * 3f) * d
            paint.color = Color.argb(((120 - i * 16) * pulse).toInt().coerceAtLeast(0), r, g, b)
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), 30 * d, 30 * d, paint)
        }
        // scanning line
        val y = ((t * 0.32f) % 1f) * h
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, y - 70 * d, 0f, y, intArrayOf(0x0000FF9C, 0x5500FF9C), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, y - 70 * d, w, y, paint)
        paint.shader = null
        paint.color = Color.argb(200, 0, 255, 156)
        c.drawRect(0f, y, w, y + 2 * d, paint)
        // corner brackets
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * d
        paint.color = Color.argb(230, 0, 255, 156)
        val m = 14 * d
        val l = 34 * d
        c.drawLine(m, m, m + l, m, paint); c.drawLine(m, m, m, m + l, paint)
        c.drawLine(w - m, m, w - m - l, m, paint); c.drawLine(w - m, m, w - m, m + l, paint)
        c.drawLine(m, h - m, m + l, h - m, paint); c.drawLine(m, h - m, m, h - m - l, paint)
        c.drawLine(w - m, h - m, w - m - l, h - m, paint); c.drawLine(w - m, h - m, w - m, h - m - l, paint)
        postInvalidateOnAnimation()
    }
}

/** Small spinning dotted ring used inside the floating pill. */
class MiniOrbView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    override fun onDraw(c: Canvas) {
        t += 0.016f
        val cx = width / 2f
        val cy = height / 2f
        val r = width.coerceAtMost(height) * 0.38f
        paint.style = Paint.Style.FILL
        for (i in 0 until 12) {
            val a = t * 3f + i * (Math.PI.toFloat() * 2f / 12f)
            val f = i / 12f
            paint.color = Color.argb((60 + 195 * f).toInt(), 0, 255, 156)
            c.drawCircle(cx + r * cos(a), cy + r * sin(a), width * (0.04f + 0.07f * f), paint)
        }
        paint.color = Color.argb(220, 0, 229, 255)
        c.drawCircle(cx, cy, width * 0.1f * (1f + 0.25f * sin(t * 6f)), paint)
        postInvalidateOnAnimation()
    }
}

/** Target reticle shown where the agent taps. */
class RippleView(ctx: Context) : View(ctx) {
    var progress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val s = width / 2f
        val a = ((1f - progress) * 235).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.06f
        paint.color = Color.argb(a, 0, 255, 156)
        val rad = s * (0.9f - 0.55f * progress)       // shrinks onto the target
        c.drawCircle(s, s, rad, paint)
        c.save()
        c.rotate(progress * 120f, s, s)
        val t0 = rad * 0.55f
        val t1 = rad * 1.25f
        c.drawLine(s - t1, s, s - t0, s, paint); c.drawLine(s + t0, s, s + t1, s, paint)
        c.drawLine(s, s - t1, s, s - t0, paint); c.drawLine(s, s + t0, s, s + t1, paint)
        c.restore()
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(a, 255, 43, 214)
        c.drawCircle(s, s, s * 0.09f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb((a * 0.7f).toInt(), 0, 229, 255)
        c.drawCircle(s, s, s * (0.3f + 0.7f * progress), paint)
    }
}

class HudController(private val svc: AgentService) {
    private val main = Handler(Looper.getMainLooper())
    private val d = svc.resources.displayMetrics.density
    private fun wm() = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pill: LinearLayout? = null
    private var label: TextView? = null
    private var glow: EdgeGlowView? = null

    private val passFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    fun show(text: String) { main.post { if (pill == null) build(); label?.text = "TS> $text" } }
    fun update(text: String) { main.post { if (pill != null) label?.text = "TS> $text" } }

    fun hide() {
        main.post {
            pill?.let { try { wm().removeView(it) } catch (e: Exception) {} }
            glow?.let { try { wm().removeView(it) } catch (e: Exception) {} }
            pill = null; glow = null; label = null
        }
    }

    private fun build() {
        val g = EdgeGlowView(svc)
        wm().addView(g, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, passFlags, PixelFormat.TRANSLUCENT))
        glow = g

        val box = LinearLayout(svc).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0xEE020C07.toInt()); setStroke((1 * d).toInt(), Theme.G); cornerRadius = 8 * d
            }
        }
        box.addView(MiniOrbView(svc), LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()))
        val tv = TextView(svc).apply {
            setTextColor(Theme.G); textSize = 12.5f; typeface = Typeface.MONOSPACE
            maxWidth = (250 * d).toInt(); setPadding((10 * d).toInt(), 0, 0, 0); maxLines = 1
        }
        label = tv
        box.addView(tv)
        box.setOnClickListener { Agent.cancelled = true; tv.text = "TS> ABORTING..." }
        wm().addView(box, WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = (44 * d).toInt() })
        pill = box
    }

    fun ripple(x: Float, y: Float) {
        main.post {
            val size = (120 * d).toInt()
            val v = RippleView(svc)
            val lp = WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, passFlags, PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = (x - size / 2f).toInt()
            lp.y = (y - size / 2f).toInt()
            try { wm().addView(v, lp) } catch (e: Exception) { return@post }
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 650
                addUpdateListener { v.progress = it.animatedValue as Float; v.invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { try { wm().removeView(v) } catch (e: Exception) {} }
                })
                start()
            }
        }
    }
}
