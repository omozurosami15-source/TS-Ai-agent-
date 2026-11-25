package com.tsagent.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Diagram: describe a process, the AI writes a Mermaid diagram and this page draws it. */
class DiagramActivity : Activity() {
    private val accent = 0xFF4DE0B3.toInt()
    private lateinit var web: WebView
    private lateinit var input: EditText
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(24)) }
        col.addView(TextView(this).apply {
            text = "DIAGRAM"; textSize = 22f; setTextColor(accent); gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        col.addView(TextView(this).apply {
            text = "Describe a process, flow or structure. MISS SARA draws it."
            textSize = 13f; setTextColor(0xFFB8D8CC.toInt()); gravity = Gravity.CENTER_HORIZONTAL
        })
        input = EditText(this).apply {
            hint = "e.g. order flow from cart to delivery"
            setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt()); textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(0xFF081A16.toInt()); setStroke(dp(1), accent); cornerRadius = dp(12).toFloat() }
        }
        col.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        col.addView(Button(this).apply { text = "DRAW"; isAllCaps = false; setOnClickListener { draw() } },
            LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { textSize = 12f; setTextColor(0xFFB8D8CC.toInt()); gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(status)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            setBackgroundColor(0xFF03090C.toInt())
            loadData(page(""), "text/html", "utf-8")
        }
        col.addView(web, LinearLayout.LayoutParams(-1, dp(420)).apply { topMargin = dp(10) })
        col.addView(Button(this).apply { text = "BACK"; isAllCaps = false; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(android.widget.ScrollView(this).apply { setBackgroundColor(0xFF040D0A.toInt()); addView(col) })
    }

    private fun draw() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) { status.text = "Describe what to draw first."; return }
        status.text = "Drawing..."
        Thread {
            val code = try {
                val raw = Llm.complete(this,
                    "You draw diagrams. Reply with ONLY Mermaid flowchart code that starts with 'flowchart TD'. No markdown, no code fences, no explanation. Keep labels short.",
                    q, 800, false)
                raw.lines().filter { !it.trim().startsWith("```") }.joinToString("\n").trim()
            } catch (e: Exception) {
                ""
            }
            runOnUiThread {
                if (code.isEmpty()) status.text = "Could not draw it. Check your AI key and try again."
                else { status.text = ""; web.loadDataWithBaseURL("https://cdn.jsdelivr.net/", page(code), "text/html", "utf-8", null) }
            }
        }.start()
    }

    private fun page(code: String): String = """<!doctype html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{margin:0;background:#03090C;color:#e6fff8;font-family:sans-serif;padding:10px}
svg{max-width:100%;height:auto}</style>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
</head><body><pre class="mermaid">$code</pre>
<script>mermaid.initialize({startOnLoad:true,theme:'dark'});</script></body></html>"""
}
