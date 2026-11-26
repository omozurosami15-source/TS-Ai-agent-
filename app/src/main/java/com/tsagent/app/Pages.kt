package com.tsagent.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Page ids. They match the position of the button in the bottom bar. */
object Pg { const val NONE = -1; const val VOICE = 1; const val TOOLS = 2; const val HISTORY = 3; const val PROFILE = 4 }

/** Icon ids for [Ico]. */
object I {
    const val ROBOT = 0; const val GEAR = 1; const val SEARCH = 2; const val TRASH = 3; const val CHAT = 4; const val MIC = 5
    const val IMAGE = 6; const val SHIELD = 7; const val SHIELDCHK = 8; const val CLOCK = 9; const val TOOLS = 10; const val DOC = 11
    const val CODE = 12; const val TRANSLATE = 13; const val LINES = 14; const val FOLDER = 15; const val CHIP = 16; const val PERSON = 17
    const val BELL = 18; const val PALETTE = 19; const val GLOBE = 20; const val HELP = 21; const val LOGOUT = 22; const val HEADPHONES = 23
    const val LIVE = 24; const val CHEV = 25; const val STAR = 26; const val COINS = 27; const val CROWN = 28; const val BARS = 29
    const val CHEVDOWN = 30
}

private fun pgA(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)

/** Saved history of chats, voice commands and tools, newest last in storage. */
object Hist {
    class Item(val type: String, val title: String, val q: String, var a: String, val ts: Long)

    private fun p(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    @Synchronized
    private fun load(ctx: Context): JSONArray = try { JSONArray(p(ctx).getString("hist_log", "[]")) } catch (e: Exception) { JSONArray() }

    @Synchronized
    fun add(ctx: Context, type: String, title: String, q: String) {
        val arr = load(ctx)
        arr.put(JSONObject().put("t", type).put("n", title).put("q", q.take(600)).put("a", "").put("ts", System.currentTimeMillis()))
        val out = JSONArray()
        val from = if (arr.length() > 200) arr.length() - 200 else 0
        for (i in from until arr.length()) out.put(arr.get(i))
        p(ctx).edit().putString("hist_log", out.toString()).apply()
    }

    @Synchronized
    fun addReply(ctx: Context, reply: String) {
        val arr = load(ctx)
        if (arr.length() == 0) return
        val o = arr.getJSONObject(arr.length() - 1)
        val cur = o.optString("a", "")
        if (cur.length < 1200) o.put("a", (if (cur.isEmpty()) reply else cur + "\n" + reply).take(1200))
        p(ctx).edit().putString("hist_log", arr.toString()).apply()
    }

    @Synchronized
    fun list(ctx: Context): List<Item> {
        val arr = load(ctx)
        val out = ArrayList<Item>()
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.getJSONObject(i)
            out.add(Item(o.optString("t"), o.optString("n"), o.optString("q"), o.optString("a"), o.optLong("ts")))
        }
        return out
    }

    @Synchronized
    fun clear(ctx: Context) { p(ctx).edit().remove("hist_log").apply() }
}

