package com.tsagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Account {
    /** Blocking: call from a background thread. Returns (httpCode, json). */
    fun post(action: String, body: JSONObject, token: String? = null): Pair<Int, JSONObject> {
        if (!Config.enabled()) return 0 to JSONObject().put("error", "Server URL is not set in Config.kt")
        return try {
            val c = URL("${Config.SERVER}/api.php?action=$action").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 20000
            c.readTimeout = 40000
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("User-Agent", "TS-Agent/1.0 (Android)")
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("X-Token", token)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "{}"
            val j = try { JSONObject(text) } catch (e: Exception) { JSONObject().put("error", "Invalid response from server ($code)") }
            code to j
        } catch (e: Exception) {
            0 to JSONObject().put("error", "Network error: ${e.message}")
        }
    }
}
