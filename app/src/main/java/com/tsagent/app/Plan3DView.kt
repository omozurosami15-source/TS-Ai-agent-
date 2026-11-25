package com.tsagent.app

import android.content.Context
import android.content.res.AssetManager
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.TimeZone
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time 3D for the 17 plans (OpenGL ES 2.0). No photos: every plan is a 3D scene built from lit meshes
 * (sphere, torus, box, cylinder, gears...) with its own animation, in front of an animated shader backdrop.
 *
 *  - setPlan(1..17)         which plan scene to show
 *  - setMode(0..3)          the AI state: 0 ready, 1 listening, 2 speaking, 3 thinking (the scene reacts to it)
 *  - pulse()                a quick energy burst, used when the AI answers
 *  - setFocus(x, y, size)   where the scene sits in the view (0..1) and how big (fraction of the shorter side)
 *  - setBackdrop(on)        the animated background behind the object
 * Drag with a finger to turn the object.
 */
class Plan3DView(context: Context) : GLSurfaceView(context) {
    private val scene = Plan3DRenderer()
    private var lastX = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setRenderer(scene)
        renderMode = RENDERMODE_CONTINUOUSLY
        scene.assets = context.applicationContext.assets
        scene.setPlan(1)
    }

    fun setPlan(number: Int) { scene.setPlan(number) }
    fun setMode(m: Int) { scene.mode = m }
    fun pulse() { scene.burst = 1f }
    fun setBackdrop(on: Boolean) { scene.backdrop = on }
    fun setFocus(cx: Float, cy: Float, size: Float) { scene.fx = cx; scene.fy = cy; scene.fsize = size }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> { scene.dragYaw += (e.x - lastX) * 0.45f; lastX = e.x }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

// ------------------------------------------------------------------------------------------------
// geometry
// ------------------------------------------------------------------------------------------------

private class Mesh(val v: FloatArray, val i: ShortArray)

private const val PIF = 3.14159265f

private object Geo {
    private fun finish(v: ArrayList<Float>, idx: ArrayList<Int>): Mesh =
        Mesh(v.toFloatArray(), ShortArray(idx.size) { idx[it].toShort() })

    private fun vtx(v: ArrayList<Float>, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        v.add(x); v.add(y); v.add(z); v.add(nx); v.add(ny); v.add(nz)
    }

    fun sphere(r: Float, lat: Int = 14, lon: Int = 22): Mesh {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (y in 0..lat) {
            val th = PIF * y / lat
            for (x in 0..lon) {
                val ph = 2f * PIF * x / lon
                val nx = sin(th) * cos(ph); val ny = cos(th); val nz = sin(th) * sin(ph)
                vtx(v, nx * r, ny * r, nz * r, nx, ny, nz)
            }
        }
        for (y in 0 until lat) for (x in 0 until lon) {
            val a = y * (lon + 1) + x; val b = a + lon + 1
            idx.add(a); idx.add(b); idx.add(a + 1); idx.add(a + 1); idx.add(b); idx.add(b + 1)
        }
        return finish(v, idx)
    }

    /** A ring in the XY plane (it faces the camera). R = ring radius, r = tube radius. */
    fun torus(R: Float, r: Float, seg: Int = 40, side: Int = 10): Mesh {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (i in 0..seg) {
            val u = 2f * PIF * i / seg
            for (j in 0..side) {
                val w = 2f * PIF * j / side
                val nx = cos(w) * cos(u); val ny = cos(w) * sin(u); val nz = sin(w)
                vtx(v, (R + r * cos(w)) * cos(u), (R + r * cos(w)) * sin(u), r * sin(w), nx, ny, nz)
            }
        }
        for (i in 0 until seg) for (j in 0 until side) {
            val a = i * (side + 1) + j; val b = a + side + 1
            idx.add(a); idx.add(b); idx.add(a + 1); idx.add(a + 1); idx.add(b); idx.add(b + 1)
        }
        return finish(v, idx)
    }