/** One neon line icon, drawn on a 24 x 24 grid. */
class Ico(ctx: Context, var kind: Int, var color: Int, private val sw: Float = 2f) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()

    private fun line(c: Canvas, a: Float, b: Float, x: Float, y: Float) = c.drawLine(a, b, x, y, paint)

    private fun poly(c: Canvas, close: Boolean, vararg v: Float) {
        val p = Path(); p.moveTo(v[0], v[1])
        var i = 2
        while (i < v.size) { p.lineTo(v[i], v[i + 1]); i += 2 }
        if (close) p.close()
        c.drawPath(p, paint)
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float) {
        val s = paint.style; paint.style = Paint.Style.FILL; c.drawCircle(x, y, r, paint); paint.style = s
    }

    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24f * u) / 2f, (height - 24f * u) / 2f)
        c.scale(u, u)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sw; paint.color = color
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (kind) {
            I.ROBOT -> {
                rf.set(4f, 8f, 20f, 20f); c.drawRoundRect(rf, 3f, 3f, paint)
                line(c, 12f, 8f, 12f, 4.6f); dot(c, 12f, 3.6f, 1.4f)
                dot(c, 9f, 13.6f, 1.5f); dot(c, 15f, 13.6f, 1.5f)
                line(c, 2f, 12.5f, 2f, 16f); line(c, 22f, 12.5f, 22f, 16f)
                line(c, 9.5f, 17.2f, 14.5f, 17.2f)
            }
            I.GEAR -> {
                c.drawCircle(12f, 12f, 3.2f, paint); c.drawCircle(12f, 12f, 6.6f, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(c, 12f + 7.4f * cos(a).toFloat(), 12f + 7.4f * sin(a).toFloat(), 12f + 10.2f * cos(a).toFloat(), 12f + 10.2f * sin(a).toFloat())
                }
            }
            I.SEARCH -> { c.drawCircle(10f, 10f, 6.2f, paint); line(c, 14.6f, 14.6f, 20.5f, 20.5f) }
            I.TRASH -> {
                line(c, 4f, 7f, 20f, 7f); poly(c, false, 9f, 7f, 9f, 4.4f, 15f, 4.4f, 15f, 7f)
                poly(c, false, 6f, 7f, 7f, 20.5f, 17f, 20.5f, 18f, 7f)
                line(c, 10f, 11f, 10f, 17f); line(c, 14f, 11f, 14f, 17f)
            }
            I.CHAT -> {
                rf.set(3f, 4f, 21f, 17f); c.drawRoundRect(rf, 4.5f, 4.5f, paint)
                poly(c, false, 8f, 17f, 7f, 21f, 12.5f, 17f)
                dot(c, 8f, 10.5f, 1.3f); dot(c, 12f, 10.5f, 1.3f); dot(c, 16f, 10.5f, 1.3f)
            }
            I.MIC -> {
                rf.set(9f, 2.5f, 15f, 14f); c.drawRoundRect(rf, 3f, 3f, paint)
                rf.set(5.5f, 7f, 18.5f, 19f); c.drawArc(rf, 0f, 180f, false, paint)
                line(c, 12f, 19f, 12f, 22f); line(c, 8.5f, 22f, 15.5f, 22f)
            }
            I.IMAGE -> {
                rf.set(3f, 4f, 21f, 20f); c.drawRoundRect(rf, 3f, 3f, paint)
                dot(c, 9f, 10f, 1.9f)
                poly(c, false, 4f, 18f, 9.5f, 13f, 13f, 16f, 16f, 12.5f, 20f, 17f)
            }
            I.SHIELD, I.SHIELDCHK -> {
                val p = Path()
                p.moveTo(12f, 2.5f); p.lineTo(20f, 5.5f); p.lineTo(19.2f, 14f)
                p.quadTo(17f, 19.5f, 12f, 21.8f); p.quadTo(7f, 19.5f, 4.8f, 14f); p.lineTo(4f, 5.5f); p.close()
                c.drawPath(p, paint)
                if (kind == I.SHIELDCHK) poly(c, false, 8f, 12f, 11f, 15f, 16.5f, 8.8f)
            }
            I.CLOCK -> { c.drawCircle(12f, 12f, 9f, paint); poly(c, false, 12f, 6.5f, 12f, 12f, 16f, 14.5f) }
            I.TOOLS -> {
                line(c, 4f, 20f, 14f, 10f); line(c, 20f, 20f, 10f, 10f)
                line(c, 10f, 10f, 5.5f, 4.5f); c.drawCircle(17.5f, 6.5f, 3.6f, paint)
            }
            I.DOC -> {
                poly(c, true, 6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f)
                poly(c, false, 14f, 3f, 14f, 8f, 19f, 8f)
                line(c, 9f, 12.5f, 16f, 12.5f); line(c, 9f, 16.5f, 16f, 16.5f)
            }
            I.CODE -> {
                poly(c, false, 9f, 7f, 4f, 12f, 9f, 17f); poly(c, false, 15f, 7f, 20f, 12f, 15f, 17f)
                line(c, 13.5f, 5.5f, 10.5f, 18.5f)
            }
            I.TRANSLATE -> {
                line(c, 3f, 6f, 12f, 6f); line(c, 7.5f, 4f, 7.5f, 6f)
                poly(c, false, 5f, 6f, 6.2f, 10f, 10f, 13f); poly(c, false, 10.5f, 6f, 8.5f, 11f, 4f, 14f)
                poly(c, false, 12.5f, 20.5f, 16.8f, 9f, 21f, 20.5f); line(c, 14.2f, 16.6f, 19.4f, 16.6f)
            }
            I.LINES -> {
                line(c, 4f, 6f, 20f, 6f); line(c, 4f, 11f, 15f, 11f); line(c, 4f, 16f, 20f, 16f); line(c, 4f, 21f, 11f, 21f)
            }
            I.FOLDER -> poly(c, true, 3f, 6f, 9f, 6f, 11f, 9f, 21f, 9f, 21f, 19f, 3f, 19f)
            I.CHIP -> {
                rf.set(6f, 6f, 18f, 18f); c.drawRoundRect(rf, 2.5f, 2.5f, paint)
                rf.set(9.5f, 9.5f, 14.5f, 14.5f); c.drawRoundRect(rf, 1f, 1f, paint)
                for (x in listOf(9f, 12f, 15f)) { line(c, x, 3f, x, 6f); line(c, x, 18f, x, 21f) }
                for (y in listOf(9f, 12f, 15f)) { line(c, 3f, y, 6f, y); line(c, 18f, y, 21f, y) }
            }
            I.PERSON -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(12f, 8f, 4.2f, paint)
                rf.set(4f, 14f, 20f, 30f); c.drawArc(rf, 180f, 180f, true, paint)
            }
            I.BELL -> {
                paint.style = Paint.Style.FILL
                val p = Path()
                p.moveTo(4.5f, 17.5f); p.lineTo(19.5f, 17.5f); p.lineTo(17.8f, 15.2f); p.lineTo(17.8f, 10.5f)
                p.cubicTo(17.8f, 7f, 15.2f, 4.8f, 12f, 4.8f); p.cubicTo(8.8f, 4.8f, 6.2f, 7f, 6.2f, 10.5f)
                p.lineTo(6.2f, 15.2f); p.close()
                c.drawPath(p, paint)
                rf.set(9.8f, 17.8f, 14.2f, 22f); c.drawArc(rf, 0f, 180f, true, paint)
                c.drawCircle(12f, 3.6f, 1.2f, paint)
            }
            I.PALETTE -> {
                paint.style = Paint.Style.FILL
                val p = Path()
                p.moveTo(12f, 2.5f); p.cubicTo(5.5f, 2.5f, 2.5f, 7.5f, 3f, 12.5f); p.cubicTo(3.5f, 17.5f, 7.5f, 21.5f, 12.5f, 21.5f)
                p.cubicTo(14.5f, 21.5f, 15f, 20f, 14.2f, 18.6f); p.cubicTo(13.2f, 16.8f, 14.5f, 15.5f, 16.5f, 15.5f)
                p.lineTo(18.5f, 15.5f); p.cubicTo(21f, 15.5f, 21.8f, 13.5f, 21.5f, 11.5f); p.cubicTo(20.8f, 6.5f, 17f, 2.5f, 12f, 2.5f); p.close()
                c.drawPath(p, paint)
                paint.color = Theme.BG
                c.drawCircle(8f, 10f, 1.5f, paint); c.drawCircle(12f, 7.2f, 1.5f, paint); c.drawCircle(16.2f, 9.5f, 1.5f, paint); c.drawCircle(7.6f, 14.5f, 1.5f, paint)
            }
            I.GLOBE -> {
                c.drawCircle(12f, 12f, 9f, paint)
                rf.set(8f, 3f, 16f, 21f); c.drawOval(rf, paint)
                line(c, 3f, 12f, 21f, 12f); line(c, 4.8f, 7.5f, 19.2f, 7.5f); line(c, 4.8f, 16.5f, 19.2f, 16.5f)
            }
            I.HELP -> {
                paint.style = Paint.Style.FILL
                c.drawCircle(12f, 12f, 10f, paint)
                paint.color = Theme.BG; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 15f
                c.drawText("?", 12f, 17.4f, paint)
                paint.typeface = Typeface.DEFAULT
            }
            I.LOGOUT -> {
                poly(c, false, 10f, 4f, 4f, 4f, 4f, 20f, 10f, 20f)
                line(c, 9f, 12f, 20f, 12f); poly(c, false, 16f, 8f, 20f, 12f, 16f, 16f)
            }
            I.HEADPHONES -> {
                rf.set(4f, 3.5f, 20f, 19.5f); c.drawArc(rf, 180f, 180f, false, paint)
                paint.style = Paint.Style.FILL
                rf.set(3.5f, 12.5f, 8f, 20f); c.drawRoundRect(rf, 2f, 2f, paint)
                rf.set(16f, 12.5f, 20.5f, 20f); c.drawRoundRect(rf, 2f, 2f, paint)
            }
            I.LIVE -> {
                dot(c, 12f, 12f, 2.2f)
                rf.set(7.5f, 7.5f, 16.5f, 16.5f); c.drawArc(rf, -45f, 90f, false, paint); c.drawArc(rf, 135f, 90f, false, paint)
                rf.set(3.5f, 3.5f, 20.5f, 20.5f); c.drawArc(rf, -45f, 90f, false, paint); c.drawArc(rf, 135f, 90f, false, paint)
            }
            I.CHEV -> poly(c, false, 8.5f, 4.5f, 16f, 12f, 8.5f, 19.5f)
            I.CHEVDOWN -> poly(c, false, 4.5f, 8.5f, 12f, 16f, 19.5f, 8.5f)
            I.STAR -> {
                paint.style = Paint.Style.FILL
                val p = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val rr = if (i % 2 == 0) 10.5f else 4.4f
                    val x = 12f + (rr * cos(a)).toFloat(); val y = 12.5f + (rr * sin(a)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); c.drawPath(p, paint)
            }
            I.COINS -> {
                paint.style = Paint.Style.FILL
                rf.set(3f, 12f, 17f, 18.5f); c.drawOval(rf, paint)
                rf.set(3f, 7.5f, 17f, 14f); c.drawOval(rf, paint)
                paint.color = pgA(Color.WHITE, 170)
                rf.set(9f, 9f, 21f, 15f); c.drawOval(rf, paint)
                rf.set(9f, 13f, 21f, 19.5f); c.drawOval(rf, paint)
            }
            I.CROWN -> {
                paint.style = Paint.Style.FILL
                poly(c, true, 3f, 19f, 3f, 7f, 8f, 12f, 12f, 4.5f, 16f, 12f, 21f, 7f, 21f, 19f)
            }
            I.BARS -> {
                paint.style = Paint.Style.FILL
                val hs = floatArrayOf(8f, 15f, 20f, 12f, 17f)
                for (i in hs.indices) { rf.set(2.5f + i * 4.2f, 12f - hs[i] / 2f, 5.5f + i * 4.2f, 12f + hs[i] / 2f); c.drawRoundRect(rf, 1.4f, 1.4f, paint) }
            }
        }
        c.restore()
    }
}

