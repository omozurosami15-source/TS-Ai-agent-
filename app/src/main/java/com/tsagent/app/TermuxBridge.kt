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
        "date" to "date; sleep 5",
        // runbg: starts a .py file in the background and keeps running after this window closes (uses a wake lock)
        "runbg" to """cd ~/storage/downloads/MissSara 2>/dev/null || { echo 'No MissSara folder yet'; sleep 6; exit 1; }; termux-wake-lock 2>/dev/null; nohup python3 "$1" > "$1.log" 2>&1 & echo "Started in background: $1 (log: $1.log)"; sleep 8""",
        // stopbg: stops background runs of a file
        "stopbg" to """pkill -f "python3 $1" && echo "Stopped $1" || echo "Not running: $1"; sleep 8""",
        // runpy: runs a .py or .js file from ~/storage/downloads/MissSara. The name is checked before this runs.
        "runpy" to """cd ~/storage/downloads/MissSara 2>/dev/null || { echo 'No MissSara folder yet'; sleep 6; exit 1; }; case "$1" in *.py) python3 "$1" 2>&1 | tail -n 40;; *.js) node "$1" 2>&1 | tail -n 40;; *) echo 'Not allowed';; esac; echo; sleep 15""",
        // edit: joins the clips in ~/edit/in (sorted), makes a 9:16 1080x1920 video, adds ~/edit/sound.m4a if it exists, saves to ~/edit/out
        "edit" to """cd ~/edit 2>/dev/null || { echo 'Make the folder ~/edit with an in/ folder first'; sleep 6; exit 1; }; mkdir -p out; ls in/*.mp4 2>/dev/null | sort | sed "s/.*/file '&'/" > list.txt; if [ ! -s list.txt ]; then echo 'No clips in ~/edit/in'; sleep 6; exit 1; fi; ffmpeg -y -f concat -safe 0 -i list.txt -vf "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1" -c:v libx264 -preset veryfast -crf 23 -an v.mp4 && N=out/$(date +%Y%m%d_%H%M).mp4; if [ -f sound.m4a ]; then ffmpeg -y -i v.mp4 -stream_loop -1 -i sound.m4a -map 0:v -map 1:a -shortest -c:v copy -c:a aac "${'$'}N"; else mv v.mp4 "${'$'}N"; fi; echo "Saved ${'$'}N"; sleep 8"""
    )

    fun run(ctx: Context, job: String, text: String): String {
        // Editing is paused for now. The code stays here, so it can be turned back on later.
        if (job.lowercase() == "edit") return "Editing is paused for now."
        val script = jobs[job.lowercase()] ?: return "Unknown Termux job. Use animate, gitlog or date."
        val clean = if (job.lowercase() in setOf("runpy", "runbg", "stopbg")) text.trim() else text.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(40).ifBlank { "MISS SARA" }
        if ((job.lowercase() == "runpy" || job.lowercase() == "runbg" || job.lowercase() == "stopbg") && !Code.validName(clean)) return "That file name is not allowed."
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
