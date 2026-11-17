package com.tsagent.app

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
    private lateinit var mic: MicView
    private lateinit var transcript: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var online: TextView
    private var titleView: GlitchTitle? = null
    /** True while the user wants the app to keep listening; only the mic button turns it off. */
    private var keepListening = false
    /** True while the recognizer is actually open, so two listeners never run at the same time. */
    private var listening = false
    private lateinit var sysLine: TextView
    private lateinit var tasksText: TextView
    private lateinit var newsText: TextView
    private lateinit var chatRow: LinearLayout
    private lateinit var input: EditText
    private var boot: BootOverlay? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private val buf = SpannableStringBuilder()
    private val speakHandler: (String, String?) -> Unit = { t, l -> speak(t, l) }
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
            online.text = if (on) "● ONLINE" else "● SETUP"
            online.setTextColor(if (on) Theme.G else Theme.A)
            val s = (System.currentTimeMillis() - t0) / 1000
            sysLine.text = String.format(Locale.US, "UPTIME %02d:%02d:%02d | ACCESSIBILITY:%s | OWNER:%s | PLAN:%s | COINS:%s | COMPANION:%s",
                s / 3600, s / 60 % 60, s % 60, if (on) "ON" else "OFF",
                if (!OwnerAuth.isSet(this@MainActivity)) "-" else if (OwnerAuth.unlocked(this@MainActivity)) "ON" else "LOCKED",
                Entitlements.plan(this@MainActivity).ifEmpty { "free" }.uppercase(Locale.US),
                prefs.getString("coins_text", "-"),
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
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG

        root = FrameLayout(this).apply { setBackgroundColor(Theme.BG) }
        root.addView(RainView(this), FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(34), dp(14), dp(12))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // header
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val gt = GlitchTitle(this, "MISS SARA")
        titleView = gt
        header.addView(gt, LinearLayout.LayoutParams(0, -2, 1f))
        online = TextView(this).apply {
            textSize = 11f; typeface = mono; setPadding(dp(10), dp(4), dp(10), dp(4)); background = card(Theme.DIM)
        }
        val settingsTop = TextView(this).apply {
            text = "⚙ SETTINGS"; typeface = mono; textSize = 11f; setTextColor(Theme.A)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply { setStroke(dp(1), Theme.A); cornerRadius = dp(8).toFloat() }
            setOnClickListener { showSettings() }
        }
        pressFx(settingsTop)
        header.addView(settingsTop, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        header.addView(online)
        content.addView(header)
        sysLine = TextView(this).apply { textSize = 11f; typeface = mono; setTextColor(Theme.DIM) }
        content.addView(sysLine)

        // terminal transcript
        transcript = TextView(this).apply { setTextColor(Theme.TX); textSize = 12.5f; typeface = mono; setLinkTextColor(Theme.C); movementMethod = LinkMovementMethod.getInstance() }
        transcriptScroll = ScrollView(this).apply {
            background = card(0x8800FF9C.toInt())
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(transcript)
        }
        content.addView(transcriptScroll, LinearLayout.LayoutParams(-1, dp(120)).apply { topMargin = dp(8) })

        // animated stage
        stage = StageView(this)
        stage.onNode = { i ->
            when (i) {
                0 -> if (OwnerAuth.unlocked(this)) editNote("MEMORY // what the AI remembers", "memory", "") else show("AGENT", "Owner login required. Open SETTINGS > Owner login.")
                1 -> chatRow.visibility = if (chatRow.visibility == View.GONE) View.VISIBLE else View.GONE
                2 -> editNote("SOUL // the AI personality", "soul", "You are TS Agent, a friendly assistant.")
                else -> showSettings()
            }
        }
        content.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        // tasks
        tasksText = TextView(this).apply {
            setTextColor(Theme.TX); textSize = 11f; typeface = mono
            background = card(Theme.DIM)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { editNote("TODAY.TASKS // one per line, put ✓ at the start when done", "tasks", "") }
        }
        newsText = TextView(this).apply {
            setTextColor(Theme.TX); textSize = 11f; typeface = mono; setLinkTextColor(Theme.C)
            movementMethod = LinkMovementMethod.getInstance()
            background = card(Theme.DIM)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { refreshNews() }
        }
        val bottom = LinearLayout(this)
        newsText.visibility = View.GONE   // headlines box removed; the news text is kept hidden
        bottom.addView(RadarPanel(this), LinearLayout.LayoutParams(0, dp(150), 1.15f).apply { marginEnd = dp(6) })
        bottom.addView(newsText, LinearLayout.LayoutParams(0, -2, 0f).apply { width = 0 })
        bottom.addView(tasksText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(bottom, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        pressFx(tasksText); pressFx(newsText)

        // chat input
        chatRow = LinearLayout(this).apply { visibility = View.GONE }
        input = EditText(this).apply {
            hint = "$ type a command..."; setTextColor(Theme.G); setHintTextColor(Theme.DIM); typeface = mono
        }
        chatRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        chatRow.addView(Button(this).apply {
            text = "EXEC"; typeface = mono
            setOnClickListener { val t = input.text.toString(); input.setText(""); submit(t) }
        })
        pressFx(chatRow.getChildAt(1))
        content.addView(chatRow)

        // controls
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        controls.addView(TextView(this).apply {
            text = "[ ABORT ]"; setTextColor(Theme.RED); textSize = 12f; typeface = mono
            gravity = Gravity.CENTER; background = card(Theme.RED)
            setPadding(dp(14), dp(9), dp(14), dp(9)); setOnClickListener { stopAll() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12); marginStart = dp(12) })
        mic = MicView(this).apply {
            setOnClickListener { if (keepListening) stopKeep() else { keepListening = true; listen() } }
            setOnLongClickListener { show("AGENT", "Voice language: " + toggleVoiceLang()); true }
        }
        controls.addView(mic, LinearLayout.LayoutParams(dp(80), dp(80)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(12); marginEnd = dp(12) })
        content.addView(controls)
        pressFx(controls.getChildAt(0))   // ABORT

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
        show("AGENT", "Updates and support: ${Config.TELEGRAM_URL}")

        Agent.onSpeak = speakHandler
        keepListening = prefs.getBoolean("auto_listen", true)
        loadAccount()
        checkRemote()
        refreshNews()
        main.postDelayed(newsTick, 900000L)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.let { t ->
                t.setLanguage(Locale.forLanguageTag(voiceLang()))
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { runOnUiThread { setState(2) } }
                    override fun onDone(id: String?) { runOnUiThread { setState(0); afterSpeak() } }
                    override fun onError(id: String?) { runOnUiThread { setState(0) } }
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        trace("onResume")
        // The user came back to the app: a task still running in the background must not press Home or Back and kick them out.
        if (keepListening) listen()
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

    private fun setState(s: Int) { stage.state = s; mic.active = (s == 1) }

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

    private fun show(who: String, text: String) {
        main.removeCallbacks(typer)
        if (pending.isNotEmpty()) { buf.append(pending); pending = "" }
        val label = if (who == "YOU") "root@ts:~$ " else "[ts] "
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

    /** Adds the typed key(s) to the saved list for this provider. The saved keys are kept; duplicates are dropped. */
    private fun mergedKeys(prefKey: String, typed: String): String {
        val split = { x: String -> x.split(Regex("[\\r\\n,]+")).map { it.trim() }.filter { it.isNotEmpty() } }
        return (split(prefs.getString(prefKey, "") ?: "") + split(typed)).distinct().joinToString(",")
    }

    private fun submit(text: String) {
        if (text.isBlank()) return
        show("YOU", text)
        CommandRunner(this) { s ->
            if (s.startsWith("•")) stage.caption = s.removePrefix("•").trim() else show("AGENT", s)
        }.run(text)
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
        if (Voice.configured(this)) {
            Voice.speak(this, text, { setState(2) }, { if (stage.state == 2) setState(0); afterSpeak() }) { systemSpeak(text, lang) }
            return
        }
        systemSpeak(text, lang)
    }

    private fun systemSpeak(text: String, lang: String?) {
        tts?.setLanguage(Locale.forLanguageTag(if (!lang.isNullOrBlank()) lang else voiceLang()))
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
    private fun pickSpoken(alts: List<String>): String? {
        val list = alts.filter { it.isNotBlank() }
        if (list.isEmpty()) return null
        val best = list.firstOrNull { it.contains("সারা") || it.contains("sara", ignoreCase = true) } ?: list.first()
        return best.replace(Regex("(^|\\s)ছায়া(?=$|\\s|[?!।.,])"), "$1সারা")
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
                override fun onBeginningOfSpeech() {}
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
                    val t = pickSpoken(r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                    if (!t.isNullOrBlank()) submit(t)
                    if (keepListening) main.postDelayed({ if (keepListening) listen() }, 300)
                }
                override fun onPartialResults(b: Bundle?) {
                    // Talking over the phone interrupts it, so the user is always heard first.
                    val p = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!p.isNullOrBlank() && p.trim().length >= 3 && (Agent.running || stage.state == 2)) interrupt()
                }
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang())
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // a short pause ends the sentence, so the answer comes quickly
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
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
        val done = all.count { it.trim().startsWith("✓") }
        val cells = 16
        val filled = if (all.isEmpty()) 0 else done * cells / all.size
        val pct = if (all.isEmpty()) 0 else done * 100 / all.size
        val bar = "[" + "█".repeat(filled) + "░".repeat(cells - filled) + "] $pct%"
        val shown = all.take(4).joinToString("\n") { if (it.trim().startsWith("✓")) it.trim() else "▸ ${it.trim()}" }
        tasksText.text = "[ TODAY.TASKS ] ${if (all.isEmpty()) "" else "$done/${all.size}"}\n$bar\n" +
            (if (all.isEmpty()) "Tap to add tasks" else shown)
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
                    sb.append("▸ " + h.title.take(60) + "\n")
                    sb.setSpan(URLSpan(h.link), s, sb.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                newsText.text = sb
                flashIn(newsText)
            }
        }.start()
    }

    private fun pickSong() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"), 77)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
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

    private fun showSettings() {
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
        val songLink = field("Favorite song link (optional)", "fav_song_link")
        val songBtn = Button(this).apply { typeface = mono; text = "PICK FAVORITE SONG FILE"; setOnClickListener { pickSong() } }
        col.addView(songBtn)
        if (!Entitlements.isOwner(this)) { songLink.visibility = View.GONE; songBtn.visibility = View.GONE }
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
        val compName = field("Companion: her name (optional)", "comp_name")
        val compCall = field("Companion: what she calls you (default: jaan)", "comp_call")
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
        if (acct) b("LOGIN") { auth("login") }
        if (acct) b("SIGN UP") { auth("register") }
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
        b("LAST AGENT RUN (log)") { show("AGENT", prefs.getString("agent_log", "(no run yet)") ?: "(no run yet)") }
        b("LOG OUT OF WEBSITE (use the phone's own key)") {
            prefs.edit().remove("svtoken").apply()
            show("AGENT", "Logged out of the website. The phone's own AI key and voice are used now.")
        }
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
        b("Reminder times and messages") { editNote("REMINDERS // one per line: HH:MM message. {call} = her pet name for you", "rem_lines", Reminders.DEFAULT_LINES) }
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
                if (Entitlements.isOwner(this)) ed.putString("fav_song_link", songLink.text.toString().trim())
                ed.putString("comp_name", compName.text.toString().trim())
                ed.putString("comp_call", compCall.text.toString().trim())
                ed.apply()
            }
            .setNegativeButton("Close", null).show()
    }
}
