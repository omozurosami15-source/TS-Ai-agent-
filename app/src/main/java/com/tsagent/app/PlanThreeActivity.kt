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
import kotlin.math.sin

/** Plan 3 premium page: Memory Vault. Layout follows the holographic microphone reference picture. */
class PlanThreeActivity : Activity() {
    private val blue = 0xFF4DB8FF.toInt()
    private val ice = 0xFFBFE9FF.toInt()
    private val panel = 0xFF06101C.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text("MEMORY VAULT · ACTIVE", 16f, blue, bold = true).apply { gravity = Gravity.START })
        col.addView(text("Plan 3 · Memory Vault", 12f, ice).apply { gravity = Gravity.START })

        // the holographic microphone scene: glow, arc rings and the capsule
        val stage = FrameLayout(this).apply { background = card(blue, panel, 18) }
        stage.addView(MicView(this, blue, ice), FrameLayout.LayoutParams(-1, dp(230)))
        col.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // status: real values only (saved notes are shown for the owner only)
        val owner = OwnerAuth.unlocked(this)
        val notes = if (owner) (prefs.getString("memory", "") ?: "").lines().count { it.isNotBlank() } else -1
        val memText = if (owner) "SAVED NOTES: $notes" else "MEMORY: LOCKED (owner)"
        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        status.addView(text("$memText\nPLAN: ${Entitlements.label(this)}", 12f, Color.WHITE).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(text("MIC: ${if (prefs.getBoolean("auto_listen", false)) "ON" else "OFF"}", 12f, ice).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // ask the plan's AI
        answer = TextView(this).apply {
            text = "Ask about your notes or anything else. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(blue, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Message Memory Vault"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt())
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

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF030812.toInt()); isFillViewport = true; addView(col) })
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = 3
                Llm.complete(this, PlanCatalog.get(3).role, q, PlanAi.tokens(this, 600), PlanAi.search(this, 600))
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

/** The holographic microphone: a glowing capsule over a platform, with arc rings and ripples that move. */
class MicView(ctx: Context, private val blue: Int, private val ice: Int) : View(ctx) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val cx = w / 2f; val cy = h * 0.46f
        val pulse = 0.5f + 0.5f * sin(t * 2f * Math.PI.toFloat())

        // soft light behind the capsule
        glow.shader = RadialGradient(cx, cy, h * 0.55f,
            intArrayOf(withA(blue, 110), withA(blue, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, h * 0.55f, glow)
        glow.shader = null

        // arc rings around the capsule
        stroke.strokeWidth = dp(2f)
        for (k in 0 until 3) {
            val r = h * (0.34f + k * 0.07f)
            val oval = RectF(cx - r, cy - r, cx + r, cy + r)
            stroke.color = withA(if (k == 0) ice else blue, 200 - k * 50)
            c.drawArc(oval, -150f + t * 360f * (if (k % 2 == 0) 1 else -1), 120f, false, stroke)
            c.drawArc(oval, 30f + t * 360f * (if (k % 2 == 0) -0.5f else 0.5f), 70f, false, stroke)
        }

        // the capsule: outer glow, body, inner light
        val cw = h * 0.20f; val ch = h * 0.34f
        val body = RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        stroke.strokeWidth = dp(3f)
        for (g in 3 downTo 1) {
            stroke.color = withA(blue, 40 + pulse.toInt() * 10 - g * 8)
            stroke.strokeWidth = dp(g * 4f)
            c.drawRoundRect(body, cw / 2f, cw / 2f, stroke)
        }
        fill.color = withA(0xFF0A2A44.toInt(), 230)
        c.drawRoundRect(body, cw / 2f, cw / 2f, fill)
        stroke.strokeWidth = dp(2f); stroke.color = ice
        c.drawRoundRect(body, cw / 2f, cw / 2f, stroke)

        // the stand: an arm down to the platform
        stroke.strokeWidth = dp(3f); stroke.color = blue
        c.drawLine(cx, body.bottom, cx, cy + h * 0.30f, stroke)

        // platform with ripples
        val py = cy + h * 0.30f
        for (k in 0 until 3) {
            val f = ((t + k / 3f) % 1f)
            val rw = w * (0.12f + 0.20f * f)
            val rh = rw * 0.22f
            stroke.strokeWidth = dp(1.5f)
            stroke.color = withA(ice, (220 * (1f - f)).toInt())
            c.drawOval(RectF(cx - rw, py - rh, cx + rw, py + rh), stroke)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
