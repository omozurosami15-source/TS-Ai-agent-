package com.tsagent.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Small helpers for the pictures Sara Watch sends: shrink to a small JPEG, and tell whether a picture changed. */
object Snap {
    fun jpeg(src: Bitmap, maxSide: Int, quality: Int): ByteArray {
        val s = maxSide.toFloat() / maxOf(src.width, src.height)
        val bm = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true) else src
        val bo = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, quality, bo)
        return bo.toByteArray()
    }

    /** 24x24 grey thumbnail of a JPEG, used only to compare two pictures. Never stored. */
    fun signature(jpg: ByteArray?): IntArray? {
        if (jpg == null) return null
        val bm = BitmapFactory.decodeByteArray(jpg, 0, jpg.size) ?: return null
        val t = Bitmap.createScaledBitmap(bm, 24, 24, true)
        val px = IntArray(24 * 24)
        t.getPixels(px, 0, 24, 0, 0, 24, 24)
        return IntArray(px.size) { val c = px[it]; (Color.red(c) * 3 + Color.green(c) * 6 + Color.blue(c)) / 10 }
    }

    /** Average brightness difference 0..255 between two signatures (255 if one is missing). */
    fun diff(a: IntArray?, b: IntArray?): Int {
        if (a == null || b == null || a.size != b.size) return 255
        var s = 0L
        for (i in a.indices) s += abs(a[i] - b[i])
        return (s / a.size).toInt()
    }
}
