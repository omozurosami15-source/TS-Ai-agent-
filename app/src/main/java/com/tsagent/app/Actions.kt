package com.tsagent.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Everyday phone abilities the AI can use. They open a pre-filled screen of the normal app and you tap the final
 * button yourself, so nothing is sent or saved behind your back (except saving a file to Downloads).
 */
object Actions {
    private fun launch(ctx: Context, i: Intent): String =
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened" } catch (e: Exception) { "failed: ${e.message}" }

    private fun phoneOk(n: String) = Regex("^[+0-9 ()-]{3,20}$").matches(n.trim())

    fun addEvent(ctx: Context, title: String, start: String, minutes: Int, notes: String): String {
        val t = try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(start)?.time } catch (e: Exception) { null }
            ?: return "invalid start time, use yyyy-MM-ddTHH:mm"
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, notes)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, t)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, t + minutes.coerceIn(5, 1440) * 60_000L)
        return launch(ctx, i) + ", the user reviews and taps Save"
    }

    fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "invalid time"
        return launch(ctx, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    fun setTimer(ctx: Context, seconds: Int, label: String): String {
        if (seconds !in 1..86400) return "invalid length"
        return launch(ctx, Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    fun dial(ctx: Context, number: String): String =
        if (!phoneOk(number)) "invalid number" else launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.trim()))))

    fun draftSms(ctx: Context, number: String, text: String): String =
        if (!phoneOk(number)) "invalid number"
        else launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number.trim()))).putExtra("sms_body", text)) + ", the user taps Send"

    fun draftEmail(ctx: Context, to: String, subject: String, body: String): String =
        launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to.trim()))).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)) + ", the user taps Send"

    /** Saves text to Download/TS-Agent (Android 10+). */
    fun saveFile(ctx: Context, name: String, content: String): String {
        if (Build.VERSION.SDK_INT < 29) return "saving files needs Android 10 or newer"
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "note.txt" }
        val mime = when (safe.substringAfterLast('.', "txt").lowercase()) {
            "md" -> "text/markdown"; "csv" -> "text/csv"; "html" -> "text/html"; "json" -> "application/json"; else -> "text/plain"
        }
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safe)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/TS-Agent")
        }
        return try {
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return "could not create the file"
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            "saved Download/TS-Agent/$safe"
        } catch (e: Exception) { "failed: ${e.message}" }
    }
}
