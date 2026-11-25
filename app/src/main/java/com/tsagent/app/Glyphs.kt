package com.tsagent.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small icons drawn in code, used in place of emoji. Each icon is drawn into a bitmap and placed inside text,
 * so it lines up with the letters and follows the text colour settings.
 */
object Glyphs {
    const val DOT = 1
    const val SQUARE = 2
    const val CROSS = 3
    const val CHECK = 4
    const val HEART = 5
    const val STAR = 6
    const val GEAR = 7
    const val GLOBE = 8
    const val MIC = 9
    const val ARROW = 10

    private fun draw(kind: Int, color: Int, px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val u = px / 24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2.6f * u
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        p.style = if (kind == DOT || kind == SQUARE || kind == HEART || kind == STAR) Paint.Style.FILL else Paint.Style.STROKE
        when (kind) {
            DOT -> c.drawCircle(12 * u, 12 * u, 6 * u, p)
            SQUARE -> c.drawRoundRect(RectF(5 * u, 5 * u, 19 * u, 19 * u), 2.5f * u, 2.5f * u, p)
            CROSS -> {
                c.drawLine(6 * u, 6 * u, 18 * u, 18 * u, p)
                c.drawLine(18 * u, 6 * u, 6 * u, 18 * u, p)
            }
            CHECK -> {
                val path = Path().apply { moveTo(5 * u, 12.5f * u); lineTo(10 * u, 17 * u); lineTo(19 * u, 7 * u) }
                c.drawPath(path, p)
            }
            HEART -> {
                val path = Path().apply {
                    moveTo(12 * u, 20 * u)
                    cubicTo(3 * u, 14 * u, 3 * u, 7 * u, 8 * u, 6 * u)
                    cubicTo(10 * u, 5.5f * u, 11.5f * u, 7 * u, 12 * u, 8.5f * u)
                    cubicTo(12.5f * u, 7 * u, 14 * u, 5.5f * u, 16 * u, 6 * u)
                    cubicTo(21 * u, 7 * u, 21 * u, 14 * u, 12 * u, 20 * u)
                    close()
                }
                c.drawPath(path, p)
            }
            STAR -> {
                val path = Path()
                for (i in 0 until 10) {
                    val a = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) 10f * u else 4.2f * u
                    val x = 12 * u + (r * cos(a)).toFloat()
                    val y = 12.5f * u + (r * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                c.drawPath(path, p)
            }
            GEAR -> {
                c.drawCircle(12 * u, 12 * u, 3.5f * u, p)
                c.drawCircle(12 * u, 12 * u, 7f * u, p)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    c.drawLine(12 * u + ca * 8.6f * u, 12 * u + sa * 8.6f * u, 12 * u + ca * 10.6f * u, 12 * u + sa * 10.6f * u, p)
                }
            }
            GLOBE -> {
                c.drawCircle(12 * u, 12 * u, 9 * u, p)
                c.drawLine(3 * u, 12 * u, 21 * u, 12 * u, p)
                c.drawOval(RectF(8 * u, 3 * u, 16 * u, 21 * u), p)
            }
            MIC -> {
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(9 * u, 2.5f * u, 15 * u, 14 * u), 3 * u, 3 * u, p)
                p.style = Paint.Style.STROKE
                c.drawArc(RectF(5.5f * u, 7 * u, 18.5f * u, 19 * u), 0f, 180f, false, p)
                c.drawLine(12 * u, 19 * u, 12 * u, 22 * u, p)
                c.drawLine(8.5f * u, 22 * u, 15.5f * u, 22 * u, p)
            }
            ARROW -> {
                val path = Path().apply { moveTo(9 * u, 6 * u); lineTo(15 * u, 12 * u); lineTo(9 * u, 18 * u) }
                c.drawPath(path, p)
            }
            else -> {}
        }
        return bmp
    }

    /** One icon as a piece of text. */
    fun span(ctx: Context, kind: Int, color: Int, dpSize: Float = 13f): CharSequence {
        val px = (dpSize * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(8)
        val drawable = BitmapDrawable(ctx.resources, draw(kind, color, px)).apply { setBounds(0, 0, px, px) }
        val ss = SpannableString(" ")
        ss.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BASELINE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ss
    }

    /** An icon followed by a word, for buttons and labels. */
    fun label(ctx: Context, kind: Int, color: Int, text: CharSequence, dpSize: Float = 13f): CharSequence =
        SpannableStringBuilder(span(ctx, kind, color, dpSize)).append("  ").append(text)
}
