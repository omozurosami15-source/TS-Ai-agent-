package com.tsagent.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * First-run setup screen. Each permission shows its state (done or waiting) and one tap opens the right prompt.
 * The app never switches a permission on by itself: the user taps ALLOW and confirms on the Android screen.
 */
class SetupWizard(private val act: Activity, private val onClose: () -> Unit) : FrameLayout(act) {
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val warn = TextView(act).apply { textSize = 12.5f; setTextColor(Theme.A); setPadding(0, dp(10), 0, dp(4)) }

    private val steps = listOf(
        Triple("microphone", "Microphone", "So Miss Sara can hear your voice."),
        Triple("notifications", "Notifications", "So reminders and replies can reach you."),
        Triple("overlay", "Show over other apps", "So the assistant bubble can appear on top."),
        Triple("accessibility", "Accessibility", "So Miss Sara can read and tap the screen when you ask. Find TS Agent in the list and turn it on."),
        Triple("media", "Photos and videos", "Optional. So it can find your latest photo or video to share.")
    )
    private val required = setOf("microphone", "accessibility")

    private fun dp(v: Int) = (v * d).toInt()

    init {
        setBackgroundColor(Theme.BG)
        isClickable = true
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(28), dp(18), dp(18)) }
        col.addView(TextView(act).apply {
            text = "SETUP"; setTextColor(Theme.G); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f * d, 0f, 0f, Theme.G)
        })
        col.addView(TextView(act).apply {
            text = "Allow these once. Each ALLOW opens the right screen. Green means done."
            setTextColor(Theme.TX); textSize = 13f; setPadding(0, dp(6), 0, dp(12))
        })
        val scroll = ScrollView(act).apply { overScrollMode = View.OVER_SCROLL_NEVER; isVerticalScrollBarEnabled = false }
        scroll.addView(list, FrameLayout.LayoutParams(-1, -2))
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(warn, LinearLayout.LayoutParams(-1, -2))
        col.addView(TextView(act).apply {
            text = "CONTINUE"; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; textSize = 15f
            gravity = Gravity.CENTER; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = NeonBg(Theme.G, 0xDD02100A.toInt(), 10f, 1.6f)
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        addView(col, FrameLayout.LayoutParams(-1, -1))
        rebuild()
    }

    /** Re-reads every permission. Called when the screen comes back to the front. */
    fun rebuild() {
        list.removeAllViews()
        var missing = mutableListOf<String>()
        for ((key, title, desc) in steps) {
            val ok = Device.granted(act, key)
            if (!ok && key in required) missing.add(title)
            list.addView(row(key, title, desc, ok), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        warn.text = if (missing.isEmpty()) "All required steps are done."
        else "Still needed for voice and phone control: " + missing.joinToString(", ")
    }

    private fun row(key: String, title: String, desc: String, ok: Boolean): View {
        val r = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(10), dp(10))
            background = NeonBg(if (ok) Theme.G else Theme.A, 0xCC03120C.toInt(), 10f, 1.4f)
        }
        val txt = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        txt.addView(TextView(act).apply {
            text = Glyphs.label(act, if (ok) Glyphs.CHECK else Glyphs.DOT, if (ok) Theme.G else Theme.A, title)
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; textSize = 15f
        })
        txt.addView(TextView(act).apply { text = desc; setTextColor(Theme.TX); textSize = 12f })
        r.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(TextView(act).apply {
            text = if (ok) "DONE" else "ALLOW"
            typeface = Typeface.DEFAULT_BOLD; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = NeonBg(if (ok) Theme.G else Theme.C, 0xDD02100A.toInt(), 8f, 1.4f)
            setOnClickListener { if (!ok) Permissions.request(act, key) }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        return r
    }
}
