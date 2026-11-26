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
 * Plan 1 premium page: AI Core, built in layers on top of its photo.
 *   Layer 1: the photo (the room, the curved panel and the brain).
 *   Layer 2: the title and live status, placed inside the curved panel.
 *   Layer 3: a tap area over the brain that opens the question box.
 *   Layer 4: the control bar, placed on the platform under the brain.
 * Positions are fractions of the photo, so the layers stay on the same spots at any screen width.
 */
class PremiumActivity : Activity() {
    private val mint = 0xFF5CF2D6.toInt()
    private val violet = 0xFFB58CFF.toInt()
    private val panel = 0xFF0A1420.toInt()
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(12), dp(10), dp(20)) }

        // layer 1: the photo, which sets the height of the whole stage
        val stage = FrameLayout(this)
        stage.addView(ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadAsset("plan_designs/rich_01.png"))
        }, FrameLayout.LayoutParams(-1, -2))

        // layer 2: title and real status, inside the curved panel (top area of the photo)
        val head = text("AI CORE v4.2", 13f, mint, bold = true).apply { gravity = Gravity.START }
        val status = text(
            "PLAN: ${Entitlements.label(this)}\nMIC: ${if (prefs.getBoolean("auto_listen", false)) "ON" else "OFF"}\nREPLY: ${prefs.getString("reply_lang", "auto")}",
            10f, Color.WHITE).apply { gravity = Gravity.START }
        stage.addView(head); stage.addView(status)

        // layer 3: a tap area over the brain opens the question box
        val brain = View(this).apply { setOnClickListener { input.requestFocus() } }
        stage.addView(brain)

        // layer 4: the control bar on the platform
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val buttons = listOf(
            "CHAT" to { open(MainActivity::class.java) },
            "VISION" to { open(VisionLabActivity::class.java) },
            "DIAGRAM" to { open(DiagramActivity::class.java) },
            "PLANS" to { open(PlanDashboardActivity::class.java) }
        )
        for ((label, action) in buttons) {
            bar.addView(TextView(this).apply {
                text = label; textSize = 9f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = card(mint, 0xCC0A1420.toInt(), 8)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        stage.addView(bar)

        var placed = false
        stage.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            if (placed) return@addOnLayoutChangeListener
            placed = true
            val w = (r - l).toFloat(); val h = (b - t).toFloat()
            place(head, 0.14f, 0.075f, 0.32f, 0.06f, w, h)
            place(status, 0.14f, 0.13f, 0.30f, 0.20f, w, h)
            place(brain, 0.36f, 0.45f, 0.28f, 0.27f, w, h)
            place(bar, 0.20f, 0.80f, 0.60f, 0.09f, w, h)
        }
        col.addView(stage, LinearLayout.LayoutParams(-1, -2))

        // ask the plan's AI, under the stage
        answer = TextView(this).apply {
            text = "Tap the brain or type below. Replies use your AI key."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(mint, panel, 12)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Message Neural Core"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(mint, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { ask() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF03070C.toInt()); isFillViewport = true; addView(col) })
    }

    /** Puts a layer at a fraction of the photo: x, y, width and height as fractions (0..1). */
    private fun place(v: View, fx: Float, fy: Float, fw: Float, fh: Float, w: Float, h: Float) {
        v.layoutParams = FrameLayout.LayoutParams((w * fw).toInt(), (h * fh).toInt())
        v.x = w * fx
        v.y = h * fy
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = 1
                Llm.complete(this, PlanCatalog.get(1).role, q, PlanAi.tokens(this, 600), PlanAi.search(this, 600))
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread { answer.text = reply }
        }.start()
    }

    private fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    private fun loadAsset(path: String): Bitmap? =
        try { assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
