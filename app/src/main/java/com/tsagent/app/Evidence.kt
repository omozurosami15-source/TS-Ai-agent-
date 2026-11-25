package com.tsagent.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps evidence on the phone only: a screenshot (if available) and a note with the time.
 * Nothing is sent anywhere. You decide what to do with it.
 */
object Evidence {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "evidence").apply { mkdirs() }
    private fun log(ctx: Context): File = File(dir(ctx), "log.txt")
    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date())

    fun save(ctx: Context, note: String): String {
        val when_ = stamp()
        var picture = "no screenshot"
        val jpg = AgentService.instance?.screenshotJpeg()
        if (jpg != null) {
            val f = File(dir(ctx), "shot_" + System.currentTimeMillis() + ".jpg")
            f.writeBytes(jpg)
            picture = f.name
        }
        log(ctx).appendText("[" + when_ + "] " + note.trim() + " | " + picture + "\n")
        return "Saved evidence at $when_ (" + picture + ")."
    }

    fun list(ctx: Context): String {
        val f = log(ctx)
        if (!f.exists()) return "No evidence saved yet."
        val lines = f.readLines()
        return "Evidence saved: " + lines.size + " item(s). Last: " + lines.takeLast(3).joinToString(" / ")
    }

    /** Text of all notes, for drafting a report on your own. */
    fun notes(ctx: Context): String = log(ctx).takeIf { it.exists() }?.readText()?.takeLast(3000) ?: ""
}
