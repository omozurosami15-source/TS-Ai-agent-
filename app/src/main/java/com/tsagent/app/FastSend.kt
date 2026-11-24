package com.tsagent.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URLEncoder

/**
 * Fast WhatsApp message: opens the chat with the text already filled in, then taps Send.
 * No AI call is used, so it takes a few seconds instead of minutes.
 */
object FastSend {
    fun whatsapp(ctx: Context, number: String, text: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 8) return "Give a full phone number with the country code."
        val url = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(text, "UTF-8")
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage("com.whatsapp")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            Thread {
                Thread.sleep(2000)
                clickSend()
            }.start()
            "Opening WhatsApp and sending."
        } catch (e: Exception) {
            "WhatsApp is not installed or could not open: " + e.message
        }
    }

    /** Finds a Send button on the screen and clicks it. Tries a few times while the app loads. */
    private fun clickSend() {
        val svc = AgentService.instance ?: return
        for (k in 0 until 6) {
            val root = svc.rootInActiveWindow
            if (root != null) {
                val nodes = root.findAccessibilityNodeInfosByText("Send")
                val btn = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()?.parent
                if (btn != null && btn.isClickable) {
                    if (btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                }
            }
            Thread.sleep(500)
        }
    }
}
