package com.tsagent.app

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Ramadan start, prayer times and loud alarms. Times come from Aladhan (free, no key). Alarms go to the phone's own clock app. */
object Ramadan {
    private val dmy = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    /** Days until the next Ramadan (1 Ramadan) and that date. */
    fun daysUntil(): String {
        return try {
            val today = LocalDate.now()
            val h = JSONObject(get("https://api.aladhan.com/v1/gToH/" + today.format(dmy)))
                .getJSONObject("data").getJSONObject("hijri")
            val year = h.getInt("year")
            val month = h.getJSONObject("month").getInt("number")
            if (month == 9) return "Ramadan is on now."
            var start = gregorianOf(year)
            if (!start.isAfter(today)) start = gregorianOf(year + 1)
            "Ramadan starts on " + start.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) +
                " (in " + java.time.temporal.ChronoUnit.DAYS.between(today, start) + " days)."
        } catch (e: Exception) {
            "Ramadan date is not available right now: " + e.message
        }
    }

    private fun gregorianOf(hijriYear: Int): LocalDate {
        val j = JSONObject(get("https://api.aladhan.com/v1/hToG/01-09-$hijriYear"))
        return LocalDate.parse(j.getJSONObject("data").getJSONObject("gregorian").getString("date"), dmy)
    }

    /** Sets loud alarms in the phone's clock for today's prayers, sehri end and iftar. */
    fun setTodayAlarms(ctx: Context, city: String): String {
        return try {
            val c = Bangladesh.placeName(city)
            val t = Bangladesh.timings(city)
            val fajr = hm(t.getString("Fajr"))
            val sehriEnd = fajr.minusMinutes(10)
            val list = listOf(
                Triple(sehriEnd, "Sehri ends in 10 minutes. Eat now.", "sehri"),
                Triple(fajr, "Fajr adhan. Time for Fajr.", "fajr"),
                Triple(hm(t.getString("Dhuhr")), "Dhuhr time.", "dhuhr"),
                Triple(hm(t.getString("Asr")), "Asr time.", "asr"),
                Triple(hm(t.getString("Maghrib")), "Iftar time. Maghrib adhan.", "iftar"),
                Triple(hm(t.getString("Isha")), "Isha time.", "isha")
            )
            for ((time, label, _) in list) {
                ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "Alarms set for today in $c: sehri end, Fajr, Dhuhr, Asr, Iftar, Isha. Set again each day for new times."
        } catch (e: Exception) {
            "Could not set the alarms: " + e.message
        }
    }

    private fun hm(s: String): java.time.LocalTime {
        val p = s.substringBefore(" ").split(":")
        return java.time.LocalTime.of(p[0].toInt(), p[1].toInt())
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
