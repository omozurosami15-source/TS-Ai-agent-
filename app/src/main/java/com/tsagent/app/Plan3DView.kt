package com.tsagent.app

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.Matrix
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Real-time 3D plan object rendered with OpenGL ES 2.0 (depth-tested, lit and continuously rotated). */
class Plan3DView(context: Context) : GLSurfaceView(context) {
    private val scene = Plan3DRenderer()
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setRenderer(scene)
        renderMode = RENDERMODE_CONTINUOUSLY
        scene.setPlan(1)
    }
    fun setPlan(number: Int) { scene.setPlan(number) }
}

private class Plan3DRenderer : GLSurfaceView.Renderer {
    @Volatile private var plan = 1
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var indexCount = 0
    private var angle = 0f
    private var builtPlan = 0
    private var width = 1
    private var height = 1
    private var color = floatArrayOf(.20f, .92f, .88f)
    private val colors = arrayOf(
        floatArrayOf(.18f,.92f,.88f), floatArrayOf(.92f,.30f,.78f), floatArrayOf(.30f,.72f,1f),
        floatArrayOf(.30f,.94f,.66f), floatArrayOf(.40f,.95f,.74f), floatArrayOf(.22f,.76f,1f),
        floatArrayOf(.62f,.42f,1f), floatArrayOf(.32f,.82f,.95f), floatArrayOf(.35f,.95f,.70f),
        floatArrayOf(1f,.72f,.30f), floatArrayOf(.25f,.88f,.90f), floatArrayOf(.95f,.38f,.70f),
        floatArrayOf(.30f,.80f,1f), floatArrayOf(.30f,.88f,.70f), floatArrayOf(1f,.68f,.25f),
        floatArrayOf(.28f,.95f,.78f), floatArrayOf(.45f,.75f,1f)
    )

    fun setPlan(value: Int) { plan = value.coerceIn(1, 17) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val vertex = """
            uniform mat4 uMvp; uniform mat4 uModel;
            attribute vec3 aPosition; attribute vec3 aNormal;
            varying vec3 vNormal; varying vec3 vPosition;
            void main(){ vec4 pos=uModel*vec4(aPosition,1.0); vPosition=pos.xyz; vNormal=mat3(uModel)*aNormal; gl_Position=uMvp*vec4(aPosition,1.0); }
        """.trimIndent()
        val fragment = """
            precision mediump float; uniform vec3 uColor; varying vec3 vNormal; varying vec3 vPosition;
            void main(){ vec3 n=normalize(vNormal); vec3 l=normalize(vec3(-0.45,0.8,1.0));
              float diffuse=max(dot(n,l),0.0); float rim=pow(1.0-max(dot(n,normalize(vec3(0.0,0.0,1.0))),0.0),2.0);
              float light=0.30+0.70*diffuse; vec3 col=uColor*light+uColor*rim*0.62;
              gl_FragColor=vec4(col,0.88); }
        """.trimIndent()
        program = link(compile(GLES20.GL_VERTEX_SHADER, vertex), compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        rebuildMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1); height = h.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return
        if (builtPlan != plan) { rebuildMesh(); builtPlan = plan }
        color = colors[plan - 1]
        angle = (angle + 0.65f) % 360f
        val projection = FloatArray(16)
        val view = FloatArray(16)
        val model = FloatArray(16)
        val tilt = FloatArray(16)
        val viewModel = FloatArray(16)
        val mvp = FloatArray(16)
        Matrix.perspectiveM(projection, 0, 42f, width.toFloat()/height.toFloat(), .1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 4.3f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setRotateM(model, 0, angle, 0f, 1f, 0f)
        Matrix.setRotateM(tilt, 0, 18f, 1f, 0f, 0f)
        Matrix.multiplyMM(model, 0, model, 0, tilt, 0)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)
        GLES20.glUseProgram(program)
        putMatrix("uMvp", mvp); putMatrix("uModel", model)
        val c = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glUniform3fv(c, 1, color, 0)
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val normal = GLES20.glGetAttribLocation(program, "aNormal")
        val vb = vertexBuffer ?: return
        vb.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,24,vb)
        vb.position(3); GLES20.glEnableVertexAttribArray(normal); GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,vb)
        indexBuffer?.position(0)
        indexBuffer?.let { GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,it) }
        GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(normal)
    }

    private fun rebuildMesh() {
        val lat = 32; val lon = 48
        val vertices = ArrayList<Float>(); val indices = ArrayList<Short>()
        val shape = plan - 1
        val lobesA = 2 + (shape % 7)
        val lobesB = 3 + ((shape * 3) % 8)
        val amp = .055 + (shape % 4) * .018
        val sx = .84 + (shape % 3) * .055
        val sy = .82 + ((shape + 1) % 4) * .045
        val sz = .84 + ((shape + 2) % 3) * .06
        for (y in 0..lat) {
            val v = y.toFloat()/lat; val theta = v*PI
            for (x in 0..lon) {
                val u = x.toFloat()/lon; val phi = u*2.0*PI
                val wave = sin(phi*lobesA + theta*(shape%5)).let { it * sin(theta*lobesB) }
                val ridge = cos(theta*(2 + shape%6)) * .035
                val base = .82 + amp*wave + ridge
                val r = base.toFloat()
                val px = (r*sx*sin(theta)*cos(phi)).toFloat(); val py=(r*sy*cos(theta)).toFloat(); val pz=(r*sz*sin(theta)*sin(phi)).toFloat()
                val len=sqrt(px*px+py*py+pz*pz).coerceAtLeast(.001f)
                vertices.add(px);vertices.add(py);vertices.add(pz);vertices.add(px/len);vertices.add(py/len);vertices.add(pz/len)
            }
        }
        for (y in 0 until lat) for (x in 0 until lon) {
            val a=(y*(lon+1)+x).toShort(); val b=(a.toInt()+lon+1).toShort()
            indices.add(a);indices.add(b);indices.add((a.toInt()+1).toShort())
            indices.add((a.toInt()+1).toShort());indices.add(b);indices.add((b.toInt()+1).toShort())
        }
        vertexBuffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { vertices.forEach { put(it) }; position(0) }
        indexBuffer=ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { indices.forEach { put(it) }; position(0) }
        indexCount=indices.size
    }

    private fun compile(type:Int, source:String):Int { val s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s); return s }
    private fun link(v:Int,f:Int):Int { val p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p); return p }
    private fun putMatrix(name:String,m:FloatArray){ val loc=GLES20.glGetUniformLocation(program,name); GLES20.glUniformMatrix4fv(loc,1,false,m,0) }
}
