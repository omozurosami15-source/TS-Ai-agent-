package com.tsagent.app

import android.content.Context
import android.content.Intent

/**
 * Runs a few fixed, safe Termux jobs. The AI picks a job name and a short text; it never sends free shell code.
 * Needs: Termux with allow-external-apps=true in ~/.termux/termux.properties, and the RUN_COMMAND permission granted.
 */
object TermuxBridge {
    private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"

    private val jobs: Map<String, String> = mapOf(
        // typed-out animation of the given text (letters, digits and spaces only, 40 max)
        "animate" to "T=\"\$1\"; for ((i=0;i<=\${#T};i++)); do printf \"\\r\${T:0:\$i}\"; sleep 0.08; done; echo; sleep 5",
        // last 10 commits of the Miss Sara project
        "gitlog" to "cd ~/tsagent 2>/dev/null && git log --oneline -10; sleep 8",
        // date and time on the phone
        "date" to "date; sleep 5"
    )

    fun run(ctx: Context, job: String, text: String): String {
        val script = jobs[job.lowercase()] ?: return "Unknown Termux job. Use animate, gitlog or date."
        val clean = text.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(40).ifBlank { "MISS SARA" }
        return try {
            val args = arrayOf("-c", script, "_", clean)
            val intent = Intent("com.termux.RUN_COMMAND")
                .setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            ctx.startService(intent)
            "Termux job $job started."
        } catch (e: Exception) {
            "Could not start Termux. Install Termux, set allow-external-apps=true, and allow the permission. " + e.message
        }
    }
}