    /** Box with half sizes hx, hy, hz. */
    fun box(hx: Float, hy: Float, hz: Float): Mesh {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        fun face(nx: Float, ny: Float, nz: Float, ux: Float, uy: Float, uz: Float, wx: Float, wy: Float, wz: Float) {
            val base = v.size / 6
            val corners = arrayOf(floatArrayOf(-1f, -1f), floatArrayOf(1f, -1f), floatArrayOf(1f, 1f), floatArrayOf(-1f, 1f))
            for (c in corners) {
                vtx(v, (nx + ux * c[0] + wx * c[1]) * hx, (ny + uy * c[0] + wy * c[1]) * hy,
                    (nz + uz * c[0] + wz * c[1]) * hz, nx, ny, nz)
            }
            idx.add(base); idx.add(base + 1); idx.add(base + 2); idx.add(base); idx.add(base + 2); idx.add(base + 3)
        }
        face(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        face(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        face(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        face(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        face(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f)
        face(0f, 0f, -1f, 1f, 0f, 0f, 0f, 1f, 0f)
        return finish(v, idx)
    }

    /** Cylinder or cone along Y: r1 is the bottom radius, r2 the top radius (0 = a point), h the height. */
    fun cyl(r1: Float, r2: Float, h: Float, seg: Int = 20): Mesh {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        val hh = h / 2f
        val slope = (r1 - r2) / h
        val len = sqrt(1f + slope * slope)
        for (i in 0..seg) {
            val a = 2f * PIF * i / seg
            val c = cos(a); val s = sin(a)
            vtx(v, c * r1, -hh, s * r1, c / len, slope / len, s / len)
            vtx(v, c * r2, hh, s * r2, c / len, slope / len, s / len)
        }
        for (i in 0 until seg) {
            val a = i * 2
            idx.add(a); idx.add(a + 1); idx.add(a + 2); idx.add(a + 2); idx.add(a + 1); idx.add(a + 3)
        }
        fun cap(y: Float, r: Float, ny: Float) {
            if (r <= 0.0001f) return
            val c0 = v.size / 6
            vtx(v, 0f, y, 0f, 0f, ny, 0f)
            for (i in 0..seg) {
                val a = 2f * PIF * i / seg
                vtx(v, cos(a) * r, y, sin(a) * r, 0f, ny, 0f)
            }
            for (i in 0 until seg) { idx.add(c0); idx.add(c0 + 1 + i); idx.add(c0 + 2 + i) }
        }
        cap(-hh, r1, -1f)
        cap(hh, r2, 1f)
        return finish(v, idx)
    }

    /** A gear lying in the XZ plane (axis = Y): body plus teeth. Tilt the part with rx = 90 to face the camera. */
    fun gear(r: Float, teeth: Int, thick: Float): Mesh {
        val list = ArrayList<Mesh>()
        list.add(cyl(r, r, thick, 28))
        val tl = r * 0.22f
        val tw = (2f * PIF * r / teeth) * 0.28f
        for (k in 0 until teeth) {
            val a = 2f * PIF * k / teeth
            val deg = a * 180f / PIF
            list.add(place(box(tl, thick / 2f, tw), cos(a) * (r + tl * 0.6f), 0f, sin(a) * (r + tl * 0.6f), -deg, 0f))
        }
        return merge(list)
    }

    /** Moves a mesh; rotates it first about Z (rz) and then about Y (ry), in degrees. */
    fun place(m: Mesh, tx: Float, ty: Float, tz: Float, ry: Float = 0f, rz: Float = 0f): Mesh {
        val out = FloatArray(m.v.size)
        val ay = ry * PIF / 180f; val az = rz * PIF / 180f
        val cy = cos(ay); val sy = sin(ay); val cz = cos(az); val sz = sin(az)
        var i = 0
        while (i < m.v.size) {
            val x = m.v[i]; val y = m.v[i + 1]; val z = m.v[i + 2]
            val nx = m.v[i + 3]; val ny = m.v[i + 4]; val nz = m.v[i + 5]
            val x1 = x * cz - y * sz; val y1 = x * sz + y * cz
            val nx1 = nx * cz - ny * sz; val ny1 = nx * sz + ny * cz
            out[i] = x1 * cy + z * sy + tx
            out[i + 1] = y1 + ty
            out[i + 2] = -x1 * sy + z * cy + tz
            out[i + 3] = nx1 * cy + nz * sy
            out[i + 4] = ny1
            out[i + 5] = -nx1 * sy + nz * cy
            i += 6
        }
        return Mesh(out, m.i)
    }

    fun merge(list: List<Mesh>): Mesh {
        val v = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (m in list) {
            val base = v.size / 6
            for (f in m.v) v.add(f)
            for (s in m.i) idx.add(base + s.toInt())
        }
        return finish(v, idx)
    }
}

// ------------------------------------------------------------------------------------------------
// scene graph (flat list of animated parts)
// ------------------------------------------------------------------------------------------------

private class Part(m: Mesh, val color: FloatArray) {
    val vb: FloatBuffer = ByteBuffer.allocateDirect(m.v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(m.v); position(0) }
    val ib: ShortBuffer = ByteBuffer.allocateDirect(m.i.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(m.i); position(0) }
    val count = m.i.size
    var emis = 0f
    var alpha = 1f
    var tx = 0f; var ty = 0f; var tz = 0f
    var rx = 0f; var ry = 0f; var rz = 0f
    /** Spin about the part's own Y axis, applied before every other rotation (used by gears). */
    var spin = 0f
    var sx = 1f; var sy = 1f; var sz = 1f
    var anim: ((Part, Float, Float) -> Unit)? = null
}

private class Scene(val ac: FloatArray) {
    val parts = ArrayList<Part>()
    fun add(m: Mesh, color: FloatArray, x: Float = 0f, y: Float = 0f, z: Float = 0f,
            emis: Float = 0f, alpha: Float = 1f, anim: ((Part, Float, Float) -> Unit)? = null): Part {
        val p = Part(m, color)
        p.tx = x; p.ty = y; p.tz = z; p.emis = emis; p.alpha = alpha; p.anim = anim
        parts.add(p)
        return p
    }
}

private fun rgb(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)
private fun lite(c: FloatArray, k: Float) = floatArrayOf(minOf(1f, c[0] + k), minOf(1f, c[1] + k), minOf(1f, c[2] + k))
private fun darker(c: FloatArray, k: Float) = floatArrayOf(c[0] * k, c[1] * k, c[2] * k)
private fun alt(c: FloatArray) = floatArrayOf(c[2], c[0], c[1])
private fun degOf(rad: Float) = rad * 180f / PIF

/** Small sphere that flies around the centre on a tilted circle. */
private fun Scene.orbiter(size: Float, color: FloatArray, radius: Float, speed: Float, phase: Float, tilt: Float, emis: Float = 0.9f) {
    add(Geo.sphere(size, 8, 12), color, emis = emis) { p, t, _ ->
        val a = t * speed + phase
        p.tx = cos(a) * radius
        p.ty = sin(a) * radius * sin(tilt)
        p.tz = sin(a) * radius * cos(tilt)
    }
}

/** A magnifying glass whose lens centre follows f(t) = (x, y, z). */
private fun Scene.magnifier(color: FloatArray, f: (Float) -> FloatArray) {
    val white = rgb(.9f, .96f, 1f)
    add(Geo.torus(.27f, .042f), color, emis = .8f) { p, t, _ -> val c = f(t); p.tx = c[0]; p.ty = c[1]; p.tz = c[2] }
    val glass = add(Geo.sphere(.25f, 10, 14), color, alpha = .22f, emis = .35f) { p, t, _ -> val c = f(t); p.tx = c[0]; p.ty = c[1]; p.tz = c[2] }
    glass.sz = .14f
    val handle = add(Geo.cyl(.038f, .038f, .4f, 10), white, emis = .2f) { p, t, _ -> val c = f(t); p.tx = c[0] + .27f; p.ty = c[1] - .27f; p.tz = c[2] }
    handle.rz = 45f
}

// ---- 1  Neural Core: pulsing core, rings, living nodes ----
private fun Scene.neural() {
    val white = rgb(.92f, .98f, 1f); val b = alt(ac)
    add(Geo.sphere(.40f), ac, emis = .65f) { p, t, e -> val s = 1f + .08f * sin(t * 3f) * (1f + e); p.sx = s; p.sy = s; p.sz = s }
    add(Geo.sphere(.66f, 12, 18), ac, alpha = .16f, emis = .2f)
    add(Geo.torus(.95f, .022f), ac, emis = .9f) { p, t, e -> p.rx = 70f; p.rz = t * (25f + 30f * e) }
    add(Geo.torus(1.05f, .018f), b, emis = .9f) { p, t, e -> p.ry = 40f; p.rz = -t * (30f + 30f * e) }
    add(Geo.torus(.85f, .016f), white, emis = .8f) { p, t, e -> p.ry = 25f; p.rx = t * (20f + 20f * e) }
    for (i in 0 until 12) {
        val y = 1f - 2f * (i + .5f) / 12f
        val r = sqrt(1f - y * y)
        val th = i * 2.39996f
        add(Geo.sphere(.07f, 8, 10), if (i % 3 == 0) white else ac, cos(th) * r * 1.2f, y * 1.2f, sin(th) * r * 1.2f, emis = .9f) { p, t, e ->
            val s = .8f + .5f * abs(sin(t * 2f + i)) * (1f + e); p.sx = s; p.sy = s; p.sz = s
        }
    }
}

// ---- 2  Voice Studio: circular equalizer around a microphone ----
private fun Scene.voice() {
    val white = rgb(.93f, .96f, 1f); val b = alt(ac)
    val n = 28
    for (i in 0 until n) {
        val a = 2f * PIF * i / n
        add(Geo.box(.045f, .5f, .045f), if (i % 2 == 0) ac else lite(ac, .25f), cos(a) * 1.05f, 0f, sin(a) * 1.05f, emis = .7f) { p, t, e ->
            p.sy = .25f + .55f * abs(sin(t * 3f + i * .45f)) * (.5f + e) + .15f * abs(sin(t * 5.3f + i))
        }
    }
    add(Geo.cyl(.2f, .2f, .5f), white, 0f, .08f, 0f)
    add(Geo.sphere(.2f), white, 0f, .33f, 0f)
    add(Geo.sphere(.2f), white, 0f, -.17f, 0f)
    add(Geo.torus(.205f, .014f, 24, 6), ac, 0f, .08f, 0f, emis = .9f).rx = 90f
    add(Geo.torus(.34f, .03f), b, 0f, .05f, 0f, emis = .7f)
    add(Geo.cyl(.03f, .03f, .5f, 10), white, 0f, -.55f, 0f)
    add(Geo.cyl(.3f, .3f, .04f), darker(ac, .6f), 0f, -.82f, 0f, emis = .3f)
    add(Geo.torus(1.05f, .014f), ac, emis = .8f, alpha = .6f) { p, t, e -> p.rx = 90f; val s = 1f + .08f * sin(t * 4f) * (.4f + e); p.sx = s; p.sy = s }
}

// ---- 3  Memory Vault: glass vault, spinning data core, orbiting memory chips ----
private fun Scene.vault() {
    val white = rgb(.92f, .97f, 1f); val b = alt(ac)
    add(Geo.box(.72f, .72f, .72f), ac, alpha = .18f, emis = .25f)
    for (sy in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
        add(Geo.box(.72f, .022f, .022f), ac, 0f, sy * .72f, sz * .72f, emis = .9f)
        add(Geo.box(.022f, .72f, .022f), ac, sy * .72f, 0f, sz * .72f, emis = .9f)
        add(Geo.box(.022f, .022f, .72f), ac, sy * .72f, sz * .72f, 0f, emis = .9f)
    }
    for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f))
        add(Geo.sphere(.07f, 8, 10), white, sx * .72f, sy * .72f, sz * .72f, emis = .9f)
    add(Geo.box(.26f, .26f, .26f), b, emis = .7f) { p, t, e -> p.ry = t * (35f + 40f * e); p.rx = t * 25f }
    add(Geo.sphere(.18f, 10, 14), white, emis = .9f) { p, t, e -> val s = 1f + .12f * sin(t * 3f) * (1f + e); p.sx = s; p.sy = s; p.sz = s }
    for (i in 0 until 6) {
        add(Geo.box(.09f, .12f, .012f), if (i % 2 == 0) ac else white, emis = .8f) { p, t, _ ->
            val a = t * .9f + i * 1.0472f
            p.tx = cos(a) * 1.15f; p.tz = sin(a) * 1.15f; p.ty = sin(t * 1.3f + i) * .3f; p.ry = -degOf(a) + 90f
        }
    }
}

// ---- 4  Soul Studio: a calm breathing lotus with floating light ----
private fun Scene.soul() {
    val white = rgb(.95f, .98f, 1f); val b = alt(ac)
    add(Geo.sphere(.30f), white, 0f, .02f, 0f, emis = .85f) { p, t, _ -> val s = 1f + .12f * sin(t * 1.3f); p.sx = s; p.sy = s; p.sz = s }
    for (layer in 0..1) {
        val n = 8
        for (i in 0 until n) {
            val a = 2f * PIF * i / n + layer * PIF / n
            val rad = if (layer == 0) .5f else .72f
            val pt = add(Geo.sphere(.5f, 10, 14), if (layer == 0) ac else b, cos(a) * rad, if (layer == 0) -.02f else -.22f, sin(a) * rad,
                emis = .3f, alpha = .8f) { p, t, _ -> p.rz = (if (layer == 0) 34f else 20f) + 8f * sin(t * 1.2f + i) }
            pt.sx = if (layer == 0) .55f else .7f; pt.sy = .1f; pt.sz = .22f; pt.ry = -degOf(a)
        }
    }
    add(Geo.torus(.95f, .014f), ac, 0f, .75f, 0f, emis = .9f) { p, t, _ -> p.rx = 90f; p.rz = t * 14f }
    for (i in 0 until 7) orbiter(.045f, white, .9f + (i % 3) * .12f, .5f + i * .07f, i * .9f, .35f + i * .15f, 1f)
}

// ---- 5  Vision Lab: a scanning eye ----
private fun Scene.vision() {
    val white = rgb(.93f, .97f, 1f); val b = alt(ac)
    add(Geo.sphere(.72f, 18, 26), white, emis = .08f)
    val iris = add(Geo.sphere(.40f, 14, 20), ac, 0f, 0f, .62f, emis = .55f) { p, t, _ -> p.tx = sin(t * .8f) * .12f; p.ty = sin(t * 1.1f) * .08f }
    iris.sz = .35f
    val pupil = add(Geo.sphere(.18f, 10, 14), rgb(.02f, .03f, .05f), 0f, 0f, .78f) { p, t, e ->
        p.tx = sin(t * .8f) * .12f; p.ty = sin(t * 1.1f) * .08f
        val s = 1f - .25f * e + .1f * sin(t * 2f); p.sx = s; p.sy = s
    }
    pupil.sz = .4f
    add(Geo.sphere(.05f, 8, 10), white, .08f, .09f, .86f, emis = 1f) { p, t, _ -> p.tx = .08f + sin(t * .8f) * .12f; p.ty = .09f + sin(t * 1.1f) * .08f }
    add(Geo.torus(1.0f, .02f), ac, emis = .9f) { p, t, e -> p.rz = t * (40f + 40f * e) }
    add(Geo.torus(1.12f, .015f), b, emis = .8f) { p, t, e -> p.rx = 75f; p.rz = -t * (30f + 30f * e) }
    add(Geo.box(.9f, .014f, .014f), ac, 0f, 0f, .95f, emis = 1f, alpha = .8f) { p, t, _ -> p.ty = sin(t * 2f) * .7f }
}

// ---- 6  Web Intelligence: a connected globe with satellites ----
private fun Scene.web() {
    val white = rgb(.93f, .97f, 1f); val b = alt(ac)
    add(Geo.sphere(.7f, 16, 24), darker(ac, .5f), alpha = .4f, emis = .3f)
    for (k in -2..2) {
        val lat = k * 30f * PIF / 180f
        val r = .7f * cos(lat)
        add(Geo.torus(r, .012f, 36, 6), ac, 0f, .7f * sin(lat), 0f, emis = .9f).rx = 90f
    }
    for (i in 0 until 4) add(Geo.torus(.7f, .012f, 36, 6), ac, emis = .9f) { p, t, _ -> p.ry = i * 45f + t * 12f }
    for (i in 0 until 7) {
        val la = (i * 53 % 140 - 70) * PIF / 180f
        val lo = i * 2.1f
        add(Geo.sphere(.05f, 8, 10), white, .7f * cos(la) * cos(lo), .7f * sin(la), .7f * cos(la) * sin(lo), emis = 1f) { p, t, _ ->
            val s = .7f + .8f * abs(sin(t * 2f + i)); p.sx = s; p.sy = s; p.sz = s
        }
    }
    for (i in 0 until 4) {
        val tilt = .4f + i * .45f
        orbiter(.09f, if (i % 2 == 0) b else white, 1.15f, .7f + i * .12f, i * 1.6f, tilt, 1f)
        add(Geo.torus(1.15f, .007f, 48, 4), ac, emis = .7f, alpha = .6f).rx = 90f - degOf(tilt)
    }
}

// ---- 7  Coding Studio: floating </> with code blocks ----
private fun Scene.code() {
    val white = rgb(.95f, .97f, 1f); val b = alt(ac)
    for (sg in listOf(-1f, 1f)) {
        add(Geo.box(.35f, .07f, .07f), ac, sg * .6f, .25f, 0f, emis = .85f).rz = -sg * 45f
        add(Geo.box(.35f, .07f, .07f), ac, sg * .6f, -.25f, 0f, emis = .85f).rz = sg * 45f
    }
    add(Geo.box(.05f, .55f, .05f), white, emis = .9f) { p, t, e -> p.rz = -20f; val s = 1f + .1f * sin(t * 4f) * (.3f + e); p.sy = s }
    add(Geo.box(.05f, .28f, .03f), white, .95f, -.78f, 0f, emis = 1f) { p, t, _ -> p.emis = if (sin(t * 6f) > 0f) 1f else 0.05f }
    add(Geo.cyl(.95f, .95f, .05f, 36), darker(ac, .55f), 0f, -.98f, 0f, emis = .3f, alpha = .8f)
    add(Geo.torus(.97f, .014f), ac, 0f, -.97f, 0f, emis = .9f).rx = 90f
    for (i in 0 until 6) {
        add(Geo.box(.07f, .07f, .07f), if (i % 2 == 0) b else ac, emis = .8f) { p, t, _ ->
            val a = t * .8f + i * 1.0472f
            p.tx = cos(a) * 1.2f; p.tz = sin(a) * 1.2f; p.ty = sin(t * 1.2f + i) * .5f; p.rx = t * 60f; p.ry = t * 45f
        }
    }
}

// ---- 8  File Reader: fanned pages and a scanning magnifier ----
private fun Scene.files() {
    val white = rgb(.94f, .97f, 1f)
    for (i in 0 until 4) {
        val tint = if (i == 3) white else lite(darker(ac, .55f), .25f + i * .08f)
        val pg = add(Geo.box(.5f, .66f, .015f), tint, (i - 1.5f) * .22f, 0f, i * .06f, emis = if (i == 3) .1f else .05f)
        pg.ry = (1.5f - i) * 10f
    }
    for (l in 0 until 6) add(Geo.box(.3f, .012f, .01f), ac, .33f, .44f - l * .13f, .2f, emis = .9f)
    add(Geo.box(.3f, .05f, .01f), ac, .33f, .56f, .2f, emis = .9f, alpha = .7f) { p, t, _ -> p.ty = .1f + sin(t * 1.6f) * .35f }
    magnifier(ac) { t -> floatArrayOf(sin(t * .9f) * .4f, .1f + cos(t * .7f) * .3f, .5f) }
}

// ---- 9  Task Automation: meshing gears and a flowing task line ----
private fun Scene.tasks() {
    val white = rgb(.93f, .97f, 1f); val b = alt(ac)
    val g1 = add(Geo.gear(.5f, 12, .16f), ac, -.55f, .25f, 0f, emis = .35f) { p, t, _ -> p.spin = t * 28f }
    g1.rx = 90f
    val g2 = add(Geo.gear(.38f, 9, .16f), b, .42f, .45f, 0f, emis = .35f) { p, t, _ -> p.spin = -t * 37f + 20f }
    g2.rx = 90f
    val g3 = add(Geo.gear(.42f, 10, .16f), lite(ac, .15f), 0f, -.5f, 0f, emis = .35f) { p, t, _ -> p.spin = t * 33f }
    g3.rx = 90f
    add(Geo.sphere(.12f, 8, 10), white, -.55f, .25f, .12f, emis = .9f)
    add(Geo.sphere(.1f, 8, 10), white, .42f, .45f, .12f, emis = .9f)
    add(Geo.sphere(.1f, 8, 10), white, 0f, -.5f, .12f, emis = .9f)
    add(Geo.box(1.15f, .012f, .012f), ac, 0f, -1.05f, 0f, emis = .8f, alpha = .6f)
    for (i in 0 until 8) add(Geo.sphere(.05f, 8, 10), white, 0f, -1.05f, 0f, emis = 1f) { p, t, _ -> p.tx = -1.1f + 2.2f * ((t * .3f + i / 8f) % 1f) }
    add(Geo.cyl(.1f, 0f, .22f, 10), ac, 1.2f, -1.05f, 0f, emis = .9f).rz = -90f
}

// ---- 10  Smart Notes: notebook with a pencil that writes ----
private fun Scene.notes() {
    val white = rgb(.96f, .97f, 1f)
    add(Geo.box(.62f, .82f, .06f), darker(ac, .55f), 0f, 0f, -.07f, emis = .15f)
    add(Geo.box(.56f, .76f, .06f), white, 0f, 0f, 0f, emis = .12f)
    for (i in 0 until 7) add(Geo.torus(.065f, .014f, 14, 6), rgb(.8f, .85f, .9f), -.58f, (i - 3) * .2f, 0f, emis = .4f).rx = 90f
    for (l in 0 until 6) add(Geo.box(.38f, .012f, .01f), ac, .05f, .46f - l * .13f, .04f, emis = .9f)
    add(Geo.box(.38f, .04f, .012f), ac, .05f, 0f, .045f, emis = .9f, alpha = .55f) { p, t, _ -> p.ty = .46f - ((t * .5f) % 1f) * .65f }
    val ax = .766f; val ay = .643f
    add(Geo.cyl(.04f, .04f, .7f, 10), rgb(1f, .8f, .2f), 0f, 0f, .14f, emis = .3f) { p, t, _ ->
        p.tx = .15f + .2f * sin(t * 2f) + ax * .42f; p.ty = -.15f + .1f * sin(t * 4f) + ay * .42f
    }.rz = -50f
    add(Geo.cyl(0f, .04f, .14f, 10), rgb(.93f, .78f, .58f), 0f, 0f, .14f, emis = .2f) { p, t, _ ->
        p.tx = .15f + .2f * sin(t * 2f) + ax * .07f; p.ty = -.15f + .1f * sin(t * 4f) + ay * .07f
    }.rz = -50f
    add(Geo.cyl(.042f, .042f, .08f, 10), rgb(1f, .5f, .6f), 0f, 0f, .14f, emis = .3f) { p, t, _ ->
        p.tx = .15f + .2f * sin(t * 2f) + ax * .8f; p.ty = -.15f + .1f * sin(t * 4f) + ay * .8f
    }.rz = -50f
}

// ---- 11  Daily Briefing: a clock that shows the real time, with the sun ----
private fun Scene.briefing() {
    val white = rgb(.94f, .98f, 1f); val red = rgb(1f, .3f, .35f); val sun = rgb(1f, .78f, .25f)
    add(Geo.cyl(.78f, .78f, .12f, 40), rgb(.07f, .1f, .15f), emis = .1f).rx = 90f
    add(Geo.torus(.8f, .05f, 48, 8), ac, emis = .9f)
    for (i in 0 until 12) {
        val a = 2f * PIF * i / 12f
        add(Geo.box(.02f, if (i % 3 == 0) .08f else .045f, .015f), white, sin(a) * .66f, cos(a) * .66f, .07f, emis = .9f).rz = -degOf(a)
    }
    fun secs(): Float {
        val ms = System.currentTimeMillis()
        return (((ms + TimeZone.getDefault().getOffset(ms)) / 1000L) % 86400L).toFloat() + (ms % 1000L) / 1000f
    }
    fun hand(len: Float, w: Float, color: FloatArray, z: Float, angle: () -> Float) {
        add(Geo.box(w, len / 2f, .012f), color, 0f, 0f, z, emis = .9f) { p, _, _ ->
            val a = angle(); p.tx = sin(a) * len / 2f; p.ty = cos(a) * len / 2f; p.rz = -degOf(a)
        }
    }
    hand(.42f, .028f, white, .09f) { 2f * PIF * ((secs() / 3600f) % 12f) / 12f }
    hand(.6f, .02f, ac, .1f) { 2f * PIF * ((secs() / 60f) % 60f) / 60f }
    hand(.66f, .008f, red, .11f) { 2f * PIF * (secs() % 60f) / 60f }
    add(Geo.sphere(.05f, 8, 10), white, 0f, 0f, .12f, emis = 1f)
    add(Geo.sphere(.17f, 12, 16), sun, 1.0f, .8f, 0f, emis = .9f) { p, t, _ -> val s = 1f + .08f * sin(t * 2f); p.sx = s; p.sy = s; p.sz = s }
    for (i in 0 until 8) add(Geo.box(.018f, .08f, .018f), sun, 0f, 0f, 0f, emis = .9f) { p, t, _ ->
        val a = t * .6f + i * PIF / 4f
        p.tx = 1.0f + sin(a) * .3f; p.ty = .8f + cos(a) * .3f; p.rz = -degOf(a)
    }
}

// ---- 12  Translator: two language cards swapping places ----
private fun Scene.translate() {
    val white = rgb(.96f, .98f, 1f); val warm = rgb(.95f, .35f, .4f)
    fun cx(t: Float, ph: Float): Float = cos(t * 1.1f + ph) * .78f
    fun cz(t: Float, ph: Float): Float = sin(t * 1.1f + ph) * .78f
    // card A with an A shaped letter
    add(Geo.box(.46f, .46f, .05f), ac, emis = .35f) { p, t, _ -> p.tx = cx(t, 0f); p.tz = cz(t, 0f) }
    add(Geo.box(.035f, .2f, .02f), white, emis = .9f) { p, t, _ -> p.tx = cx(t, 0f) - .075f; p.tz = cz(t, 0f) + .05f; p.rz = -16f }
    add(Geo.box(.035f, .2f, .02f), white, emis = .9f) { p, t, _ -> p.tx = cx(t, 0f) + .075f; p.tz = cz(t, 0f) + .05f; p.rz = 16f }
    add(Geo.box(.07f, .02f, .02f), white, emis = .9f) { p, t, _ -> p.tx = cx(t, 0f); p.ty = -.04f; p.tz = cz(t, 0f) + .05f }
    // card B with a round letter
    add(Geo.box(.46f, .46f, .05f), warm, emis = .35f) { p, t, _ -> p.tx = cx(t, PIF); p.tz = cz(t, PIF) }
    add(Geo.torus(.09f, .022f, 20, 6), white, emis = .9f) { p, t, _ -> p.tx = cx(t, PIF) - .04f; p.tz = cz(t, PIF) + .05f }
    add(Geo.box(.022f, .15f, .02f), white, emis = .9f) { p, t, _ -> p.tx = cx(t, PIF) + .09f; p.tz = cz(t, PIF) + .05f }
    add(Geo.torus(.95f, .022f), ac, emis = .8f)
    add(Geo.torus(1.1f, .022f), warm, emis = .8f)
    for (k in 0..1) add(Geo.cyl(.07f, 0f, .16f, 10), if (k == 0) ac else warm, emis = 1f) { p, t, _ ->
        val a = (if (k == 0) t * 1.5f else -t * 1.5f) + k * PIF
        val rad = if (k == 0) .95f else 1.1f
        p.tx = cos(a) * rad; p.ty = sin(a) * rad
        p.rz = if (k == 0) degOf(a) else degOf(a + PIF)
    }
}

// ---- 13  Research Desk: books under a moving lens ----
private fun Scene.research() {
    val white = rgb(.94f, .97f, 1f); val b = alt(ac)
    add(Geo.box(.7f, .09f, .5f), b, 0f, -.3f, 0f, emis = .12f)
    add(Geo.box(.66f, .09f, .46f), ac, 0f, -.15f, 0f, emis = .2f).ry = 8f
    add(Geo.box(.62f, .09f, .44f), lite(ac, .2f), 0f, 0f, 0f, emis = .2f).ry = -6f
    add(Geo.box(.6f, .05f, .43f), white, 0f, -.15f, .01f, emis = .05f).ry = 8f
    magnifier(ac) { t -> floatArrayOf(sin(t * 1.1f) * .4f, .5f + cos(t * 1.7f) * .08f, cos(t * 1.1f) * .25f + .3f) }
    for (i in 0 until 6) add(Geo.box(.1f, .13f, .01f), if (i % 2 == 0) white else ac, emis = .8f) { p, t, _ ->
        val a = t * .7f + i * 1.0472f
        p.tx = cos(a) * 1.15f; p.tz = sin(a) * 1.15f; p.ty = .35f + sin(t * 1.4f + i) * .25f; p.ry = -degOf(a) + 90f
    }
    add(Geo.torus(.95f, .014f), ac, 0f, -.45f, 0f, emis = .9f) { p, t, e -> p.rx = 90f; val s = 1f + .06f * sin(t * 3f) * (1f + e); p.sx = s; p.sy = s }
}

// ---- 14  Calendar Hub: a calendar whose days light up ----
private fun Scene.calendar() {
    val white = rgb(.95f, .98f, 1f); val b = alt(ac)
    add(Geo.box(.75f, .7f, .06f), white, 0f, -.05f, 0f, emis = .12f)
    add(Geo.box(.75f, .17f, .08f), ac, 0f, .5f, .01f, emis = .45f)
    for (sd in listOf(-1f, 1f)) add(Geo.torus(.07f, .02f, 16, 6), b, sd * .35f, .7f, 0f, emis = .8f)
    for (r in 0 until 4) for (c in 0 until 5) {
        val idx = r * 5 + c
        add(Geo.box(.1f, .075f, .02f), ac, (c - 2) * .28f, .22f - r * .27f, .05f, emis = .05f) { p, t, _ ->
            val sel = ((t * .8f).toInt() % 20) == idx
            p.emis = if (sel) .95f + .2f * sin(t * 8f) else .05f
            val s = if (sel) 1.25f else 1f; p.sx = s; p.sy = s
        }
    }
    add(Geo.torus(.22f, .035f, 28, 8), b, .75f, -.65f, .12f, emis = .9f)
    add(Geo.box(.012f, .065f, .01f), white, .75f, -.65f, .14f, emis = 1f) { p, t, _ ->
        p.rz = -t * 60f; p.tx = .75f + sin(t * 1.047f) * .065f; p.ty = -.65f + cos(t * 1.047f) * .065f
    }
    add(Geo.box(.012f, .04f, .01f), white, .75f, -.65f, .14f, emis = 1f) { p, t, _ ->
        p.rz = -t * 5f; p.tx = .75f + sin(t * .0873f) * .04f; p.ty = -.65f + cos(t * .0873f) * .04f
    }
}

// ---- 15  Reminder Center: a ringing bell with sound waves ----
private fun Scene.reminders() {
    val white = rgb(1f, .98f, .92f); val red = rgb(1f, .25f, .3f)
    add(Geo.sphere(.5f, 12, 18), ac, 0f, .25f, 0f, emis = .4f) { p, t, e -> p.rz = sin(t * 9f) * (4f + 10f * e) }
    add(Geo.cyl(.72f, .5f, .5f, 28), ac, 0f, -.15f, 0f, emis = .4f) { p, t, e -> p.rz = sin(t * 9f) * (4f + 10f * e) }
    add(Geo.torus(.72f, .05f, 36, 8), lite(ac, .2f), 0f, -.4f, 0f, emis = .6f).rx = 90f
    add(Geo.sphere(.09f, 8, 10), white, 0f, .78f, 0f, emis = .6f)
    add(Geo.sphere(.16f, 10, 14), white, 0f, -.5f, 0f, emis = .5f) { p, t, e -> p.tx = sin(t * 9f) * .18f * (.4f + e) }
    add(Geo.sphere(.17f, 10, 14), red, .62f, .7f, .2f, emis = .9f) { p, t, _ -> val s = 1f + .18f * sin(t * 6f); p.sx = s; p.sy = s; p.sz = s }
    for (k in 0 until 3) add(Geo.torus(.9f, .012f, 40, 6), ac, 0f, 0f, -.1f, emis = .9f, alpha = .5f) { p, t, _ ->
        val f = (t * .5f + k / 3f) % 1f
        val s = 1f + f * .6f; p.sx = s; p.sy = s; p.alpha = .55f * (1f - f)
    }
}

// ---- 16  Security Center: shield with lock and scan ----
private fun Scene.security() {
    val white = rgb(.95f, .98f, 1f); val b = alt(ac)
    add(Geo.box(.55f, .36f, .11f), ac, 0f, .36f, 0f, emis = .35f)
    add(Geo.cyl(0f, .55f, .85f, 24), ac, 0f, -.425f, 0f, emis = .35f).sz = .2f
    add(Geo.box(.45f, .28f, .02f), lite(darker(ac, .7f), .1f), 0f, .38f, .1f, emis = .2f)
    add(Geo.cyl(0f, .45f, .7f, 24), lite(darker(ac, .7f), .1f), 0f, -.35f, .1f, emis = .2f).sz = .04f
    add(Geo.box(.2f, .16f, .07f), white, 0f, .05f, .16f, emis = .5f)
    add(Geo.torus(.11f, .028f, 24, 6), white, 0f, .2f, .16f, emis = .6f)
    add(Geo.sphere(.03f, 6, 8), rgb(.05f, .08f, .12f), 0f, .05f, .21f)
    add(Geo.torus(1.05f, .015f), ac, emis = .9f) { p, t, e -> p.ry = t * (50f + 50f * e) }
    add(Geo.torus(1.12f, .012f), b, emis = .8f) { p, t, _ -> p.rx = 70f; p.ry = -t * 40f }
    add(Geo.box(.62f, .012f, .1f), ac, 0f, 0f, .2f, emis = 1f, alpha = .6f) { p, t, _ -> p.ty = sin(t * 1.8f) * .65f - .1f }
    for (i in 0 until 5) orbiter(.04f, white, 1.0f, .8f + i * .1f, i * 1.3f, .3f + i * .3f, 1f)
}

// ---- 17  System Analytics: live bars and a rising trend ----
private fun Scene.analytics() {
    val white = rgb(.95f, .98f, 1f); val b = alt(ac)
    add(Geo.box(.95f, .03f, .5f), darker(ac, .5f), 0f, -.78f, 0f, emis = .25f)
    val base = floatArrayOf(.35f, .5f, .45f, .7f, .85f, 1.05f)
    for (i in 0 until 6) {
        add(Geo.box(.1f, .5f, .1f), if (i % 2 == 0) ac else lite(ac, .2f), (i - 2.5f) * .3f, -.6f, 0f, emis = .45f) { p, t, e ->
            val h = base[i] * (.88f + .12f * sin(t * 2f + i)) * (1f + .2f * e)
            p.sy = h; p.ty = -.77f + h / 2f
        }
    }
    add(Geo.cyl(.03f, .03f, 1.97f, 10), white, .05f, .375f, .22f, emis = .8f).rz = -54.3f
    add(Geo.cyl(.1f, 0f, .25f, 12), white, .85f, .95f, .22f, emis = .9f).rz = -54.3f
    for (i in 0 until 5) {
        val f = i / 4f
        add(Geo.sphere(.05f, 8, 10), b, -.75f + 1.6f * f, -.2f + 1.15f * f, .22f, emis = .9f) { p, t, _ ->
            val s = .8f + .7f * maxOf(0f, sin(t * 3f - i * .9f)); p.sx = s; p.sy = s; p.sz = s
        }
    }
    add(Geo.torus(1.2f, .012f), ac, emis = .7f) { p, t, _ -> p.rx = 75f; p.rz = t * 20f }
}

// ------------------------------------------------------------------------------------------------
// glTF / GLB loader: put assets/plan_models/plan_01.glb ... plan_17.glb and that model replaces the built-in scene
// ------------------------------------------------------------------------------------------------

private class GPrim(val pos: FloatArray, val nor: FloatArray, val idx: IntArray, val col: FloatArray, val emis: Float, val alpha: Float)

private object Gltf {
    fun load(assets: AssetManager, path: String, s: Scene): Boolean {
        val bytes = try { assets.open(path).use { it.readBytes() } } catch (e: Exception) { return false }
        return try { parse(bytes, s) } catch (e: Throwable) { Log.e("Plan3D", "glb error $path: $e"); false }
    }

    private fun parse(b: ByteArray, s: Scene): Boolean {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        if (b.size < 20 || bb.getInt(0) != 0x46546C67) return false
        var off = 12
        var json: JSONObject? = null
        var binOff = -1
        while (off + 8 <= b.size) {
            val len = bb.getInt(off); val type = bb.getInt(off + 4)
            if (type == 0x4E4F534A) json = JSONObject(String(b, off + 8, len, Charsets.UTF_8))
            else if (type == 0x004E4942 && binOff < 0) binOff = off + 8
            off += 8 + len
        }
        val j = json ?: return false
        if (binOff < 0 || j.has("extensionsRequired")) return false
        val accessors = j.getJSONArray("accessors")
        val views = j.getJSONArray("bufferViews")
        val nodes = j.optJSONArray("nodes") ?: return false
        val meshes = j.optJSONArray("meshes") ?: return false
        val mats = j.optJSONArray("materials")

        fun readAcc(idx: Int): FloatArray {
            val a = accessors.getJSONObject(idx)
            val view = views.getJSONObject(a.getInt("bufferView"))
            val comps = when (a.getString("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; else -> 1 }
            val ct = a.getInt("componentType"); val count = a.getInt("count")
            val csize = when (ct) { 5126, 5125 -> 4; 5123, 5122 -> 2; else -> 1 }
            var stride = view.optInt("byteStride", 0)
            if (stride == 0) stride = comps * csize
            val start = binOff + view.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val out = FloatArray(count * comps)
            for (i in 0 until count) for (c in 0 until comps) {
                val p = start + i * stride + c * csize
                out[i * comps + c] = when (ct) {
                    5126 -> bb.getFloat(p)
                    5125 -> (bb.getInt(p).toLong() and 0xFFFFFFFFL).toFloat()
                    5123 -> (bb.getShort(p).toInt() and 0xFFFF).toFloat()
                    5122 -> bb.getShort(p).toFloat()
                    5121 -> (bb.get(p).toInt() and 0xFF).toFloat()
                    else -> bb.get(p).toFloat()
                }
            }
            return out
        }

        val prims = ArrayList<GPrim>()

        fun addMesh(mi: Int, m: FloatArray) {
            val prs = meshes.getJSONObject(mi).optJSONArray("primitives") ?: return
            for (pi in 0 until prs.length()) {
                val pr = prs.getJSONObject(pi)
                if (pr.optInt("mode", 4) != 4) continue
                val at = pr.getJSONObject("attributes")
                if (!at.has("POSITION")) continue
                val src = readAcc(at.getInt("POSITION"))
                val n = src.size / 3
                if (n > 65000) { Log.e("Plan3D", "glb primitive has $n vertices, limit is 65000"); continue }
                val idx = if (pr.has("indices")) readAcc(pr.getInt("indices")).let { f -> IntArray(f.size) { f[it].toInt() } } else IntArray(n) { it }
                val pos = FloatArray(n * 3)
                for (i in 0 until n) {
                    val x = src[i * 3]; val y = src[i * 3 + 1]; val z = src[i * 3 + 2]
                    pos[i * 3] = m[0] * x + m[4] * y + m[8] * z + m[12]
                    pos[i * 3 + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
                    pos[i * 3 + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
                }
                val nor = FloatArray(n * 3)
                if (at.has("NORMAL")) {
                    val ns = readAcc(at.getInt("NORMAL"))
                    for (i in 0 until n) {
                        val x = ns[i * 3]; val y = ns[i * 3 + 1]; val z = ns[i * 3 + 2]
                        nor[i * 3] = m[0] * x + m[4] * y + m[8] * z
                        nor[i * 3 + 1] = m[1] * x + m[5] * y + m[9] * z
                        nor[i * 3 + 2] = m[2] * x + m[6] * y + m[10] * z
                    }
                } else {
                    var t = 0
                    while (t + 2 < idx.size) {
                        val a = idx[t]; val b2 = idx[t + 1]; val c = idx[t + 2]
                        val ux = pos[b2 * 3] - pos[a * 3]; val uy = pos[b2 * 3 + 1] - pos[a * 3 + 1]; val uz = pos[b2 * 3 + 2] - pos[a * 3 + 2]
                        val vx = pos[c * 3] - pos[a * 3]; val vy = pos[c * 3 + 1] - pos[a * 3 + 1]; val vz = pos[c * 3 + 2] - pos[a * 3 + 2]
                        val fx = uy * vz - uz * vy; val fy = uz * vx - ux * vz; val fz = ux * vy - uy * vx
                        for (v in intArrayOf(a, b2, c)) { nor[v * 3] += fx; nor[v * 3 + 1] += fy; nor[v * 3 + 2] += fz }
                        t += 3
                    }
                }
                for (i in 0 until n) {
                    val l = sqrt(nor[i * 3] * nor[i * 3] + nor[i * 3 + 1] * nor[i * 3 + 1] + nor[i * 3 + 2] * nor[i * 3 + 2]).coerceAtLeast(0.0001f)
                    nor[i * 3] /= l; nor[i * 3 + 1] /= l; nor[i * 3 + 2] /= l
                }
                var col = floatArrayOf(.8f, .82f, .88f); var alpha = 1f; var emis = 0f
                val mat = if (mats != null && pr.has("material")) mats.optJSONObject(pr.getInt("material")) else null
                if (mat != null) {
                    val base = mat.optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor")
                    if (base != null && base.length() >= 3) {
                        col = floatArrayOf(base.getDouble(0).toFloat(), base.getDouble(1).toFloat(), base.getDouble(2).toFloat())
                        if (base.length() > 3 && mat.optString("alphaMode") == "BLEND") alpha = base.getDouble(3).toFloat()
                    }
                    val em = mat.optJSONArray("emissiveFactor")
                    if (em != null && em.length() >= 3 && (em.getDouble(0) + em.getDouble(1) + em.getDouble(2)) > 0.3) emis = .6f
                }
                prims.add(GPrim(pos, nor, idx, col, emis, alpha))
            }
        }

        fun visit(n: Int, parent: FloatArray) {
            val node = nodes.getJSONObject(n)
            var local = FloatArray(16)
            Matrix.setIdentityM(local, 0)
            if (node.has("matrix")) {
                val mm = node.getJSONArray("matrix")
                for (k in 0 until 16) local[k] = mm.getDouble(k).toFloat()
            } else {
                val t = node.optJSONArray("translation"); val r = node.optJSONArray("rotation"); val sc = node.optJSONArray("scale")
                if (t != null) Matrix.translateM(local, 0, t.getDouble(0).toFloat(), t.getDouble(1).toFloat(), t.getDouble(2).toFloat())
                if (r != null) {
                    val x = r.getDouble(0).toFloat(); val y = r.getDouble(1).toFloat(); val z = r.getDouble(2).toFloat(); val w = r.getDouble(3).toFloat()
                    val rot = FloatArray(16)
                    rot[0] = 1f - 2f * (y * y + z * z); rot[1] = 2f * (x * y + z * w); rot[2] = 2f * (x * z - y * w)
                    rot[4] = 2f * (x * y - z * w); rot[5] = 1f - 2f * (x * x + z * z); rot[6] = 2f * (y * z + x * w)
                    rot[8] = 2f * (x * z + y * w); rot[9] = 2f * (y * z - x * w); rot[10] = 1f - 2f * (x * x + y * y); rot[15] = 1f
                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, local, 0, rot, 0)
                    local = tmp
                }
                if (sc != null) Matrix.scaleM(local, 0, sc.getDouble(0).toFloat(), sc.getDouble(1).toFloat(), sc.getDouble(2).toFloat())
            }
            val world = FloatArray(16)
            Matrix.multiplyMM(world, 0, parent, 0, local, 0)
            if (node.has("mesh")) addMesh(node.getInt("mesh"), world)
            val kids = node.optJSONArray("children")
            if (kids != null) for (k in 0 until kids.length()) visit(kids.getInt(k), world)
        }

        val ident = FloatArray(16)
        Matrix.setIdentityM(ident, 0)
        val scenes = j.optJSONArray("scenes")
        val roots = scenes?.optJSONObject(j.optInt("scene", 0))?.optJSONArray("nodes")
        if (roots != null) for (k in 0 until roots.length()) visit(roots.getInt(k), ident)
        else for (k in 0 until nodes.length()) visit(k, ident)
        if (prims.isEmpty()) return false

        // centre the model and scale it to fit the stage
        var minX = 1e9f; var minY = 1e9f; var minZ = 1e9f; var maxX = -1e9f; var maxY = -1e9f; var maxZ = -1e9f
        for (p in prims) for (i in 0 until p.pos.size / 3) {
            val x = p.pos[i * 3]; val y = p.pos[i * 3 + 1]; val z = p.pos[i * 3 + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
        }
        val ext = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.0001f)
        val k = 2.4f / ext
        val cx = (minX + maxX) / 2f; val cy = (minY + maxY) / 2f; val cz = (minZ + maxZ) / 2f
        for (p in prims) {
            val n = p.pos.size / 3
            val v = FloatArray(n * 6)
            for (i in 0 until n) {
                v[i * 6] = (p.pos[i * 3] - cx) * k; v[i * 6 + 1] = (p.pos[i * 3 + 1] - cy) * k; v[i * 6 + 2] = (p.pos[i * 3 + 2] - cz) * k
                v[i * 6 + 3] = p.nor[i * 3]; v[i * 6 + 4] = p.nor[i * 3 + 1]; v[i * 6 + 5] = p.nor[i * 3 + 2]
            }
            s.add(Mesh(v, ShortArray(p.idx.size) { p.idx[it].toShort() }), p.col, emis = p.emis, alpha = p.alpha)
        }
        return true
    }
}

// ------------------------------------------------------------------------------------------------
// renderer
// ------------------------------------------------------------------------------------------------

private class Plan3DRenderer : GLSurfaceView.Renderer {
    @Volatile private var plan = 1
    @Volatile var mode = 0
    @Volatile var burst = 0f
    @Volatile var backdrop = true
    @Volatile var fx = 0.5f
    @Volatile var fy = 0.5f
    @Volatile var fsize = 0.8f
    @Volatile var dragYaw = 0f
    @Volatile var assets: AssetManager? = null

    private var prog = 0
    private var bgProg = 0
    private var w = 1
    private var h = 1
    private var builtPlan = 0
    private var parts: List<Part> = emptyList()
    private var energy = 0f
    private var yaw = 0f
    private var lastNs = 0L
    private val t0 = System.nanoTime()

    private var uMvp = 0; private var uModel = 0; private var uColor = 0; private var uEmis = 0; private var uAlpha = 0
    private var aPos = 0; private var aNor = 0
    private var bgT = 0; private var bgC = 0; private var bgRes = 0; private var bgE = 0; private var bgPos = 0
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    private var accent = floatArrayOf(.2f, .9f, .9f)

    private val m = FloatArray(16)
    private val world = FloatArray(16)
    private val mvp = FloatArray(16)

    fun setPlan(value: Int) { plan = value.coerceIn(1, 17) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val vs = """
            uniform mat4 uMvp; uniform mat4 uModel;
            attribute vec3 aPos; attribute vec3 aNor;
            varying vec3 vN;
            void main(){ vN=(uModel*vec4(aNor,0.0)).xyz; gl_Position=uMvp*vec4(aPos,1.0); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform vec3 uColor; uniform float uEmis; uniform float uAlpha;
            varying vec3 vN;
            void main(){
              vec3 n=normalize(vN);
              vec3 l=normalize(vec3(-0.45,0.8,1.0));
              float diff=max(dot(n,l),0.0);
              float rim=pow(1.0-clamp(n.z,0.0,1.0),2.0);
              float spec=pow(max(dot(n,normalize(l+vec3(0.0,0.0,1.0))),0.0),24.0);
              vec3 col=uColor*(0.30+0.70*diff)+uColor*rim*0.55+vec3(spec*0.35)+uColor*uEmis;
              gl_FragColor=vec4(col,uAlpha);
            }
        """.trimIndent()
        prog = link(compile(GLES20.GL_VERTEX_SHADER, vs), compile(GLES20.GL_FRAGMENT_SHADER, fs))
        if (prog != 0) {
            uMvp = GLES20.glGetUniformLocation(prog, "uMvp"); uModel = GLES20.glGetUniformLocation(prog, "uModel")
            uColor = GLES20.glGetUniformLocation(prog, "uColor"); uEmis = GLES20.glGetUniformLocation(prog, "uEmis")
            uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha")
            aPos = GLES20.glGetAttribLocation(prog, "aPos"); aNor = GLES20.glGetAttribLocation(prog, "aNor")
        }
        val bvs = """
            attribute vec2 aP; varying vec2 vUv;
            void main(){ vUv=aP*0.5+0.5; gl_Position=vec4(aP,0.0,1.0); }
        """.trimIndent()
        val bfs = """
            precision mediump float;
            uniform float uT; uniform vec3 uC; uniform vec2 uRes; uniform float uE;
            varying vec2 vUv;
            float h21(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
            void main(){
              vec2 uv=vUv;
              float asp=uRes.x/uRes.y;
              vec2 p=(uv-vec2(0.5,0.5))*vec2(asp,1.0);
              float r=length(p);
              float glow=exp(-r*r*5.0)*(0.55+0.35*uE+0.10*sin(uT*2.0));
              float a1=sin(p.x*4.0+uT*0.7+sin(p.y*3.0+uT*0.5)*1.5);
              float rib=smoothstep(0.75,1.0,a1)*0.18*(1.0-r);
              float ring=0.0;
              for(int k=0;k<3;k++){
                float ph=fract(uT*0.18+float(k)/3.0);
                ring+=smoothstep(0.012,0.0,abs(r-ph*0.9))*(1.0-ph);
              }
              ring*=(0.35+0.5*uE);
              float horizon=0.30;
              float fl=0.0;
              if(uv.y<horizon){
                float z=1.0/(horizon-uv.y+0.06);
                float gx=abs(fract((uv.x-0.5)*z*1.6+0.5)-0.5);
                float gz=abs(fract(z*0.9-uT*0.5)-0.5);
                fl=(smoothstep(0.03,0.0,gx)+smoothstep(0.03,0.0,gz))*(1.0-(horizon-uv.y)/horizon)*0.55;
              }
              vec2 cell=uv*vec2(asp*38.0,38.0);
              float s=h21(floor(cell));
              vec2 f=fract(cell)-0.5;
              float star=step(0.985,s)*smoothstep(0.18,0.0,length(f))*(0.5+0.5*sin(uT*3.0+s*60.0));
              vec3 col=vec3(0.01,0.03,0.05)+uC*(glow+rib+ring+fl)+vec3(star*0.8);
              float edge=smoothstep(0.0,0.06,uv.x)*smoothstep(0.0,0.06,1.0-uv.x)*smoothstep(0.0,0.06,uv.y)*smoothstep(0.0,0.06,1.0-uv.y);
              gl_FragColor=vec4(col,0.90*edge);
            }
        """.trimIndent()
        bgProg = link(compile(GLES20.GL_VERTEX_SHADER, bvs), compile(GLES20.GL_FRAGMENT_SHADER, bfs))
        if (bgProg != 0) {
            bgT = GLES20.glGetUniformLocation(bgProg, "uT"); bgC = GLES20.glGetUniformLocation(bgProg, "uC")
            bgRes = GLES20.glGetUniformLocation(bgProg, "uRes"); bgE = GLES20.glGetUniformLocation(bgProg, "uE")
            bgPos = GLES20.glGetAttribLocation(bgProg, "aP")
        }
        builtPlan = 0
        lastNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        w = width.coerceAtLeast(1); h = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, w, h)
    }

    private fun build(n: Int) {
        val c = PlanCatalog.get(n).accent
        accent = floatArrayOf(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        val s = Scene(accent)
        val am = assets
        val glb = "plan_models/plan_" + n.toString().padStart(2, '0') + ".glb"
        if (am == null || !Gltf.load(am, glb, s)) when (n) {
            1 -> s.neural()
            2 -> s.voice()
            3 -> s.vault()
            4 -> s.soul()
            5 -> s.vision()
            6 -> s.web()
            7 -> s.code()
            8 -> s.files()
            9 -> s.tasks()
            10 -> s.notes()
            11 -> s.briefing()
            12 -> s.translate()
            13 -> s.research()
            14 -> s.calendar()
            15 -> s.reminders()
            16 -> s.security()
            else -> s.analytics()
        }
        parts = s.parts
        builtPlan = n
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (prog == 0) return
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastNs = now
        val t = (now - t0) / 1_000_000_000f

        if (builtPlan != plan) build(plan)

        // the AI state drives the energy of the whole scene
        val target = when (mode) { 1 -> .45f; 2 -> 1f; 3 -> .7f; else -> 0f }
        burst = maxOf(0f, burst - dt * 1.4f)
        energy += (target + burst * .8f - energy) * minOf(1f, dt * 4f)
        yaw += dt * (14f + 40f * energy)

        if (backdrop && bgProg != 0) drawBackdrop(t)

        for (p in parts) p.anim?.invoke(p, t, energy)

        val proj = FloatArray(16); val view = FloatArray(16); val adj = FloatArray(16)
        val proj2 = FloatArray(16); val pv = FloatArray(16); val g = FloatArray(16)
        Matrix.perspectiveM(proj, 0, 42f, w.toFloat() / h.toFloat(), .1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 4.3f, 0f, 0f, 0f, 0f, 1f, 0f)
        // place and size the object inside the view
        val k = ((fsize * minOf(w, h).toFloat()) / h.toFloat()) / 0.79f
        Matrix.setIdentityM(adj, 0)
        Matrix.translateM(adj, 0, fx * 2f - 1f, 1f - fy * 2f, 0f)
        Matrix.scaleM(adj, 0, k, k, 1f)
        Matrix.multiplyMM(proj2, 0, adj, 0, proj, 0)
        Matrix.multiplyMM(pv, 0, proj2, 0, view, 0)

        val bob = .04f * sin(t * 1.6f)
        val sc = 1f + .03f * energy * sin(t * 9f) + burst * .1f
        Matrix.setIdentityM(g, 0)
        Matrix.translateM(g, 0, 0f, bob, 0f)
        Matrix.rotateM(g, 0, 14f, 1f, 0f, 0f)
        Matrix.rotateM(g, 0, yaw + dragYaw, 0f, 1f, 0f)
        Matrix.scaleM(g, 0, sc, sc, sc)

        GLES20.glUseProgram(prog)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNor)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        for (pass in 0..1) {
            GLES20.glDepthMask(pass == 0)
            for (p in parts) {
                val solid = p.alpha >= .99f
                if (solid != (pass == 0)) continue
                if (!solid && p.alpha <= .01f) continue
                drawPart(p, g, pv)
            }
        }
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNor)
    }

    private fun drawPart(p: Part, g: FloatArray, pv: FloatArray) {
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, p.tx, p.ty, p.tz)
        Matrix.rotateM(m, 0, p.ry, 0f, 1f, 0f)
        Matrix.rotateM(m, 0, p.rx, 1f, 0f, 0f)
        Matrix.rotateM(m, 0, p.rz, 0f, 0f, 1f)
        Matrix.rotateM(m, 0, p.spin, 0f, 1f, 0f)
        Matrix.scaleM(m, 0, p.sx, p.sy, p.sz)
        Matrix.multiplyMM(world, 0, g, 0, m, 0)
        Matrix.multiplyMM(mvp, 0, pv, 0, world, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, world, 0)
        GLES20.glUniform3fv(uColor, 1, p.color, 0)
        GLES20.glUniform1f(uEmis, p.emis)
        GLES20.glUniform1f(uAlpha, p.alpha)
        p.vb.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, p.vb)
        p.vb.position(3)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 24, p.vb)
        p.ib.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, p.count, GLES20.GL_UNSIGNED_SHORT, p.ib)
    }

    private fun drawBackdrop(t: Float) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(bgProg)
        GLES20.glUniform1f(bgT, t % 600f)
        GLES20.glUniform3fv(bgC, 1, accent, 0)
        GLES20.glUniform2f(bgRes, w.toFloat(), h.toFloat())
        GLES20.glUniform1f(bgE, energy.coerceIn(0f, 1.2f))
        quad.position(0)
        GLES20.glEnableVertexAttribArray(bgPos)
        GLES20.glVertexAttribPointer(bgPos, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(bgPos)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compile(type: Int, source: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, source)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("Plan3D", "shader error: " + GLES20.glGetShaderInfoLog(s))
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }

    private fun link(v: Int, f: Int): Int {
        if (v == 0 || f == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("Plan3D", "link error: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return 0
        }
        return p
    }
}
