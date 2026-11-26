package com.tsagent.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream

/**
 * The full chat page. Opened from every CHAT button. Layout: messages, then a bottom bar with
 * + (photo, web search, clear), a text box, MIC and SEND. Each AI reply has COPY, SHARE, SPEAK and AGAIN.
 * Messages stay on this page while the app runs; they are not saved between launches.
 */
class ChatPageActivity : Activity() {
    private val accent = 0xFF28E070.toInt()
    private val user = 0xFF2D8CFF.toInt()
    private val panel = 0xFF0B1410.toInt()
    private val msgs = mutableListOf<Pair<Boolean, String>>()   // true = the user, false = MISS SARA
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF05080A.toInt()) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(10)) }
        top.addView(text("‹ BACK", 13f, accent).apply { setOnClickListener { finish() } })
        top.addView(text("CHAT", 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("NEW", 13f, accent).apply { setOnClickListener { msgs.clear(); list.removeAllViews(); hint() } })
        root.addView(top)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(8)) }
        scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        hint()

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(10)) }
        bar.addView(pill("+"), LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(6) })
        input = EditText(this).apply {
            hint = "Message MISS SARA"; setTextColor(Color.WHITE); setHintTextColor(0xFF6F8577.toInt()); textSize = 15f
            setPadding(dp(14), dp(10), dp(14), dp(10)); background = card(accent, panel, 22)
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(pill("MIC"), LinearLayout.LayoutParams(dp(56), dp(44)).apply { leftMargin = dp(6) })
        bar.addView(pill("SEND").apply { setOnClickListener { send() } }, LinearLayout.LayoutParams(dp(60), dp(44)).apply { leftMargin = dp(6) })
        root.addView(bar)

        setContentView(root)
        bar.getChildAt(0).setOnClickListener { menu() }
        bar.getChildAt(2).setOnClickListener { listen() }
    }

    private fun hint() {
        list.addView(text("Ask anything. Type, speak with MIC, or add a photo with +.", 13f, 0xFF8FA99A.toInt()).apply { setPadding(dp(6), dp(20), dp(6), dp(12)) })
    }

    /** The + menu: a photo, web search on or off, and clear. */
    private fun menu() {
        val search = prefs.getBoolean("search_on", false)
        AlertDialog.Builder(this).setTitle("Add")
            .setItems(arrayOf("Add a photo", "Web search: " + (if (search) "ON" else "OFF") + " (tap to change)", "Clear this chat")) { _, i ->
                when (i) {
                    0 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 91)
                    1 -> prefs.edit().putBoolean("search_on", !search).apply()
                    2 -> { msgs.clear(); list.removeAllViews(); hint() }
                }
            }.show()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try { startActivityForResult(i, 77) } catch (e: Exception) { add(false, "Speech input is not available on this phone.") }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data == null) return
        when (req) {
            77 -> data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input.setText(it) }
            91 -> data.data?.let { askWithPhoto(it) }
        }
    }

    private fun send() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        input.setText("")
        add(true, q)
        val history = msgs.takeLast(8).dropLast(1).joinToString("\n") { (if (it.first) "User: " else "MISS SARA: ") + it.second }
        val pending = add(false, "...")
        val search = prefs.getBoolean("search_on", false) && Entitlements.premium(this)
        Thread {
            val reply = try {
                Llm.complete(this, SYSTEM, if (history.isBlank()) q else "Earlier:\n$history\n\nNewest message:\n$q", 1400, search)
            } catch (e: Exception) { "Not available: " + (e.message ?: "") }
            runOnUiThread { replace(pending, Md.plain(reply.trim().ifBlank { "No answer. Please try again." })) }
        }.start()
    }

    private fun askWithPhoto(uri: android.net.Uri) {
        val bytes = try {
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
            val small = if (maxOf(bmp.width, bmp.height) > 1024) {
                val f = 1024f / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
            } else bmp
            ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        } catch (e: Exception) { add(false, "Could not open the photo."); return }
        val q = input.text.toString().trim().ifEmpty { "What is in this picture? Explain clearly." }
        input.setText("")
        add(true, "[photo] $q")
        val pending = add(false, "...")
        Thread {
            val reply = try { Llm.completeVision(this, SYSTEM, q, listOf("Picture: the user's photo." to bytes), 900) }
                catch (e: Exception) { "Not available: " + (e.message ?: "") }
            runOnUiThread { replace(pending, Md.plain(reply.trim())) }
        }.start()
    }

    /** Adds a message bubble and returns its view, so it can be replaced later. */
    private fun add(isUser: Boolean, body: String): LinearLayout {
        msgs += isUser to body
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(if (isUser) user else accent, if (isUser) 0xFF0C1A2B.toInt() else panel, 14)
        }
        bubble.addView(TextView(this).apply { text = body; textSize = 15f; setTextColor(Color.WHITE) })
        if (!isUser) bubble.addView(actions(body, bubble))
        val row = LinearLayout(this).apply { gravity = if (isUser) Gravity.END else Gravity.START; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(bubble, LinearLayout.LayoutParams(dp(290), -2))
        list.addView(row)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return row.also { it.tag = bubble }
    }

    private fun replace(row: LinearLayout, body: String) {
        val bubble = row.tag as LinearLayout
        bubble.removeAllViews()
        bubble.addView(TextView(this).apply { text = body; textSize = 15f; setTextColor(Color.WHITE) })
        bubble.addView(actions(body, bubble))
        val idx = msgs.indexOfLast { !it.first && it.second == "..." }
        if (idx >= 0) msgs[idx] = false to body
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /** COPY, SHARE, SPEAK, AGAIN under each AI reply. */
    private fun actions(body: String, bubble: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        fun act(label: String, f: () -> Unit) = text(label, 11f, accent).apply {
            setPadding(dp(6), dp(4), dp(6), dp(4)); setOnClickListener { f() }
        }
        row.addView(act("COPY") { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MISS SARA", body)) })
        row.addView(act("SHARE") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Share")) })
        row.addView(act("SPEAK") { Voice.speak(this, body, {}, {}) { } })
        row.addView(act("AGAIN") { regenerate() })
        return row
    }

    private fun regenerate() {
        val lastUser = msgs.lastOrNull { it.first }?.second ?: return
        input.setText(lastUser)
        send()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun pill(label: String) = TextView(this).apply {
        text = label; textSize = 12f; setTextColor(accent); gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = card(accent, panel, 22)
    }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    companion object {
        private const val SYSTEM = """You are MISS SARA, a warm and clever assistant from TS Agent, made by Tarek. If asked who made you, say so. Never name another company as your maker.
Understand the meaning even when the message is short, mixed (Bangla and English), or written in Latin letters.
Reply in the user's language and style. Give a full, clear answer. Use plain text only: no markdown, no ** bold, no # headings.
If the request is unclear, ask one short question."""
    }
}
