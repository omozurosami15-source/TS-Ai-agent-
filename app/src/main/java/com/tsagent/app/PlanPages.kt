package com.tsagent.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Base page for every plan. It shows the plan's own photo at full width, with the status and the
 * controls placed on top, plus the question box. No animation: each plan's design is redone from its
 * photo one at a time, and the photo is the only artwork here for now.
 */
abstract class PlanBasePage : Activity() {
    protected abstract val planNumber: Int
    protected abstract val photo: String
    protected abstract val title: String
    protected abstract val accent: Int
    protected open val hint: String = "Ask a question"
    protected open val tokenBase: Int = 900
    /** Where the layers sit on the photo, as fractions: x, y, width, height. */
    protected open val statusBox: FloatArray = floatArrayOf(0.04f, 0.04f, 0.26f, 0.14f)
    protected open val barBox: FloatArray = floatArrayOf(0.10f, 0.86f, 0.80f, 0.08f)
    /** Optional tap area over the centre object: a tap opens the question box. */
    protected open val tapBox: FloatArray? = null
    /** Animation: centre of the glowing object (fractions), or null for no glow. */
    protected open val glowCentre: FloatArray? = null
    /** Animation: a slow zoom of the photo. */
    protected open val photoZoom: Boolean = false
    private lateinit var answer: TextView
    private lateinit var input: EditText
    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(10), dp(8), dp(20)) }
        col.addView(text(title, 15f, accent, true).apply { gravity = Gravity.CENTER })

        val stage = FrameLayout(this)
        stage.addView(ImageView(this).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadAsset("plan_designs/plan_%02d_scene.%s".format(planNumber, if (planNumber == 12) "jpg" else "png")))
        }, FrameLayout.LayoutParams(-1, -2))
        if (photoZoom) (stage.getChildAt(0) as ImageView).let { pic ->
            android.animation.ObjectAnimator.ofPropertyValuesHolder(pic,
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.03f),
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.03f)).apply {
                duration = 5000; repeatCount = android.animation.ObjectAnimator.INFINITE
                repeatMode = android.animation.ObjectAnimator.REVERSE; start()
            }
        }
        glowCentre?.let { c -> stage.addView(GlowView(this, accent, c[0], c[1]), FrameLayout.LayoutParams(-1, -1)) }

        val status = text("PLAN: ${Entitlements.label(this)}\nMIC: ${if (prefs.getBoolean("auto_listen", false)) "ON" else "OFF"}", 10f, Color.WHITE)
        status.gravity = Gravity.START
        status.background = card(accent, 0xCC05080C.toInt(), 8)
        status.setPadding(dp(6), dp(4), dp(6), dp(4))
        stage.addView(status)
 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val items = listOf("CHAT" to { open(ChatPageActivity::class.java) }, "VISION" to { open(VisionLabActivity::class.java) },
            "PLANS" to { open(PlanDashboardActivity::class.java) }, "BACK" to { finish() })
        for ((label, action) in items) {
            bar.addView(TextView(this).apply {
                text = label; textSize = 9f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = card(accent, 0xCC05080C.toInt(), 8)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        stage.addView(bar)
        tapBox?.let { stage.addView(View(this).apply { setOnClickListener { input.requestFocus() } }) }

        var placed = false
        stage.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (placed) return@addOnLayoutChangeListener
            placed = true
            val w = (r - l).toFloat(); val h = (b - t).toFloat()
            place(status, statusBox[0], statusBox[1], statusBox[2], statusBox[3], w, h)
            place(bar, barBox[0], barBox[1], barBox[2], barBox[3], w, h)
            tapBox?.let { t -> place(stage.getChildAt(stage.childCount - 1), t[0], t[1], t[2], t[3], w, h) }
        }
        col.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        answer = TextView(this).apply {
            text = "Ask anything. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(accent, 0xFF071018.toInt(), 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = this@PlanBasePage.hint; setTextColor(Color.WHITE); setHintTextColor(0xFF7A8A96.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(accent, 0xFF071018.toInt(), 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { ask() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF03060A.toInt()); isFillViewport = true; addView(col) })
    }

    private fun place(v: View, fx: Float, fy: Float, fw: Float, fh: Float, w: Float, h: Float) {
        v.layoutParams = FrameLayout.LayoutParams((w * fw).toInt(), (h * fh).toInt())
        v.x = w * fx; v.y = h * fy
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = planNumber
                Llm.complete(this, PlanCatalog.get(planNumber).role, q, PlanAi.tokens(this, tokenBase), false)
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread { answer.text = Md.plain(reply) }
        }.start()
    }

    protected fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    private fun loadAsset(path: String): Bitmap? =
        try { assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    protected fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    protected fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}

// One page per plan. Each is redone from its own photo, one at a time.
/** Plan 1, AI Core. Layers placed on the photo: status in the curved top panel, a tap area on the brain, the control bar on the platform. */
class PremiumActivity : PlanBasePage() {
    override val planNumber = 1
    override val photo = ""
    override val title = "AI CORE v4.2 · PLAN 1"
    override val accent = 0xFF5CF2D6.toInt()
    override val hint = "Message Neural Core"
    override val statusBox = floatArrayOf(0.14f, 0.09f, 0.30f, 0.30f)
    override val tapBox = floatArrayOf(0.36f, 0.45f, 0.28f, 0.26f)
    override val barBox = floatArrayOf(0.26f, 0.80f, 0.48f, 0.08f)
    override val glowCentre = floatArrayOf(0.49f, 0.60f)
    override val photoZoom = true
}
class PlanTwoActivity : PlanBasePage() { override val planNumber = 2; override val photo = ""; override val title = "VOICE STUDIO · PLAN 2"; override val accent = 0xFF3EE6FF.toInt(); override val hint = "Message Voice Studio" }
class PlanThreeActivity : PlanBasePage() { override val planNumber = 3; override val photo = ""; override val title = "MEMORY VAULT · PLAN 3"; override val accent = 0xFF4DB8FF.toInt(); override val hint = "Message Memory Vault" }
class PlanFourActivity : PlanBasePage() { override val planNumber = 4; override val photo = ""; override val title = "SOUL STUDIO · PLAN 4"; override val accent = 0xFF1E9BFF.toInt(); override val hint = "Message Soul Studio" }
class PlanSixActivity : PlanBasePage() { override val planNumber = 6; override val photo = ""; override val title = "WEB INTELLIGENCE · PLAN 6"; override val accent = 0xFF7DFF9A.toInt(); override val hint = "Ask Web Intelligence" }
class PlanSevenActivity : PlanBasePage() { override val planNumber = 7; override val photo = ""; override val title = "CODING STUDIO · PLAN 7"; override val accent = 0xFF4FE3D6.toInt(); override val hint = "Paste code or describe the problem" }
class PlanEightActivity : PlanBasePage() { override val planNumber = 8; override val photo = ""; override val title = "FILE READER · PLAN 8"; override val accent = 0xFF7FD8FF.toInt(); override val hint = "Ask about a document" }
class PlanNineActivity : PlanBasePage() { override val planNumber = 9; override val photo = ""; override val title = "TASK AUTOMATION · PLAN 9"; override val accent = 0xFFFFB347.toInt(); override val hint = "Describe a task" }
class PlanTenActivity : PlanBasePage() { override val planNumber = 10; override val photo = ""; override val title = "SMART NOTES · PLAN 10"; override val accent = 0xFFFFD166.toInt(); override val hint = "Write or paste a note" }
class PlanElevenActivity : PlanBasePage() { override val planNumber = 11; override val photo = ""; override val title = "DAILY BRIEFING · PLAN 11"; override val accent = 0xFF5CE1E6.toInt(); override val hint = "Ask what matters today" }
class PlanTwelveActivity : PlanBasePage() { override val planNumber = 12; override val photo = ""; override val title = "TRANSLATOR · PLAN 12"; override val accent = 0xFFF28BFF.toInt(); override val hint = "Type a sentence to translate" }
class PlanThirteenActivity : PlanBasePage() { override val planNumber = 13; override val photo = ""; override val title = "RESEARCH DESK · PLAN 13"; override val accent = 0xFF8AC8FF.toInt(); override val hint = "Ask a research question" }
class PlanFourteenActivity : PlanBasePage() { override val planNumber = 14; override val photo = ""; override val title = "CALENDAR HUB · PLAN 14"; override val accent = 0xFF6EE7B7.toInt(); override val hint = "Ask about dates or a schedule" }
class PlanFifteenActivity : PlanBasePage() { override val planNumber = 15; override val photo = ""; override val title = "REMINDER CENTER · PLAN 15"; override val accent = 0xFFFFAD40.toInt(); override val hint = "Describe what to remember" }
class PlanSixteenActivity : PlanBasePage() { override val planNumber = 16; override val photo = ""; override val title = "SECURITY CENTER · PLAN 16"; override val accent = 0xFF2CE6D0.toInt(); override val hint = "Ask about account or device safety" }
class PlanSeventeenActivity : PlanBasePage() { override val planNumber = 17; override val photo = ""; override val title = "SYSTEM ANALYTICS · PLAN 17"; override val accent = 0xFF3FF2FF.toInt(); override val hint = "Ask about numbers and trends" }

/** The moving layer over the brain: a pulsing light and two orbit rings that turn in opposite directions. */
class GlowView(ctx: Context, private val accent: Int, private val cx: Float, private val cy: Float) : View(ctx) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4200; repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val x = w * cx; val y = h * cy
        val pulse = 0.5f + 0.5f * kotlin.math.sin(t * 2f * Math.PI.toFloat())
        val r = w * 0.20f * (0.94f + 0.06f * pulse)
        p.style = android.graphics.Paint.Style.FILL
        p.shader = android.graphics.RadialGradient(x, y, r,
            intArrayOf(withA(accent, (60 + 60 * pulse).toInt()), withA(accent, 0)), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
        p.shader = null
        p.style = android.graphics.Paint.Style.STROKE
        p.strokeWidth = dp(1.6f)
        for (k in 0 until 2) {
            val rx = w * (0.30f + k * 0.03f); val ry = rx * 0.24f
            p.color = withA(accent, 210 - k * 90)
            c.save()
            c.rotate((if (k == 0) -14f else 10f) + t * 360f * (if (k == 0) 1f else -1f) * 0.35f, x, y)
            c.drawOval(android.graphics.RectF(x - rx, y - ry, x + rx, y + ry), p)
            c.restore()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun withA(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
}
