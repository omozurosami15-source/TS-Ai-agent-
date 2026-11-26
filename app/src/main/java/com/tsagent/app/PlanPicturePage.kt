package com.tsagent.app

import android.animation.ObjectAnimator
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
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * A premium page built around one reference picture (the picture is the hero, with a slow zoom).
 * Used by plans 8 and 9. The status lines show only real values.
 */
abstract class PlanPicturePage : Activity() {
    protected abstract val planNumber: Int
    protected abstract val heroAsset: String
    protected abstract val title: String
    protected abstract val subtitle: String
    protected abstract val accent: Int
    protected abstract val chatHint: String
    protected abstract val tokenBase: Int
    protected val panel = 0xFF050C14.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(text(title, 16f, accent, true).apply { gravity = Gravity.START })
        col.addView(text(subtitle, 12f, accent).apply { gravity = Gravity.START })

        // the reference picture, with a slow zoom
        val frame = FrameLayout(this).apply { background = card(accent, panel, 18); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        val pic = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(loadAsset(heroAsset)) }
        frame.addView(pic, FrameLayout.LayoutParams(-1, dp(220)))
        ObjectAnimator.ofPropertyValuesHolder(pic,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)).apply {
            duration = 4500; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; start()
        }
        col.addView(frame, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        // real status only
        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        status.addView(text("PLAN: ${Entitlements.label(this)}", 12f, Color.WHITE).apply { gravity = Gravity.START },
            LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(text("MIC: ${if (prefs.getBoolean("auto_listen", false)) "ON" else "OFF"}", 12f, accent).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        answer = TextView(this).apply {
            text = "Ask anything. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(accent, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = chatHint; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8A9A.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(accent, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { ask() } },
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

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF03060B.toInt()); isFillViewport = true; addView(col) })
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
            runOnUiThread { answer.text = reply }
        }.start()
    }

    private fun loadAsset(path: String): Bitmap? =
        try { assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    protected fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    protected fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    protected fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    protected fun tile(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = card(accent, panel, 12)
        setOnClickListener { action() }
    }
}

/** Plan 8: File Reader. Hero is the sci-fi bridge picture. */
class PlanEightActivity : PlanPicturePage() {
    override val planNumber = 8
    override val heroAsset = "plan_designs/plan_08_scene.png"
    override val title = "FILE READER · ACTIVE"
    override val subtitle = "Plan 8 · File Reader"
    override val accent = 0xFF7FD8FF.toInt()
    override val chatHint = "Ask about a document or paste its text"
    override val tokenBase = 900
}

/** Plan 9: Task Automation. Hero is the control-room picture. */
class PlanNineActivity : PlanPicturePage() {
    override val planNumber = 9
    override val heroAsset = "plan_designs/plan_09_scene.png"
    override val title = "TASK AUTOMATION · ACTIVE"
    override val subtitle = "Plan 9 · Task Automation"
    override val accent = 0xFFFFB347.toInt()
    override val chatHint = "Describe a task to break into steps"
    override val tokenBase = 900
}


/** Plan 10: Smart Notes. Hero is a reference picture. */
class PlanTenActivity : PlanPicturePage() {
    override val planNumber = 10
    override val heroAsset = "plan_designs/plan_10_scene.png"
    override val title = "SMART NOTES · ACTIVE"
    override val subtitle = "Plan 10 · Smart Notes"
    override val accent = 0xFFFFD166.toInt()
    override val chatHint = "Write or paste a note to tidy"
    override val tokenBase = 900
}

/** Plan 11: Daily Briefing. Hero is a reference picture. */
class PlanElevenActivity : PlanPicturePage() {
    override val planNumber = 11
    override val heroAsset = "plan_designs/plan_11_scene.png"
    override val title = "DAILY BRIEFING · ACTIVE"
    override val subtitle = "Plan 11 · Daily Briefing"
    override val accent = 0xFF5CE1E6.toInt()
    override val chatHint = "Ask what matters today"
    override val tokenBase = 900
}

/** Plan 12: Translator. Hero is a reference picture. */
class PlanTwelveActivity : PlanPicturePage() {
    override val planNumber = 12
    override val heroAsset = "plan_designs/plan_12_scene.jpg"
    override val title = "TRANSLATOR · ACTIVE"
    override val subtitle = "Plan 12 · Translator"
    override val accent = 0xFFF28BFF.toInt()
    override val chatHint = "Type a sentence to translate"
    override val tokenBase = 900
}

/** Plan 13: Research Desk. Hero is a reference picture. */
class PlanThirteenActivity : PlanPicturePage() {
    override val planNumber = 13
    override val heroAsset = "plan_designs/plan_13_scene.png"
    override val title = "RESEARCH DESK · ACTIVE"
    override val subtitle = "Plan 13 · Research Desk"
    override val accent = 0xFF8AC8FF.toInt()
    override val chatHint = "Ask a research question"
    override val tokenBase = 900
}

/** Plan 14: Calendar Hub. Hero is a reference picture. */
class PlanFourteenActivity : PlanPicturePage() {
    override val planNumber = 14
    override val heroAsset = "plan_designs/plan_14_scene.png"
    override val title = "CALENDAR HUB · ACTIVE"
    override val subtitle = "Plan 14 · Calendar Hub"
    override val accent = 0xFF6EE7B7.toInt()
    override val chatHint = "Ask about dates or a schedule"
    override val tokenBase = 900
}

/** Plan 15: Reminder Center. Hero is a reference picture. */
class PlanFifteenActivity : PlanPicturePage() {
    override val planNumber = 15
    override val heroAsset = "plan_designs/plan_15_scene.png"
    override val title = "REMINDER CENTER · ACTIVE"
    override val subtitle = "Plan 15 · Reminder Center"
    override val accent = 0xFFFFAD40.toInt()
    override val chatHint = "Describe what to remember"
    override val tokenBase = 900
}

/** Plan 16: Security Center. Hero is a reference picture. */
class PlanSixteenActivity : PlanPicturePage() {
    override val planNumber = 16
    override val heroAsset = "plan_designs/plan_16_scene.png"
    override val title = "SECURITY CENTER · ACTIVE"
    override val subtitle = "Plan 16 · Security Center"
    override val accent = 0xFF47F2C7.toInt()
    override val chatHint = "Ask about account or device safety"
    override val tokenBase = 900
}

/** Plan 17: System Analytics. Hero is a reference picture. */
class PlanSeventeenActivity : PlanPicturePage() {
    override val planNumber = 17
    override val heroAsset = "plan_designs/plan_17_scene.png"
    override val title = "SYSTEM ANALYTICS · ACTIVE"
    override val subtitle = "Plan 17 · System Analytics"
    override val accent = 0xFF73BFFF.toInt()
    override val chatHint = "Ask about numbers and trends"
    override val tokenBase = 900
}
