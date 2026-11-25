package com.tsagent.app

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Goes to the Inbox / Messages tab of the app on screen (or of a named app), with no AI call.
 * It looks for a button whose text or description says Inbox, Messages, Chats or Direct.
 */
object Inbox {
    private val words = listOf("inbox", "messages", "message", "chats", "direct", "ইনবক্স", "মেসেজ")

    fun open(ctx: Context, appName: String): String {
        val svc = AgentService.instance ?: return "Accessibility is off. Turn it on in Settings first."
        if (appName.isNotBlank()) {
            val res = CommandRunner.openApp(ctx, appName)
            if (!res.startsWith("Opened")) return res
            Thread.sleep(2000)
        }
        for (k in 0 until 8) {
            val root = svc.rootInActiveWindow
            if (root != null) {
                val hit = find(root)
                if (hit != null && hit.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return "Opened the inbox."
                }
                // a parent of a matching label may be the clickable one
                val parent = findLabel(root)?.let { clickableParent(it) }
                if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Opened the inbox."
            }
            Thread.sleep(500)
        }
        return "Could not find the inbox button on this screen."
    }

    private fun matches(n: AccessibilityNodeInfo): Boolean {
        val t = (n.text?.toString() ?: "").lowercase()
        val d = (n.contentDescription?.toString() ?: "").lowercase()
        return words.any { w -> (t.isNotBlank() && t == w) || (d.isNotBlank() && d == w) || d.contains(w) }
    }

    private fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isClickable && matches(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun findLabel(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (matches(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun clickableParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        for (i in 0 until 4) {
            p = p?.parent ?: return null
            if (p.isClickable) return p
        }
        return null
    }
}
