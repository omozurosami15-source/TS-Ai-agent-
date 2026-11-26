package com.tsagent.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.cos
import kotlin.math.sin

/** Plan 6 premium page: Web Intelligence. Layout follows the green control-room reference picture. */
class PlanSixActivity : Activity() {
    private val green = 0xFF7DFF9A.toInt()
    private val lime = 0xFFC6FF5A.toInt()
    private val panel = 0xFF07140E.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text("WEB INTELLIGENCE · ACTIVE", 16f, green, bold = true).apply { gravity = Gravity.START })
        col.addView(text("Plan 6 · Web Intelligence", 12f, lime).apply { gravity = Gravity.START })

        // the control-room stage: flowing green waves and a turning network globe
        val stage = FrameLayout(this).apply { background = card(green, panel, 18) }
        stage.addView(WebView(this, green, lime), FrameLayout.LayoutParams(-1, dp(220)))
        col.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // status: real values only
        val search = prefs.getBoolean("search_on", false)
        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        status.addView(text("WEB SEARCH: ${if (search) "ON" else "OFF"}", 12f, Color.WHITE).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(text("PLAN: ${Entitlements.label(this)}", 12f, lime).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ask for current facts
        answer = TextView(this).apply {
            text = "Ask for facts, news or research. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(green, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Ask Web Intelligence"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E7A.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(green, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "ASK"; isAllCaps = false; setOnClickListener { ask() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val grid = GridLayout(this).apply { columnCount = 2 }
        val items: List<Pair<String, () -> Unit>> = listOf(
            Pair("Chat", { open(MainActivity::class.java) }),
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

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF03100A.toInt()); isFillViewport = true; addView(col) })
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Searching..."
        Thread {
            val reply = try {
                PlanAi.forced = 6
                // search is allowed only at a higher token budget (see PlanAi.search)
                Llm.complete(this, PlanCatalog.get(6).role, q, PlanAi.tokens(this, 1000), PlanAi.search(this, 1000))
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
        background = card(green, panel, 12)
        setOnClickListener { action() }
    }
}

/** Green flowing waves and a turning network globe (drawn in code). */
class WebView(ctx: Context, private val green: Int, private val lime: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }
    private val path = Path()

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val mid = h * 0.45f
        // flowing waves across the stage
        for (k in 0 until 4) {
            path.reset()
            var x = 0f; var first = true
            while (x <= w) {
                val y = mid + sin(x * 0.02f + t * 6.28f + k * 0.8f) * h * (0.10f + 0.03f * k) +
                        cos(x * 0.011f - t * 6.28f) * h * 0.05f
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 5f
            }
            p.strokeWidth = if (k == 1) dp(2.6f) else dp(1.4f)
            p.color = withA(if (k % 2 == 0) green else lime, if (k == 1) 230 else 120)
            c.drawPath(path, p)
        }
        // the network globe on the right, turning
        val cx = w * 0.78f; val cy = h * 0.5f; val r = h * 0.2f
        p.strokeWidth = dp(1f)
        val pts = 10
        val xs = FloatArray(pts); val ys = FloatArray(pts)
        for (i in 0 until pts) {
            val a = t * 6.28f + i * 0.9f
            xs[i] = cx + cos(a) * r * (0.6f + 0.4f * sin(i.toFloat()))
            ys[i] = cy + sin(a * 0.7f) * r
        }
        for (i in 0 until pts) for (j in i + 1 until pts) {
            if ((i + j) % 3 == 0) { p.color = withA(green, 110); c.drawLine(xs[i], ys[i], xs[j], ys[j], p) }
        }
        p.style = Paint.Style.FILL
        for (i in 0 until pts) { p.color = withA(lime, 230); c.drawCircle(xs[i], ys[i], dp(3f), p) }
        p.style = Paint.Style.STROKE
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
