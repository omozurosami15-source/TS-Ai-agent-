package com.tsagent.app

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * Floating assistant: when you leave the app, the neon orb floats over every other app.
 * Tap it to talk, drag it anywhere. It speaks the answers and keeps running until you stop it.
 */
class AssistantService : Service() {
    companion object {
        @Volatile var instance: AssistantService? = null
        private const val CH = "assistant"

        private fun enabled(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("float", true)

        /** Call while the app is visible. Starts the service if the floating assistant is enabled and permissions are granted. */
        fun ensure(ctx: Context) {
            if (instance != null || !enabled(ctx) || !Entitlements.premium(ctx)) return
            if (!Settings.canDrawOverlays(ctx)) return
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            try { ctx.startForegroundService(Intent(ctx, AssistantService::class.java)) } catch (e: Exception) {}
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, AssistantService::class.java)) }
        fun show() { instance?.showBubble() }
        fun hide() { instance?.hideBubble() }
    }

    private val d by lazy { resources.displayMetrics.density }
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private var wm: WindowManager? = null
    private var box: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    /** True while the user wants the assistant to keep listening; only a tap on the orb turns it off. */
    private var keepListening = false
    /** Errors in a row while listening; a few are retried, then listening stops with a message. */
    private var errStreak = 0
    private lateinit var orb: FloatOrbView
    private lateinit var card: TextView
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var visible = false
    private var expanded = false
    private var idleTicks = 0
    private lateinit var chips: LinearLayout

    private val speakHandler: (String, String?) -> Unit = { t, l -> speak(t, l) }
    private val hideCard = Runnable { if (::card.isInitialized) card.visibility = View.GONE }
    private val tick = object : Runnable {
        override fun run() {
            if (::orb.isInitialized) {
                if (Agent.running && orb.state == 0) orb.state = 3
                else if (!Agent.running && orb.state == 3) orb.state = 0
                if (visible) {
                    val busy = orb.state != 0 || card.visibility == View.VISIBLE
                    if (busy) { idleTicks = 0; if (!expanded) setExpanded(true) }
                    else if (expanded && ++idleTicks >= 8) setExpanded(false)
                }
            }
            main.postDelayed(this, 600)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post { if (::orb.isInitialized) orb.state = 2 } }
                override fun onDone(id: String?) { main.post { if (::orb.isInitialized && orb.state == 2) orb.state = 0; afterSpeak() } }
                override fun onError(id: String?) { main.post { if (::orb.isInitialized && orb.state == 2) orb.state = 0 } }
            })
        }
        main.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Floating assistant", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 2, Intent(this, AssistantService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setContentTitle("TS Agent assistant is on")
            .setContentText("The floating orb appears when you leave the app. Tap to open.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "STOP", stopPi).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(2, n)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        main.removeCallbacksAndMessages(null)
        box?.let { try { wm?.removeView(it) } catch (e: Exception) {} }
        box = null
        tts?.shutdown()
        recognizer?.destroy()
        if (Agent.onSpeak === speakHandler) Agent.onSpeak = null
    }

    // ---- bubble ----
    fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return
        if (box == null) build()
        box?.visibility = View.VISIBLE
        visible = true
        Agent.onSpeak = speakHandler
    }

    fun hideBubble() {
        box?.visibility = View.GONE
        visible = false
        expanded = false
        tts?.stop(); Voice.stop()
        recognizer?.cancel()
        if (::orb.isInitialized) orb.state = 0
    }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        card = TextView(this).apply {
            setTextColor(Theme.TX); typeface = Typeface.MONOSPACE; textSize = 11.5f
            maxWidth = (230 * d).toInt(); maxLines = 4
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { setColor(0xEE02100A.toInt()); setStroke((1 * d).toInt(), Theme.G); cornerRadius = 6 * d }
            visibility = View.GONE
        }
        orb = FloatOrbView(this)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun chip(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
            text = label; setTextColor(color); typeface = Typeface.MONOSPACE; textSize = 10.5f
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt())
            background = GradientDrawable().apply { setColor(0xCC02100A.toInt()); setStroke((1 * d).toInt(), color); cornerRadius = 12 * d }
            setOnClickListener { onClick() }
        }
        row.addView(chip("■ ABORT", Theme.RED) { Agent.cancelled = true; tts?.stop(); Voice.stop(); recognizer?.cancel(); orb.state = 0 },
            LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * d).toInt() })
        row.addView(chip("🌐", Theme.C) { showCard("Voice language: " + toggleVoiceLang()) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * d).toInt() })
        row.addView(chip("✕", Theme.DIM) { stopSelf() })
        chips = row
        row.visibility = View.GONE
        orb.alpha = 0.72f
        col.addView(card)
        col.addView(orb, LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt()))
        col.addView(row)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = (12 * d).toInt(); y = (180 * d).toInt() }
        params = p

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        orb.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = p.x; oy = p.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > 8 * d || abs(dy) > 8 * d) moved = true
                    if (moved) { p.x = (ox + dx).toInt(); p.y = (oy + dy).toInt(); try { wm?.updateViewLayout(col, p) } catch (x: Exception) {} }
                }
                MotionEvent.ACTION_UP -> if (!moved) {
                    if (keepListening || Agent.running) stopKeep()
                    else { keepListening = prefs.getBoolean("auto_listen", true); setExpanded(true); listen() }
                } else savePosition()
            }
            true
        }
        wm?.addView(col, p)
        box = col
    }

    /** Small and see-through when idle; grows only while it listens, thinks or speaks. */
    private fun setExpanded(e: Boolean) {
        if (!::orb.isInitialized || expanded == e) return
        expanded = e
        idleTicks = 0
        val s = ((if (e) 104 else 54) * d).toInt()
        orb.layoutParams = LinearLayout.LayoutParams(s, s)
        orb.alpha = if (e) 1f else 0.72f
        chips.visibility = if (e) View.VISIBLE else View.GONE
        if (!e) card.visibility = View.GONE
        val b = box
        val p = params
        if (b != null && p != null) try { wm?.updateViewLayout(b, p) } catch (x: Exception) {}
        main.post { snapToEdge() }
    }

    /** Remembers where the orb was dropped, so it opens in the same place. */
    private fun savePosition() {
        val p = params ?: return
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putInt("orb_x", p.x).putInt("orb_y", p.y).apply()
    }

    /** Places the orb: at the saved place, or in the middle of the screen the first time. */
    private fun snapToEdge() {
        val b = box ?: return
        val p = params ?: return
        val w = if (b.width > 0) b.width else (54 * d).toInt()
        val h = if (b.height > 0) b.height else (54 * d).toInt()
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        if (sp.contains("orb_x")) {
            p.x = sp.getInt("orb_x", 0); p.y = sp.getInt("orb_y", 0)
            try { wm?.updateViewLayout(b, p) } catch (x: Exception) {}
            return
        }
        val tx = (resources.displayMetrics.widthPixels - w) / 2
        val ty = (resources.displayMetrics.heightPixels - h) / 2
        val sx = p.x
        val sy = p.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener {
                val f = it.animatedValue as Float
                p.x = (sx + (tx - sx) * f).toInt()
                p.y = (sy + (ty - sy) * f).toInt()
                try { wm?.updateViewLayout(b, p) } catch (x: Exception) {}
            }
            start()
        }
    }

    private fun showCard(text: String) {
        if (!::card.isInitialized) return
        card.text = text
        card.visibility = View.VISIBLE
        main.removeCallbacks(hideCard)
        main.postDelayed(hideCard, 7000L + text.length * 40L)
    }

    // ---- voice ----
    private fun voiceLangs(): List<String> =
        (prefs.getString("vlang", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(Config.VOICE_LOCALE) }

    private fun voiceLang(): String { val l = voiceLangs(); return l[prefs.getInt("vlang_idx", 0) % l.size] }

    private fun toggleVoiceLang(): String {
        val l = voiceLangs()
        val n = (prefs.getInt("vlang_idx", 0) + 1) % l.size
        prefs.edit().putInt("vlang_idx", n).apply()
        return l[n]
    }

    private fun speak(text: String, lang: String?) {
        if (!visible) return
        if (Voice.configured(this)) {
            Voice.speak(this, text, { orb.state = 2 }, { if (orb.state == 2) orb.state = 0; afterSpeak() }) { systemSpeak(text, lang) }
            return
        }
        systemSpeak(text, lang)
    }

    private fun systemSpeak(text: String, lang: String?) {
        tts?.setLanguage(Locale.forLanguageTag(if (!lang.isNullOrBlank()) lang else voiceLang()))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ts")
    }

    /** After a reply finishes, listen again so the conversation keeps going. */
    private fun afterSpeak() {
        if (!keepListening) return
        main.postDelayed({ if (keepListening && orb.state == 0) listen() }, 600)
    }

    /** Only the user turns listening off. */
    private fun stopKeep() {
        keepListening = false
        Agent.cancelled = true
        main.post { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
        if (::orb.isInitialized) orb.state = 0
        showCard("Stopped. Tap the orb to talk again.")
    }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showCard("Microphone permission is needed. Open TS Agent and allow it."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { showCard("Speech recognition is not available on this phone"); return }
        tts?.stop(); Voice.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { orb.state = 1; showCard("🎙 Listening... speak now") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { showCard("Thinking...") }
                override fun onError(e: Int) {
                    orb.state = 0
                    main.post { recognizer?.destroy(); recognizer = null }
                    if (keepListening && e != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS && ++errStreak <= 5) {
                        main.postDelayed({ if (keepListening) listen() }, 400)
                    } else {
                        keepListening = false
                        showCard("Could not hear you, tap again")
                    }
                }
                override fun onResults(r: Bundle?) {
                    orb.state = 0
                    errStreak = 0
                    main.post { recognizer?.destroy(); recognizer = null }
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t != null) submit(t)
                }
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang())
                    // wait for a full 3 seconds of silence before deciding the sentence is finished
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            )
        }
    }

    private fun submit(text: String) {
        if (text.isBlank()) return
        showCard("> $text")
        CommandRunner(this) { s -> if (!s.startsWith("•")) showCard(s) }.run(text)
    }
}
