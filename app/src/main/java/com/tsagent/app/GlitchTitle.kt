package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Title that periodically glitches with RGB split and slice shifts. */
class GlitchTitle(ctx: Context, initial: String) : View(ctx) {
    var label: String = initial
        private set
    /** Changes the shown name, for example when the admin changes the site name. */
    fun setLabel(text: String) { label = text; invalidate() }
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 28f * d
        letterSpacing = 0.12f
    }
    private var t = 0f

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(resolveSize((paint.measureText(label) + 6 * d).toInt(), wSpec), (40 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        t += 0.016f
        val base = height * 0.74f
        val glitch = (t % 3.2f) < 0.22f
        if (glitch) {
            val dx = (Math.random().toFloat() - 0.5f) * 10 * d
            paint.clearShadowLayer()
            paint.color = Color.argb(210, 255, 43, 214); c.drawText(label, dx, base, paint)
            paint.color = Color.argb(210, 0, 229, 255); c.drawText(label, -dx, base, paint)
            c.save()
            c.clipRect(0f, height * 0.38f, width.toFloat(), height * 0.58f)
            c.translate((if (Math.random() > 0.5) 1 else -1) * 8 * d, 0f)
            paint.color = Theme.G
            c.drawText(label, 0f, base, paint)
            c.restore()
        }
        paint.color = Theme.G
        paint.setShadowLayer(8 * d, 0f, 0f, Theme.G)
        c.drawText(label, 0f, base, paint)
        postInvalidateOnAnimation()
    }
}
