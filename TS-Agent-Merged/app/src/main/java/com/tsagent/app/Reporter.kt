package com.tsagent.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends crashes and errors to the server, so they appear in Admin > Bug reports. Never blocks the app. */
object Reporter {
    fun send(kind: String, message: String, context: String = "") {
        if (!Config.enabled()) return
        Thread {
            try {
                val c = URL(Config.SERVER + "/api.php?action=report").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("User-Agent", "TS-Agent/1.0 (Android)")
                c.outputStream.use {
                    it.write(JSONObject().put("kind", kind).put("message", message.take(2000)).put("context", context.take(4000)).toString().toByteArray())
                }
                c.responseCode
                c.disconnect()
            } catch (_: Exception) {}
        }.start()
    }
}
