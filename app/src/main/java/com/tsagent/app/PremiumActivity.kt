package com.tsagent.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Premium design page for one plan (1..17). Opened from REVIEW, only for phones with the premium
 * design (PlanRights.Feature.PREMIUM_DESIGN). The AI on this page is the plan's own AI.
 */
class PremiumActivity : Activity() {
    private var plan = 1
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.PREMIUM_DESIGN)) { finish(); return }
        plan = intent.getIntExtra("plan", 1).coerceIn(1, 17)
        val spec = PremiumDesigns.get(plan)
        val accent = spec.accent
        val name = PlanCatalog.get(plan).name

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(24))
        }
        col.addView(text("PLAN " + plan.toString().padStart(2, '0') + "  •  " + name.uppercase(), 12f, accent, bold = true))
        col.addView(text(spec.tagline, 13f, 0xFFB8D8CC.toInt()))

        // hero: this plan's animated design
        val hero = FrameLayout(this).apply {
            clipToOutline = true
            background = card(accent, 0xFF03090C.toInt(), 18)
        }
        val picture = if (Entitlements.can(this, PlanRights.Feature.PREMIUM_RICH)) PremiumPictures.file(plan)?.let { loadAsset(it) } else null
        if (picture != null) {
            val iv = android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setImageBitmap(picture)
            }
            hero.addView(iv, FrameLayout.LayoutParams(-1, -1))
            android.animation.ObjectAnimator.ofPropertyValuesHolder(iv,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
            ).apply { duration = 4500; repeatCount = android.animation.ObjectAnimator.INFINITE; repeatMode = android.animation.ObjectAnimator.REVERSE
                if (!getSharedPreferences("cfg", MODE_PRIVATE).getBoolean("reduce_motion", false)) start()
            }
        } else {
            hero.addView(PremiumBgView(this, spec), FrameLayout.LayoutParams(-1, -1))
        }
        hero.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(text("MISS SARA", 12f, 0xFFCFE9DF.toInt(), bold = true))
            addView(text(spec.hero, 24f, accent, bold = true).apply { setShadowLayer(16f, 0f, 0f, accent) })
        }, FrameLayout.LayoutParams(-1, -1))
        col.addView(hero, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(10) })

        // the plan's own AI
        col.addView(text("ASK THIS PLAN'S AI", 12f, accent, bold = true).apply { setPadding(0, dp(18), 0, dp(6)) })
        answer = TextView(this).apply {
            text = "Ask anything. Replies use your own AI key."
            textSize = 14f; setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = card(accent, 0xFF0B2620.toInt(), 14)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Message $name"; setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt())
            textSize = 14f; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(accent, 0xFF081A16.toInt(), 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { askAi() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // shortcuts to the existing screens
        col.addView(text("QUICK ACCESS", 12f, accent, bold = true).apply { setPadding(0, dp(18), 0, dp(6)) })
        val grid = GridLayout(this).apply { columnCount = 2 }
        val tiles = mutableListOf<Triple<String, String, () -> Unit>>()
        tiles += Triple("Chat", "Talk to MISS SARA") { open(MainActivity::class.java) }
        tiles += Triple("Voice", "Speak and listen") { open(MainActivity::class.java) }
        tiles += Triple("Vision Lab", "Ask about a picture") { open(VisionLabActivity::class.java) }
        tiles += Triple("Diagram", "Draw a diagram") { open(DiagramActivity::class.java) }
        tiles += Triple("Premium Plans", "All 17 plans") { open(PlanDashboardActivity::class.java) }
        tiles += Triple("Payment Panel", "Pay for plans") { open(PlanDashboardActivity::class.java) }
        tiles += Triple("Settings", "Keys and options") { open(MainActivity::class.java) }
        if (Entitlements.isOwner(this)) {
            tiles += Triple("Admin Panel", "Owner website") {
                if (Config.enabled()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SERVER + "/admin.php")))
            }
        }
        for ((title, sub, action) in tiles) {
            grid.addView(tile(title, sub, accent, action), GridLayout.LayoutParams().apply {
                width = dp(160); height = dp(92); setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        col.addView(grid)

        col.addView(TextView(this).apply {
            text = "Back"; textSize = 14f; setTextColor(accent); gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = card(accent, 0xFF0B2620.toInt(), 12)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF040D0A.toInt()); isFillViewport = true; addView(col) }
        setContentView(scroll)
    }

    private fun askAi() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        answer.text = "Thinking..."
        Thread {
            val reply = try {
                PlanAi.forced = plan
                Llm.complete(this, PlanCatalog.get(plan).role, q, PlanAi.tokens(this, 600), PlanAi.search(this, 600))
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread { answer.text = reply }
        }.start()
    }

    private fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    private fun loadAsset(path: String): android.graphics.Bitmap? =
        try { assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun tile(title: String, sub: String, accent: Int, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = card(accent, 0xFF0B2620.toInt(), 14)
        addView(TextView(this@PremiumActivity).apply {
            text = title; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@PremiumActivity).apply { text = sub; textSize = 11f; setTextColor(accent); gravity = Gravity.CENTER })
        setOnClickListener { action() }
    }
}
