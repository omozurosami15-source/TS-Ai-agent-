package com.tsagent.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Your Telegram channel. Opens it and posts a message into it, with no AI call. */
object TelegramChannel {
    const val DOMAIN = "ts_official_top"
    private const val PKG = "org.telegram.messenger"

    fun open(ctx: Context): String {
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$DOMAIN"))
                .setPackage(PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opened the Telegram channel."
        } catch (e: Exception) {
            "Telegram is not installed or could not open: " + e.message
        }
    }

    /** Opens the channel, types the text into the message box, and taps Send. */
    fun post(ctx: Context, text: String): String {
        if (text.isBlank()) return "Tell me what to post."
        open(ctx)
        Thread {
            Thread.sleep(2500)
            val svc = AgentService.instance ?: return@Thread
            for (k in 0 until 8) {
                val root = svc.rootInActiveWindow
                if (root != null) {
                    val box = findEditable(root)
                    if (box != null) {
                        box.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        }
                        box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        Thread.sleep(400)
                        val sendRoot = svc.rootInActiveWindow
                        val send = sendRoot?.let { findSend(it) }
                        send?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return@Thread
                    }
                }
                Thread.sleep(500)
            }
        }.start()
        return "Posting to the Telegram channel."
    }

    private fun findEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isEditable || (n.className?.toString()?.contains("EditText") == true)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val d = (n.contentDescription?.toString() ?: "").lowercase()
            val t = (n.text?.toString() ?: "").lowercase()
            if ((d.contains("send") || t == "send" || d.contains("post")) && n.isClickable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return null
    }
}
