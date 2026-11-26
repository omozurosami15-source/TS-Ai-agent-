package com.tsagent.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.sin

/** Plan 4 premium page: Soul Studio. Layout follows the holographic brain reference picture. */
class PlanFourActivity : Activity() {
    private val blue = 0xFF1E9BFF.toInt()
    private val cyan = 0xFF3FF2FF.toInt()
    private val panel = 0xFF06142A.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text("SOUL STUDIO · ACTIVE", 16f, cyan, bold = true).apply { gravity = Gravity.START })
        col.addView(text("Plan 4 · Soul Studio", 12f, blue).apply { gravity = Gravity.START })

        // the holographic stage: rotating rings, the glowing brain-core and the platform
        val stage = FrameLayout(this).apply { background = card(blue, panel, 18) }
        stage.addView(BrainView(this, blue, cyan), FrameLayout.LayoutParams(-1, dp(250)))
        col.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // side panels: real account values only
        val info = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        info.addView(text("PLAN\n${Entitlements.label(this)}", 12f, Color.WHITE).apply { gravity = Gravity.START; background = card(blue, panel, 12); setPadding(dp(10), dp(8), dp(10), dp(8)) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), 0) })
        info.addView(text("MODE\nSoul Studio", 12f, cyan).apply { gravity = Gravity.START; background = card(blue, panel, 12); setPadding(dp(10), dp(8), dp(10), dp(8)) },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ask the plan's AI
        answer = TextView(this).apply {
            text = "Talk about anything you feel or want to think through. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(blue, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Message Soul Studio"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(blue, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { ask() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // open other features
        val grid = GridLayout(this).apply { columnCount = 2 }
        val items: List<Pair<String, () -> Unit>> = listOf(
            Pair("Voice", { open(MainActivity::class.java) }),
            Pair("Chat", { open(MainActivity::class.java) }),
            Pair("Vision Lab", { open(VisionLabActivity::class.java) }),
            Pair("Diagram", { open(DiagramActivity::class.java) }),
            Pair("Plans", { open(PlanDashboardActivity::class.java) }),
            Pair("Back", { finish() })
        )
        for ((label, action) in items) {
            grid.addView(tile(label, action), GridLayout.LayoutParams().apply {
                width = dp(160); height = dp(56); setMargins(dp(4), dp(10), dp(4), dp(4))
            })
        }
        col.addView(grid)

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF020A18.toInt()); isFillViewport = true; addView(col) })
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = 4
                Llm.complete(this, PlanCatalog.get(4).role, q, PlanAi.tokens(this, 600), PlanAi.search(this, 600))
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread { answer.text = reply }
        }.start()
    }

    private fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun tile(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = card(blue, panel, 12)
        setOnClickListener { action() }
    }
}

/** The holographic brain core: a glowing light column down to a platform, with orbit rings that turn. */
class BrainView(ctx: android.content.Context, private val blue: Int, private val cyan: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h * 0.42f
        val pulse = 0.5f + 0.5f * sin(t * 2f * Math.PI.toFloat())

        // light column down to the platform
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(cx, cy, cx, h * 0.85f, withA(cyan, 120), withA(blue, 0), Shader.TileMode.CLAMP)
        c.drawRect(cx - w * 0.16f, cy, cx + w * 0.16f, h * 0.85f, p)
        p.shader = null

        // the brain core: a soft glow with a cyan centre
        p.shader = RadialGradient(cx, cy, h * 0.30f, intArrayOf(withA(cyan, 170), withA(blue, 90), withA(blue, 0)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, h * 0.30f, p)
        p.shader = null
        p.color = withA(blue, 200)
        c.drawCircle(cx, cy, h * 0.16f + pulse * h * 0.01f, p)

        // two orbit rings that turn in opposite directions
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(2.2f)
        for (k in 0 until 2) {
            val rx = w * (0.40f + k * 0.04f); val ry = h * (0.14f + k * 0.03f)
            p.color = withA(cyan, 230 - k * 70)
            c.save()
            c.rotate((if (k == 0) 18f else -18f) + t * 360f * (if (k == 0) 1 else -1) * 0.25f, cx, cy)
            c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            c.restore()
        }

        // the platform with ripples
        val py = h * 0.84f
        p.style = Paint.Style.FILL
        p.color = withA(blue, 180)
        c.drawOval(RectF(cx - w * 0.30f, py - h * 0.05f, cx + w * 0.30f, py + h * 0.05f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f)
        for (k in 0 until 3) {
            val f = (t + k / 3f) % 1f
            val rw = w * (0.10f + 0.22f * f)
            p.color = withA(cyan, (200 * (1f - f)).toInt())
            c.drawOval(RectF(cx - rw, py - rw * 0.2f, cx + rw, py + rw * 0.2f), p)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
