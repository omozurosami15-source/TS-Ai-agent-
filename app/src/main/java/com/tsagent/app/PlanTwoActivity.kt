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
import kotlin.math.sin

/** Plan 2 premium page: Voice Studio. Layout follows the AI VOICE ASSISTANT reference picture. */
class PlanTwoActivity : Activity() {
    private val cyan = 0xFF3EE6FF.toInt()
    private val pink = 0xFFFF5AD9.toInt()
    private val panel = 0xFF071420.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text("AI VOICE ASSISTANT: ACTIVE", 16f, cyan, bold = true).apply { gravity = Gravity.START })
        col.addView(text("Plan 2 · Voice Studio", 12f, pink).apply { gravity = Gravity.START })

        // glass panel with the animated waveform (cyan and magenta, as in the reference)
        val glass = FrameLayout(this).apply { background = card(cyan, panel, 16); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        glass.addView(WaveView(this, cyan, pink), FrameLayout.LayoutParams(-1, dp(170)))
        col.addView(glass, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // status: only real values from the app
        val mic = if (prefs.getBoolean("auto_listen", false)) "ON" else "OFF"
        val reply = prefs.getString("reply_lang", "auto") ?: "auto"
        val voice = prefs.getString("vlang", "")?.ifBlank { "default" } ?: "default"
        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        status.addView(text("MIC: $mic\nPLAN: ${Entitlements.label(this)}", 12f, Color.WHITE).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(text("REPLY: $reply\nVOICE: $voice", 12f, pink).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ask the plan's AI
        answer = TextView(this).apply {
            text = "Speak or type. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(cyan, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Message Voice Studio"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(cyan, panel, 12)
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

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF05080F.toInt()); isFillViewport = true; addView(col) })
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = 2
                Llm.complete(this, PlanCatalog.get(2).role, q, PlanAi.tokens(this, 600), PlanAi.search(this, 600))
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
        background = card(cyan, panel, 12)
        setOnClickListener { action() }
    }
}

/** Animated waveform: a bright cyan wave and a softer magenta wave, tapered at both ends. */
class WaveView(ctx: Context, private val cyan: Int, private val magenta: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f }
    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val mid = h / 2f
        if (w <= 0f) return
        for (k in 0 until 3) {
            p.color = if (k == 1) magenta else cyan
            p.alpha = if (k == 0) 255 else 140
            val path = Path()
            var x = 0f
            var first = true
            while (x <= w) {
                val env = sin(Math.PI * x / w).toFloat()
                val y = mid + sin(x * 0.035f + phase * (1f + k * 0.4f) + k) * h * 0.32f * env *
                        (0.6f + 0.4f * sin(x * 0.01f + phase))
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                x += 4f
            }
            c.drawPath(path, p)
        }
    }
}
