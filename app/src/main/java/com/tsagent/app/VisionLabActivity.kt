package com.tsagent.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream

/** Vision Lab: pick a picture, ask the AI about it (uses Llm.completeVision, a vision model). */
class VisionLabActivity : Activity() {
    private val accent = 0xFF66F2BD.toInt()
    private var jpg: ByteArray? = null
    private lateinit var preview: ImageView
    private lateinit var prompt: EditText
    private lateinit var result: TextView
    private lateinit var status: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(24)) }
        col.addView(label("VISION LAB", 22f, accent, bold = true))
        col.addView(label("Pick a picture, then ask what it shows.", 13f, 0xFFB8D8CC.toInt()))
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF03090C.toInt())
            background = card(accent, 0xFF03090C.toInt(), 14)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        col.addView(preview, LinearLayout.LayoutParams(-1, dp(260)).apply { topMargin = dp(12) })
        col.addView(Button(this).apply { text = "PICK PICTURE"; isAllCaps = false; setOnClickListener { pick() } },
            LinearLayout.LayoutParams(-1, -2))
        prompt = EditText(this).apply {
            hint = "What do you want to know? (e.g. what does this error mean?)"
            setTextColor(Color.WHITE); setHintTextColor(0xFF6E8E84.toInt()); textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(accent, 0xFF081A16.toInt(), 12)
        }
        col.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        col.addView(Button(this).apply { text = "ANALYZE"; isAllCaps = false; setOnClickListener { analyze() } },
            LinearLayout.LayoutParams(-1, -2))
        status = label("", 12f, 0xFFB8D8CC.toInt())
        col.addView(status)
        result = TextView(this).apply {
            textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(14), dp(12), dp(14), dp(12))
            background = card(accent, 0xFF0B2620.toInt(), 14)
        }
        col.addView(result, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        col.addView(Button(this).apply { text = "BACK"; isAllCaps = false; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF040D0A.toInt()); addView(col) })
    }

    private fun pick() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 91)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 91 || res != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw Exception("cannot open the picture")
            val small = scaleDown(bmp, 1024)
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
            jpg = out.toByteArray()
            preview.setImageBitmap(small)
            status.text = "Picture ready."
        } catch (e: Exception) {
            status.text = "Could not open the picture: " + (e.message ?: "")
        }
    }

    private fun scaleDown(b: Bitmap, max: Int): Bitmap {
        val s = maxOf(b.width, b.height)
        if (s <= max) return b
        val f = max.toFloat() / s
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    private fun analyze() {
        val bytes = jpg
        if (bytes == null) { status.text = "Pick a picture first."; return }
        val q = prompt.text.toString().trim().ifEmpty { "Describe what is in this picture clearly and briefly." }
        status.text = "Looking at the picture..."
        result.text = ""
        Thread {
            val reply = try {
                Llm.completeVision(this, PlanCatalog.get(5).role, q, listOf("Picture: the user's image." to bytes), 500)
            } catch (e: Exception) {
                "Not available: " + (e.message ?: "")
            }
            runOnUiThread { status.text = ""; result.text = Md.plain(reply) }
        }.start()
    }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_HORIZONTAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
}
