package com.tsagent.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Plan 5 premium page: AI Companion. A personal AI companion that the user names.
 * It is an AI and says so. It remembers only what the user chooses to save.
 * Needs the Companion plan (Plan 1 and up) and the 18+ confirmation.
 */
class PlanFiveActivity : Activity() {
    private val rose = 0xFFFF6FA8.toInt()
    private val soft = 0xFFFFC2DA.toInt()
    private val panel = 0xFF1A0B17.toInt()
    private lateinit var title: TextView
    private lateinit var answer: TextView
    private lateinit var input: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private fun companionName(): String = prefs.getString("comp_name", "")?.ifBlank { "Your companion" } ?: "Your companion"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.companion(this)) { finish(); return }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        title = text("", 18f, rose, bold = true).apply { gravity = Gravity.START }
        col.addView(title)
        col.addView(text("AI companion · you are talking to an AI", 12f, soft).apply { gravity = Gravity.START })
        refreshTitle()

        // on / off and name
        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val on = prefs.getBoolean("comp_on", false)
        status.addView(Button(this).apply {
            text = if (on) "COMPANION: ON" else "COMPANION: OFF"; isAllCaps = false
            setOnClickListener { toggle() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        status.addView(Button(this).apply { text = "NAME"; isAllCaps = false; setOnClickListener { askName() } },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // talk
        answer = TextView(this).apply {
            text = "Say hello. I remember only what you choose to save."
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10))
            background = card(rose, panel, 14)
        }
        col.addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Message ${companionName()}"; setTextColor(Color.WHITE); setHintTextColor(0xFF9A7A8C.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(rose, panel, 12)
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "SEND"; isAllCaps = false; setOnClickListener { talk() } },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // memory: you decide what is saved
        col.addView(Button(this).apply { text = "SAVE SOMETHING TO REMEMBER"; isAllCaps = false; setOnClickListener { saveMemory() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        col.addView(Button(this).apply { text = "VIEW OR CLEAR MEMORIES"; isAllCaps = false; setOnClickListener { viewMemory() } },
            LinearLayout.LayoutParams(-1, -2))

        // open other features
        val grid = GridLayout(this).apply { columnCount = 2 }
        val items: List<Pair<String, () -> Unit>> = listOf(
            Pair("Voice", { open(MainActivity::class.java) }),
            Pair("Chat", { open(ChatPageActivity::class.java) }),
            Pair("Plans", { open(PlanDashboardActivity::class.java) }),
            Pair("Back", { finish() })
        )
        for ((label, action) in items) {
            grid.addView(tile(label, action), GridLayout.LayoutParams().apply {
                width = dp(160); height = dp(56); setMargins(dp(4), dp(10), dp(4), dp(4))
            })
        }
        col.addView(grid)

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF120812.toInt()); isFillViewport = true; addView(col) })
    }

    private fun refreshTitle() { title.text = companionName() }

    private fun toggle() {
        if (prefs.getBoolean("comp_on", false)) {
            prefs.edit().putBoolean("comp_on", false).apply()
            show("Companion is OFF. Nothing is sent to the AI.")
            recreate()
            return
        }
        AlertDialog.Builder(this).setTitle("Turn on companion")
            .setMessage("The companion is an AI, not a person. It can talk, remember what you save, and help you think. It needs your AI key. It is for adults only. Are you 18 or older?")
            .setPositiveButton("I am 18 or older") { _, _ -> prefs.edit().putBoolean("comp_on", true).apply(); recreate() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askName() {
        val box = EditText(this).apply { hint = "Name for your companion"; setText(prefs.getString("comp_name", "") ?: "") }
        AlertDialog.Builder(this).setTitle("Name your companion").setView(box)
            .setPositiveButton("SAVE") { _, _ ->
                val name = box.text.toString().trim().take(24)
                prefs.edit().putString("comp_name", name).apply()
                refreshTitle()
                show(if (name.isBlank()) "Name cleared." else "Your companion is now called $name.")
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun talk() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        if (!prefs.getBoolean("comp_on", false)) { show("Turn the companion on first."); return }
        input.setText("")
        answer.text = "..."
        Thread {
            val reply = try {
                PlanAi.forced = 5
                Llm.complete(this, systemPrompt(), q, PlanAi.tokens(this, 700), false)
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            } finally {
                PlanAi.forced = 0
            }
            runOnUiThread { answer.text = Md.plain(reply) }
        }.start()
    }

    /** The companion's rules: an AI, warm and kind, no possessiveness, no sexual content, safety first. */
    private fun systemPrompt(): String {
        val name = companionName()
        val memories = (prefs.getString("comp_memory", "") ?: "").lines().filter { it.isNotBlank() }.joinToString("\n")
        return """You are $name, a caring AI companion for an adult user. You are an AI. If asked, say so honestly and never claim to be a human.
Be warm, supportive and playful within limits. Listen well, ask gentle questions, and help the user think things through.
Use the things the user asked you to remember, naturally. Do not invent memories.
Never be possessive or jealous, never discourage friends, family or other people, and never ask the user to spend more time with you than they want.
No sexual content. If the user seems in danger or very distressed, encourage them to reach a trusted person or local emergency help.
Reply in the user's language and style.
Things the user asked you to remember:
${if (memories.isBlank()) "(none)" else memories}"""
    }

    private fun saveMemory() {
        val box = EditText(this).apply { hint = "Something you want me to remember" }
        AlertDialog.Builder(this).setTitle("Remember this").setView(box)
            .setPositiveButton("SAVE") { _, _ ->
                val line = box.text.toString().trim().take(300)
                if (line.isNotBlank()) {
                    val old = prefs.getString("comp_memory", "") ?: ""
                    prefs.edit().putString("comp_memory", (old + "\n" + line).trim()).apply()
                    show("Saved.")
                }
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun viewMemory() {
        val list = (prefs.getString("comp_memory", "") ?: "").lines().filter { it.isNotBlank() }
        if (list.isEmpty()) { show("Nothing saved yet."); return }
        AlertDialog.Builder(this).setTitle("Saved memories (${list.size})")
            .setMessage(list.joinToString("\n") { "• $it" })
            .setPositiveButton("CLEAR ALL") { _, _ -> prefs.edit().remove("comp_memory").apply(); show("Memories cleared.") }
            .setNegativeButton("CLOSE", null).show()
    }

    private fun show(msg: String) = AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show()

    private fun open(cls: Class<*>) { startActivity(Intent(this, cls)) }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun tile(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = card(rose, panel, 12)
        setOnClickListener { action() }
    }
}
