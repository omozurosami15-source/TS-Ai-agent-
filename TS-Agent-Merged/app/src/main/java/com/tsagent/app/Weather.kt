package com.tsagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Real weather from Open-Meteo. It is free and needs no API key. */
object Weather {
    fun report(city: String): String {
        return try {
            val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8")))
            val r = g.optJSONArray("results")?.optJSONObject(0) ?: return "Could not find $city."
            val lat = r.getDouble("latitude")
            val lon = r.getDouble("longitude")
            val f = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m"))
            val c = f.getJSONObject("current")
            r.optString("name") + ": " + c.getDouble("temperature_2m") + " °C, humidity " +
                c.getInt("relative_humidity_2m") + "%, wind " + c.getDouble("wind_speed_10m") + " km/h"
        } catch (e: Exception) {
            "Weather is not available right now: " + e.message
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
