package com.tsagent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One layer for many AI providers, always with the user's own key kept on the phone.
 *   anthropic = Claude; openai = OpenAI; gemini = Google Gemini; grok = xAI;
 *   custom = any OpenAI-compatible service (OpenRouter, Groq, DeepSeek, Mistral, Together, a local server...) via its base URL.
 * Model names change often, so the model is chosen from the provider's own list (SETTINGS > Choose model).
 */
object Llm {
    val PROVIDERS = listOf("anthropic", "openai", "gemini", "grok", "custom", "cloud")
    /** Only providers that have free models are shown in the app. */
    val SHOWN = listOf("gemini", "custom", "cloud")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Logged in to the server = the server's free AI is used automatically. Otherwise the phone's own key is used. */
    fun provider(ctx: Context): String {
        if (Config.enabled() && !prefs(ctx).getString("svtoken", "").isNullOrBlank()) return "cloud"
        return prefs(ctx).getString("llm_provider", "gemini")?.takeIf { it in SHOWN } ?: "gemini"
    }

    fun next(ctx: Context): String {
        val n = SHOWN[(SHOWN.indexOf(provider(ctx)).coerceAtLeast(0) + 1) % SHOWN.size]
        prefs(ctx).edit().putString("llm_provider", n).apply()
        return n
    }

    private fun key(ctx: Context, p: String): String =
        when (p) {
            "anthropic" -> prefs(ctx).getString("apikey", "")
            "cloud" -> prefs(ctx).getString("svtoken", "")
            else -> prefs(ctx).getString("key_$p", "")
        }.orEmpty().trim()

    fun model(ctx: Context, p: String = provider(ctx)): String =
        prefs(ctx).getString("model_$p", "").orEmpty().trim().ifBlank { when (p) { "anthropic" -> "claude-sonnet-5-5"; "cloud" -> "2"; else -> "" } }

    private fun base(ctx: Context, p: String): String = when (p) {
        "openai" -> "https://api.openai.com/v1"
        "grok" -> "https://api.x.ai/v1"
        "custom" -> prefs(ctx).getString("llm_base", "").orEmpty().trim()
        else -> ""
    }.trimEnd('/')

    fun configured(ctx: Context): Boolean {
        val p = provider(ctx)
        return key(ctx, p).isNotEmpty() && model(ctx, p).isNotEmpty() && (p != "custom" || base(ctx, p).startsWith("https://")) && (p != "cloud" || Config.enabled())
    }

    private fun http(method: String, url: String, headers: Map<String, String>, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 90000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        return code to text
    }

