package com.tsagent.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Natural female voice from ElevenLabs, using the user's own key (kept only on the phone).
 * If it is not set up or a request fails, the caller falls back to the phone's built-in voice.
 */
object Voice {
    private const val API = "https://api.elevenlabs.io"
    private var player: MediaPlayer? = null
    private var seq = 0
    private val main = Handler(Looper.getMainLooper())
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Natural voice is on when the server is reachable with a login, or when the phone has its own key and voice. */
    fun configured(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (!p.getBoolean("el_on", true)) return false
        val serverReady = Config.enabled() && !p.getString("svtoken", "").isNullOrBlank()
        val localReady = !p.getString("el_key", "").isNullOrBlank() && !p.getString("el_voice", "").isNullOrBlank()
        return serverReady || localReady
    }

    /** Asks the server's speak endpoint. The admin's ElevenLabs key and voice ID are used there, never sent to the phone. */
    private fun fetchServer(ctx: Context, text: String): File? {
        val tok = prefs(ctx).getString("svtoken", null) ?: return null
        if (!Config.enabled()) return null
        return try {
            val c = URL(Config.SERVER + "/api.php?action=speak").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("X-Token", tok)
            c.setRequestProperty("User-Agent", "TS-Agent/1.0 (Android)")
            c.outputStream.use { it.write(JSONObject().put("text", text.take(1000)).toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val bytes = c.inputStream.readBytes()
            if (bytes.isEmpty()) return null
            File(ctx.cacheDir, "tts_${System.nanoTime()}.mp3").apply { writeBytes(bytes) }
        } catch (e: Exception) { null }
    }

    private fun call(path: String, key: String, body: String? = null): Pair<Int, ByteArray> {
        val c = URL(API + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("xi-api-key", key)
        if (body != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "audio/mpeg")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
        return code to bytes
    }

    /** Blocking. Female voices on the account as (voiceId, label), or an error message. */
    fun femaleVoices(key: String): Pair<List<Pair<String, String>>, String?> {
        try {
            val (code, b) = call("/v2/voices?gender=female&page_size=100", key)
            if (code != 200) return emptyList<Pair<String, String>>() to "ElevenLabs error $code. Check the API key."
            val arr = JSONObject(String(b)).getJSONArray("voices")
            val out = ArrayList<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val v = arr.getJSONObject(i)
                val labels = v.optJSONObject("labels")
                if (labels?.optString("gender").equals("female", ignoreCase = true)) {
                    val extra = listOf(labels?.optString("accent").orEmpty(), labels?.optString("descriptive").orEmpty()).filter { it.isNotBlank() }
                    out.add(v.getString("voice_id") to (v.optString("name") + if (extra.isEmpty()) "" else " (" + extra.joinToString(", ") + ")"))
                }
            }
            return out to null
        } catch (e: Exception) {
            return emptyList<Pair<String, String>>() to "Network error: ${e.message}"
        }
    }

    /** Picks the ElevenLabs speech model that lists Bengali and has the most languages; falls back to multilingual v2. */
    private fun model(ctx: Context, key: String): String {
        val p = prefs(ctx)
        val cached = p.getString("el_model", null)
        if (cached != null && System.currentTimeMillis() - p.getLong("el_model_at", 0L) < 86_400_000L) return cached
        var best = "eleven_multilingual_v2"
        try {
            val (code, b) = call("/v1/models", key)
            if (code == 200) {
                val arr = JSONArray(String(b))
                var bestN = 0
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    if (!m.optBoolean("can_do_text_to_speech", false) || m.optBoolean("requires_alpha_access", false)) continue
                    val langs = m.optJSONArray("languages") ?: continue
                    var bn = false
                    for (j in 0 until langs.length()) if (langs.getJSONObject(j).optString("language_id") == "bn") bn = true
                    if (bn && langs.length() > bestN) { bestN = langs.length(); best = m.getString("model_id") }
                }
                p.edit().putString("el_model", best).putLong("el_model_at", System.currentTimeMillis()).apply()
            }
        } catch (e: Exception) {}
        return best
    }

    private fun fetch(ctx: Context, text: String): File? {
        fetchServer(ctx, text)?.let { return it }
        val p = prefs(ctx)
        val key = p.getString("el_key", "") ?: ""
        val voice = (p.getString("el_voice", "") ?: "").replace(Regex("[^A-Za-z0-9]"), "")
        val body = JSONObject().put("text", text.take(1500)).put("model_id", model(ctx, key)).toString()
        // several keys: the next one is tried only when this one is out of quota or rejected
        var code = 0
        var bytes = ByteArray(0)
        for (k in Llm.keyList(key)) {
            val r = call("/v1/text-to-speech/$voice?output_format=mp3_44100_128", k, body)
            code = r.first; bytes = r.second
            if (code == 200 && bytes.isNotEmpty()) break
            if (code !in listOf(401, 402, 403, 429)) break
        }
        if (code != 200 || bytes.isEmpty()) return null
        val f = File(ctx.cacheDir, "tts_${System.nanoTime()}.mp3")
        f.writeBytes(bytes)
        return f
    }

    /** Callbacks run on the main thread. onFail means the caller should use the phone voice instead. */
    fun speak(ctx: Context, text: String, onStart: () -> Unit, onDone: () -> Unit, onFail: () -> Unit) {
        val my = ++seq
        var answered = false
        // Do not keep the user waiting: if the natural voice is not ready in time, the phone voice starts now.
        main.postDelayed({ if (!answered && my == seq) { answered = true; onFail() } }, 1000)
        Thread {
            val file = try { fetch(ctx, text) } catch (e: Exception) { null }
            main.post {
                if (my != seq || answered) { file?.delete(); return@post }
                answered = true
                if (file == null) { onFail(); return@post }
                release()
                val mp = MediaPlayer()
                player = mp
                try {
                    mp.setDataSource(file.absolutePath)
                    mp.setOnPreparedListener { onStart(); it.start() }
                    mp.setOnCompletionListener { onDone(); file.delete() }
                    mp.setOnErrorListener { _, _, _ -> onDone(); file.delete(); true }
                    mp.prepareAsync()
                } catch (e: Exception) { file.delete(); onFail() }
            }
        }.start()
    }

    private fun release() {
        player?.let { try { it.stop() } catch (e: Exception) {}; it.release() }
        player = null
    }

    fun stop() { seq++; release() }
}
