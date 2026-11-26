package com.tsagent.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
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

/** Plan 7 premium page: Coding Studio. Layout follows the robot-desk reference picture. */
class PlanSevenActivity : Activity() {
    private val teal = 0xFF4FE3D6.toInt()
    private val amber = 0xFFFFC857.toInt()
    private val panel = 0xFF0C1518.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text("CODING STUDIO · ACTIVE", 16f, teal, bold = true).apply { gravity = Gravity.START })
        col.addView(text("Plan 7 · Coding Studio", 12f, amber).apply { gravity = Gravity.START })

        // the robot on its platform, with the blink and the glow
        val stage = FrameLayout(this).apply { background = card(teal, panel, 18) }
        stage.addView(RobotView(this, teal, amber), FrameLayout.LayoutParams(-1, dp(210)))
        col.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // panel: the tools row, drawn as tiles
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (label in listOf("TERMINAL", "FILES", "DEBUG", "HISTORY")) {
            tools.addView(text(label, 10f, teal, bold = true).apply { background = card(teal, panel, 8); setPadding(dp(4), dp(8), dp(4), dp(8)) },
                LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        col.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ask for code help
        answer = TextView(this).apply {
            text = "Paste code or describe a bug. Replies use your AI key."
            textSize = 13f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(teal, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Paste code or describe the problem"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8A8C.toInt())
            typeface = android.graphics.Typeface.MONOSPACE; minLines = 2
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(teal, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "RUN"; isAllCaps = false; setOnClickListener { ask() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val grid = GridLayout(this).apply { columnCount = 2 }
        val items: List<Pair<String, () -> Unit>> = listOf(
            Pair("Chat", { open(MainActivity::class.java) }),
            Pair("Vision Lab", { open(VisionLabActivity::class.java) }),
            Pair("Plans", { open(PlanDashboardActivity::class.java) }),
            Pair("Back", { finish() })
        )
        for ((label, action) in items) {
            grid.addView(tile(label, action), GridLayout.LayoutParams().apply {
                width = dp(160); height = dp(56); setMargins(dp(4), dp(10), dp(4), dp(4))
            })
        }
        col.addView(grid)

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF06100F.toInt()); isFillViewport = true; addView(col) })
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = 7
                Llm.complete(this, PlanCatalog.get(7).role, q, PlanAi.tokens(this, 1200), false)
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
        background = card(teal, panel, 12)
        setOnClickListener { action() }
    }
}

/** The robot head on its platform: a soft glow and eyes that blink. */
class RobotView(ctx: Context, private val teal: Int, private val amber: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h * 0.42f
        val rr = h * 0.23f

        // glow behind the head
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, rr * 2.2f, intArrayOf(withA(teal, 120), withA(teal, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rr * 2.2f, p)
        p.shader = null

        // head body
        p.color = withA(0xFFEAF6F3.toInt(), 255)
        c.drawOval(RectF(cx - rr, cy - rr * 0.85f, cx + rr, cy + rr * 0.85f), p)

        // eyes: they blink once in each cycle
        val blink = if (t > 0.92f) 0.1f else 1f
        val ey = cy - rr * 0.05f; val er = rr * 0.22f
        p.color = withA(teal, 255)
        c.drawOval(RectF(cx - rr * 0.48f - er, ey - er * blink, cx - rr * 0.48f + er, ey + er * blink), p)
        c.drawOval(RectF(cx + rr * 0.48f - er, ey - er * blink, cx + rr * 0.48f + er, ey + er * blink), p)

        // a small amber light on the forehead
        p.color = withA(amber, 230)
        c.drawCircle(cx, cy - rr * 0.6f, rr * 0.07f, p)

        // platform ring
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f)
        p.color = withA(teal, 200)
        c.drawOval(RectF(cx - rr * 1.5f, cy + rr * 0.95f, cx + rr * 1.5f, cy + rr * 1.25f), p)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