/** Round photo with a glowing ring and a green "online" dot. */
class ProfilePhoto(ctx: Context) : View(ctx) {
    private val pBrightFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(1.35f,0f,0f,0f,25f, 0f,1.35f,0f,0f,25f, 0f,0f,1.35f,0f,25f, 0f,0f,0f,1f,0f)))
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private var bmp: Bitmap? = null
    private val clip = Path()
    private val rf = RectF()
    private val src = android.graphics.Rect()
    private var t = 0f

    init {
        for (n in listOf("ms_sara_logo.png", "miss_sara_avatar.png")) {
            try { ctx.assets.open(n).use { bmp = BitmapFactory.decodeStream(it) } } catch (_: Exception) {}
            if (bmp != null) break
        }
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 7f * d
        paint.style = Paint.Style.STROKE
        paint.color = pgA(Theme.G, 45); paint.strokeWidth = 9f * d; c.drawCircle(cx, cy, r, paint)
        paint.color = Theme.G; paint.strokeWidth = 3.2f * d; c.drawCircle(cx, cy, r, paint)
        paint.color = pgA(Theme.C, 200); paint.strokeWidth = 1.6f * d
        c.save(); c.rotate(t * 35f, cx, cy)
        rf.set(cx - r - 5f * d, cy - r - 5f * d, cx + r + 5f * d, cy + r + 5f * d)
        for (i in 0 until 6) c.drawArc(rf, i * 60f, 24f, false, paint)
        c.restore()
        val ri = r - 4f * d
        val b = bmp
        paint.style = Paint.Style.FILL
        if (b != null) {
            clip.reset(); clip.addCircle(cx, cy, ri, Path.Direction.CW)
            src.set(0, 0, b.width, b.height); rf.set(cx - ri, cy - ri, cx + ri, cy + ri)
            c.save(); c.clipPath(clip)
            paint.color = 0xFF2B1A55.toInt(); c.drawCircle(cx, cy, ri, paint) // lit backdrop so the dark logo shows
            paint.colorFilter = pBrightFilter
            c.drawBitmap(b, src, rf, paint)
            paint.colorFilter = null
            c.restore()
        } else { paint.color = 0xFF0A2A1C.toInt(); c.drawCircle(cx, cy, ri, paint) }
        // online dot
        val dx = cx + r * 0.72f; val dy = cy + r * 0.72f
        paint.color = Theme.BG; c.drawCircle(dx, dy, 9f * d, paint)
        paint.color = Theme.G; c.drawCircle(dx, dy, 6.5f * d, paint)
        postInvalidateOnAnimation()
    }
}

