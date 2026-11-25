package com.tsagent.app

import android.text.SpannableStringBuilder
import android.app.Activity
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import android.text.InputType
import kotlin.math.max

/** Native Android plan dashboard. The supplied artwork remains the visual source of truth. */
class PlanDashboardActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private var current = 1
    private lateinit var engineLabel: TextView
    private lateinit var modelButton: Button
    private lateinit var testButton: Button
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var motionLabel: TextView
    private lateinit var descriptionLabel: TextView
    private lateinit var featureLabel: TextView
    private lateinit var makeDefault: Button
    private lateinit var unlockButton: Button
    private lateinit var selector: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var fx: PlanFxView
    private lateinit var threeD: Plan3DView

    private val names = listOf(
        "Neural Core", "Voice Studio", "Memory Vault", "Soul Studio", "Vision Lab",
        "Web Intelligence", "Coding Studio", "File Reader", "Task Automation",
        "Smart Notes", "Daily Briefing", "Translator", "Research Desk",
        "Calendar Hub", "Reminder Center", "Security Center", "System Analytics"
    )
    private val motionNames = listOf(
        "NEURAL PROCESSING PULSE", "VOICE WAVEFORM", "MEMORY NODE GLOW",
        "CALM ORBIT FIELD", "VISION SCAN", "WEB KNOWLEDGE LINKS",
        "CODE SCAN & SYNTAX PULSE", "DOCUMENT PAGE SCAN", "AUTOMATION FLOW",
        "SMART NOTE HIGHLIGHT", "DAILY BRIEFING TIMELINE", "LANGUAGE FLIP",
        "RESEARCH FOCUS RING", "CALENDAR DATE PULSE", "REMINDER NOTIFICATION",
        "SECURITY SHIELD SCAN", "ANALYTICS GRAPH PULSE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = Entitlements.dashboardPlanNumber(this).takeIf { it in 1..17 }
            ?: prefs.getInt("dashboard_plan", 1).coerceIn(1, 17)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        buildUi()
        showPlan(current)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun panelBg(stroke: Int = 0xFF285D67.toInt()) = GradientDrawable().apply {
        setColor(0xE609111A.toInt()); setStroke(dp(1), stroke); cornerRadius = dp(14).toFloat()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private fun buildUi() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(3, 8, 15))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = Button(this).apply {
            text = "‹  APP"; isAllCaps = false; setOnClickListener { finish() }
        }
        top.addView(back, LinearLayout.LayoutParams(-2, dp(44)))
        title = text("PLAN 01", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        top.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        status = text("PREVIEW", 10f, 0xFF66F4E5.toInt(), true).apply { gravity = Gravity.END }
        top.addView(status, LinearLayout.LayoutParams(-2, dp(44)))
        base.addView(top)

        val sub = text("THE DEFAULT PLAN'S REAL 3D OBJECT AND AI ENGINE RUN ON THE MAIN APP HOME", 10f, 0xFF8CA7B7.toInt())
        sub.setPadding(dp(4), dp(8), dp(4), dp(8)); base.addView(sub)

        val selectorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        selector = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectorScroll.addView(selector)
        base.addView(selectorScroll, LinearLayout.LayoutParams(-1, dp(48)))

        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val artFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK); setStroke(dp(1), 0xFF234B60.toInt()); cornerRadius = dp(16).toFloat()
            }
            clipToOutline = true
        }
        threeD = Plan3DView(this).apply { setBackdrop(true); setFocus(0.5f, 0.52f, 0.80f) }
        artFrame.addView(threeD, FrameLayout.LayoutParams(-1, -1))
        fx = PlanFxView(this).apply { alpha = 0.6f }
        artFrame.addView(fx, FrameLayout.LayoutParams(-1, -1))
        body.addView(artFrame, LinearLayout.LayoutParams(-1, dp(280)).apply { bottomMargin = dp(10) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = panelBg(); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        motionLabel = text(motionNames[current - 1], 11f, 0xFF6CF5E8.toInt(), true)
        info.addView(motionLabel)
        descriptionLabel = text(planDescription(current), 12f, 0xFFD2E0EA.toInt()).apply {
            setPadding(0, dp(6), 0, 0)
        }
        info.addView(descriptionLabel)
        featureLabel = text(planFeatures(current), 11f, 0xFF9FE9DC.toInt()).apply {
            setPadding(0, dp(10), 0, 0)
        }
        info.addView(featureLabel)
        engineLabel = text("", 11f, 0xFFFFD27A.toInt()).apply { setPadding(0, dp(10), 0, 0) }
        info.addView(engineLabel)
        body.addView(info, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        makeDefault = Button(this).apply {
            text = "SET ACTIVE DEFAULT"; isAllCaps = false
            setOnClickListener {
                val ok = Entitlements.selectPlan(this@PlanDashboardActivity, "p$current")
                if (ok) {
                    prefs.edit().putInt("dashboard_plan", current).apply()
                    Toast.makeText(this@PlanDashboardActivity, "Plan $current saved as default", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PlanDashboardActivity, "Unlock this plan with its valid code first.", Toast.LENGTH_LONG).show()
                }
                refreshPlanState()
            }
        }
        actions.addView(makeDefault, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        unlockButton = Button(this).apply {
            text = "UNLOCK"; isAllCaps = false
            setOnClickListener { showRedeemDialog() }
        }
        actions.addView(unlockButton, LinearLayout.LayoutParams(dp(108), dp(48)).apply { marginEnd = dp(6) })
        val close = Button(this).apply { text = "DONE"; isAllCaps = false; setOnClickListener { finish() } }
        actions.addView(close, LinearLayout.LayoutParams(dp(76), dp(48)))
        body.addView(actions)
        val actions2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        modelButton = Button(this).apply { text = "PLAN AI MODEL"; isAllCaps = false; setOnClickListener { showModelDialog() } }
        testButton = Button(this).apply { text = "TEST PLAN AI"; isAllCaps = false; setOnClickListener { testPlanAi() } }
        actions2.addView(modelButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actions2.addView(testButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(actions2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        scroll.addView(body)
        base.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(base)

        // Landscape expands the visual stage for a workspace presentation.
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        artFrame.layoutParams = (artFrame.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (landscape) dp(360) else dp(280)
            weight = 0f
        }
    }

    override fun onResume() { super.onResume(); if (::threeD.isInitialized) threeD.onResume() }
    override fun onPause() { if (::threeD.isInitialized) threeD.onPause(); super.onPause() }

    private fun planDescription(n: Int): String = when (n) {
        1 -> "Advanced AI processing workspace with a neural-core visual identity."
        2 -> "Voice tools dashboard with a live waveform-inspired visual stage."
        3 -> "Memory Vault concept for saved context, recall and organized knowledge."
        4 -> "Soul Studio concept with a calm, wellness-inspired visual environment."
        5 -> "Vision Lab workspace for camera, image and visual-analysis tools."
        6 -> "Web Intelligence workspace for web search, sources and connected facts."
        7 -> "Coding Studio workspace for code, developer utilities and focused editing."
        8 -> "File Reader workspace for documents, PDFs and file-based reading."
        9 -> "Task Automation workspace for organizing repeatable tasks and workflows."
        10 -> "Smart Notes workspace for capturing, organizing and revisiting ideas."
        11 -> "Daily Briefing workspace for a clear summary of daily updates."
        12 -> "Translator workspace for multi-language text and language assistance."
        13 -> "Research Desk workspace for deeper research, source review and synthesis."
        14 -> "Calendar Hub workspace for dates, schedules and calendar planning."
        15 -> "Reminder Center workspace for alerts and time-based reminders."
        16 -> "Security Center workspace for account safety and security controls."
        else -> "System Analytics workspace for tracking activity, trends and performance."
    }

    private fun planFeatures(n: Int): String = when (n) {
        1 -> "CORE PROCESSING  •  NEURAL VISUALIZATION  •  AI WORKSPACE"
        2 -> "VOICE TOOLS  •  WAVEFORM DISPLAY  •  AUDIO WORKSPACE"
        3 -> "SAVED CONTEXT  •  MEMORY ORGANIZATION  •  RECALL"
        4 -> "MINDFUL SPACE  •  CALM VISUALS  •  WELLNESS"
        5 -> "CAMERA  •  GALLERY  •  IMAGE ANALYSIS"
        6 -> "WEB SEARCH  •  SOURCES  •  FACT FINDING"
        7 -> "CODE EDITOR CONCEPT  •  DEVELOPER TOOLS  •  SYNTAX VIEW"
        8 -> "PDF READING  •  DOCUMENTS  •  FILE VIEWER"
        9 -> "WORKFLOWS  •  TASK CHAINS  •  AUTOMATION"
        10 -> "QUICK NOTES  •  ORGANIZATION  •  IDEA CAPTURE"
        11 -> "DAILY SUMMARY  •  UPDATES  •  BRIEFING"
        12 -> "LANGUAGE TOOLS  •  TRANSLATION  •  MULTI-LANGUAGE"
        13 -> "RESEARCH  •  SOURCE REVIEW  •  DEEP DIVE"
        14 -> "CALENDAR  •  SCHEDULE  •  DATE PLANNING"
        15 -> "ALERTS  •  REMINDERS  •  NOTIFICATIONS"
        16 -> "SECURITY STATUS  •  PRIVACY  •  PROTECTION"
        else -> "METRICS  •  TRENDS  •  PERFORMANCE"
    }

    private fun showPlan(number: Int) {
        current = number.coerceIn(1, 17)
        if (::fx.isInitialized) fx.setPlan(current)
        if (::threeD.isInitialized) threeD.setPlan(current)
        title.text = "PLAN ${current.toString().padStart(2, '0')}  •  ${names[current - 1]}"
        motionLabel.text = motionNames[current - 1]
        descriptionLabel.text = planDescription(current)
        featureLabel.text = planFeatures(current)
        refreshPlanState()
        refreshSelector()
    }

    private fun refreshPlanState() {
        val active = Entitlements.isPlanActive(this, "p$current")
        val isDefault = Entitlements.dashboardPlanNumber(this) == current
        status.text = when {
            !active -> "PREVIEW ONLY"
            isDefault -> "ACTIVE DEFAULT"
            else -> "UNLOCKED"
        }
        makeDefault.isEnabled = active
        makeDefault.alpha = if (active) 1f else .55f
        engineLabel.text = PlanAi.summary(this, current)
        modelButton.isEnabled = active; modelButton.alpha = if (active) 1f else .55f
        testButton.isEnabled = active; testButton.alpha = if (active) 1f else .55f
        unlockButton.text = if (active) "UNLOCKED" else "UNLOCK"
        unlockButton.isEnabled = !active
        refreshSelector()
    }

    private fun showRedeemDialog() {
        val field = EditText(this).apply {
            hint = "Enter the code for Plan $current"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Unlock ${names[current - 1]}")
            .setMessage("Enter a valid activation code issued for this plan. A preview does not activate paid access.")
            .setView(field)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("ACTIVATE", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val code = field.text.toString().trim()
                        if (code.isBlank()) {
                            field.error = "Enter your activation code"
                            return@setOnClickListener
                        }
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                        Thread {
                            val result = Entitlements.redeem(this, code)
                            runOnUiThread {
                                if (result.startsWith("Activated:") &&
                                    Entitlements.isPlanActive(this, "p$current")) {
                                    Toast.makeText(this, "Plan $current activated", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                                }
                                dialog.dismiss()
                                refreshPlanState()
                            }
                        }.start()
                    }
                }
                dialog.show()
            }
    }

    private fun showModelDialog() {
        if (!Entitlements.isPlanActive(this, "p$current")) {
            Toast.makeText(this, "Unlock this plan first.", Toast.LENGTH_SHORT).show(); return
        }
        val provider = Llm.provider(this)
        val field = EditText(this).apply {
            hint = if (provider == "cloud") "TS Cloud: this plan uses tier ${PlanCatalog.get(current).tier}" else "Model for this plan (empty = SETTINGS model)"
            setText(PlanAi.savedModel(this@PlanDashboardActivity, current, provider)); setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("AI model: " + names[current - 1] + " / " + provider.uppercase(Locale.US))
            .setMessage("Every plan can use its own model with your own key. Leave it empty to use the model chosen in SETTINGS.")
            .setView(field)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                PlanAi.setModel(this, current, provider, field.text.toString())
                refreshPlanState()
            }.show()
    }

    /** Asks this plan's own AI engine a question, so the plan can be checked before it is the default. */
    private fun testPlanAi() {
        if (!Entitlements.isPlanActive(this, "p$current")) {
            Toast.makeText(this, "Unlock this plan first.", Toast.LENGTH_SHORT).show(); return
        }
        val n = current
        Toast.makeText(this, "Asking the plan AI...", Toast.LENGTH_SHORT).show()
        Thread {
            val reply = try {
                PlanAi.forced = n
                Llm.complete(this, "You are the AI engine of one plan inside the TS Agent app.",
                    "In two short sentences say what you are and what you are best at.", 200, false)
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread {
                AlertDialog.Builder(this).setTitle(names[n - 1]).setMessage(reply.trim())
                    .setPositiveButton("OK", null).show()
            }
        }.start()
    }

    private fun refreshSelector() {
        selector.removeAllViews()
        val default = Entitlements.dashboardPlanNumber(this)
        for (i in 1..17) {
            val selected = i == current
            val unlocked = Entitlements.isPlanActive(this, "p$i")
            val b = TextView(this).apply {
                text = SpannableStringBuilder("P$i").apply {
                    if (unlocked) { append(" "); append(Glyphs.span(this@PlanDashboardActivity, Glyphs.DOT, 0xFF8AF4C9.toInt())) }
                    if (i == default && unlocked) { append(" "); append(Glyphs.span(this@PlanDashboardActivity, Glyphs.STAR, 0xFFFFC21F.toInt())) }
                }
                textSize = 12f; gravity = Gravity.CENTER
                setTextColor(if (selected) Color.BLACK else if (unlocked) 0xFF8AF4C9.toInt() else 0xFFB8D3E0.toInt())
                background = GradientDrawable().apply {
                    setColor(if (selected) 0xFF70F0DD.toInt() else 0xFF101D29.toInt())
                    setStroke(dp(1), if (selected) 0xFF70F0DD.toInt() else 0xFF315166.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(18), 0, dp(18), 0)
                setOnClickListener { showPlan(i) }
            }
            selector.addView(b, LinearLayout.LayoutParams(dp(64), dp(40)).apply { marginEnd = dp(7) })
        }
    }

    /** Plan-aware HUD motion. Effects are translucent so source artwork stays readable. */
    private class PlanFxView(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var plan = 1
        private var phase = 0f
        private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        private val runner = object : Runnable {
            override fun run() { phase = (phase + 0.035f) % 1f; invalidate(); postDelayed(this, 32L) }
        }
        fun setPlan(value: Int) { plan = value.coerceIn(1, 17); invalidate() }
        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null); post(runner) }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cyan = 0xFF62F5E7.toInt()
            val magenta = 0xFFFF72D2.toInt()
            val green = 0xFF70F5B0.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.25f
            p.color = cyan; p.alpha = 45
            val scanY = phase * h
            c.drawLine(0f, scanY, w, scanY, p)
            p.alpha = 16
            c.drawRect(0f, max(0f, scanY - 10f), w, scanY, p)

            when (plan) {
                1, 2, 8 -> {
                    val pts = arrayOf(w*.18f to h*.34f, w*.34f to h*.25f, w*.50f to h*.48f, w*.67f to h*.29f, w*.83f to h*.43f)
                    p.color = if (plan == 2) magenta else cyan; p.alpha = 65
                    for (i in 0 until pts.size-1) c.drawLine(pts[i].first,pts[i].second,pts[i+1].first,pts[i+1].second,p)
                    p.style = Paint.Style.FILL
                    for ((x,y) in pts) { p.alpha = (90 + 90*kotlin.math.abs(kotlin.math.sin(phase*6.28f))).toInt(); c.drawCircle(x,y,2.2f,p) }
                    p.style = Paint.Style.STROKE; p.alpha = 55
                    val r = dp(12).toFloat() + dp(7).toFloat()*kotlin.math.abs(kotlin.math.sin(phase*6.28f)).toFloat()
                    c.drawCircle(w*.5f,h*.5f,r,p)
                }
                3, 5 -> {
                    p.color = if (plan == 5) green else cyan
                    p.style = Paint.Style.FILL
                    for (i in 0..11) {
                        val barH = h * (.08f + .12f * kotlin.math.abs(kotlin.math.sin(phase*6.28f + i*.55f)).toFloat())
                        val x = w*(.16f + i*.061f)
                        p.alpha = 38 + (35*kotlin.math.abs(kotlin.math.sin(phase*6.28f + i*.55f))).toInt()
                        c.drawRoundRect(x,h*.72f-barH,x+dp(3),h*.72f,dp(2).toFloat(),dp(2).toFloat(),p)
                    }
                    p.style = Paint.Style.STROKE
                }
                7 -> {
                    // Coding Studio: subtle editor scanline, syntax brackets and code cursor pulse.
                    p.color = 0xFF8B6CFF.toInt(); p.alpha = 62; p.strokeWidth = 1.4f
                    val left = w * .24f; val right = w * .76f
                    val top = h * .28f; val bottom = h * .68f
                    c.drawRoundRect(left, top, right, bottom, dp(9).toFloat(), dp(9).toFloat(), p)
                    p.color = cyan; p.alpha = 80
                    val cursorX = left + (right-left) * phase
                    c.drawLine(cursorX, top + dp(8), cursorX, bottom - dp(8), p)
                    p.style = Paint.Style.FILL; p.alpha = 115
                    val pulse = 2.5f + 2.0f * kotlin.math.abs(kotlin.math.sin(phase*6.28f)).toFloat()
                    c.drawCircle(cursorX, bottom + dp(13), pulse, p)
                    p.style = Paint.Style.STROKE
                    p.color = 0xFF8B6CFF.toInt(); p.alpha = 75
                    val midY = (top + bottom) * .5f
                    c.drawLine(left + dp(12), midY, left + dp(28), midY, p)
                    c.drawLine(right - dp(28), midY, right - dp(12), midY, p)
                }
                9 -> {
                    // Task Automation: moving workflow nodes and connecting paths.
                    p.color = green; p.alpha = 65; p.strokeWidth = 1.5f
                    val xs = floatArrayOf(.22f, .42f, .62f, .82f)
                    val y = h * .48f
                    for (i in 0 until xs.size-1) c.drawLine(w*xs[i], y, w*xs[i+1], y, p)
                    p.style = Paint.Style.FILL
                    for (i in xs.indices) {
                        p.alpha = if (i == (phase*4).toInt().coerceIn(0,3)) 210 else 90
                        c.drawCircle(w*xs[i], y, dp(4).toFloat(), p)
                    }
                    p.style = Paint.Style.STROKE
                }
                10 -> {
                    // Smart Notes: a moving highlight across a note-card surface.
                    p.color = 0xFFFFC86A.toInt(); p.alpha = 65; p.strokeWidth = 1.4f
                    val l=w*.25f; val r=w*.75f; val t=h*.30f; val b=h*.70f
                    c.drawRoundRect(l,t,r,b,dp(8).toFloat(),dp(8).toFloat(),p)
                    p.alpha = 100
                    val yy=t+(b-t)*phase
                    c.drawLine(l+dp(10),yy,r-dp(10),yy,p)
                    p.color=cyan; p.alpha=75
                    for (i in 0..3) c.drawLine(l+dp(12),t+dp(22+i*18),r-dp(18+i*9),t+dp(22+i*18),p)
                }
                11 -> {
                    // Daily Briefing: timeline markers and a traveling progress point.
                    p.color=cyan; p.alpha=65; p.strokeWidth=1.4f
                    val y=h*.58f; c.drawLine(w*.18f,y,w*.82f,y,p)
                    for(i in 0..5) {
                        val x=w*(.18f+i*.128f)
                        p.style=Paint.Style.FILL; p.alpha=85
                        c.drawCircle(x,y,dp(3).toFloat(),p)
                    }
                    p.alpha=220; c.drawCircle(w*(.18f+.64f*phase),y,dp(5).toFloat(),p)
                    p.style=Paint.Style.STROKE
                }
                12 -> {
                    // Translator: paired language panels with a swapping arrow.
                    p.color=cyan; p.alpha=70; p.strokeWidth=1.5f
                    c.drawRoundRect(w*.20f,h*.34f,w*.44f,h*.64f,dp(8).toFloat(),dp(8).toFloat(),p)
                    p.color=magenta
                    c.drawRoundRect(w*.56f,h*.34f,w*.80f,h*.64f,dp(8).toFloat(),dp(8).toFloat(),p)
                    p.color=green; p.alpha=150
                    val yy=h*(.42f+.16f*phase)
                    c.drawLine(w*.44f,yy,w*.56f,yy,p)
                }
                13 -> {
                    // Research Desk: focus ring sweeps around a central target.
                    p.color=cyan; p.alpha=75; p.strokeWidth=1.5f
                    val cx=w*.5f; val cy=h*.5f; val radius=minOf(w,h)*.16f
                    c.drawCircle(cx,cy,radius,p)
                    val angle=phase*6.28318f
                    val sx=cx+kotlin.math.cos(angle)*radius
                    val sy=cy+kotlin.math.sin(angle)*radius
                    p.style=Paint.Style.FILL; p.alpha=210
                    c.drawCircle(sx,sy,dp(4).toFloat(),p)
                    p.style=Paint.Style.STROKE
                }
                14 -> {
                    // Calendar Hub: softly highlights a moving day cell.
                    p.color=cyan; p.alpha=70; p.strokeWidth=1.3f
                    val l=w*.28f; val t=h*.28f; val cellW=w*.11f; val cellH=h*.10f
                    c.drawRoundRect(l,t,w*.72f,h*.72f,dp(8).toFloat(),dp(8).toFloat(),p)
                    val idx=(phase*21).toInt().coerceIn(0,20)
                    val x=l+(idx%5)*cellW; val y=t+dp(20)+(idx/5)*cellH
                    p.style=Paint.Style.FILL; p.color=green; p.alpha=95
                    c.drawRoundRect(x,y,x+cellW-dp(4),y+cellH-dp(4),dp(4).toFloat(),dp(4).toFloat(),p)
                    p.style=Paint.Style.STROKE
                }
                15 -> {
                    // Reminder Center: notification bell pulse rings.
                    p.color=0xFFFFC85C.toInt(); p.alpha=85; p.strokeWidth=1.5f
                    val r=dp(12).toFloat()+dp(10).toFloat()*phase
                    c.drawCircle(w*.5f,h*.48f,r,p)
                    c.drawCircle(w*.5f,h*.48f,r+dp(8),p)
                    p.style=Paint.Style.FILL; p.alpha=170
                    c.drawCircle(w*.5f,h*.48f,dp(4).toFloat(),p)
                    p.style=Paint.Style.STROKE
                }
                16 -> {
                    // Security Center: shield outline with a scanning beam.
                    p.color=cyan; p.alpha=85; p.strokeWidth=1.6f
                    val path=android.graphics.Path().apply {
                        moveTo(w*.5f,h*.25f); lineTo(w*.70f,h*.34f)
                        lineTo(w*.67f,h*.58f); lineTo(w*.5f,h*.73f)
                        lineTo(w*.33f,h*.58f); lineTo(w*.30f,h*.34f); close()
                    }
                    c.drawPath(path,p)
                    p.color=green; p.alpha=130
                    val yy=h*(.30f+.40f*phase)
                    c.drawLine(w*.34f,yy,w*.66f,yy,p)
                }
                17 -> {
                    // System Analytics: rising bars with a moving trend line.
                    p.color=green; p.alpha=100; p.style=Paint.Style.FILL
                    for(i in 0..6) {
                        val bh=h*(.10f+.08f*i+.035f*kotlin.math.sin(phase*6.28f+i).toFloat())
                        val x=w*(.22f+i*.085f)
                        c.drawRoundRect(x,h*.72f-bh,x+dp(7),h*.72f,dp(3).toFloat(),dp(3).toFloat(),p)
                    }
                    p.style=Paint.Style.STROKE; p.color=cyan; p.alpha=140
                    val path=android.graphics.Path()
                    for(i in 0..6) {
                        val x=w*(.22f+i*.085f)
                        val y=h*(.58f-.035f*i-.025f*kotlin.math.sin(phase*6.28f+i).toFloat())
                        if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                    }
                    c.drawPath(path,p)
                }
                6 -> {
                    for (line in 0..3) {
                        p.color = if (line % 2 == 0) cyan else magenta
                        p.alpha = 58 - line*8; p.strokeWidth = 1.1f
                        val path = android.graphics.Path()
                        for (i in 0..80) {
                            val x = w*i/80f
                            val envelope = kotlin.math.sin(i/80f*Math.PI).toFloat()
                            val y = h*.52f + kotlin.math.sin(i*.23f + phase*6.28f + line*.7f).toFloat() * h*.055f * envelope
                            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
                        }
                        c.drawPath(path,p)
                    }
                }
                4 -> {
                    p.color = green; p.alpha = 65; p.strokeWidth = 1.2f
                    val radius = minOf(w,h)*(.12f + .012f*kotlin.math.sin(phase*6.28f).toFloat())
                    c.drawCircle(w*.5f,h*.52f,radius,p)
                    c.drawCircle(w*.5f,h*.52f,radius*1.35f,p)
                    c.drawLine(w*.5f-radius*1.6f,h*.52f,w*.5f+radius*1.6f,h*.52f,p)
                }
            }
            if (plan >= 7) {
                // Animated neural-orbit field: independent of the reusable AI-core artwork.
                val cx = w * .5f
                val cy = h * .50f
                val radius = minOf(w, h) * .30f
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(1).toFloat()
                for (ring in 0..2) {
                    p.color = if (ring == 1) magenta else cyan
                    p.alpha = 48 + ring * 12
                    val oval = RectF(cx - radius * (1f + ring * .16f),
                        cy - radius * (.34f + ring * .07f),
                        cx + radius * (1f + ring * .16f),
                        cy + radius * (.34f + ring * .07f))
                    c.save()
                    c.rotate(phase * 360f + ring * 60f, cx, cy)
                    c.drawOval(oval, p)
                    c.restore()
                }
                p.style = Paint.Style.FILL
                for (i in 0..23) {
                    val angle = (i / 24f) * 6.28318f + phase * 6.28318f * (if (i % 2 == 0) 1f else -1f)
                    val orbit = radius * (.48f + (i % 4) * .16f)
                    val x = cx + kotlin.math.cos(angle) * orbit
                    val y = cy + kotlin.math.sin(angle) * orbit * .40f
                    p.color = if (i % 5 == 0) magenta else cyan
                    p.alpha = 90 + ((kotlin.math.sin(phase * 6.28318f + i) + 1f) * 65).toInt()
                    c.drawCircle(x, y, dp(if (i % 5 == 0) 2 else 1).toFloat(), p)
                }
                p.style = Paint.Style.STROKE
                p.color = green
                p.alpha = 80
                c.drawCircle(cx, cy, radius * (.22f + .025f * kotlin.math.sin(phase * 6.28318f)), p)
            }
            p.alpha = 255
        }
        override fun onDetachedFromWindow() { removeCallbacks(runner); super.onDetachedFromWindow() }
    }
    override fun onDestroy() {
        super.onDestroy()
    }
}
