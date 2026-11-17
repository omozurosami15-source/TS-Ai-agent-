package com.tsagent.app

import android.content.Context

/** Your own shortcuts: a phrase you say and the command it runs. Kept on the phone, works offline. */
object Shortcuts {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Pair<String, String>> =
        (prefs(ctx).getString("shortcuts", "") ?: "").lines().mapNotNull { line ->
            val p = line.split("=>", limit = 2)
            if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) p[0].trim().lowercase() to p[1].trim() else null
        }

    /** The command for a spoken phrase, or null when no shortcut matches. */
    fun lookup(ctx: Context, said: String): String? {
        val s = said.trim().lowercase()
        return all(ctx).firstOrNull { it.first == s }?.second
    }

    /** Accepts "phrase = command". Returns false when the text is not in that form. */
    fun add(ctx: Context, text: String): Boolean {
        val p = text.split("=", limit = 2)
        if (p.size != 2 || p[0].isBlank() || p[1].isBlank()) return false
        val line = p[0].trim().lowercase() + "=>" + p[1].trim()
        val old = prefs(ctx).getString("shortcuts", "") ?: ""
        prefs(ctx).edit().putString("shortcuts", (old.lines().filter { it.isNotBlank() } + line).joinToString("\n")).apply()
        return true
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove("shortcuts").apply()
}