/** Big animated microphone with rings and voice waves on both sides. */
class VoiceHero(ctx: Context) : View(ctx) {
    var state = 0
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private var t = 0f

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = minOf(h / 2f - 10f * d, w * 0.2f)
        val col = when (state) { 1 -> Theme.C; 2 -> Theme.M; 3 -> Theme.A; else -> Theme.G }
        val amp = when (state) { 1 -> 1f; 2 -> 0.85f; 3 -> 0.45f; else -> 0.18f }
        // glow
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, r * 1.5f, intArrayOf(pgA(col, 95), pgA(col, 0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 1.5f, paint)
        paint.shader = null
        paint.color = 0xDD02100A.toInt(); c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.color = pgA(col, 45); paint.strokeWidth = 10f * d; c.drawCircle(cx, cy, r, paint)
        paint.color = col; paint.strokeWidth = 3f * d; c.drawCircle(cx, cy, r, paint)
        // turning dashes
        paint.strokeWidth = 2f * d; paint.color = pgA(col, 190)
        c.save(); c.rotate(t * (if (state == 1) 90f else 30f), cx, cy)
        rf.set(cx - r * 1.14f, cy - r * 1.14f, cx + r * 1.14f, cy + r * 1.14f)
        for (i in 0 until 14) c.drawArc(rf, i * (360f / 14f), 14f, false, paint)
        c.restore()
        // magenta sweep
        paint.color = Theme.M; paint.strokeWidth = 3.4f * d
        c.save(); c.rotate(-t * 60f, cx, cy)
        rf.set(cx - r * 0.9f, cy - r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
        c.drawArc(rf, 0f, 62f, false, paint)
        c.restore()
        // microphone
        val m = r * 0.5f
        paint.style = Paint.Style.FILL
        paint.shader = android.graphics.LinearGradient(cx, cy - m, cx, cy + m, Theme.G, 0xFF12C98A.toInt(), Shader.TileMode.CLAMP)
        rf.set(cx - m * 0.42f, cy - m * 1.0f, cx + m * 0.42f, cy + m * 0.32f)
        c.drawRoundRect(rf, m * 0.42f, m * 0.42f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE; paint.color = Theme.G; paint.strokeWidth = 3.4f * d; paint.strokeCap = Paint.Cap.ROUND
        rf.set(cx - m * 0.78f, cy - m * 0.5f, cx + m * 0.78f, cy + m * 0.62f)
        c.drawArc(rf, 0f, 180f, false, paint)
        c.drawLine(cx, cy + m * 0.62f, cx, cy + m * 1.0f, paint)
        c.drawLine(cx - m * 0.4f, cy + m * 1.0f, cx + m * 0.4f, cy + m * 1.0f, paint)
        // voice waves
        val start = r * 1.34f
        val step = 10f * d
        val count = ((w / 2f - start - 4f * d) / step).toInt().coerceAtLeast(1)
        paint.style = Paint.Style.FILL
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until count) {
                val near = 1f - i.toFloat() / (count + 2)
                val wave = 0.5f + 0.5f * sin(t * 7f + i * 0.8f + side * 1.3f)
                val bh = (7f + amp * 36f * wave * near) * d
                val x = cx + side * (start + i * step)
                paint.color = pgA(col, (255 * (0.45f + 0.55f * near)).toInt())
                rf.set(x - 2.6f * d, cy - bh / 2f, x + 2.6f * d, cy + bh / 2f)
                c.drawRoundRect(rf, 2f * d, 2f * d, paint)
            }
        }
        postInvalidateOnAnimation()
    }
}

/** A full-screen page (PROFILE, HISTORY, AI TOOLS, VOICE MODE) that sits on top of Home, with the same bottom bar. */
class PageLayer(private val act: MainActivity, private val kind: Int) : FrameLayout(act) {
    private val d = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val prefs = act.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private var ticker: Runnable? = null
    private var navMic: MicView? = null
    private var histFilter = "ALL"
    private var histQuery = ""
    private var toolQuery = ""
    // voice page
    private var hero: VoiceHero? = null
    private var statusPill: TextView? = null
    private var langPill: TextView? = null
    private var replyText: TextView? = null
    private var bigMic: MicView? = null
    private var liveChip: LinearLayout? = null
    private var shownState = -1
    private var shownKeep: Boolean? = null
    private var shownReply = "\u0000"
    private var shownBn: Boolean? = null

    private fun dp(v: Int) = (v * d).toInt()

