package com.tsagent.app

import java.io.File

import android.animation.ValueAnimator
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var stage: StageView
    private lateinit var plan3d: Plan3DView
    private var reviewBtn: TextView? = null
    private var reviewLayer: View? = null
    private var reviewView: Plan3DView? = null
    private var setupWizard: SetupWizard? = null
    /** Home parts that an active plan hides (status strip, terminal, radar/tasks). */
    private var homeMid: List<View> = emptyList()
    private lateinit var planThemeLabel: TextView
    private lateinit var mic: MicView
    private lateinit var transcript: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var online: TextView
    private var titleView: GlitchTitle? = null
    /** True while the user wants the app to keep listening; only the mic button turns it off. */
    private var keepListening = false
    /** True while the recognizer is actually open, so two listeners never run at the same time. */
    private var listening = false
    private lateinit var statusPanel: StatusPanel
    private lateinit var tasksText: TasksPanel
    private lateinit var newsText: TextView
    private lateinit var chatRow: LinearLayout
    private lateinit var input: EditText
    private var boot: BootOverlay? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private val buf = SpannableStringBuilder()
    private var pendingAttachment: File? = null
    private var planMode = false
    private var lastSpoken = ""
    private var lastSpokenAt = 0L
    private var page: PageLayer? = null
    private var lastReply = ""
    private var nextHistType: String? = null
    private var nextHistTitle: String? = null

    /** Removes pet words such as "jaan" from replies, so they are neither shown nor spoken. */
    fun cleanReply(text: String): String =
        text.replace(Regex("(হ্যাঁ\\s*)?(জান|jaan|Jaan|JAAN)(?=[!,।.?\\s]|$)[!,।.?\\s]*"), "").replace(Regex("\\s{2,}"), " ").trim()
    private var lastSubmitted: String? = null
    private var lastSubmittedAt = 0L

    /** Speaks each reply once. Replies from commands and from the agent both go through here. */
    private fun speakOnce(text: String, lang: String? = null) {
        val t = text.trim()
        if (t.isEmpty() || t.startsWith("•")) return
        // the same text is skipped only if it was just spoken (the same reply can come twice in one moment), not forever
        val now = System.currentTimeMillis()
        if (t == lastSpoken && now - lastSpokenAt < 2500L) return
        // short status lines must not cut off a longer reply that is still being spoken
        val status = listOf("Switching", "Error:", "Stopped", "Opening", "Posting", "Opened", "Saved", "Voice changed", "Evidence", "Could not")
        if (status.any { t.startsWith(it) }) return
        lastSpoken = t
        lastSpokenAt = now
        speak(cleanReply(t), lang)
    }

    private val speakHandler: (String, String?) -> Unit = { t, l -> speakOnce(t, l) }
    private val t0 = System.currentTimeMillis()
    private val mono = Typeface.MONOSPACE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Soft fade-in when a card's content changes, so updates feel alive instead of jumping. */
    private fun flashIn(v: View) {
        v.animate().cancel()
        v.alpha = 0.35f
        v.animate().alpha(1f).setDuration(320).start()
    }

    /** Gives any button or card a soft press-down and release animation. Clicks still work. */
    private fun pressFx(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            false
        }
    }
    private fun card(stroke: Int, r: Int = 4) = GradientDrawable().apply {
        setColor(0xCC02100A.toInt()); setStroke(dp(1), stroke); cornerRadius = dp(r).toFloat()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (Agent.running && stage.state == 0) setState(3)
            else if (!Agent.running && stage.state == 3) setState(0)
            val on = AgentService.instance != null
            online.text = Glyphs.label(this@MainActivity, Glyphs.DOT, if (on) Theme.G else Theme.A, if (on) "ONLINE" else "SETUP")
            online.setTextColor(if (on) Theme.G else Theme.A)
            val s = (System.currentTimeMillis() - t0) / 1000
            val dn = Entitlements.dashboardPlanNumber(this@MainActivity)
            val pl = Entitlements.plan(this@MainActivity)
            val planName = if (pl == "owner") "OWNER" else if (dn in 1..17) PlanCatalog.get(dn).name.uppercase(Locale.US)
                else pl.ifEmpty { "free" }.uppercase(Locale.US)
            statusPanel.set(String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60), on,
                if (!OwnerAuth.isSet(this@MainActivity)) "TAP TO SET" else if (OwnerAuth.unlocked(this@MainActivity)) "ON" else "LOCKED",
                planName, prefs.getString("coins_text", "-") ?: "-",
                if (prefs.getBoolean("comp_on", false) && OwnerAuth.unlocked(this@MainActivity)) "ON" else "OFF")
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        java.io.File(filesDir, "trace.txt").writeText("")
        trace("onCreate start")
        try { setup(savedInstanceState) } catch (e: Throwable) { showStartupError(e) }
        trace("onCreate end")
    }

    private fun trace(msg: String) {
        try {
            java.io.File(filesDir, "trace.txt").appendText(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()) + " " + msg + "\n")
        } catch (_: Exception) {}
    }

    /** Opening screen: the logo floats over an animated glow, the name types in, and the loading bar fills for 5 seconds; then the main screen appears. */
    private fun startSplash() {
        val anims = ArrayList<android.animation.Animator>()
        val panel = android.widget.FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = android.view.Gravity.CENTER_HORIZONTAL }

        // logo with glow behind it
        val stage = android.widget.FrameLayout(this)
        val glow = SplashGlow(this)
        val logo = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.splash_logo)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            scaleX = 0.85f; scaleY = 0.85f; alpha = 1f; rotation = -4f
        }
        stage.addView(glow, android.widget.FrameLayout.LayoutParams(-1, -1))
        stage.addView(logo, android.widget.FrameLayout.LayoutParams(dp(200), dp(200), android.view.Gravity.CENTER))
        logo.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(700)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
        // after the entrance: gentle floating up and down, and a slow breathing size change
        anims.add(android.animation.ObjectAnimator.ofFloat(logo, "translationY", -dp(7).toFloat(), dp(7).toFloat()).apply {
            duration = 1700; startDelay = 700
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            start()
        })
        anims.add(android.animation.ObjectAnimator.ofPropertyValuesHolder(logo,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.05f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.05f)).apply {
            duration = 1300; startDelay = 700
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
            start()
        })

        // name types in letter by letter
        val name = "MISS SARA"
        val title = TextView(this).apply {
            text = ""; typeface = mono; textSize = 26f; setTextColor(Theme.G); letterSpacing = 0.16f
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Theme.G)
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(10), 0, dp(2)); minHeight = dp(52)
        }
        anims.add(android.animation.ValueAnimator.ofInt(0, name.length).apply {
            startDelay = 150; duration = name.length * 100L
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { title.text = name.substring(0, it.animatedValue as Int) }
            start()
        })
        // thin neon line under the name, fades out at both ends
        val accent = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x0000FF9C, Theme.G, 0xFFB04CFF.toInt(), 0x00B04CFF)).apply { cornerRadius = dp(2).toFloat() }
            alpha = 0f
        }
        accent.animate().alpha(1f).setStartDelay(1000).setDuration(600).start()
        val tag = TextView(this).apply {
            text = "My AI Agent"; typeface = mono; textSize = 12f; setTextColor(0xFFD08BFF.toInt())
            letterSpacing = 0.1f; gravity = android.view.Gravity.CENTER; alpha = 0f; translationY = dp(8).toFloat()
            setPadding(0, 0, 0, dp(18))
        }
        tag.animate().alpha(1f).translationY(0f).setStartDelay(1100).setDuration(600).start()

        // loading bar + text
        val trackW = dp(240)
        val track = android.widget.FrameLayout(this).apply { setBackgroundColor(Theme.DIM and 0x55FFFFFF) }
        val fill = View(this).apply { setBackgroundColor(Theme.G) }
        track.addView(fill, android.widget.FrameLayout.LayoutParams(0, -1))
        val load = TextView(this).apply {
            text = "LOADING"; typeface = mono; textSize = 12f; setTextColor(Theme.DIM)
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        }
        box.addView(stage, LinearLayout.LayoutParams(dp(320), dp(320)))
        box.addView(title, LinearLayout.LayoutParams(-2, -2))
        box.addView(accent, LinearLayout.LayoutParams(dp(200), dp(2)))
        box.addView(tag, LinearLayout.LayoutParams(-2, -2))
        box.addView(track, LinearLayout.LayoutParams(trackW, dp(4)))
        box.addView(load, LinearLayout.LayoutParams(-2, -2))
        panel.addView(box, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        addContentView(panel, android.widget.FrameLayout.LayoutParams(-1, -1))
        anims.add(android.animation.ValueAnimator.ofInt(0, 100).apply {
            duration = 5000
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val pct = it.animatedValue as Int
                fill.layoutParams = android.widget.FrameLayout.LayoutParams(trackW * pct / 100, -1)
                load.text = "LOADING" + ".".repeat(pct / 6 % 3 + 1) + "  " + pct + "%"
            }
            start()
        })
        main.postDelayed({
            panel.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(500)
                .withEndAction {
                    anims.forEach { a -> a.cancel() }
                    (panel.parent as? android.view.ViewGroup)?.removeView(panel)
                }.start()
        }, 5000)
    }

    /** Shows a startup error on screen instead of closing the app silently. */
    private fun showStartupError(e: Throwable) {
        Reporter.send("startup", e.stackTraceToString())
        try {
            AlertDialog.Builder(this).setTitle("Startup error")
                .setMessage(e.stackTraceToString().take(3000))
                .setPositiveButton("OK", null).show()
        } catch (_: Throwable) {}
    }

    private fun setup(savedInstanceState: Bundle?) {
        // Crash log: if the app crashes, the reason is saved and shown the next time it opens.
        val crashFile = java.io.File(filesDir, "crash.txt")
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText(e.stackTraceToString().take(2500)) } catch (_: Exception) {}
            prevHandler?.uncaughtException(t, e)
        }
        prefs.edit().putInt("sessions", prefs.getInt("sessions", 0) + 1).apply()
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG

        root = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        root.addView(RainView(this), FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(22), dp(14), dp(12))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // ===== Home screen (follows the Figma design) =====
        // header: photo, name, tagline, SETTINGS and SETUP
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AvatarView(this), LinearLayout.LayoutParams(dp(74), dp(74)).apply { marginEnd = dp(8) })
        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val gt = GlitchTitle(this, "MISS SARA")
        titleView = gt
        titleCol.addView(gt)
        titleCol.addView(TextView(this).apply {
            val sp = SpannableStringBuilder("Your AI Assistant ").append(Glyphs.span(this@MainActivity, Glyphs.HEART, Theme.M))
            sp.setSpan(ForegroundColorSpan(Theme.M), sp.length - 1, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = sp; textSize = 12.5f; setTextColor(Theme.C); typeface = Typeface.DEFAULT_BOLD; maxLines = 1
        })
        header.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        val btnBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(6))
            background = NeonBg(Theme.G, 0xCC03120C.toInt(), 8f)
        }
        fun hdrBtn(label: CharSequence, color: Int, run: () -> Unit): TextView {
            val v = TextView(this@MainActivity).apply {
                text = label; typeface = Typeface.DEFAULT_BOLD; textSize = 12f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; setPadding(dp(10), dp(7), dp(10), dp(7)); maxLines = 1
                background = NeonBg(color, 0xDD02100A.toInt(), 7f, 1.4f)
                setOnClickListener { run() }
            }
            pressFx(v)
            return v
        }
        btnBox.addView(hdrBtn(Glyphs.label(this@MainActivity, Glyphs.GEAR, Theme.C, "SETTINGS"), Theme.C) { showSettings() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        val setupBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = NeonBg(Theme.M, 0xDD02100A.toInt(), 7f, 1.4f)
            setOnClickListener { openSetupWizard() }
        }
        setupBtn.addView(RocketIconView(this), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(7) })
        setupBtn.addView(TextView(this).apply {
            text = "SETUP"; typeface = Typeface.DEFAULT_BOLD; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1
        })
        pressFx(setupBtn)
        btnBox.addView(setupBtn, LinearLayout.LayoutParams(-1, -2))
        header.addView(btnBox)
        content.addView(header)
        // the old "online" badge is kept (it feeds ACCESSIBILITY in the status strip)
        online = TextView(this)

        // system strip
        statusPanel = StatusPanel(this).apply { onCell = { i -> if (i == 3) openPlanDashboard(); if (i == 2) showOwnerDialog() } }
        content.addView(statusPanel, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(6) })

        // terminal transcript + AI CORE badge
        transcript = TextView(this).apply { setTextColor(Theme.TX); textSize = 12.5f; typeface = mono; setLinkTextColor(Theme.C); movementMethod = LinkMovementMethod.getInstance() }
        transcriptScroll = ScrollView(this).apply {
            background = NeonBg(Theme.G, 0xDD03120C.toInt(), 10f)
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(transcript)
        }
        val termRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        termRow.addView(transcriptScroll, LinearLayout.LayoutParams(0, dp(100), 1f))
        termRow.addView(AiCoreBadge(this), LinearLayout.LayoutParams(dp(82), dp(82)).apply { marginStart = dp(6) })
        content.addView(termRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // main stage: animated plan background + real 3D plan object (only with a plan) and the HUD on top
        val stageFrame = FrameLayout(this)
        plan3d = Plan3DView(this).apply { visibility = View.GONE; setBackdrop(true) }
        stageFrame.addView(plan3d, FrameLayout.LayoutParams(-1, -1))
        reviewBtn = TextView(this).apply {
            text = "REVIEW  ›"; typeface = Typeface.DEFAULT_BOLD; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(10), dp(6), dp(10), dp(6)); visibility = View.GONE
            background = NeonBg(Theme.C, 0xDD02100A.toInt(), 8f, 1.4f)
            setOnClickListener {
                if (Entitlements.can(this@MainActivity, PlanRights.Feature.PREMIUM_DESIGN)) { when (Entitlements.dashboardPlanNumber(this@MainActivity)) {
                    1 -> startActivity(Intent(this@MainActivity, PremiumActivity::class.java))
                    2 -> startActivity(Intent(this@MainActivity, PlanTwoActivity::class.java))
                    3 -> startActivity(Intent(this@MainActivity, PlanThreeActivity::class.java))
                    4 -> startActivity(Intent(this@MainActivity, PlanFourActivity::class.java))
                    5 -> startActivity(Intent(this@MainActivity, PlanFiveActivity::class.java))
                    6 -> startActivity(Intent(this@MainActivity, PlanSixActivity::class.java))
                    7 -> startActivity(Intent(this@MainActivity, PlanSevenActivity::class.java))
                    8 -> startActivity(Intent(this@MainActivity, PlanEightActivity::class.java))
                    9 -> startActivity(Intent(this@MainActivity, PlanNineActivity::class.java))
                    10 -> startActivity(Intent(this@MainActivity, PlanTenActivity::class.java))
                    11 -> startActivity(Intent(this@MainActivity, PlanElevenActivity::class.java))
                    12 -> startActivity(Intent(this@MainActivity, PlanTwelveActivity::class.java))
                    13 -> startActivity(Intent(this@MainActivity, PlanThirteenActivity::class.java))
                    14 -> startActivity(Intent(this@MainActivity, PlanFourteenActivity::class.java))
                    15 -> startActivity(Intent(this@MainActivity, PlanFifteenActivity::class.java))
                    16 -> startActivity(Intent(this@MainActivity, PlanSixteenActivity::class.java))
                    17 -> startActivity(Intent(this@MainActivity, PlanSeventeenActivity::class.java))
                    else -> openPlanReview()
                } }
                else openPlanReview()
            }
        }
        stageFrame.addView(reviewBtn, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(8); marginStart = dp(8) })
        planThemeLabel = TextView(this).apply {
            textSize = 9f; typeface = mono; setTextColor(Theme.C)
            setPadding(dp(7), dp(4), dp(7), dp(4)); alpha = 0f
            background = GradientDrawable().apply { setColor(0xAA00120C.toInt()); setStroke(dp(1), Theme.C); cornerRadius = dp(5).toFloat() }
        }
        stage = StageView(this)
        stage.onNode = { i ->
            when (i) {
                0 -> if (OwnerAuth.unlocked(this)) editNote("MEMORY // what the AI remembers", "memory", "") else show("AGENT", "Owner login required. Open SETTINGS > Owner login.")
                1 -> startActivity(Intent(this, ChatPageActivity::class.java))
                2 -> editNote("SOUL // the AI personality", "soul", "You are TS Agent, a friendly assistant.")
                else -> showSettings()
            }
        }
        stageFrame.addView(stage, FrameLayout.LayoutParams(-1, -1))
        stageFrame.addView(planThemeLabel, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(8); marginEnd = dp(8) })
        val abortChip = TextView(this).apply {
            text = Glyphs.label(this@MainActivity, Glyphs.SQUARE, Theme.RED, "ABORT"); setTextColor(Theme.RED); textSize = 11f; typeface = mono
            setPadding(dp(10), dp(5), dp(10), dp(5)); background = NeonBg(Theme.RED, 0xCC1A0509.toInt(), 6f, 1.2f)
            setOnClickListener { stopAll() }
        }
        pressFx(abortChip)
        stageFrame.addView(abortChip, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { bottomMargin = dp(2); marginEnd = dp(2) })
        stageFrame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placePlan3d() }
        content.addView(stageFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        // SIGNAL SCAN + TODAY.TASKS
        tasksText = TasksPanel(this).apply {
            setOnClickListener { editNote("TODAY.TASKS // one per line, put ✓ at the start when done", "tasks", "") }
        }
        newsText = TextView(this).apply {
            setTextColor(Theme.TX); textSize = 11f; typeface = mono; setLinkTextColor(Theme.C)
            movementMethod = LinkMovementMethod.getInstance()
            setOnClickListener { refreshNews() }
        }
        val bottom = LinearLayout(this)
        bottom.addView(RadarPanel(this), LinearLayout.LayoutParams(0, dp(132), 1f).apply { marginEnd = dp(8) })
        bottom.addView(tasksText, LinearLayout.LayoutParams(0, dp(132), 1f))
        content.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        pressFx(tasksText)
        homeMid = listOf(statusPanel, termRow, bottom)
        applyPurchasedPlanHome()

        // chat input (opened by the CHAT button)
        chatRow = LinearLayout(this).apply { visibility = View.GONE }
        input = EditText(this).apply {
            hint = "$ type a command..."; setTextColor(Theme.G); setHintTextColor(Theme.DIM); typeface = mono
        }
        chatRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        chatRow.addView(Button(this).apply {
            text = "＋ FILE"; typeface = mono
            contentDescription = "Attach code, ZIP, photo or document"
            setOnClickListener { pickAssistantFile() }
        })
        chatRow.addView(Button(this).apply {
            text = "PLAN"; typeface = mono
            contentDescription = "Make a step-by-step plan for the next task"
            setOnClickListener {
                planMode = !planMode
                text = if (planMode) "PLAN ON" else "PLAN"
                show("AGENT", if (planMode) "Plan mode is on. Send your task and I will organize it into clear steps first." else "Plan mode is off.")
            }
        })
        chatRow.addView(Button(this).apply {
            text = "EXEC"; typeface = mono
            setOnClickListener { val t = input.text.toString(); input.setText(""); submit(t) }
        })
        for (i in 1 until chatRow.childCount) pressFx(chatRow.getChildAt(i))
        content.addView(chatRow)

        // bottom bar: HOME / VOICE MODE / AI TOOLS / HISTORY / PROFILE
        fun voiceToggle() { if (keepListening) stopKeep() else { keepListening = true; listen() } }
        fun navBtn(label: String, color: Int, icon: View, active: Boolean, run: () -> Unit): LinearLayout {
            val b = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = NeonBg(color, if (active) ((color and 0xFFFFFF) or 0x55000000) else 0xCC03100C.toInt(), 9f, 1.8f)
                setOnClickListener { run() }
            }
            b.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
            val lbl = TextView(this@MainActivity).apply {
                text = label; textSize = 10f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; maxLines = 1
            }
            b.addView(lbl, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1) })
            // shrink the label until it fits inside its button (so "VOICE MODE" never leaves the border)
            val meas = android.graphics.Paint().apply { typeface = Typeface.DEFAULT_BOLD }
            b.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val room = (r - l) - dp(8).toFloat()
                var sz = dp(10).toFloat()
                meas.textSize = sz
                while (sz > dp(6).toFloat() && meas.measureText(label) > room) { sz -= 0.5f; meas.textSize = sz }
                if (lbl.textSize != sz) lbl.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sz)
            }
            pressFx(b)
            return b
        }
        mic = MicView(this).apply {
            setOnClickListener { voiceToggle() }
            setOnLongClickListener { show("AGENT", "Voice language: " + toggleVoiceLang()); true }
        }
        val nav = LinearLayout(this).apply { setPadding(0, dp(6), 0, 0) }
        val navLp = { LinearLayout.LayoutParams(0, dp(64), 1f).apply { marginEnd = dp(4) } }
        nav.addView(navBtn("HOME", 0xFF2D8CFF.toInt(), NavIconView(this, 0, 0xFF5AA9FF.toInt()), true) { openPage(Pg.NONE); flashIn(stage) }, navLp())
        nav.addView(navBtn("VOICE MODE", 0xFFD13CFF.toInt(), mic, false) { openPage(Pg.VOICE) }, navLp())
        nav.addView(navBtn("AI TOOLS", 0xFF28E070.toInt(), NavIconView(this, 1, 0xFF3DF08A.toInt()), false) { openPage(Pg.TOOLS) }, navLp())
        nav.addView(navBtn("HISTORY", 0xFFFFA726.toInt(), NavIconView(this, 2, 0xFFFFB84D.toInt()), false) { openPage(Pg.HISTORY) }, navLp())
        nav.addView(navBtn("PROFILE", 0xFF22D3EE.toInt(), NavIconView(this, 3, 0xFF3FE0F5.toInt()), false) { openPage(Pg.PROFILE) }, navLp().apply { marginEnd = 0 })
        content.addView(nav)
        content.addView(TextView(this).apply {
            text = "──────   TS Agent v16  •  Smart AI for You   ──────"
            textSize = 10.5f; setTextColor(Theme.C); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, 0)
        })

        // CRT overlay + boot sequence
        root.addView(ScanView(this), FrameLayout.LayoutParams(-1, -1))
        boot = BootOverlay(this) { boot?.let { root.removeView(it) }; boot = null }
        root.addView(boot, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        trace("content ok")
        startSplash()
        if (crashFile.exists()) {
            show("AGENT", "The app stopped last time. Reason:\n" + crashFile.readText())
            Reporter.send("crash", crashFile.readText())
            crashFile.delete()
        }
        CommandRunner.setServerAliases(prefs.getString("srv_aliases", "").orEmpty())
        Thread { syncServer() }.start()
        // soft fade-in when the app opens
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(700).start()
        setState(0)
        refreshTasks()
        show("AGENT", "System ready. Tap the mic and speak. First open SETTINGS to log in, grant permissions and add your own API key.")
        

        // Sara Watch is OFF by default. One-time reset for phones where it was switched on before.
        if (!prefs.getBoolean("watch_default_v2", false)) {
            prefs.edit().putBoolean("watch_on", false).putBoolean("watch_consent", false).putBoolean("watch_default_v2", true).apply()
        }
        if (!prefs.getBoolean("setup_seen", false)) { prefs.edit().putBoolean("setup_seen", true).apply(); showSetup() }

        Agent.onSpeak = speakHandler
        keepListening = prefs.getBoolean("auto_listen", true)
        loadAccount()
        checkRemote()
        refreshNews()
        main.postDelayed(newsTick, 900000L)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.let { t ->
                applyVoice(t, voiceLang())
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { runOnUiThread { setState(2) } }
                    override fun onDone(id: String?) { runOnUiThread { setState(0); afterSpeak() } }
                    override fun onError(id: String?) { runOnUiThread { setState(0) } }
                    // tts.stop() (barge-in, ABORT, stop) ends the utterance without onDone; without this the mic stayed stuck in SPEAKING
                    override fun onStop(id: String?, interrupted: Boolean) { runOnUiThread { if (stage.state == 2) setState(0) } }
                })
            }
        }
    }

    override fun onResume() {
        setupWizard?.rebuild()
        if (setupWizard == null && !prefs.getBoolean("setup_wizard_seen", false)) main.post { openSetupWizard() }
        super.onResume()
        trace("onResume")
        if (::plan3d.isInitialized) { applyPurchasedPlanHome(); plan3d.onResume() }
        // The user came back to the app: a task still running in the background must not press Home or Back and kick them out.
        if (keepListening) listen()
        Security.showPending(this)?.let { show("SECURITY", it) }
        if (prefs.getBoolean("acc_lost", false) && AgentService.instance == null)
            show("AGENT", "Accessibility was turned off by Android. Turn it on again: SETTINGS > ENABLE ACCESSIBILITY.")
        try { resumeSafe() } catch (e: Throwable) { showStartupError(e) }
    }

    private fun resumeSafe() {
        main.post(tick); main.post(blink)
        Agent.onSpeak = speakHandler
        AssistantService.hide()
        AssistantService.ensure(this)
        WatchService.resumeIfEnabled(this)
    }

    override fun onPause() {
        super.onPause()
        trace("onPause")
        if (::plan3d.isInitialized) plan3d.onPause()
        try { pauseSafe() } catch (e: Throwable) { showStartupError(e) }
        listening = false
        main.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
    }

    private fun pauseSafe() {
        main.removeCallbacks(tick); main.removeCallbacks(blink)
        AssistantService.show()
    }
    override fun onStop() {
        super.onStop()
        trace("onStop finishing=" + isFinishing)
    }

    override fun onDestroy() {
        super.onDestroy()
        trace("onDestroy finishing=" + isFinishing)
        main.removeCallbacks(newsTick)
        if (Agent.onSpeak === speakHandler) Agent.onSpeak = null
        tts?.shutdown()
        recognizer?.destroy()
    }

    private fun setState(s: Int) { stage.state = s; mic.active = (s == 1); if (::plan3d.isInitialized) plan3d.setMode(s) }

    // ---- terminal transcript: typing animation + blinking cursor ----
    private var pending = ""
    private var cursorOn = true
    private val blink = object : Runnable {
        override fun run() { cursorOn = !cursorOn; render(follow = false); main.postDelayed(this, 500) }
    }
    private val typer = object : Runnable {
        override fun run() {
            if (pending.isEmpty()) return
            val n = (pending.length / 50).coerceAtLeast(1)
            buf.append(pending.substring(0, n))
            pending = pending.substring(n)
            render()
            if (pending.isNotEmpty()) main.postDelayed(this, 22)
        }
    }

    // follow=false (cursor blink) must not jump the view, so the user can scroll back and read.
    private fun render(follow: Boolean = true) {
        val sb = SpannableStringBuilder(buf)
        sb.append(if (cursorOn) "█" else " ")
        transcript.text = sb
        Linkify.addLinks(transcript, Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
        if (follow) transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun show(who: String, raw: String) {
        val text = if (who == "AGENT") cleanReply(raw) else raw
        main.removeCallbacks(typer)
        if (pending.isNotEmpty()) { buf.append(pending); pending = "" }
        val label = if (who == "YOU") "root@ts:~$ " else "[ts] "
        if (who == "AGENT" && ::plan3d.isInitialized) plan3d.pulse()
        val start = buf.length
        buf.append(label)
        buf.setSpan(ForegroundColorSpan(if (who == "YOU") Theme.G else Theme.C), start, buf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (who == "AGENT") {
            pending = text + "\n"
            main.post(typer)
        } else {
            buf.append(text + "\n")
            render()
        }
    }

    /** In-app guide: what Miss Sara can do and how to use it. Opened by typing "guide". */
    fun showGuide() {
        val text = "What Miss Sara does:\n" +
            "- Talks and answers. Say a question, get an answer.\n" +
            "- Phone tasks: open apps, time, date, battery, torch, volume, alarms.\n" +
            "- Messages: \"wa <number> : text\" sends on WhatsApp. \"inbox\" opens the inbox of the app on screen.\n" +
            "- Telegram: \"telegram post <text>\" posts to the channel. \"telegram open\" opens it.\n" +
            "- Writing: \"write a post about <topic>\" writes it and copies it to the clipboard.\n" +
            "- Prayer times and Ramadan: \"prayer Dhaka\", \"ramadan\".\n" +
            "- Evidence: \"evidence save <note>\" keeps a note and screenshot on this phone only.\n\n" +
            "Tips: keep the phone on and connected. Keep Accessibility and Notifications on. Use headphones to stop it hearing its own voice."
        AlertDialog.Builder(this).setTitle("Guide").setMessage(text).setPositiveButton("OK", null).show()
    }


    // ---- full-screen pages (PROFILE, HISTORY, AI TOOLS, VOICE MODE) ----
    /** Shows a page on top of Home. Pg.NONE goes back to Home. */
    fun openPage(kind: Int) {
        page?.let { root.removeView(it); it.dispose() }
        page = null
        if (kind == Pg.NONE) return
        val p = PageLayer(this, kind)
        page = p
        root.addView(p, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onBackPressed() {
        if (setupWizard != null) closeSetupWizard()
        else if (reviewLayer != null) closeReview()
        else if (page != null) openPage(Pg.NONE)
        else super.onBackPressed()
    }

    fun pgState(): Int = if (::stage.isInitialized) stage.state else 0
    fun pgKeep(): Boolean = keepListening
    fun pgLang(): String = voiceLang()
    fun pgLastReply(): String = lastReply
    fun pgVoiceToggle() { if (keepListening) stopKeep() else { keepListening = true; listen() } }
    fun pgListenOnce() { if (!listening) listen() }
    fun pgSpeakLast() { if (lastReply.isNotBlank()) speak(lastReply) else show("AGENT", "Nothing to speak yet.") }
    fun pgToggleLang() { val l = toggleVoiceLang(); show("AGENT", "Voice language: $l") }
    fun pgSettings() = showSettings()
    fun pgOwner() = showOwnerDialog()

    /** Shown when a free user taps a paid AI tool. AI Chat and Voice Mode stay free. */
    fun pgLockedTool(tool: String) {
        AlertDialog.Builder(this).setTitle("Paid tool")
            .setMessage("$tool needs a plan. AI Chat and Voice Mode are free. Buy a plan to unlock every AI tool.")
            .setPositiveButton("SEE PLANS") { _, _ -> pgPlans() }
            .setNegativeButton("CANCEL", null).show()
    }

    /** Security: owner mode, passphrase, how long a login lasts, clearing data, phone permissions. */
    fun pgSecurity() {
        val owner = if (!OwnerAuth.isSet(this)) "not set" else if (OwnerAuth.unlocked(this)) "unlocked" else "locked"
        val acc = if (AgentService.instance != null) "on" else "off"
        val overlay = if (Settings.canDrawOverlays(this)) "on" else "off"
        val days = prefs.getInt("owner_days", 7)
        val items = arrayOf(
            "Owner mode: $owner  (plan: ${Entitlements.label(this)})",
            "Accessibility: $acc",
            "Overlay permission: $overlay",
            "Lock owner mode now",
            "Owner login / set passphrase",
            "Change owner passphrase",
            "Owner login lasts: $days days (change)",
            "Clear chat history",
            "Clear saved memory (owner)",
            "Open Accessibility settings",
            "Open Overlay permission"
        )
        AlertDialog.Builder(this).setTitle("Security").setItems(items) { _, i ->
            when (i) {
                3 -> { OwnerAuth.logout(this); show("AGENT", "Owner mode locked") }
                4 -> showOwnerDialog()
                5 -> showChangePassDialog()
                6 -> pickOwnerDays()
                7 -> confirmClear("Clear chat history?", "This removes the saved chat context the AI uses. It cannot be undone.", "chat_log", "Chat history cleared.")
                8 -> if (!OwnerAuth.unlocked(this)) show("AGENT", "Owner login required first.")
                     else confirmClear("Clear saved memory?", "This removes everything the AI remembers about you. It cannot be undone.", "memory", "Saved memory cleared.")
                9 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                10 -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun showChangePassDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val oldP = EditText(this).apply { hint = "Current passphrase"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val newP = EditText(this).apply { hint = "New passphrase (8+ characters)"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(oldP); box.addView(newP)
        AlertDialog.Builder(this).setTitle("Change owner passphrase").setView(box)
            .setPositiveButton("CHANGE") { _, _ -> show("AGENT", OwnerAuth.change(this, oldP.text.toString(), newP.text.toString())) }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun pickOwnerDays() {
        val opts = arrayOf("1 day", "7 days", "30 days")
        val days = intArrayOf(1, 7, 30)
        AlertDialog.Builder(this).setTitle("Owner login lasts").setItems(opts) { _, i ->
            prefs.edit().putInt("owner_days", days[i]).apply()
            show("AGENT", "Owner login now lasts ${days[i]} days after each login.")
        }.setNegativeButton("CANCEL", null).show()
    }

    private fun confirmClear(title: String, msg: String, key: String, done: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("CLEAR") { _, _ -> prefs.edit().remove(key).apply(); show("AGENT", done) }
            .setNegativeButton("CANCEL", null).show()
    }

    /** Appearance: motion and the floating assistant. */
    fun pgAppearance() {
        val motion = if (prefs.getBoolean("reduce_motion", false)) "ON" else "OFF"
        val float = if (prefs.getBoolean("float", true)) "ON" else "OFF"
        val items = arrayOf("Reduce motion: $motion", "Floating assistant bubble: $float", "Restore default home")
        AlertDialog.Builder(this).setTitle("Appearance").setItems(items) { _, i ->
            when (i) {
                0 -> { prefs.edit().putBoolean("reduce_motion", !prefs.getBoolean("reduce_motion", false)).apply(); pgAppearance() }
                1 -> {
                    val on = !prefs.getBoolean("float", true)
                    prefs.edit().putBoolean("float", on).apply()
                    if (on) AssistantService.ensure(this) else AssistantService.stop(this)
                    pgAppearance()
                }
                2 -> confirmRestoreHome()
            }
        }.setNegativeButton("CLOSE", null).show()
    }

    /**
     * Puts the home page back to the app's defaults. The defaults are built into the app, so nothing needs
     * to be stored. Chat, memory, tasks, plan and owner data are not touched.
     */
    private fun confirmRestoreHome() {
        AlertDialog.Builder(this).setTitle("Restore default home?")
            .setMessage("Home shortcuts, the dashboard plan, motion and the floating bubble go back to the defaults. Chat, memory, tasks, plan and owner data stay.")
            .setPositiveButton("RESTORE") { _, _ ->
                prefs.edit().remove("shortcuts").remove("dashboard_plan").remove("reduce_motion")
                    .putBoolean("float", true).apply()
                AssistantService.ensure(this)
                show("AGENT", "Home restored to the defaults.")
            }
            .setNegativeButton("CANCEL", null).show()
    }

    /** Language: the voice language and the language the AI replies in. */
    fun pgLanguage() {
        val voice = prefs.getString("vlang", "")?.ifBlank { "default" } ?: "default"
        val reply = prefs.getString("reply_lang", "auto") ?: "auto"
        val items = arrayOf(
            "Voice: English (en-US)", "Voice: Bangla (bn-BD)", "Voice: Hindi (hi-IN)",
            "Reply: same as you (auto)", "Reply: always English", "Reply: always Bangla"
        )
        AlertDialog.Builder(this).setTitle("Language  (voice: $voice, reply: $reply)").setItems(items) { _, i ->
            when (i) {
                0 -> prefs.edit().putString("vlang", "en-US").apply()
                1 -> prefs.edit().putString("vlang", "bn-BD,en-US").apply()
                2 -> prefs.edit().putString("vlang", "hi-IN,en-US").apply()
                3 -> prefs.edit().putString("reply_lang", "auto").apply()
                4 -> prefs.edit().putString("reply_lang", "en").apply()
                5 -> prefs.edit().putString("reply_lang", "bn").apply()
            }
            show("AGENT", "Language saved.")
        }.setNegativeButton("CLOSE", null).show()
    }

    /** Notifications: reminder alerts, a test notification, and the system settings. */
    fun pgNotif() {
        val rem = if (Reminders.enabled(this)) "ON" else "OFF"
        val items = arrayOf("Reminder alerts: $rem", "Send a test notification", "Open system notification settings")
        AlertDialog.Builder(this).setTitle("Notifications").setItems(items) { _, i ->
            when (i) {
                0 -> {
                    val on = !Reminders.enabled(this)
                    Reminders.setEnabled(this, on)
                    show("AGENT", "Reminder alerts " + (if (on) "ON" else "OFF"))
                    if (on && Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
                }
                1 -> testNotification()
                2 -> try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (e: Exception) { show("AGENT", "Could not open notification settings.") }
            }
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun testNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val ch = "ts_test"
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel(ch, "TS Agent", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val n = (if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, ch) else android.app.Notification.Builder(this))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TS Agent")
            .setContentText("Notifications are working.")
            .setAutoCancel(true)
            .build()
        nm.notify(4242, n)
        show("AGENT", "Test notification sent.")
    }

    /** Help: the website pages, Telegram and the setup guide. */
    fun pgHelp() {
        val items = arrayOf("Terms of Service", "Privacy Policy", "Copyright", "Contact us", "Telegram channel", "Setup guide")
        val pages = arrayOf("terms.php", "privacy.php", "copyright.php", "contact.php")
        AlertDialog.Builder(this).setTitle("Help").setItems(items) { _, i ->
            when (i) {
                0, 1, 2, 3 -> {
                    if (!Config.enabled()) show("AGENT", "The website is not connected yet. Ask the owner for the link.")
                    else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SERVER + "/" + pages[i])))
                }
                4 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL)))
                5 -> showSetup()
            }
        }.setNegativeButton("CLOSE", null).show()
    }
    fun pgPlans() = openPlanDashboard()
    fun pgFeed() { openPage(Pg.NONE); showFeed() }

    /** Closes the page, opens the chat box and puts the tool's starting words in it. */
    fun pgTool(tool: String, prefix: String) {
        if (tool.isNotEmpty() && !Entitlements.can(this, PlanRights.Feature.AI_TOOLS)) { pgLockedTool(tool); return }
        if (tool.isNotEmpty()) { nextHistType = "tools"; nextHistTitle = tool }
        openPage(Pg.NONE)
        chatRow.visibility = View.VISIBLE
        input.setText(prefix)
        input.setSelection(input.text.length)
        input.requestFocus()
        try { (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, 0) } catch (_: Exception) {}
    }

    fun pgPickFile() {
        if (!Entitlements.can(this, PlanRights.Feature.AI_TOOLS)) { pgLockedTool("File analyzer"); return }
        nextHistType = "tools"; nextHistTitle = "FILE ANALYZER"
        openPage(Pg.NONE)
        chatRow.visibility = View.VISIBLE
        pickAssistantFile()
    }

    fun pgEditName() {
        val et = EditText(this).apply {
            setText(prefs.getString("user_name", "") ?: ""); hint = "Your name"
            setTextColor(Theme.G); setHintTextColor(Theme.DIM); typeface = mono
        }
        val box = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0) }
        box.addView(et, LinearLayout.LayoutParams(-1, -2))
        AlertDialog.Builder(this).setTitle("Personal Information").setView(box)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("user_name", et.text.toString().trim()).apply(); openPage(Pg.PROFILE) }
            .setNegativeButton("Cancel", null).show()
    }

    fun pgLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Lock owner mode and sign out of your account on this phone?")
            .setPositiveButton("Logout") { _, _ ->
                OwnerAuth.logout(this)
                prefs.edit().remove("svtoken").apply()
                openPage(Pg.NONE)
                show("AGENT", "Logged out. Open SETTINGS to log in again.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    /** Tap OWNER on the status strip: set the passphrase the first time, unlock later, or lock again. */
    private fun showOwnerDialog() {
        if (OwnerAuth.unlocked(this)) {
            AlertDialog.Builder(this).setTitle("OWNER MODE").setMessage("Owner mode is ON.")
                .setPositiveButton("Close", null)
                .setNegativeButton("Lock") { _, _ -> OwnerAuth.logout(this); show("AGENT", "Owner mode locked") }.show()
            return
        }
        val first = !OwnerAuth.isSet(this)
        val et = EditText(this).apply {
            hint = if (first) "Choose a passphrase (8+ characters)" else "Owner passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = mono
        }
        val box = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0) }
        box.addView(et, LinearLayout.LayoutParams(-1, -2))
        AlertDialog.Builder(this).setTitle("OWNER MODE")
            .setMessage(if (first) "First time: choose a passphrase. It is stored only as a salted hash on this phone." else "Enter your owner passphrase to unlock.")
            .setView(box)
            .setPositiveButton(if (first) "Set" else "Unlock") { _, _ -> show("AGENT", OwnerAuth.login(this, et.text.toString())) }
            .setNegativeButton("Cancel", null).show()
    }

    /** First-time setup guide: what to add, where to get it. Shown once, and again by typing "setup". */
    fun showSetup() {
        val text = "1. Open SETTINGS.\n" +
            "2. AI key: get a free Gemini key from aistudio.google.com/apikey and paste it in the Gemini key box, then press SAVE.\n" +
            "3. Voice (optional): get an ElevenLabs key from elevenlabs.io, paste it in the ElevenLabs key box, then pick a voice ID.\n" +
            "4. Allow Accessibility and Notifications when the app asks.\n" +
            "5. Say hello to test."
        AlertDialog.Builder(this).setTitle("Setup").setMessage(text).setPositiveButton("OK", null).show()
    }

    /** Words that end the session at once. Extend this list if needed. */
    private val rudeWords = listOf("fuck", "shit", "bitch", "madarchod", "bokachoda", "হারামি", "শুয়োর")

    private fun isRude(s: String): Boolean {
        val l = s.lowercase()
        return rudeWords.any { l.contains(it) }
    }

    /** Stops listening and speech, then closes the assistant right away. */
    private fun shutDownForRude() {
        keepListening = false
        Agent.cancelled = true
        listening = false
        tts?.stop(); Voice.stop()
        main.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
        show("AGENT", "Rude language detected. Closing now. Open me again when you are ready.")
        finishAndRemoveTask()
    }

    /** Adds the typed key(s) to the saved list for this provider. The saved keys are kept; duplicates are dropped. */
    private fun mergedKeys(prefKey: String, typed: String): String {
        // a new ElevenLabs key clears the one-hour pause, so the new key is tried at once
        if (prefKey == "el_key" && typed.isNotBlank()) prefs.edit().remove("el_pause_until").apply()
        val split = { x: String -> x.split(Regex("[\\r\\n,]+")).map { it.trim() }.filter { it.isNotEmpty() } }
        return (split(prefs.getString(prefKey, "") ?: "") + split(typed)).distinct().joinToString(",")
    }

    private fun submit(text: String) {
        if (text.isBlank() && pendingAttachment == null) return
        val attachment = pendingAttachment
        val userText = text.ifBlank { "Please inspect the attached file and explain what it contains." }
        val hType = nextHistType ?: "chat"
        Hist.add(this, hType, nextHistTitle ?: (if (hType == "voice") "Voice Assistant" else "AI Chat"), userText)
        nextHistType = null; nextHistTitle = null
        show("YOU", userText + (attachment?.let { "\\n[Attached: ${it.name}]" } ?: ""))
        val prompt = buildString {
            if (planMode) append("First create a short, easy-to-understand step-by-step plan. Then carry out the safe parts and verify the result.\\n\\n")
            append(userText)
            if (attachment != null) {
                append("\\n\\n[LOCAL ATTACHMENT CONTEXT]\\n")
                append(readAssistantAttachment(attachment))
                append("\\n[END ATTACHMENT CONTEXT]")
            }
        }
        pendingAttachment = null
        planMode = false
        CommandRunner(this) { s ->
            if (s.startsWith("•")) stage.caption = s.removePrefix("•").trim() else { show("AGENT", s); speakOnce(s); lastReply = cleanReply(s); Hist.addReply(this, cleanReply(s)) }
        }.run(prompt)
    }

    /** Opens Android's document picker for source code, ZIP archives, photos and documents. */
    private fun pickAssistantFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/*", "application/zip", "application/x-zip-compressed",
                "application/json", "application/pdf", "image/*",
                "application/octet-stream"
            ))
        }
        startActivityForResult(intent, 79)
    }

    /** Copies a selected document into private app storage so its content can be read safely. */
    private fun saveAssistantAttachment(uri: Uri) {
        try {
            val name = queryDisplayName(uri).ifBlank { "attachment_${System.currentTimeMillis()}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_").take(100)
            val out = File(filesDir, "assistant_uploads").apply { mkdirs() }
            val target = File(out, name)
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Could not open the selected file")
            pendingAttachment = target
            show("AGENT", "Selected: ${target.name} (${target.length()} bytes). Now write what you want me to do with it, then tap EXEC.")
        } catch (e: Exception) {
            show("AGENT", "File could not be attached: ${e.message ?: "unknown error"}")
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null) ?: return ""
        return cursor.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && index >= 0) it.getString(index).orEmpty() else ""
        }
    }

    /** Text/code files are included as bounded text; ZIP files get an entry inventory.
     *  Photos/PDFs are attached locally but are not sent as image/PDF bytes to the text-only model.
     */
    private fun readAssistantAttachment(file: File): String {
        val name = file.name.lowercase()
        val maxBytes = 96 * 1024
        return try {
            when {
                name.endsWith(".zip") -> {
                    val lines = mutableListOf<String>()
                    java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null && lines.size < 250) {
                            if (!entry.isDirectory) lines.add("${entry.name} (${entry.size} bytes)")
                            entry = zip.nextEntry
                        }
                    }
                    "ZIP archive: ${file.name}\nFiles inside (up to 250):\n" + lines.joinToString("\n")
                }
                name.matches(Regex(".*\\\\.(kt|java|py|js|ts|html|css|xml|json|md|txt|yml|yaml|gradle|kts|sh|sql|php|properties)$")) -> {
                    val bytes = file.inputStream().use { stream ->
                        val buffer = ByteArray(maxBytes)
                        var count = 0
                        while (count < maxBytes) {
                            val n = stream.read(buffer, count, maxBytes - count)
                            if (n <= 0) break
                            count += n
                        }
                        buffer.copyOf(count)
                    }
                    "Text/code file: ${file.name}\n" + String(bytes, Charsets.UTF_8) +
                        if (file.length() > maxBytes) "\n[Preview truncated at 96 KB]" else ""
                }
                name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") -> 
                    "Image file selected: ${file.name}, ${file.length()} bytes. The current text-only AI connection cannot view the image pixels yet. Ask the user to describe the relevant part or connect a vision-capable upload endpoint."
                name.endsWith(".pdf") ->
                    "PDF file selected: ${file.name}, ${file.length()} bytes. PDF page text extraction is not enabled in this Android build."
                else -> "File selected: ${file.name}, ${file.length()} bytes. This file type is stored locally; automatic content parsing is not enabled."
            }
        } catch (e: Exception) {
            "Could not read attachment ${file.name}: ${e.message}"
        }
    }

    private fun voiceLangs(): List<String> =
        (prefs.getString("vlang", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(Config.VOICE_LOCALE) }

    private fun voiceLang(): String { val l = voiceLangs(); return l[prefs.getInt("vlang_idx", 0) % l.size] }

    private fun toggleVoiceLang(): String {
        val l = voiceLangs()
        val n = (prefs.getInt("vlang_idx", 0) + 1) % l.size
        prefs.edit().putInt("vlang_idx", n).apply()
        return l[n]
    }

    private fun speak(text: String, lang: String? = null) {
        if (!SpeechGate.claim("main", text) { tts?.stop(); Voice.stop() }) return
        recognizer?.cancel() // the mic is off while the reply is spoken
        if (Voice.configured(this)) {
            Voice.speak(this, text, { setState(2) }, { if (stage.state == 2) setState(0); afterSpeak() }) { systemSpeak(text, lang) }
            return
        }
        systemSpeak(text, lang)
    }

    /** Picks the phone voice for the language. For Bangla it prefers Bangla (India), the voice you chose on the phone. */
    private fun applyVoice(t: TextToSpeech, tag: String) {
        val loc = Locale.forLanguageTag(tag)
        t.setLanguage(loc)
        if (loc.language != "bn") return
        val voices = try { t.voices } catch (e: Exception) { null } ?: return
        // The same voice every time: the chosen name is saved. Only Bangla offline voices are candidates.
        val candidates = voices.filter { it.locale.language == "bn" && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country != "IN" }, { it.name }))
        if (candidates.isEmpty()) return
        val saved = prefs.getString("tts_voice_name", "") ?: ""
        val pick = candidates.firstOrNull { it.name == saved } ?: candidates.first()
        prefs.edit().putString("tts_voice_name", pick.name).apply()
        t.voice = pick
    }

    /** "voice switch": moves to the next Bangla voice on the phone, then keeps it. */
    fun switchVoice(): String {
        val t = tts ?: return "Text-to-speech is not ready yet."
        val voices = try { t.voices } catch (e: Exception) { null } ?: return "This phone does not list voices."
        val candidates = voices.filter { it.locale.language == "bn" && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country != "IN" }, { it.name }))
        if (candidates.isEmpty()) return "No Bangla offline voice found on this phone."
        val cur = prefs.getString("tts_voice_name", "") ?: ""
        val idx = candidates.indexOfFirst { it.name == cur }
        val next = candidates[(idx + 1 + candidates.size) % candidates.size]
        prefs.edit().putString("tts_voice_name", next.name).apply()
        t.voice = next
        t.speak("Hello, this is my new voice.", TextToSpeech.QUEUE_FLUSH, null, "vsw")
        return "Voice changed to " + next.name + "."
    }

    private fun systemSpeak(text: String, lang: String?) {
        tts?.let { applyVoice(it, if (!lang.isNullOrBlank()) lang else voiceLang()) }
        // one utterance, one voice: the whole reply in the same voice (no chunks, so the voice does not change mid-reply)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ts")
    }

    private fun stopAll() {
        Agent.cancelled = true
        tts?.stop(); Voice.stop()
        recognizer?.cancel()
        setState(0)
    }

    /** After a reply finishes, listen again so the conversation keeps going. */
    private fun afterSpeak() {
        if (!keepListening) return
        main.postDelayed({ if (keepListening) listen() }, 300)
    }

    /** Picks the best of the recognizer's guesses. If one contains the name, it wins; a lone "ছায়া" is read as the name. */
    /** Common mis-hearings of the name and words, fixed on the phone with no AI call. Add more pairs here. */
    private val voiceFix: List<Pair<Regex, String>> = listOf(
        Regex("(^|\\s)ছায়া(?=$|\\s|[?!।.,])") to "$1সারা",
        Regex("(^|\\s)শারা(?=$|\\s|[?!।.,])") to "$1সারা",
        Regex("(^|\\s)সাড়া(?=$|\\s|[?!।.,])") to "$1সারা",
        Regex("(^|\\s)সারাহ(?=$|\\s|[?!।.,])") to "$1সারা",
        Regex("(?i)\\bsara\\b") to "Sara",
        Regex("(টার্মার্কস|টারমাক্স|টার্মাক্স|টার্মাকস|টারমার্কস|টার্মাস|termax|termus|term ox|term max)") to "Termux"
    )

    private fun fixVoice(s: String): String {
        var out = s
        for ((rx, rep) in voiceFix) out = rx.replace(out, rep)
        return out
    }

    private fun pickSpoken(alts: List<String>): String? {
        val list = alts.filter { it.isNotBlank() }.map { fixVoice(it) }
        if (list.isEmpty()) return null
        return list.firstOrNull { it.contains("সারা") || it.contains("Sara") } ?: list.first()
    }

    /** The user started talking while the phone was replying or working: stop the reply and the task, then listen. */
    private fun interrupt() {
        Agent.cancelled = true
        tts?.stop(); Voice.stop()
        if (stage.state == 2) setState(1)
    }

    /** Only the user turns listening off. */
    private fun stopKeep() {
        keepListening = false
        Agent.cancelled = true
        listening = false
        tts?.stop(); Voice.stop()
        main.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
        setState(0)
        show("AGENT", "Stopped. Tap the mic to talk again.")
    }

    private fun listen() {
        if (listening) return
        if (stage.state == 2) return // never open the mic while the reply is spoken
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            show("AGENT", "Speech recognition was not found on this phone. Update the Google app."); return
        }
        listening = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { if (stage.state == 0) setState(1) }
                override fun onBeginningOfSpeech() {
                    // The mic is off while the reply is spoken, so nothing here cuts the reply.
                }
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(e: Int) {
                    listening = false
                    main.post { recognizer?.destroy(); recognizer = null }
                    if (stage.state == 1) setState(0)
                    if (!keepListening) return
                    if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        keepListening = false
                        show("AGENT", "Microphone permission is needed. Allow it in settings.")
                        return
                    }
                    // Silence, no match, busy or network: just listen again. The mic does not turn off on its own.
                    val wait = when (e) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> 2000L
                        else -> 300L
                    }
                    main.postDelayed({ if (keepListening) listen() }, wait)
                }
                override fun onResults(r: Bundle?) {
                    listening = false
                    main.post { recognizer?.destroy(); recognizer = null }
                    if (stage.state == 1) setState(0)
                    // a result that came while the phone was still talking is its own echo: drop it
                    val t = pickSpoken(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                    // While the phone is talking, drop only its own voice coming back into the mic (echo).
                    // A real new sentence from the user interrupts the reply and is answered.
                    // The reply is still being spoken: whatever the mic caught (often the phone's own voice) is ignored.
                    // Listening restarts by itself when the reply ends (afterSpeak). Only the ABORT button stops a reply.
                    if (stage.state == 2) return
                    if (!t.isNullOrBlank() && isRude(t)) { main.post { shutDownForRude() }; return }
                    // the recognizer sometimes returns the same words again (echo or late result): skip a repeat within 6 seconds
                    val now = System.currentTimeMillis()
                    if (t != null && SpeechGate.isEcho(t)) { if (keepListening) main.postDelayed({ if (keepListening) listen() }, 300); return }
                    val repeat = t != null && t == lastSubmitted && now - lastSubmittedAt < 6000L
                    if (!t.isNullOrBlank() && !repeat) { lastSubmitted = t; lastSubmittedAt = now; nextHistType = "voice"; nextHistTitle = "Voice Assistant"; submit(t) }
                    if (keepListening) main.postDelayed({ if (keepListening) listen() }, 300)
                }
                override fun onPartialResults(b: Bundle?) {
                    // Talking over the phone interrupts it, so the user is always heard first.
                    val p = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!p.isNullOrBlank() && isRude(p)) { main.post { shutDownForRude() }; return }
                    // Only while the phone is talking, and only for real words: a running task is not cut off by noise.
                    // while the phone is talking, only a real sentence counts (short echo of its own voice is ignored)
                    // Listening is off while the reply is spoken, so partial results do not interrupt it.
                }
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // a short pause ends the sentence, so the answer comes quickly
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) AssistantService.ensure(this)
        if (code == 2 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
        if (code == 4 && WatchService.enabled(this)) WatchService.start(this)
    }

    private fun refreshTasks() {
        val all = (prefs.getString("tasks", "") ?: "").lines().filter { it.isNotBlank() }
        tasksText.setTasks(all)
        flashIn(tasksText)
    }

    private fun editNote(title: String, key: String, def: String) {
        val et = EditText(this).apply {
            setText(prefs.getString(key, def)); minLines = 6; gravity = Gravity.TOP
            setTextColor(Theme.G); typeface = mono
        }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString(key, et.text.toString()).apply(); refreshTasks()
                if (key == "rem_lines") Reminders.scheduleAll(this)
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun loadAccount() {
        val tok = prefs.getString("svtoken", null) ?: return
        Thread {
            val r = Account.post("me", JSONObject(), tok)
            runOnUiThread {
                if (r.first == 200) {
                    val j = r.second
                    val lim = if (j.optInt("limit") == 0) "∞" else j.optInt("limit").toString()
                    if (j.has("coins_cap")) { prefs.edit().putString("coins_text", "${j.optInt("coins_left")}/${j.optInt("coins_cap")}" + (if (j.optInt("bonus") > 0) "+${j.optInt("bonus")}" else "")).apply(); show("AGENT", "Plan: ${j.optString("plan")} • coins today ${j.optInt("coins_left")}/${j.optInt("coins_cap")}" + (if (j.optInt("bonus") > 0) " + ${j.optInt("bonus")} bonus" else "")) } else show("AGENT", "Plan: ${j.optString("plan")} • today ${j.optInt("used")}/$lim")
                    val n = j.optString("notice")
                    if (n.isNotBlank()) show("AGENT", n)
                } else if (r.first == 401) {
                    prefs.edit().remove("svtoken").apply()
                    show("AGENT", "Log in again")
                }
            }
        }.start()
    }

    private val newsTick = object : Runnable {
        override fun run() { refreshNews(); main.postDelayed(this, 900000L) }
    }

    private fun refreshNews() {
        newsText.text = "[ HEADLINES ]\nloading..."
        Thread {
            val items = Headlines.fetch(this, 4)
            runOnUiThread {
                val sb = SpannableStringBuilder("[ HEADLINES ]\n")
                if (items.isEmpty()) sb.append("Could not load. Tap to retry.")
                else items.forEach { h ->
                    val s = sb.length
                    sb.append(Glyphs.span(this@MainActivity, Glyphs.ARROW, Theme.C)); sb.append(" " + h.title.take(60) + "\n")
                    sb.setSpan(URLSpan(h.link), s, sb.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                newsText.text = sb
                flashIn(newsText)
            }
        }.start()
    }

    /** The one sound used for videos. Uploading a new one replaces it. */
    private fun pickVideoSound() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), 78)
    }

    private fun removeVideoSound() {
        File(filesDir, "video_sound.audio").delete()
        prefs.edit().remove("video_sound_path").apply()
        show("AGENT", "Video sound removed. No sound is used until you upload one.")
    }

    private fun pickSong() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), 77)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 79 && res == RESULT_OK) {
            data?.data?.let { saveAssistantAttachment(it) }
        }
        if (req == 78 && res == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val out = File(filesDir, "video_sound.audio")
                    contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    prefs.edit().putString("video_sound_path", out.absolutePath).apply()
                    show("AGENT", "Video sound saved. Videos will use it.")
                } catch (e: Exception) {
                    show("AGENT", "Could not save the sound: " + e.message)
                }
            }
        }
        if (req == 77 && res == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
                prefs.edit().putString("fav_song_uri", uri.toString()).apply()
                show("AGENT", "Favorite song saved. Say: play my favorite song")
            }
        }
    }

    private fun checkRemote() {
        Thread {
            Entitlements.syncRevocations(this)
            val u = Remote.checkUpdate(this)
            val n = Remote.notice()
            runOnUiThread {
                if (u != null) show("AGENT", u)
                if (n != null && n != prefs.getString("last_notice", "")) {
                    show("AGENT", n)
                    prefs.edit().putString("last_notice", n).apply()
                }
            }
        }.start()
    }

    /** Reads the admin's notice and maintenance state from the server. Runs in the background. */
    private fun syncServer() {
        if (!Config.enabled()) return
        val r = Account.post("config", JSONObject())
        if (r.first != 200) return
        val j = r.second
        prefs.edit().putBoolean("srv_voice", j.optBoolean("voice_ready")).putBoolean("srv_ai", j.optBoolean("ai_ready")).apply()
        val nm = j.optString("site_name").trim()
        if (nm.isNotBlank()) prefs.edit().putString("srv_name", nm.uppercase()).apply()
        val al = j.optString("aliases")
        prefs.edit().putString("srv_aliases", al).apply()
        CommandRunner.setServerAliases(al)
        runOnUiThread {
            val sv = j.optString("app_version").toIntOrNull() ?: 0
            if (sv > (Config.APP_VERSION.toIntOrNull() ?: 0)) show("AGENT", "A new version (" + sv + ") is available. Download: " + j.optString("app_url"))
            val n = j.optString("notice")
            if (n.isNotBlank() && n != prefs.getString("srv_notice_seen", "")) {
                show("AGENT", n)
                prefs.edit().putString("srv_notice_seen", n).apply()
            }
            if (j.optBoolean("maintenance")) show("AGENT", "The server is under maintenance. Some features may not work right now.")
        }
    }

    /** Shows the latest posts from the admin panel. Tap one to open its link or photo/video. */
    private fun showFeed() {
        show("AGENT", "Loading the latest posts...")
        Thread {
            val r = Account.post("feed", JSONObject())
            runOnUiThread {
                val arr = r.second.optJSONArray("posts")
                if (r.first != 200) {
                    show("AGENT", r.second.optString("error", "Could not load the feed"))
                } else if (arr == null || arr.length() == 0) {
                    show("AGENT", "No posts yet.")
                } else {
                    val titles = ArrayList<String>()
                    val links = ArrayList<String>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val t = o.optString("title").ifBlank { o.optString("body").take(60) }.ifBlank { "(photo or video)" }
                        titles.add(o.optString("created_at").take(10) + "  " + t)
                        val media = o.optString("media_url")
                        val mediaFull = if (media.isBlank()) "" else if (media.startsWith("http")) media else Config.SERVER + "/" + media
                        links.add(o.optString("link").ifBlank { mediaFull })
                    }
                    AlertDialog.Builder(this@MainActivity).setTitle("LATEST FROM TS")
                        .setItems(titles.toTypedArray()) { _, i ->
                            if (links[i].isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[i])))
                        }
                        .setNegativeButton("Close", null).show()
                }
            }
        }.start()
    }

    /** Settings and memory as text, without keys, tokens or passwords. Keep it safe: it restores your data after a reinstall. */
    private fun exportBackup(): String {
        val o = JSONObject()
        for ((k, v) in prefs.all) {
            if (listOf("key", "token", "secret", "pass", "hash").any { k.contains(it, true) }) continue
            if (v == null || v is Set<*>) continue
            o.put(k, v)
        }
        return o.toString()
    }

    private fun importBackup(text: String): Boolean = try {
        val o = JSONObject(text)
        val ed = prefs.edit()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            when (val v = o.get(k)) {
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
                is Double -> ed.putFloat(k, v.toFloat())
                else -> ed.putString(k, v.toString())
            }
        }
        ed.apply()
        true
    } catch (e: Exception) { false }

    /** One consistent look for every Settings button and text box: neon outline, rounded, comfortable size. */
    private fun styleSettings(v: View) {
        when (v) {
            is Button -> {
                v.background = GradientDrawable().apply { setColor(0x1A00FF9C.toInt()); setStroke(dp(1), Theme.C); cornerRadius = dp(8).toFloat() }
                v.setTextColor(Theme.C)
                v.textSize = 12f
                v.isAllCaps = false
                v.minimumHeight = dp(46)
                v.setPadding(dp(14), dp(10), dp(14), dp(10))
                v.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
            }
            is EditText -> {
                v.background = GradientDrawable().apply { setColor(0xCC02100A.toInt()); setStroke(dp(1), Theme.DIM); cornerRadius = dp(8).toFloat() }
                v.textSize = 13f
                v.setPadding(dp(12), dp(10), dp(12), dp(10))
                v.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) styleSettings(v.getChildAt(i))
    }

    private fun openPlanDashboard() {
        try { startActivity(Intent(this, PlanDashboardActivity::class.java)) }
        catch (e: Exception) { show("PLANS", "Could not open Plan Dashboard: ${e.message}") }
    }

    /** Puts the 3D object of the plan on Home. Where it sits and how big follows the globe of the stage. */
    private fun placePlan3d() {
        if (!::plan3d.isInitialized || !::stage.isInitialized) return
        val w = stage.width; val h = stage.height
        if (w <= 0 || h <= 0) return
        plan3d.setFocus(stage.orbX / w, stage.orbY / h, (stage.orbR * 2.4f) / minOf(w, h))
    }

    /**
     * Free users keep the normal home. An active plan replaces the middle of Home with that plan's real 3D animation
     * (Plan3DView). Only the top header and the bottom bar stay. REVIEW opens the same animation full screen.
     */
    private fun applyPurchasedPlanHome() {
        if (!::plan3d.isInitialized || !::stage.isInitialized) return
        val number = Entitlements.dashboardPlanNumber(this)
        if (number !in 1..17) {
            homeMid.forEach { it.visibility = View.VISIBLE }
            reviewBtn?.visibility = View.GONE
            plan3d.visibility = View.GONE
            stage.visibility = View.VISIBLE
            stage.showOrb = true
            planThemeLabel.alpha = 0f
            return
        }
        val p = PlanCatalog.get(number)
        homeMid.forEach { it.visibility = View.GONE }
        stage.visibility = View.GONE
        plan3d.setPlan(number)
        plan3d.visibility = View.VISIBLE
        reviewBtn?.visibility = View.VISIBLE
        planThemeLabel.text = "PLAN " + number.toString().padStart(2, '0') + "  " + p.name.uppercase(Locale.US)
        planThemeLabel.setTextColor(p.accent)
        planThemeLabel.alpha = 1f
        placePlan3d()
    }

    /** REVIEW: the active plan's 3D animation on its own full page, with its name and a CLOSE button. */
    private fun openPlanReview() {
        val n = Entitlements.dashboardPlanNumber(this)
        if (n !in 1..17) return
        closeReview()
        val p = PlanCatalog.get(n)
        val box = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        val view = Plan3DView(this).apply { setBackdrop(true); setPlan(n); onResume() }
        box.addView(view, FrameLayout.LayoutParams(-1, -1))
        box.addView(TextView(this).apply {
            text = "PLAN " + n.toString().padStart(2, '0') + "  " + p.name.uppercase(Locale.US)
            setTextColor(p.accent); typeface = Typeface.DEFAULT_BOLD; textSize = 17f
            setShadowLayer(8f * resources.displayMetrics.density, 0f, 0f, p.accent)
            setPadding(dp(16), dp(20), dp(16), dp(10))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        box.addView(TextView(this).apply {
            text = Glyphs.label(this@MainActivity, Glyphs.CROSS, Color.WHITE, "CLOSE"); setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; textSize = 13f
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = NeonBg(Theme.G, 0xDD02100A.toInt(), 8f, 1.4f)
            setOnClickListener { closeReview() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
        reviewLayer = box
        reviewView = view
        root.addView(box, FrameLayout.LayoutParams(-1, -1))
    }

    /** First-run setup screen, also opened from the SETUP button. */
    private fun openSetupWizard() {
        if (setupWizard != null) return
        val w = SetupWizard(this) { closeSetupWizard() }
        setupWizard = w
        root.addView(w, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeSetupWizard() {
        setupWizard?.let { root.removeView(it) }
        setupWizard = null
        prefs.edit().putBoolean("setup_wizard_seen", true).apply()
    }

    private fun closeReview() {
        reviewView?.onPause()
        reviewLayer?.let { root.removeView(it) }
        reviewLayer = null
        reviewView = null
    }

    private fun showHistory() {
        val tv = TextView(this).apply {
            text = if (buf.isEmpty()) "No conversation yet in this session." else buf.toString()
            setTextColor(Theme.TX); typeface = mono; textSize = 12f
            setPadding(dp(16), dp(8), dp(16), dp(8)); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle("HISTORY").setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Latest from TS") { _, _ -> showFeed() }.show()
    }

    private fun showProfile() {
        val dn = Entitlements.dashboardPlanNumber(this)
        val owned = Entitlements.ownedPlanNumbers(this)
        val sb = StringBuilder()
        sb.append("Plan on Home: ").append(if (dn in 1..17) "PLAN %02d  %s".format(dn, PlanCatalog.get(dn).name) else "FREE").append('\n')
        sb.append("Plans owned: ").append(if (owned.isEmpty()) "none" else owned.joinToString(", ") { "P$it" }).append('\n')
        sb.append("Coins: ").append(prefs.getString("coins_text", "-")).append('\n')
        sb.append("Access: ").append(Entitlements.label(this))
        AlertDialog.Builder(this).setTitle("PROFILE").setMessage(sb.toString())
            .setPositiveButton("Plans") { _, _ -> openPlanDashboard() }
            .setNeutralButton("Settings") { _, _ -> showSettings() }
            .setNegativeButton("Close", null).show()
    }

    private fun showSettings() {
        Thread { FeatureSwitches.refresh(this) }.start()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        col.addView(TextView(this).apply {
            text = "dev Tarek4x"; typeface = mono; textSize = 12f; setTextColor(Theme.DIM)
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(4), 0, dp(10))
        })
        fun section(title: String) {
            col.addView(TextView(this).apply {
                text = title; setTextColor(Theme.G); textSize = 13f; typeface = mono
                letterSpacing = 0.14f; setPadding(0, dp(24), 0, dp(4))
            })
            col.addView(View(this).apply { setBackgroundColor(Theme.G) },
                LinearLayout.LayoutParams(dp(56), dp(2)).apply { bottomMargin = dp(8) })
        }
        fun field(hint: String, key: String, secret: Boolean = false): EditText {
            val e = EditText(this).apply {
                this.hint = hint
                setTextColor(Theme.G); setHintTextColor(Theme.DIM); typeface = mono
                if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else setText(prefs.getString(key, ""))
            }
            col.addView(e)
            return e
        }
        section("PLANS & HOME DESIGN")
        col.addView(Button(this).apply {
            typeface = mono; text = "OPEN 17 PLAN DASHBOARD"
            setOnClickListener { openPlanDashboard() }
        })
        section("AI")
        val hasKey = prefs.getString("apikey", null) != null
        val api = field(if (hasKey) "Your Anthropic API key (saved, only on this phone)" else "Your own Anthropic API key (stays only on this phone)", "apikey", true)
        val keyOpenai = field("OpenAI key (optional)", "key_openai", true)
        /** ADD button: the key typed in the box is added to the saved list (one at a time), and the box is cleared. */
        fun keyAddButton(prefKey: String, f: EditText, name: String): Button {
            val saved = (prefs.getString(prefKey, "") ?: "").split(Regex("[\\r\\n,]+")).count { it.isNotBlank() }
            return Button(this).apply {
                typeface = mono; text = "ADD $name KEY TO LIST (saved: $saved)"
                setOnClickListener {
                    val k = f.text.toString().trim()
                    if (k.isEmpty()) { show("AGENT", "Type one key in the box first."); return@setOnClickListener }
                    val merged = mergedKeys(prefKey, k)
                    prefs.edit().putString(prefKey, merged).apply()
                    f.setText("")
                    val count = merged.split(",").size
                    text = "ADD $name KEY TO LIST (saved: $count)"
                    show("AGENT", "$name key added. Saved keys: $count. Add the next one.")
                }
            }
        }
        val keyGemini = field("Google Gemini key (optional)", "key_gemini", true)
        col.addView(keyAddButton("key_gemini", keyGemini, "GEMINI"))
        val keyGrok = field("xAI Grok key (optional)", "key_grok", true)
        val baseCustom = field("Custom AI provider base URL (OpenAI-compatible, e.g. https://openrouter.ai/api/v1)", "llm_base")
        val keyCustom = field("Custom provider key (optional)", "key_custom", true)
        col.addView(keyAddButton("key_custom", keyCustom, "CUSTOM"))
        // Only providers with free models are shown: Anthropic, OpenAI and Grok fields are hidden.
        api.visibility = View.GONE; keyOpenai.visibility = View.GONE; keyGrok.visibility = View.GONE
        fun saveKeys() {
            val ed = prefs.edit()
            if (api.text.isNotBlank()) ed.putString("apikey", api.text.toString().trim())
            listOf("openai" to keyOpenai, "gemini" to keyGemini, "grok" to keyGrok, "custom" to keyCustom)
                .forEach { (p, f) -> if (f.text.isNotBlank()) ed.putString("key_$p", mergedKeys("key_$p", f.text.toString())) }
            ed.putString("llm_base", baseCustom.text.toString().trim())
            ed.apply()
        }
        fun provLabel() = "AI PROVIDER: " + Llm.provider(this).uppercase(Locale.US) + " / " + (Llm.model(this).ifBlank { "(choose a model)" })
        val provBtn = Button(this).apply { typeface = mono; text = provLabel() }
        provBtn.setOnClickListener { Llm.next(this@MainActivity); provBtn.text = provLabel() }
        col.addView(provBtn)
        col.addView(Button(this).apply {
            typeface = mono; text = "CHOOSE MODEL (active provider)"
            setOnClickListener {
                saveKeys()
                show("AGENT", "Loading models...")
                Thread {
                    val (models, err) = Llm.listModels(this@MainActivity)
                    runOnUiThread {
                        if (err != null) show("AGENT", err)
                        else if (models.isEmpty()) show("AGENT", "No models returned")
                        else AlertDialog.Builder(this@MainActivity).setTitle(if (Llm.provider(this@MainActivity) == "cloud") "Choose a tier: 1 lite, 2 standard, 3 pro" else "Choose a model")
                            .setItems(models.toTypedArray()) { _, i ->
                                prefs.edit().putString("model_" + Llm.provider(this@MainActivity), models[i]).apply()
                                provBtn.text = provLabel()
                                show("AGENT", "Model set: ${models[i]}")
                            }.show()
                    }
                }.start()
            }
        })
        section("TELEGRAM")
        val tok = field("Telegram bot token", "token")
        val chat = field("Telegram channel (@name)", "chat")
        val vlang = field("Voice languages, comma separated, e.g. bn-BD,en-US (long-press the mic to switch)", "vlang")
        val acct = Config.enabled()
        val couponField = field("Coupon or purchase code", "")
        col.addView(Button(this).apply {
            typeface = mono; text = "REDEEM CODE   [" + Entitlements.label(this@MainActivity) + "]"
            setOnClickListener {
                val code = couponField.text.toString()
                couponField.setText("")
                Thread {
                    val m = Entitlements.redeem(this@MainActivity, code)
                    runOnUiThread { show("AGENT", m); text = "REDEEM CODE   [" + Entitlements.label(this@MainActivity) + "]"; AssistantService.ensure(this@MainActivity) }
                }.start()
            }
        })
        val songBtn = Button(this).apply { typeface = mono; text = "PICK FAVORITE SONG FILE"; setOnClickListener { pickSong() } }
        col.addView(songBtn)
        if (!Entitlements.isOwner(this)) { songBtn.visibility = View.GONE }
        section("VOICE")
        fun autoLabel() = "Keep listening after each reply: " + (if (prefs.getBoolean("auto_listen", true)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = autoLabel()
            setOnClickListener {
                prefs.edit().putBoolean("auto_listen", !prefs.getBoolean("auto_listen", true)).apply()
                text = autoLabel()
            }
        })
        val elKey = field(if (!prefs.getString("el_key", null).isNullOrBlank()) "ElevenLabs API key (saved, only on this phone)" else "ElevenLabs API key (optional, natural female voice)", "el_key", true)
        col.addView(keyAddButton("el_key", elKey, "ELEVENLABS"))
        // Manual voice ID: works even when the voice list cannot be loaded (key without voice-list permission).
        val elVoiceId = field("ElevenLabs voice ID (paste it from ElevenLabs > Voices, a female voice)", "el_voice")
        fun elLabel() = "ElevenLabs voice: " + (if (prefs.getBoolean("el_on", true)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = elLabel()
            setOnClickListener { prefs.edit().putBoolean("el_on", !prefs.getBoolean("el_on", true)).apply(); text = elLabel() }
        })
        col.addView(Button(this).apply {
            typeface = mono; text = "CHOOSE FEMALE VOICE" + (prefs.getString("el_voice_name", null)?.let { " ($it)" } ?: "")
            setOnClickListener {
                val typed = elKey.text.toString().trim()
                if (typed.isNotBlank()) { prefs.edit().putString("el_key", mergedKeys("el_key", typed)).apply(); elKey.setText("") }
                val key = prefs.getString("el_key", "") ?: ""
                if (key.isBlank()) { show("AGENT", "Enter your ElevenLabs API key first"); return@setOnClickListener }
                show("AGENT", "Loading female voices...")
                Thread {
                    val (voices, err) = Voice.femaleVoices(key)
                    runOnUiThread {
                        if (err != null) show("AGENT", err)
                        else if (voices.isEmpty()) show("AGENT", "No female voices found on this ElevenLabs account")
                        else AlertDialog.Builder(this@MainActivity).setTitle("Choose a voice")
                            .setItems(voices.map { it.second }.toTypedArray()) { _, i ->
                                prefs.edit().putString("el_voice", voices[i].first).putString("el_voice_name", voices[i].second).apply()
                                show("AGENT", "Voice set: ${voices[i].second}")
                                speak("Hello, this is how I sound.")
                            }.show()
                    }
                }.start()
            }
        })
        col.addView(Button(this).apply { typeface = mono; text = "TEST VOICE"; setOnClickListener { speak("Hello, this is how I sound.") } })
        fun searchLabel() = "Web search (uses your AI key): " + (if (prefs.getBoolean("search_on", false)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = searchLabel()
            setOnClickListener { prefs.edit().putBoolean("search_on", !prefs.getBoolean("search_on", false)).apply(); text = searchLabel() }
        })
        section("ACCOUNT")
        val email = field("Account email", "email")
        val pass = field("Account password", "", true)
        if (!acct) { email.visibility = View.GONE; pass.visibility = View.GONE }
        fun b(label: String, run: () -> Unit) = col.addView(Button(this).apply { text = label; typeface = mono; setOnClickListener { run() } })
        fun auth(action: String) {
            val em = email.text.toString().trim()
            val pw = pass.text.toString()
            Thread {
                val r = Account.post(action, JSONObject().put("email", em).put("password", pw).put("name", em.substringBefore("@")))
                runOnUiThread {
                    if (r.first == 200) {
                        prefs.edit().putString("svtoken", r.second.optString("token")).putString("email", em).apply()
                        show("AGENT", "Login successful!")
                        loadAccount()
                    } else show("AGENT", r.second.optString("error", "Error"))
                }
            }.start()
        }
        if (acct) b("ACCOUNT (website)") {
            if (Config.enabled()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SERVER + "/account.php")))
        }
        b("TELEGRAM CHANNEL") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL))) }
        b("LATEST FROM TS (feed)") { showFeed() }
        if (Config.githubReady()) {
            b("UPDATE / RELEASES") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Config.GITHUB_REPO}/releases/latest"))) }
            b("REQUEST A FEATURE") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Config.GITHUB_REPO}/issues/new"))) }
        }
        b("EXPORT BACKUP (copy this text and keep it safe)") {
            val t = exportBackup()
            AlertDialog.Builder(this).setTitle("Backup")
                .setView(ScrollView(this).apply { addView(TextView(this@MainActivity).apply { text = t; setTextIsSelectable(true); textSize = 11f; typeface = mono }) })
                .setPositiveButton("OK", null).show()
        }
        b("IMPORT BACKUP (paste the text here)") {
            val input = EditText(this)
            AlertDialog.Builder(this).setTitle("Paste backup text").setView(input)
                .setPositiveButton("RESTORE") { _, _ ->
                    show("AGENT", if (importBackup(input.text.toString())) "Backup restored. Close and open the app once." else "That text is not a valid backup.")
                }
                .setNegativeButton("CANCEL", null).show()
        }
        b("VIDEO SOUND: upload a file (replaces the current one)") { pickVideoSound() }
        b("VIDEO SOUND: remove current") { removeVideoSound() }
        b("CPANEL SITES (paste one site per line)") {
            val input = EditText(this).apply {
                hint = "name | host | cpanel user | api token | public_html"
                minLines = 4
                setText(CpanelClient.sites(this@MainActivity).joinToString("\n") { it.name + " | " + it.host + " | " + it.user + " | " + it.token + " | " + it.root })
            }
            AlertDialog.Builder(this).setTitle("cPanel sites").setView(input)
                .setPositiveButton("SAVE") { _, _ ->
                    val n = CpanelClient.saveFromText(this, input.text.toString())
                    show("AGENT", "Saved $n cPanel site(s). Keep the tokens private.")
                }
                .setNegativeButton("CANCEL", null).show()
        }
        b("LAST AGENT RUN (log)") { show("AGENT", prefs.getString("agent_log", "(no run yet)") ?: "(no run yet)") }
        b("OPEN WEBSITE / ADMIN PANEL") { if (Config.enabled()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SERVER))) }
        section("SHORTCUTS (work offline)")
        val scBox = EditText(this).apply { hint = "phrase = command, e.g. kaj shuru = open youtube"; typeface = mono; textSize = 13f }
        col.addView(scBox)
        val scCount = Shortcuts.all(this).size
        col.addView(Button(this).apply {
            typeface = mono; text = "ADD SHORTCUT (saved: $scCount)"
            setOnClickListener {
                if (Shortcuts.add(this@MainActivity, scBox.text.toString())) {
                    scBox.setText("")
                    show("AGENT", "Shortcut saved. Say the phrase to run it, even offline.")
                } else show("AGENT", "Write it like this: phrase = command")
            }
        })
        col.addView(Button(this).apply {
            typeface = mono; text = "CLEAR ALL SHORTCUTS"
            setOnClickListener { Shortcuts.clear(this@MainActivity); show("AGENT", "All shortcuts removed.") }
        })
        section("PERMISSIONS AND FEATURES")
        b("Enable Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        b("Overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        section("OWNER")
        val ownerPass = field("Owner passphrase (first time: choose one, 8+ characters)", "", true)
        col.addView(Button(this).apply {
            typeface = mono; text = "OWNER LOGIN / SET PASSPHRASE"
            setOnClickListener { show("AGENT", OwnerAuth.login(this@MainActivity, ownerPass.text.toString())); ownerPass.setText("") }
        })
        col.addView(Button(this).apply {
            typeface = mono; text = "OWNER LOGOUT"
            setOnClickListener { OwnerAuth.logout(this@MainActivity); show("AGENT", "Owner mode locked") }
        })
        fun compLabel() = "Companion mode: " + (if (prefs.getBoolean("comp_on", false)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = compLabel()
            setOnClickListener {
                if (!Entitlements.companion(this@MainActivity)) { show("AGENT", "Companion mode is a premium feature."); return@setOnClickListener }
                if (prefs.getBoolean("comp_on", false)) { prefs.edit().putBoolean("comp_on", false).apply(); text = compLabel() }
                else AlertDialog.Builder(this@MainActivity).setTitle("Companion mode")
                    .setMessage("Companion mode makes the assistant talk like an affectionate, playful partner. It is an AI, not a real person. It needs your AI key and is for adults only. Are you 18 or older?")
                    .setPositiveButton("I am 18 or older") { _, _ -> prefs.edit().putBoolean("comp_on", true).apply(); text = compLabel() }
                    .setNegativeButton("Cancel", null).show()
            }
        })
        fun floatLabel() = "Floating assistant: " + (if (prefs.getBoolean("float", true)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = floatLabel()
            setOnClickListener {
                prefs.edit().putBoolean("float", !prefs.getBoolean("float", true)).apply()
                text = floatLabel()
                if (prefs.getBoolean("float", true)) AssistantService.ensure(this@MainActivity) else AssistantService.stop(this@MainActivity)
            }
        })
        // ---- Sara Watch: she sees the screen and cameras and speaks on her own. Opt-in, always shows a notification. ----
        fun watchLabel() = "Sara Watch: " + (if (WatchService.enabled(this)) "ON" else "OFF")
        fun startWatch() {
            val need = mutableListOf<String>()
            if ((prefs.getBoolean("watch_front", true) || prefs.getBoolean("watch_back", false)) &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.CAMERA
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.POST_NOTIFICATIONS
            if (need.isEmpty()) WatchService.start(this) else requestPermissions(need.toTypedArray(), 4)
            if (prefs.getBoolean("watch_screen", true) && AgentService.instance == null) show("AGENT", "Sara Watch: tap Enable Accessibility so she can see your screen.")
        }
        col.addView(Button(this).apply {
            typeface = mono; text = watchLabel()
            setOnClickListener {
                if (!Entitlements.isOwner(this@MainActivity)) { show("AGENT", "Sara Watch is owner-only. Enter the owner code and unlock owner mode first."); return@setOnClickListener }
                if (WatchService.enabled(this@MainActivity)) {
                    prefs.edit().putBoolean("watch_on", false).apply()
                    WatchService.stop(this@MainActivity)
                    text = watchLabel()
                    show("AGENT", "Sara Watch is OFF. Nothing is being captured.")
                    return@setOnClickListener
                }
                if (!Llm.configured(this@MainActivity) || Llm.provider(this@MainActivity) == "cloud") {
                    show("AGENT", "Sara Watch needs your own AI key (Claude, OpenAI, Gemini, Grok or custom) and a model that can read images. Set it above, then try again.")
                    return@setOnClickListener
                }
                AlertDialog.Builder(this@MainActivity).setTitle("Sara Watch")
                    .setMessage("While Sara Watch is ON, Miss Sara takes a small still picture of your screen and, if you choose them, your front and back camera every few seconds, and sends it to the AI provider you set up, so Sara can see what you do and speak to you on her own.\n\n" +
                        "- A notification with PAUSE and STOP is always visible. Android also shows its camera indicator.\n" +
                        "- Nothing is saved on the phone and nothing goes to any TS server.\n" +
                        "- Password screens and apps on your block list are skipped. It pauses when the phone is locked or in a call.\n" +
                        "- Your AI provider's rules and costs apply to every picture sent.\n" +
                        "- Use it only on your own phone. Never install or switch it on for someone else without them knowing.")
                    .setPositiveButton("I understand, turn ON") { _, _ ->
                        prefs.edit().putBoolean("watch_consent", true).putBoolean("watch_on", true).apply()
                        text = watchLabel()
                        startWatch()
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        })
        b("Sara Watch: what she can see") {
            val names = arrayOf("Screen", "Front camera", "Back camera")
            val keys = arrayOf("watch_screen", "watch_front", "watch_back")
            val defs = booleanArrayOf(true, true, false)
            val cur = BooleanArray(3) { prefs.getBoolean(keys[it], defs[it]) }
            AlertDialog.Builder(this).setTitle("Sara can see")
                .setMultiChoiceItems(names, cur) { _, i, on -> cur[i] = on }
                .setPositiveButton("OK") { _, _ ->
                    val ed = prefs.edit()
                    for (i in 0..2) ed.putBoolean(keys[i], cur[i])
                    ed.apply()
                    if ((cur[1] || cur[2]) && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 4)
                    show("AGENT", "Sara Watch sources: " + names.filterIndexed { i, _ -> cur[i] }.joinToString(", ").ifEmpty { "none" })
                }
                .setNegativeButton("Cancel", null).show()
        }
        fun everyLabel() = "Sara Watch: look every " + prefs.getInt("watch_every", 30) + "s"
        col.addView(Button(this).apply {
            typeface = mono; text = everyLabel()
            setOnClickListener {
                val l = listOf(15, 30, 60, 120, 300)
                val c = prefs.getInt("watch_every", 30)
                prefs.edit().putInt("watch_every", l[(l.indexOf(c) + 1) % l.size]).apply()
                text = everyLabel()
            }
        })
        b("Sara Watch: apps she never looks at") { editNote("WATCH.BLOCK // one Android package name per line. Screens of these apps are never sent to the AI", "watch_block", WatchService.DEFAULT_BLOCK) }
        section("REMINDERS")
        fun remLabel() = "Reminders: " + (if (Reminders.enabled(this)) "ON" else "OFF")
        col.addView(Button(this).apply {
            typeface = mono; text = remLabel()
            setOnClickListener {
                val on = !Reminders.enabled(this@MainActivity)
                Reminders.setEnabled(this@MainActivity, on)
                text = remLabel()
                if (on && Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3)
            }
        })
        b("News feeds") { editNote("NEWS.FEEDS // one https RSS link per line", "feeds", "") }
        b("Allow background running (battery)") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        b("Remaining permissions (mic, media)") {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
            p += Manifest.permission.READ_MEDIA_IMAGES
            p += Manifest.permission.READ_MEDIA_VIDEO
            p += Manifest.permission.POST_NOTIFICATIONS
            requestPermissions(p.toTypedArray(), 1)
        }
        for (i in 0 until col.childCount) { val v = col.getChildAt(i); if (v is Button) pressFx(v) }
        FeatureSwitches.apply(this, col)
        styleSettings(col)
        AlertDialog.Builder(this).setTitle("SETTINGS")
            .setView(ScrollView(this).apply { setBackgroundColor(Theme.BG); setPadding(dp(4), dp(8), dp(4), dp(8)); addView(col) })
            .setPositiveButton("Save") { _, _ ->
                val ed = prefs.edit()
                if (api.text.isNotBlank()) ed.putString("apikey", api.text.toString().trim())
                listOf("openai" to keyOpenai, "gemini" to keyGemini, "grok" to keyGrok, "custom" to keyCustom)
                    .forEach { (p, f) -> if (f.text.isNotBlank()) ed.putString("key_$p", mergedKeys("key_$p", f.text.toString())) }
                ed.putString("llm_base", baseCustom.text.toString().trim())
                ed.putString("token", tok.text.toString().trim())
                ed.putString("chat", chat.text.toString().trim())
                ed.putString("vlang", vlang.text.toString().trim())
                if (elKey.text.isNotBlank()) ed.putString("el_key", mergedKeys("el_key", elKey.text.toString()))
                val vid = elVoiceId.text.toString().trim()
                if (vid.isNotBlank() && vid != prefs.getString("el_voice", "")) {
                    ed.putString("el_voice", vid).putString("el_voice_name", "voice ID " + vid.take(6))
                }
                ed.apply()
            }
            .setNegativeButton("Close", null).show()
    }
}
