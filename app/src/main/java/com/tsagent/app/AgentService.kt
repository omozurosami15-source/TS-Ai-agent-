package com.tsagent.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AgentService : AccessibilityService() {
    companion object { var instance: AgentService? = null }

    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    /** The label each numbered element had when the screen was read, so a stale number is never clicked. */
    private var labels: List<String> = emptyList()
    val hud by lazy { HudController(this) }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null; hud.hide()
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putBoolean("acc_lost", true).apply()
        return super.onUnbind(intent)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Numbered list of what is on screen, for the AI to read. */
<<<<<<< HEAD
=======
    /** Package name of the app on screen now (null if unknown). Used to wait until a launched app is up. */
    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

>>>>>>> 26712bef9165d438e019f5218df1752f7d2450eb
    fun dumpScreen(): String {
        val root = rootInActiveWindow ?: return "(screen unavailable)"
        // While TS Agent itself is on screen, the agent must not tap or type into it or press Home/Back.
        if (root.packageName == packageName) { nodes = emptyList(); labels = emptyList(); return "(TS Agent is on screen. Do not act on it.)" }
        val list = mutableListOf<AccessibilityNodeInfo>()
        val labelList = mutableListOf<String>()
        val sb = StringBuilder("app: ${root.packageName}\n")
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || list.size >= 40 || !n.isVisibleToUser) return
            val label = if (n.isPassword) "(hidden)" else (n.text ?: n.contentDescription)?.toString()?.take(60) ?: ""
            if (label.isNotBlank() || n.isClickable || n.isEditable) {
                val kind = when { n.isEditable -> "input"; n.isClickable -> "button"; else -> "text" }
                sb.append("[${list.size}] $kind: $label\n")
                list.add(n)
                labelList.add(label)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        nodes = list
        labels = labelList
        return sb.toString()
    }

    /** Sara Watch: true when this screen must not be sent to the AI (a password field is visible, or the app is on the block list). */
    fun isPrivateScreen(blocked: Set<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() in blocked) return true
        var found = false
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || found || depth > 30) return
            if (n.isPassword && n.isVisibleToUser) { found = true; return }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return found
    }

    /** Sara Watch: short plain text of what is on screen. Does not touch the numbered list the agent taps from. */
    fun watchText(): String {
        val root = rootInActiveWindow ?: return "(screen unavailable)"
        val sb = StringBuilder("app: ${root.packageName}\n")
        var count = 0
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count >= 60 || sb.length > 900 || !n.isVisibleToUser) return
            if (!n.isPassword) {
                val t = (n.text ?: n.contentDescription)?.toString()?.take(80).orEmpty()
                if (t.isNotBlank()) { sb.append(t).append('\n'); count++ }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString()
    }

    /** Sara Watch: blocking (call from a background thread). A small JPEG of the screen, or null (Android 11+ only; secure screens fail or come out black). */
    fun screenshotJpeg(): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        var out: ByteArray? = null
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        r.hardwareBuffer.close()
                        if (soft != null) out = Snap.jpeg(soft, 1024, 65)
                    } catch (e: Exception) {}
                    latch.countDown()
                }
                override fun onFailure(errorCode: Int) { latch.countDown() }
            })
        } catch (e: Exception) { return null }
        latch.await(4, TimeUnit.SECONDS)
        return out
    }

    /** True when the element with this number still has the same label as when the screen was read. */
    private fun stillSame(i: Int, n: AccessibilityNodeInfo): Boolean {
        val was = labels.getOrNull(i) ?: return true
        if (was.isBlank()) return true
        val now = (n.text ?: n.contentDescription)?.toString()?.take(60) ?: ""
        return now == was
    }

    fun tapIndex(i: Int): Boolean {
        val n = nodes.getOrNull(i) ?: return false
        if (!stillSame(i, n)) return false
        val r = Rect()
        n.getBoundsInScreen(r)
        hud.ripple(r.centerX().toFloat(), r.centerY().toFloat())
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return gesture(r.centerX().toFloat(), r.centerY().toFloat())
    }

    fun typeIndex(i: Int, text: String): Boolean {
        val n = nodes.getOrNull(i) ?: return false
        if (!stillSame(i, n)) return false
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return setText(n, text)
    }

    private fun setText(n: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return setText(f, text)
    }

    fun tapAt(x: Float, y: Float): Boolean {
        hud.ripple(x, y)
        return gesture(x, y)
    }

    private fun gesture(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, 50)).build(),
            null, null
        )
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun scrollDown() {
        val m = resources.displayMetrics
        hud.ripple(m.widthPixels / 2f, m.heightPixels * 0.7f)
        val p = Path().apply {
            moveTo(m.widthPixels / 2f, m.heightPixels * 0.7f)
            lineTo(m.widthPixels / 2f, m.heightPixels * 0.3f)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build(),
            null, null
        )
    }

    fun global(action: Int) = performGlobalAction(action)

    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(android.R.id.accessibilityActionImeEnter)
    }

    fun scrollUp() {
        val m = resources.displayMetrics
        hud.ripple(m.widthPixels / 2f, m.heightPixels * 0.3f)
        val p = Path().apply {
            moveTo(m.widthPixels / 2f, m.heightPixels * 0.3f)
            lineTo(m.widthPixels / 2f, m.heightPixels * 0.7f)
        }
        dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build(),
            null, null
        )
    }

    /** Yes/No box drawn over whatever app is open. */
    fun confirm(msg: String, onResult: (Boolean) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xEE020C07.toInt())
                setPadding(40, 40, 40, 40)
            }
            box.addView(TextView(this).apply { text = "[CONFIRM] " + msg; setTextColor(Theme.G); textSize = 15f; typeface = android.graphics.Typeface.MONOSPACE })
            val row = LinearLayout(this)
            fun b(label: String, r: Boolean) = Button(this).apply {
                text = label
                setOnClickListener { wm.removeView(box); onResult(r) }
            }
            row.addView(b("Yes", true))
            row.addView(b("No", false))
            box.addView(row)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM }
            wm.addView(box, lp)
        }
    }
}
