package com.tsagent.app

import android.content.Context
import org.json.JSONObject

/**
 * Language packs: spoken phrases in another language mapped to the built-in English commands, so offline commands
 * work without an AI key. The pack is a data file (assets/pack_bn.json, same file as docs/packs/bn.json); the app UI stays English.
 * Placeholders: {x} = any text, {n} = a number (Bengali or Western digits).
 */
object Packs {
    private class Rule(val rx: Regex, val cmd: String)
    private var rules: List<Rule>? = null
    private var aliases: Map<String, String> = emptyMap()

    private fun build(phrase: String): Regex {
        val sb = StringBuilder("^")
        var last = 0
        for (m in Regex("\\{[xn]\\}").findAll(phrase)) {
            sb.append(Regex.escape(phrase.substring(last, m.range.first)))
            sb.append(if (m.value == "{n}") "([0-9\\u09E6-\\u09EF]+)" else "(.+?)")
            last = m.range.last + 1
        }
        sb.append(Regex.escape(phrase.substring(last))).append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    private fun load(ctx: Context): List<Rule> {
        rules?.let { return it }
        val out = ArrayList<Rule>()
        val al = HashMap<String, String>()
        try {
            val j = JSONObject(ctx.assets.open("pack_bn.json").bufferedReader().readText())
            val arr = j.getJSONArray("rules")
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val say = r.getJSONArray("say")
                for (k in 0 until say.length()) out.add(Rule(build(say.getString(k).trim()), r.getString("cmd")))
            }
            val a = j.optJSONObject("aliases")
            if (a != null) for (key in a.keys()) al[key.lowercase()] = a.getString(key)
        } catch (e: Exception) {}
        aliases = al
        rules = out
        return out
    }

    private fun digits(s: String) = s.map { if (it in '\u09E6'..'\u09EF') ('0' + (it - '\u09E6')) else it }.joinToString("")

    /** Returns the English command for a Bengali phrase, or null when the text is not Bengali or matches nothing. */
    fun canonical(ctx: Context, raw: String): String? {
        if (!raw.any { it in '\u0980'..'\u09FF' }) return null
        val t = raw.trim().trimEnd('\u0964', '.', '!', '?', ' ')
        for (r in load(ctx)) {
            val m = r.rx.matchEntire(t) ?: continue
            var gi = 1
            var out = Regex("\\{[xn]\\}").replace(r.cmd) { ph ->
                val g = m.groupValues.getOrNull(gi++).orEmpty().trim()
                if (ph.value == "{n}") digits(g) else g
            }
            if (out.startsWith("open ")) out = "open " + (aliases[out.removePrefix("open ").trim().lowercase()] ?: out.removePrefix("open ").trim())
            return out
        }
        return null
    }
}
