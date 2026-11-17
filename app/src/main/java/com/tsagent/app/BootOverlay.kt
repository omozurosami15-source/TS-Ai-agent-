package com.tsagent.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Terminal boot sequence shown on launch; tap to skip. */
class BootOverlay(ctx: Context, private val onDone: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val lines = listOf(
        "TS-AGENT v0.1 // BOOT",
        "loading kernel modules ........ OK",
        "mounting accessibility bridge . OK",
        "calibrating voice engine ..... OK",
        "opening secure channel ........ OK",
        "ACCESS GRANTED"
    )
    private val total = lines.sumOf { it.length + 1 }
    private var typed = 0
    private var hold = 0
    private var fade = 1f
    private var done = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

    init { setOnClickListener { typed = total; hold = 100 } }

    override fun onDraw(c: Canvas) {
        if (done) return
        val a = (255 * fade).toInt().coerceIn(0, 255)
        c.drawColor(Color.argb(a, 2, 6, 4))
        if (typed < total) typed += 2
        var n = typed
        var y = 96 * d
        for ((i, l) in lines.withIndex()) {
            if (n <= 0) break
            val last = i == lines.lastIndex
            paint.textSize = if (last) 22f * d else 13f * d
            paint.isFakeBoldText = last
            paint.color = if (last) Color.argb(a, 0, 255, 156) else Color.argb(a, 111, 191, 154)
            c.drawText(l.take(n), 20 * d, y, paint)
            n -= l.length + 1
            y += if (last) 36 * d else 22 * d
        }
        if (typed >= total) {
            hold++
            if (hold > 35) {
                fade -= 0.06f
                if (fade <= 0f) { done = true; post { onDone() }; return }
            }
        }
        postInvalidateOnAnimation()
    }
}