    private fun tv(text: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(act).apply {
        this.text = text; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun press(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    init {
        setBackgroundColor(Theme.BG)
        isClickable = true
        build()
    }

    fun dispose() { ticker?.let { main.removeCallbacks(it) }; ticker = null }

    override fun onDetachedFromWindow() { dispose(); super.onDetachedFromWindow() }

    private fun build() {
        addView(RainView(act), FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(22), dp(14), dp(12)) }
        addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(header(), LinearLayout.LayoutParams(-1, dp(46)))
        val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        when (kind) {
            Pg.PROFILE -> profile(col)
            Pg.HISTORY -> history(col)
            Pg.TOOLS -> tools(col)
            else -> voice(col)
        }
        content.addView(navBar(), LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(6) })
        addView(ScanView(act), FrameLayout.LayoutParams(-1, -1))
        startTicker()
    }

    private fun header(): View {
        val row = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        val ic: Int; val title: String
        when (kind) {
            Pg.PROFILE -> { ic = I.ROBOT; title = "TS AGENT" }
            Pg.HISTORY -> { ic = I.CLOCK; title = "HISTORY" }
            Pg.TOOLS -> { ic = I.TOOLS; title = "AI TOOLS" }
            else -> { ic = I.MIC; title = "VOICE MODE" }
        }
        row.addView(Ico(act, ic, if (kind == Pg.VOICE) Theme.C else Theme.G, 2.2f), LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
        row.addView(tv(title, 21f, Theme.G, true).apply { letterSpacing = 0.03f; setShadowLayer(8f * d, 0f, 0f, Theme.G) }, LinearLayout.LayoutParams(0, -2, 1f))
        when (kind) {
            Pg.PROFILE -> row.addView(Ico(act, I.GEAR, Theme.C, 2f).apply { setOnClickListener { act.pgSettings() } }, LinearLayout.LayoutParams(dp(32), dp(32)))
            Pg.HISTORY -> row.addView(Ico(act, I.TRASH, Theme.G, 2f).apply {
                setOnClickListener {
                    AlertDialog.Builder(act).setTitle("Clear history").setMessage("Delete all saved history?")
                        .setPositiveButton("Delete") { _, _ -> Hist.clear(act); act.openPage(Pg.HISTORY) }
                        .setNegativeButton("Cancel", null).show()
                }
            }, LinearLayout.LayoutParams(dp(32), dp(32)))
            Pg.VOICE -> row.addView(Ico(act, I.BARS, Theme.G, 2f), LinearLayout.LayoutParams(dp(28), dp(28)))
        }
        return row
    }

    // ================= PROFILE =================
    private fun profile(col: LinearLayout) {
        val name = (prefs.getString("user_name", "") ?: "").ifBlank { "TS AGENT USER" }
        val top = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(ProfilePhoto(act), LinearLayout.LayoutParams(dp(108), dp(108)))
        val info = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        info.addView(tv(name, 20f, Color.WHITE, true).apply { maxLines = 2 })
        val on = AgentService.instance != null
        info.addView(tv(Glyphs.label(act, Glyphs.DOT, if (on) Theme.G else Theme.A, if (on) "ONLINE" else "SETUP"), 14f, if (on) Theme.G else Theme.A, true).apply { setPadding(0, dp(4), 0, dp(8)) })
        val chip = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(14), dp(6))
            background = NeonBg(Theme.A, 0xDD100A00.toInt(), 8f, 1.5f)
            setOnClickListener { act.pgPlans() }
        }
        chip.addView(Ico(act, I.CROWN, 0xFFFFC21F.toInt()), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
        chip.addView(tv(Entitlements.label(act) + " Plan", 14f, 0xFFFFE2A0.toInt(), true).apply { maxLines = 1 })
        press(chip)
        info.addView(chip, LinearLayout.LayoutParams(-2, -2))
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        // stats
        val tasks = (prefs.getString("tasks", "") ?: "").lines().count { it.isNotBlank() }
        val sessions = prefs.getInt("sessions", 1)
        val coins = prefs.getString("coins_text", "-") ?: "-"
        val stats = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(10), dp(6), dp(10))
            background = NeonBg(Theme.G, 0xCC03120C.toInt(), 12f, 1.6f)
        }
        fun cell(icon: View, value: String, label: String) {
            val v = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            v.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
            v.addView(tv(value, 21f, Color.WHITE, true).apply { maxLines = 1 })
            v.addView(tv(label, 12f, Theme.TX))
            stats.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
        }
        fun sep() { stats.addView(View(act).apply { setBackgroundColor(pgA(Theme.G, 110)) }, LinearLayout.LayoutParams(dp(1), dp(54))) }
        cell(RocketIconView(act), tasks.toString(), "Total Tasks"); sep()
        cell(Ico(act, I.STAR, 0xFFFFC21F.toInt()), sessions.toString(), "Sessions"); sep()
        cell(Ico(act, I.COINS, 0xFFFFC21F.toInt()), coins, "Coins")
        col.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val cy = 0xFF22D3EE.toInt()
        fun row(icon: Int, label: String, color: Int, run: () -> Unit) {
            col.addView(menuRow(icon, label, color, Theme.G, 0xCC03120C.toInt(), run), LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
        row(I.PERSON, "Personal Information", cy) { act.pgEditName() }
        row(I.GLOBE, "Plans", cy) { act.pgPlans() }
        row(I.SHIELDCHK, "Security", cy) { act.pgSecurity() }
        row(I.BELL, "Notifications", 0xFFFFB02E.toInt()) { act.pgNotif() }
        row(I.PALETTE, "Appearance", cy) { act.pgAppearance() }
        row(I.GLOBE, "Language", cy) { act.pgLanguage() }
        row(I.HELP, "Help", cy) { act.pgHelp() }
        col.addView(menuRow(I.LOGOUT, "Logout", Theme.RED, Theme.RED, 0xCC1A0509.toInt()) { act.pgLogout() },
            LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8); bottomMargin = dp(6) })
    }

    private fun menuRow(icon: Int, label: String, color: Int, border: Int, fill: Int, run: () -> Unit): View {
        val r = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), 0, dp(12), 0)
            background = NeonBg(border, fill, 8f, 1.4f)
            setOnClickListener { run() }
        }
        r.addView(Ico(act, icon, color, 2f), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) })
        r.addView(tv(label, 15.5f, Color.WHITE, true).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(Ico(act, I.CHEV, color, 2.6f), LinearLayout.LayoutParams(dp(18), dp(18)))
        press(r)
        return r
    }

    // ================= HISTORY =================
    private fun searchBox(hint: String, onText: (String) -> Unit): View {
        val box = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, dp(10), 0)
            background = NeonBg(Theme.G, 0xCC03120C.toInt(), 10f, 1.6f)
        }
        box.addView(Ico(act, I.SEARCH, Theme.G, 2.2f), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
        val et = EditText(act).apply {
            this.hint = hint; setHintTextColor(Theme.DIM); setTextColor(Theme.TX); textSize = 15f
            background = null; maxLines = 1; isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onText(s?.toString() ?: "") }
            })
        }
        box.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
        return box
    }

    private fun history(col: LinearLayout) {
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(searchBox("Search history...") { histQuery = it; rebuildHistory(list) }, LinearLayout.LayoutParams(-1, dp(46)))
        val chips = LinearLayout(act)
        val chipViews = ArrayList<Pair<String, LinearLayout>>()
        fun paintChips() {
            for ((key, v) in chipViews) {
                val color = when (key) { "ALL" -> 0xFF2D8CFF.toInt(); "CHAT" -> 0xFF28E070.toInt(); "VOICE" -> 0xFFD13CFF.toInt(); else -> 0xFFFFA726.toInt() }
                v.background = NeonBg(color, if (histFilter == key) pgA(color, 0x66) else 0xCC03100C.toInt(), 8f, 1.6f)
            }
        }
        fun chip(key: String, icon: Int, color: Int) {
            val v = LinearLayout(act).apply { gravity = Gravity.CENTER; setPadding(dp(4), dp(7), dp(4), dp(7)) }
            if (icon >= 0) v.addView(Ico(act, icon, color, 2f), LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(4) })
            v.addView(tv(key, 12.5f, if (icon < 0) 0xFF5AA9FF.toInt() else color, true).apply { maxLines = 1 })
            v.setOnClickListener { histFilter = key; paintChips(); rebuildHistory(list) }
            press(v)
            chipViews.add(Pair(key, v))
            chips.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        chip("ALL", -1, 0xFF2D8CFF.toInt()); chip("CHAT", I.CHAT, 0xFF28E070.toInt())
        chip("VOICE", I.MIC, 0xFFD13CFF.toInt()); chip("TOOLS", I.TOOLS, 0xFFFFA726.toInt())
        paintChips()
        col.addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        rebuildHistory(list)
    }

    private fun dayLabel(ts: Long): String {
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        if (same(now, c)) return "TODAY"
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (same(now, c)) return "YESTERDAY"
        return SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(ts)).uppercase(Locale.US)
    }

    private fun rebuildHistory(box: LinearLayout) {
        box.removeAllViews()
        val all = Hist.list(act)
        val q = histQuery.trim().lowercase(Locale.getDefault())
        val items = all.filter { h ->
            (histFilter == "ALL" || h.type.equals(histFilter, true)) &&
                (q.isEmpty() || h.title.lowercase(Locale.getDefault()).contains(q) || h.q.lowercase(Locale.getDefault()).contains(q) || h.a.lowercase(Locale.getDefault()).contains(q))
        }
        if (items.isEmpty()) {
            box.addView(tv(if (all.isEmpty()) "No history yet.\nYour chats, voice commands and tools will show up here." else "Nothing found.", 13.5f, Theme.DIM)
                .apply { gravity = Gravity.CENTER; setPadding(0, dp(34), 0, dp(20)) }, LinearLayout.LayoutParams(-1, -2))
        }
        var lastDay = ""
        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        for (h in items) {
            val day = dayLabel(h.ts)
            if (day != lastDay) {
                lastDay = day
                val head = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
                head.addView(View(act).apply { setBackgroundColor(pgA(Theme.G, 160)) }, LinearLayout.LayoutParams(0, dp(1), 1f))
                head.addView(tv(day, 17f, Theme.G, true).apply { setPadding(dp(12), 0, dp(12), 0) })
                head.addView(View(act).apply { setBackgroundColor(pgA(Theme.G, 160)) }, LinearLayout.LayoutParams(0, dp(1), 1f))
                box.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(2) })
            }
            val (icon, color) = when (h.type) {
                "voice" -> Pair(I.MIC, 0xFF22D3EE.toInt())
                "tools" -> Pair(I.TOOLS, 0xFFFFA726.toInt())
                else -> Pair(I.CHAT, 0xFF2D8CFF.toInt())
            }
            val row = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(10), dp(8))
                background = NeonBg(Theme.G, 0xCC03120C.toInt(), 10f, 1.5f)
                setOnClickListener { showHistoryItem(h) }
            }
            row.addView(Ico(act, icon, color, 2f), LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            val mid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(tv(h.title, 15f, Color.WHITE, true).apply { maxLines = 1 })
            mid.addView(tv(h.q.replace('\n', ' '), 12.5f, Theme.TX).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv(fmt.format(Date(h.ts)), 12f, Theme.TX).apply { setPadding(dp(6), 0, dp(4), 0) })
            row.addView(Ico(act, I.CHEV, Theme.G, 2.6f), LinearLayout.LayoutParams(dp(16), dp(16)))
            press(row)
            box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        }
        val feed = tv("Latest from TS  ›", 13f, Theme.C, true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(8)); setOnClickListener { act.pgFeed() }
        }
        box.addView(feed, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showHistoryItem(h: Hist.Item) {
        val msg = "YOU:\n" + h.q + (if (h.a.isNotBlank()) "\n\nAI:\n" + h.a else "")
        AlertDialog.Builder(act).setTitle(h.title).setMessage(msg)
            .setPositiveButton("Close", null)
            .setNeutralButton("Use again") { _, _ -> act.pgTool("", h.q) }.show()
    }

    // ================= AI TOOLS =================
    private class Tool(val name: String, val sub: String, val icon: Int, val color: Int, val prefix: String)

    private fun tools(col: LinearLayout) {
        val blue = 0xFF2D8CFF.toInt(); val teal = 0xFF14D6C0.toInt(); val amber = 0xFFFFB02E.toInt()
        val all = listOf(
            Tool("AI CHAT", "Ask anything", I.CHAT, blue, ""),
            Tool("IMAGE MAKER", "Create images", I.IMAGE, teal, "Create an image of "),
            Tool("TEXT WRITER", "Write content", I.DOC, teal, "Write "),
            Tool("CODE HELPER", "Write code", I.CODE, teal, "Write code to "),
            Tool("TRANSLATOR", "Translate text", I.TRANSLATE, teal, "Translate to English: "),
            Tool("SUMMARIZER", "Summarize text", I.LINES, teal, "Summarize this: "),
            Tool("WEB SEARCH", "Search online", I.SEARCH, teal, "Search the web for "),
            Tool("FILE ANALYZER", "Analyze files", I.FOLDER, amber, "\u0000file")
        )
        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun rebuild() {
            grid.removeAllViews()
            val q = toolQuery.trim().lowercase(Locale.getDefault())
            val shown = all.filter { q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) || it.sub.lowercase(Locale.getDefault()).contains(q) }
            var i = 0
            while (i < shown.size) {
                val row = LinearLayout(act)
                for (k in 0..1) {
                    if (i + k < shown.size) {
                        val t = shown[i + k]
                        val card = LinearLayout(act).apply {
                            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(8), dp(8))
                            background = NeonBg(t.color, pgA(t.color, 0x2A), 10f, 1.8f)
                            setOnClickListener { if (t.prefix == "\u0000file") act.pgPickFile() else act.pgTool(if (t.name == "AI CHAT") "" else t.name, t.prefix) }
                        }
                        card.addView(Ico(act, t.icon, if (t.color == teal) 0xFF3FE0F5.toInt() else t.color, 2f), LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(8) })
                        val tx = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                        tx.addView(tv(t.name, 13.5f, Color.WHITE, true).apply { maxLines = 1 })
                        tx.addView(tv(t.sub, 12f, 0xFFB8D8E8.toInt()).apply { maxLines = 1 })
                        card.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
                        press(card)
                        row.addView(card, LinearLayout.LayoutParams(0, dp(64), 1f).apply { if (k == 0) marginEnd = dp(8) })
                    } else row.addView(View(act), LinearLayout.LayoutParams(0, dp(64), 1f))
                }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                i += 2
            }
            if (shown.isEmpty()) grid.addView(tv("No tool found.", 13.5f, Theme.DIM).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(14)) })
            // Smart AI workspace banner
            val pur = 0xFFD13CFF.toInt()
            val ban = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(12), dp(10))
                background = NeonBg(pur, 0xDD1A0830.toInt(), 12f, 2f)
                setOnClickListener { act.pgPlans() }
            }
            ban.addView(Ico(act, I.CHIP, 0xFF8FA8FF.toInt(), 1.8f), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
            val bt = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            bt.addView(tv("SMART AI WORKSPACE", 15f, Color.WHITE, true).apply { maxLines = 1 })
            bt.addView(tv("All-in-one AI productivity hub", 12f, 0xFFD6C8F0.toInt()).apply { maxLines = 1 })
            ban.addView(bt, LinearLayout.LayoutParams(0, -2, 1f))
            ban.addView(Ico(act, I.CHEV, 0xFF8FA8FF.toInt(), 2.8f), LinearLayout.LayoutParams(dp(20), dp(20)))
            press(ban)
            grid.addView(ban, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(10); bottomMargin = dp(6) })
        }
        col.addView(searchBox("Search AI tools...") { toolQuery = it; rebuild() }, LinearLayout.LayoutParams(-1, dp(46)))
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        rebuild()
    }

    // ================= VOICE MODE =================
    private fun voice(col: LinearLayout) {
        val hv = VoiceHero(act).apply { setOnClickListener { act.pgVoiceToggle() } }
        hero = hv
        col.addView(hv, LinearLayout.LayoutParams(-1, dp(224)))
        val sp = tv("READY TO LISTEN", 21f, Theme.G, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(11), dp(8), dp(11)); maxLines = 1
            background = NeonBg(Theme.G, 0xCC03120C.toInt(), 10f, 1.8f); setShadowLayer(8f * d, 0f, 0f, Theme.G)
        }
        statusPill = sp
        col.addView(sp, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        val lp = LinearLayout(act).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(7), dp(12), dp(7))
            background = NeonBg(0xFF2D8CFF.toInt(), 0xCC03121A.toInt(), 9f, 1.6f)
            setOnClickListener { act.pgToggleLang() }
        }
        lp.addView(Ico(act, I.GLOBE, 0xFF5AA9FF.toInt(), 2f), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
        val lt = tv("", 16f, Color.WHITE, true).apply { maxLines = 1 }
        langPill = lt
        lp.addView(lt)
        lp.addView(Ico(act, I.CHEVDOWN, 0xFF5AA9FF.toInt(), 2.6f), LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) })
        press(lp)
        val lrow = LinearLayout(act).apply { gravity = Gravity.CENTER }
        lrow.addView(lp, LinearLayout.LayoutParams(-2, -2))
        col.addView(lrow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val chips = LinearLayout(act)
        fun chip(icon: Int, label: String, color: Int, run: () -> Unit): LinearLayout {
            val v = LinearLayout(act).apply {
                gravity = Gravity.CENTER; setPadding(dp(6), dp(9), dp(6), dp(9))
                background = NeonBg(color, 0xCC03100C.toInt(), 9f, 1.8f)
                setOnClickListener { run() }
            }
            v.addView(Ico(act, icon, color, 2f), LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(6) })
            v.addView(tv(label, 14f, Color.WHITE, true).apply { maxLines = 1 })
            press(v)
            chips.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            return v
        }
        chip(I.HEADPHONES, "LISTEN", 0xFF22D3EE.toInt()) { act.pgListenOnce() }
        chip(I.MIC, "SPEAK", 0xFFD13CFF.toInt()) { act.pgSpeakLast() }
        liveChip = chip(I.LIVE, "LIVE", 0xFFFFA726.toInt()) { act.pgVoiceToggle() }
        col.addView(chips, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val bm = MicView(act).apply {
            setOnClickListener { act.pgVoiceToggle() }
            setOnLongClickListener { act.pgToggleLang(); true }
        }
        bigMic = bm
        val mrow = LinearLayout(act).apply { gravity = Gravity.CENTER }
        mrow.addView(bm, LinearLayout.LayoutParams(dp(96), dp(96)))
        col.addView(mrow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val bar = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
            background = NeonBg(Theme.G, 0xCC03120C.toInt(), 10f, 1.6f)
        }
        bar.addView(Ico(act, I.CHAT, Theme.G, 2f), LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(12) })
        val rt = tv("", 15.5f, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        replyText = rt
        bar.addView(rt, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
        voiceTick()
    }

    private fun voiceTick() {
        val st = act.pgState()
        hero?.state = st
        bigMic?.active = (st == 1)
        if (st != shownState) {
            shownState = st
            val (txt, col) = when (st) {
                1 -> Pair("LISTENING...", Theme.C)
                2 -> Pair("SPEAKING...", Theme.M)
                3 -> Pair("THINKING...", Theme.A)
                else -> Pair("READY TO LISTEN", Theme.G)
            }
            statusPill?.apply { text = txt; setTextColor(col); setShadowLayer(8f * d, 0f, 0f, col); background = NeonBg(col, 0xCC03120C.toInt(), 10f, 1.8f) }
        }
        val keep = act.pgKeep()
        if (keep != shownKeep) {
            shownKeep = keep
            val amber = 0xFFFFA726.toInt()
            liveChip?.background = NeonBg(amber, if (keep) pgA(amber, 0x66) else 0xCC03100C.toInt(), 9f, 1.8f)
        }
        val bn = act.pgLang().startsWith("bn", true)
        if (bn != shownBn) {
            shownBn = bn
            val s = SpannableString("বাংলা / ENGLISH")
            val split = "বাংলা".length
            val on = Color.WHITE; val off = 0xFF6FA0C8.toInt()
            s.setSpan(ForegroundColorSpan(if (bn) on else off), 0, split, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(off), split, split + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(if (bn) off else on), split + 3, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            langPill?.text = s
        }
        val rep = act.pgLastReply().ifBlank { "আমি কীভাবে সাহায্য করতে পারি?" }
        if (rep != shownReply) { shownReply = rep; replyText?.text = rep }
    }

    private fun startTicker() {
        val r = object : Runnable {
            override fun run() {
                navMic?.active = (act.pgState() == 1)
                if (kind == Pg.VOICE) voiceTick()
                main.postDelayed(this, 250)
            }
        }
        ticker = r
        main.post(r)
    }

    // ================= bottom bar (same as Home) =================
    private fun navBar(): View {
        val nav = LinearLayout(act)
        fun item(label: String, color: Int, icon: View, active: Boolean, last: Boolean, run: () -> Unit) {
            val b = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = NeonBg(color, if (active) ((color and 0xFFFFFF) or 0x55000000) else 0xCC03100C.toInt(), 9f, 1.8f)
                setOnClickListener { run() }
            }
            b.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
            val lbl = TextView(act).apply {
                text = label; textSize = 10f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; maxLines = 1
            }
            b.addView(lbl, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1) })
            val meas = Paint().apply { typeface = Typeface.DEFAULT_BOLD }
            b.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
                val room = (r - l) - dp(8).toFloat()
                var sz = dp(10).toFloat()
                meas.textSize = sz
                while (sz > dp(6).toFloat() && meas.measureText(label) > room) { sz -= 0.5f; meas.textSize = sz }
                if (lbl.textSize != sz) lbl.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sz)
            }
            press(b)
            nav.addView(b, LinearLayout.LayoutParams(0, -1, 1f).apply { if (!last) marginEnd = dp(4) })
        }
        val mic = MicView(act)
        navMic = mic
        item("HOME", 0xFF2D8CFF.toInt(), NavIconView(act, 0, 0xFF5AA9FF.toInt()), false, false) { act.openPage(Pg.NONE) }
        item("VOICE MODE", 0xFFD13CFF.toInt(), mic, kind == Pg.VOICE, false) { if (kind != Pg.VOICE) act.openPage(Pg.VOICE) }
        item("AI TOOLS", 0xFF28E070.toInt(), NavIconView(act, 1, 0xFF3DF08A.toInt()), kind == Pg.TOOLS, false) { if (kind != Pg.TOOLS) act.openPage(Pg.TOOLS) }
        item("HISTORY", 0xFFFFA726.toInt(), NavIconView(act, 2, 0xFFFFB84D.toInt()), kind == Pg.HISTORY, false) { if (kind != Pg.HISTORY) act.openPage(Pg.HISTORY) }
        item("PROFILE", 0xFF22D3EE.toInt(), NavIconView(act, 3, 0xFF3FE0F5.toInt()), kind == Pg.PROFILE, true) { if (kind != Pg.PROFILE) act.openPage(Pg.PROFILE) }
        return nav
    }
}
