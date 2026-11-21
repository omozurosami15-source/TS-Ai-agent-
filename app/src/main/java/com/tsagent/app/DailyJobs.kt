package com.tsagent.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Timed jobs: 08:00 and 19:00 (Dhaka time). Each time the phone's own agent runs the job goal, even if the app was closed.
 * The job runs everything on the phone: story, video, download, and YouTube upload with title, description and hashtags.
 */
object DailyJobs {
    val GOAL = "ai ChatGPT অ্যাপে New Story লিখে উত্তর নাও, তারপর Kling অ্যাপে সেই গল্প দিয়ে ভিডিও বানিয়ে ফোনে ডাউনলোড করো, তারপর YouTube অ্যাপ খুলে ভিডিওটা আপলোড করো, ChatGPT-র দেওয়া টাইটেল, বিবরণ ও হ্যাশট্যাগ বসাও, আর প্রকাশ করো"
    private val zone = ZoneId.of("Asia/Dhaka")
    private val times = listOf(LocalTime.of(8, 0), LocalTime.of(19, 0))

    fun on(ctx: Context): String {
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putBoolean("daily_on", true).apply()
        for ((i, t) in times.withIndex()) arm(ctx, i, nextAt(t))
        return "Daily job is on: 08:00 and 19:00 (Dhaka time). It will run even if the app is closed."
    }

    fun off(ctx: Context): String {
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putBoolean("daily_on", false).apply()
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in times.indices) am.cancel(pi(ctx, i))
        return "Daily job is off."
    }

    fun isOn(ctx: Context): Boolean = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("daily_on", false)

    private fun nextAt(t: LocalTime): LocalDateTime {
        val now = LocalDateTime.now(zone)
        var at = LocalDateTime.of(LocalDate.now(zone), t)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at
    }

    private fun pi(ctx: Context, i: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, 7000 + i,
            Intent(ctx, DailyJobRing::class.java).putExtra("slot", i),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(ctx: Context, i: Int, at: LocalDateTime) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ms = at.atZone(zone).toInstant().toEpochMilli()
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi(ctx, i))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi(ctx, i))
        }
    }

    /** Runs the job now (called by the alarm), then sets the next one. */
    /** Shows a short status line as a notification, so a daily run never fails silently. */
    fun notify(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(android.app.NotificationChannel("daily_job", "Daily job", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        }
        val b = if (android.os.Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, "daily_job") else android.app.Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Miss Sara daily job").setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
        nm.notify(("daily" + text).hashCode(), b.build())
    }

    fun fire(ctx: Context, slot: Int) {
        if (!isOn(ctx)) return
        arm(ctx, slot, nextAt(times[slot]))
        if (!Llm.configured(ctx)) { notify(ctx, "Did not start: add your Gemini key in Settings (AI KEYS)."); return }
        if (AgentService.instance == null) { notify(ctx, "Did not start: turn on Accessibility for Miss Sara, then run again."); return }
        notify(ctx, "Started: ChatGPT first, then Kling, then YouTube.")
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        // every step message from the agent is shown as a status line
        handler.postDelayed({ Agent(ctx) { msg -> notify(ctx, msg) }.start(GOAL) }, 3000)
    }
}

class DailyJobRing : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        DailyJobs.fire(ctx, intent.getIntExtra("slot", 0))
    }
}
