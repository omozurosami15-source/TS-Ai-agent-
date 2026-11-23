package com.tsagent.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * One still picture from the front or back camera: opens the camera, waits about a second for exposure,
 * keeps a single frame in memory, and closes the camera again. Nothing is written to storage.
 */
object CameraSnap {
    private val lock = Any()

    /** Blocking (about 2 to 4 seconds). Never call from the main thread. Returns a small upright JPEG, or null. */
    fun snap(ctx: Context, front: Boolean): ByteArray? = synchronized(lock) { doSnap(ctx, front) }

    private fun doSnap(ctx: Context, front: Boolean): ByteArray? {
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return null
        val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val id = try {
            mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want }
        } catch (e: Exception) { null } ?: return null
        val chars = mgr.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val size = map.getOutputSizes(ImageFormat.YUV_420_888)
            ?.filter { it.width <= 800 && it.height <= 800 }
            ?.maxByOrNull { it.width * it.height } ?: return null
        val rotation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val thread = HandlerThread("sara-cam").also { it.start() }
        val handler = Handler(thread.looper)
        val done = CountDownLatch(1)
        var result: ByteArray? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
        var frames = 0

        reader.setOnImageAvailableListener({ r ->
            try {
                val img = r.acquireLatestImage()
                if (img != null) {
                    try {
                        frames++
                        // the first frames are usually too dark or blurry while auto exposure settles
                        if (frames == 10) { result = toJpeg(img, rotation); done.countDown() }
                    } finally { img.close() }
                }
            } catch (e: Exception) { done.countDown() }
        }, handler)

        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    device = cam
                    try {
                        @Suppress("DEPRECATION")
                        cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                try {
                                    val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(reader.surface)
                                        set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    }.build()
                                    s.setRepeatingRequest(req, null, handler)
                                } catch (e: Exception) { done.countDown() }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { done.countDown() }
                        }, handler)
                    } catch (e: Exception) { done.countDown() }
                }
                override fun onDisconnected(cam: CameraDevice) { done.countDown() }
                override fun onError(cam: CameraDevice, error: Int) { done.countDown() }
            }, handler)
        } catch (e: Exception) { done.countDown() }

        done.await(8, TimeUnit.SECONDS)
        try { session?.stopRepeating() } catch (e: Exception) {}
        try { session?.close() } catch (e: Exception) {}
        try { device?.close() } catch (e: Exception) {}
        try { reader.close() } catch (e: Exception) {}
        thread.quitSafely()
        return result
    }

    private fun toJpeg(img: Image, rotation: Int): ByteArray? {
        val w = img.width
        val h = img.height
        val nv = ByteArray(w * h * 3 / 2)
        val yp = img.planes[0]
        val up = img.planes[1]
        val vp = img.planes[2]
        var pos = 0
        val yb = yp.buffer
        for (row in 0 until h) { yb.position(row * yp.rowStride); yb.get(nv, pos, w); pos += w }
        val ub = up.buffer
        val vb = vp.buffer
        for (row in 0 until h / 2) for (col in 0 until w / 2) {
            nv[pos++] = vb.get(row * vp.rowStride + col * vp.pixelStride)
            nv[pos++] = ub.get(row * up.rowStride + col * up.pixelStride)
        }
        val bo = ByteArrayOutputStream()
        YuvImage(nv, ImageFormat.NV21, w, h, null).compressToJpeg(Rect(0, 0, w, h), 80, bo)
        val raw = bo.toByteArray()
        val bm = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val up2 = if (rotation != 0) android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else bm
        return Snap.jpeg(up2, 640, 70)
    }
}