    /** Blocking. Returns the model's text, or throws with a short error message. */
    /** Chat: try the chosen provider first, then every other provider that has a key (server included when logged in). */
    /** Several keys for one provider can be written separated by commas or new lines. */
    fun keyList(raw: String): List<String> =
        raw.split(Regex("[\\r\\n,]+")).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }

    /** True when the error means this key is out of quota, rate limited or rejected: the next key is tried. */
    fun quotaLike(e: Exception): Boolean =
        Regex("429|401|402|403|quota|rate|limit|exhaust|insufficient|billing|credit", RegexOption.IGNORE_CASE).containsMatchIn(e.message ?: "")

    fun complete(ctx: Context, system: String, prompt: String, maxTokens: Int, search: Boolean): String {
        val first = provider(ctx)
        fun usable(x: String): Boolean =
            if (x == "cloud") Config.enabled() && key(ctx, "cloud").isNotEmpty()
            else key(ctx, x).isNotEmpty() && model(ctx, x).isNotEmpty()
        val order = listOf(first, "cloud", "anthropic", "gemini", "openai", "grok", "custom")
            .distinct().filter { usable(it) }
        if (order.isEmpty()) throw Exception("No AI key is set. Add one in SETTINGS.")
        var last: Exception? = null
        for (x in order) {
            val keys = if (x == "cloud") listOf(key(ctx, "cloud")) else keyList(key(ctx, x))
            for (k in keys) {
                try { return completeWith(ctx, x, system, prompt, maxTokens, search, k) }
                catch (e: Exception) { last = e; if (!quotaLike(e)) break }
            }
        }
        throw last ?: Exception("AI is not available right now")
    }

    private fun completeWith(ctx: Context, p: String, system: String, prompt: String, maxTokens: Int, search: Boolean, keyOverride: String? = null): String {
        val key = keyOverride ?: key(ctx, p)
        val model = model(ctx, p)
        return when (p) {
            "anthropic" -> anthropic(key, model, system, prompt, maxTokens, search)
            "cloud" -> cloud(ctx, key, model, system, prompt)
            "gemini" -> gemini(key, model, system, prompt)
            else -> openAiCompatible(base(ctx, p), key, model, system, prompt)
        }
    }

    /** TS Cloud: the server holds the AI keys; each request costs coins (tier 1 lite, 2 standard, 3 pro). */
    private fun cloud(ctx: Context, token: String, tier: String, system: String, prompt: String): String {
        val r = Account.post("chat", JSONObject().put("system", system).put("prompt", prompt).put("tier", tier.toIntOrNull() ?: 1), token)
        val j = r.second
        if (r.first == 200) {
            val c = j.optJSONObject("coins")
            if (c != null) prefs(ctx).edit().putString("coins_text", "${c.optInt("daily_left")}/${c.optInt("cap")}" + (if (c.optInt("bonus") > 0) "+${c.optInt("bonus")}" else "")).apply()
            return j.optString("text")
        }
        if (r.first == 401) prefs(ctx).edit().remove("svtoken").apply()
        throw Exception(j.optString("error", "Server error ${r.first}"))
    }

    private fun anthropic(key: String, model: String, system: String, prompt: String, maxTokens: Int, search: Boolean): String {
        val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        if (search) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 3)))
        val (code, text) = http("POST", "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "content-type" to "application/json"), body.toString())
        if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
        val blocks = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return sb.toString()
    }

    private fun openAiCompatible(base: String, key: String, model: String, system: String, prompt: String): String {
        val body = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", prompt))).toString()
        val (code, text) = http("POST", "$base/chat/completions",
            mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"), body)
        if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
        val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val c = msg.opt("content")
        return if (c is JSONArray) (0 until c.length()).joinToString("") { c.getJSONObject(it).optString("text") } else msg.optString("content")
    }

    private fun gemini(key: String, model: String, system: String, prompt: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
        val (code, text) = http("POST", "https://generativelanguage.googleapis.com/v1beta/models/${model.removePrefix("models/")}:generateContent",
            mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"), body)
        if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
    }

    /**
     * Blocking. Like complete(), but with labelled JPEG pictures (used by Sara Watch).
     * Works with Claude, OpenAI, Gemini, Grok and custom OpenAI-compatible services whose model accepts images. Not available on TS Cloud.
     */
    /** Picture questions: try the chosen provider first, then every other provider that has a key. */
    fun completeVision(ctx: Context, system: String, prompt: String, images: List<Pair<String, ByteArray>>, maxTokens: Int): String {
        val first = provider(ctx)
        val order = (listOf(first, "anthropic", "gemini", "openai", "grok", "custom"))
            .distinct().filter { it != "cloud" && key(ctx, it).isNotEmpty() && model(ctx, it).isNotEmpty() }
        if (order.isEmpty()) throw Exception("Sara Watch needs your own AI key (Claude, OpenAI, Gemini, Grok or custom) with a model chosen")
        var last: Exception? = null
        for (p in order) {
            try { return visionWith(ctx, p, system, prompt, images, maxTokens) }
            catch (e: Exception) { last = e }
        }
        throw last ?: Exception("AI is not available right now")
    }

    private fun visionWith(ctx: Context, p: String, system: String, prompt: String, images: List<Pair<String, ByteArray>>, maxTokens: Int): String {
        val key = key(ctx, p)
        val model = model(ctx, p)
        if (key.isEmpty() || model.isEmpty()) throw Exception("Add an AI key and choose a model first")
        fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
        when (p) {
            "anthropic" -> {
                val content = JSONArray()
                images.forEach { (label, jpg) ->
                    content.put(JSONObject().put("type", "text").put("text", label))
                    content.put(JSONObject().put("type", "image").put("source",
                        JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(jpg))))
                }
                content.put(JSONObject().put("type", "text").put("text", prompt))
                val body = JSONObject().put("model", model).put("max_tokens", maxTokens).put("system", system)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                val (code, text) = http("POST", "https://api.anthropic.com/v1/messages",
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "content-type" to "application/json"), body.toString())
                if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
                val blocks = JSONObject(text).getJSONArray("content")
                val sb = StringBuilder()
                for (i in 0 until blocks.length()) {
                    val bl = blocks.getJSONObject(i)
                    if (bl.optString("type") == "text") sb.append(bl.optString("text"))
                }
                return sb.toString()
            }
            "gemini" -> {
                val parts = JSONArray()
                images.forEach { (label, jpg) ->
                    parts.put(JSONObject().put("text", label))
                    parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", b64(jpg))))
                }
                parts.put(JSONObject().put("text", prompt))
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts))).toString()
                val (code, text) = http("POST", "https://generativelanguage.googleapis.com/v1beta/models/${model.removePrefix("models/")}:generateContent",
                    mapOf("x-goog-api-key" to key, "Content-Type" to "application/json"), body)
                if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
                val ps = JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                return (0 until ps.length()).joinToString("") { ps.getJSONObject(it).optString("text") }
            }
            else -> {
                val base = base(ctx, p)
                val content = JSONArray()
                images.forEach { (label, jpg) ->
                    content.put(JSONObject().put("type", "text").put("text", label))
                    content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + b64(jpg))))
                }
                content.put(JSONObject().put("type", "text").put("text", prompt))
                val body = JSONObject().put("model", model).put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", content))).toString()
                val (code, text) = http("POST", "$base/chat/completions",
                    mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"), body)
                if (code !in 200..299) throw Exception("AI error $code: ${text.take(160)}")
                val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val c = msg.opt("content")
                return if (c is JSONArray) (0 until c.length()).joinToString("") { c.getJSONObject(it).optString("text") } else msg.optString("content")
            }
        }
    }

    /** Blocking. Model ids the active provider offers, or an error message. */
    /** Free models only: Gemini flash models, and OpenRouter-style ids ending in :free. */
    fun listModels(ctx: Context): Pair<List<String>, String?> {
        val (list, err) = listModelsRaw(ctx)
        val free = when (provider(ctx)) {
            "gemini" -> list.filter { id -> id.contains("flash") && !Regex("pro|image|audio|live|tts|embedding|computer|deep-research|exp").containsMatchIn(id) }
            "custom" -> list.filter { it.endsWith(":free") }
            else -> list
        }
        return free to err
    }

    private fun listModelsRaw(ctx: Context): Pair<List<String>, String?> {
        val p = provider(ctx)
        val key = key(ctx, p)
        if (key.isEmpty()) return emptyList<String>() to (if (p == "cloud") "Log in to your account first (SETTINGS)" else "Enter the key for ${p.uppercase()} first")
        if (p == "cloud") return listOf("1", "2", "3") to null
        return try {
            when (p) {
                "anthropic" -> {
                    val (c, t) = http("GET", "https://api.anthropic.com/v1/models?limit=100", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
                    if (c != 200) return emptyList<String>() to "Error $c: ${t.take(120)}"
                    val a = JSONObject(t).getJSONArray("data")
                    (0 until a.length()).map { a.getJSONObject(it).getString("id") }.sortedDescending() to null
                }
                "gemini" -> {
                    val (c, t) = http("GET", "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to key))
                    if (c != 200) return emptyList<String>() to "Error $c: ${t.take(120)}"
                    val a = JSONObject(t).getJSONArray("models")
                    val out = ArrayList<String>()
                    for (i in 0 until a.length()) {
                        val m = a.getJSONObject(i)
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        var ok = false
                        if (methods != null) for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
                        if (ok) out.add(m.getString("name").removePrefix("models/"))
                    }
                    out.sortedDescending() to null
                }
                else -> {
                    val base = base(ctx, p)
                    if (!base.startsWith("https://")) return emptyList<String>() to "Set the provider base URL first"
                    val (c, t) = http("GET", "$base/models", mapOf("Authorization" to "Bearer $key"))
                    if (c != 200) return emptyList<String>() to "Error $c: ${t.take(120)}"
                    val a = JSONObject(t).getJSONArray("data")
                    (0 until a.length()).map { a.getJSONObject(it).getString("id") }.sortedDescending() to null
                }
            }
        } catch (e: Exception) { emptyList<String>() to "Network error: ${e.message}" }
    }
}
