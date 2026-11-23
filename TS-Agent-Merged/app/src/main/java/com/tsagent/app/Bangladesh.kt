package com.tsagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Bangladesh facts from free public sources that need no API key. */
object Bangladesh {
    /** Home: Bejura, Madhabpur, Habiganj, Sylhet (from the phone's location, about 90 m accuracy). */
    const val HOME_LAT = 24.1461799
    const val HOME_LON = 91.3405391
    const val HOME_NAME = "Bejura, Madhabpur, Habiganj"

    private val homeWords = listOf("বাড়ি", "home", "bejura", "বেজুরা", "madhabpur", "মাধবপুর", "habiganj", "হবিগঞ্জ")

    /** Prayer timings for home when no city is named, or when the name is a home word. Otherwise for the named city. */
    fun timings(city: String): JSONObject {
        val c = city.trim()
        val isHome = c.isEmpty() || homeWords.any { c.lowercase().contains(it) }
        val url = if (isHome) {
            "https://api.aladhan.com/v1/timings?latitude=$HOME_LAT&longitude=$HOME_LON&method=1"
        } else {
            "https://api.aladhan.com/v1/timingsByCity?city=" + URLEncoder.encode(c, "UTF-8") + "&country=Bangladesh&method=1"
        }
        return JSONObject(get(url)).getJSONObject("data").getJSONObject("timings").let { JSONObject(it.toString()) }
    }

    fun placeName(city: String): String {
        val c = city.trim()
        val isHome = c.isEmpty() || homeWords.any { c.lowercase().contains(it) }
        return if (isHome) HOME_NAME else c
    }

    /** Dollar to taka rate, from open.er-api.com (free, no key). */
    fun dollarRate(): String {
        return try {
            val j = JSONObject(get("https://open.er-api.com/v6/latest/USD"))
            val bdt = j.getJSONObject("rates").getDouble("BDT")
            "1 USD = " + String.format("%.2f", bdt) + " BDT (updated: " + j.optString("time_last_update_utc") + ")"
        } catch (e: Exception) {
            "Dollar rate is not available right now: " + e.message
        }
    }

    /** Prayer times from Aladhan (free, no key). Method 1 is the Karachi method used widely in Bangladesh. */
    fun prayerTimes(city: String): String {
        return try {
            val t = timings(city)
            "Prayer times in " + placeName(city) + ": Fajr " + t.getString("Fajr") + ", Dhuhr " + t.getString("Dhuhr") +
                ", Asr " + t.getString("Asr") + ", Maghrib " + t.getString("Maghrib") + ", Isha " + t.getString("Isha")
        } catch (e: Exception) {
            "Prayer times are not available right now: " + e.message
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
