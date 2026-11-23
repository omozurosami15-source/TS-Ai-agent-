package com.tsagent.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Diagnostic screen: shows the last run of TS Agent (lifecycle trace and crash), with a button to open it. */
class TestActivity : Activity() {
    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setTextColor(Color.GREEN); textSize = 13f; typeface = Typeface.MONOSPACE }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(32, 120, 32, 32)
        }
        box.addView(Button(this).apply {
            text = "OPEN TS AGENT"
            setOnClickListener { startActivity(android.content.Intent(this@TestActivity, MainActivity::class.java)) }
        })
        box.addView(ScrollView(this).apply { addView(out) })
        setContentView(box)
    }

    override fun onResume() {
        super.onResume()
        val t = File(filesDir, "trace.txt")
        val c = File(filesDir, "crash.txt")
        out.text = "LAST RUN:\n" + (if (t.exists()) t.readText() else "(no trace yet)") +
            "\n\nCRASH:\n" + (if (c.exists()) c.readText() else "(none)")
    }
}
