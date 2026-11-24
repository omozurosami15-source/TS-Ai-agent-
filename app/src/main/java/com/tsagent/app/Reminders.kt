package com.tsagent.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Gentle daily notifications, for example "Have you eaten?". Off by default, a few per day, you choose times and words. */
object Reminders {
    const val DEFAULT_LINES = "13:30 Have you eaten, {call}? Take a short break and eat something.\n21:00 Dinner time, {call}. Did you eat?\n23:30 It is late, {call}. Time to rest soon. Sleep well."
    private const val MAX = 6

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("rem_on", false)
    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("rem_on", on).apply()
        if (on) scheduleAll(ctx) else cancelAll(ctx)
    }

    data class Slot(val hour: Int, val minute: Int, val text: String)

    fun slots(ctx: Context): List<Slot> {
        val raw = prefs(ctx).getString("rem_lines", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_LINES
        return raw.lines().mapNotNull { line ->
            val m = Regex("^(\\d{1,2}):(\\d{2})\\s+(.+)$").matchEntire(line.trim()) ?: return@mapNotNull null
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) Slot(h, mi, m.groupValues[3]) else null
        }.take(MAX)
    }

    private fun pending(ctx: Context, i: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100 + i, Intent(ctx, ReminderReceiver::class.java).putExtra("slot", i),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun nextTime(s: Slot): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.hour); set(Calendar.MINUTE, s.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleAll(ctx: Context) {
        if (!enabled(ctx)) return
        cancelAll(ctx)
        slots(ctx).indices.forEach { scheduleOne(ctx, it) }
    }

    fun scheduleOne(ctx: Context, i: Int) {
        val s = slots(ctx).getOrNull(i) ?: return
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(s), pending(ctx, i))
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX) am.cancel(pending(ctx, i))
    }

    /** The pet name is used only when companion mode is on and the owner is unlocked. */
    fun message(ctx: Context, s: Slot): String {
        val p = prefs(ctx)
        val pet = if (p.getBoolean("comp_on", false) && Entitlements.companion(ctx) && OwnerAuth.unlocked(ctx))
<<<<<<< HEAD
            if (p.getBoolean("ramadan_mode", false)) "" else p.getString("comp_call", "")?.trim().orEmpty().ifBlank { "" } else ""
=======
            p.getString("comp_call", "")?.trim().orEmpty().ifBlank { "jaan" } else ""
>>>>>>> 70ef1c274ce2bdca019764a62eb3127290033e2b
        return if (pet.isNotEmpty()) s.text.replace("{call}", pet)
        else s.text.replace(", {call}", "").replace(" {call}", "").replace("{call}", "")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Reminders.enabled(ctx)) return
        val i = intent.getIntExtra("slot", -1)
        val s = Reminders.slots(ctx).getOrNull(i) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 9, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(200 + i, Notification.Builder(ctx, "reminders")
            .setContentTitle("TS Agent")
            .setContentText(Reminders.message(ctx, s))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
        Reminders.scheduleOne(ctx, i)   // schedule the same slot for tomorrow
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.scheduleAll(ctx)
    }
}
