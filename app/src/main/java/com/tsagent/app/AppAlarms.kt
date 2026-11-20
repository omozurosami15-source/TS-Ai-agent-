package com.tsagent.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.app.Notification
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.LocalTime

/**
 * The app's own loud prayer alarms. They ring through the alarm channel (alarm volume) and do not use the phone's clock app.
 * Each day at 00:10 the app fetches new times for home and sets the alarms again.
 */
object AppAlarms {
    private const val CHANNEL = "prayer_alarm"
    private const val REFRESH_CODE = 9001
    private val zone = ZoneId.of("Asia/Dhaka")

    private fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ch = NotificationChannel(CHANNEL, "Prayer alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Loud alarms for sehri, prayers and iftar"
            enableVibration(true)
            setSound(alarmSound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        nm.createNotificationChannel(ch)
    }

    /** Sets today's alarms for home (or a named city) and a daily refresh at 00:10. */
    fun scheduleToday(ctx: Context, city: String): String {
        return try {
            channel(ctx)
            val t = Bangladesh.timings(city)
            val fajr = LocalTime.parse(t.getString("Fajr").substringBefore(" "))
            val list = listOf(
                fajr.minusMinutes(10) to "Sehri ends in 10 minutes. Eat now.",
                fajr to "Fajr. Time for Fajr prayer.",
                LocalTime.parse(t.getString("Dhuhr").substringBefore(" ")) to "Dhuhr. Time for prayer.",
                LocalTime.parse(t.getString("Asr").substringBefore(" ")) to "Asr. Time for prayer.",
                LocalTime.parse(t.getString("Maghrib").substringBefore(" ")) to "Iftar time. Maghrib adhan.",
                LocalTime.parse(t.getString("Isha").substringBefore(" ")) to "Isha. Time for prayer."
            )
            val now = LocalDateTime.now(zone)
            var set = 0
            list.forEachIndexed { i, (time, label) ->
                val at = LocalDateTime.of(LocalDate.now(zone), time)
                if (at.isAfter(now)) { arm(ctx, at, i, label); set++ }
            }
            scheduleRefresh(ctx)
            "Prayer alarms set for " + Bangladesh.placeName(city) + ": " + set + " alarm(s) left today. Loud alarm sound."
        } catch (e: Exception) {
            "Could not set the alarms: " + e.message
        }
    }

    private fun arm(ctx: Context, at: LocalDateTime, code: Int, label: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, AlarmRing::class.java).putExtra("label", label)
        val pi = PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ms = at.atZone(zone).toInstant().toEpochMilli()
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun scheduleRefresh(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, AlarmRing::class.java).putExtra("refresh", true)
        val pi = PendingIntent.getBroadcast(ctx, REFRESH_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next = LocalDate.now(zone).plusDays(1).atTime(0, 10).atZone(zone).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    /** Shows the alarm as a loud notification with the alarm sound. */
    fun ring(ctx: Context, label: String) {
        channel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Miss Sara")
            .setContentText(label)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX)
        nm.notify(label.hashCode(), b.build())
    }
}

/** Fires each prayer alarm, and the daily refresh. */
class AlarmRing : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra("refresh", false)) {
            val pending = goAsync()
            Thread {
                try { AppAlarms.scheduleToday(ctx, "") } finally { pending.finish() }
            }.start()
            return
        }
        AppAlarms.ring(ctx, intent.getStringExtra("label") ?: "Miss Sara")
    }
}
