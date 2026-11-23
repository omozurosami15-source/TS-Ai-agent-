package com.tsagent.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Named command lists, stored on the phone. "routine add morning: open youtube; scroll down" then "run morning". */
object Routines {
    private fun load(ctx: Context): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        (ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("routines", "") ?: "")
            .lines().filter { it.contains("=") }.forEach { m[it.substringBefore("=")] = it.substringAfter("=") }
        return m
    }

    private fun save(ctx: Context, m: Map<String, String>) {
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
            .putString("routines", m.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    /** Returns a reply, or null when the text is not a routine command. */
    fun handle(act: Context, raw: String, say: (String) -> Unit): String? {
        val t = raw.trim()
        val l = t.lowercase()
        val all = load(act)
        if (l == "routines" || l == "routine list") {
            return if (all.isEmpty()) "No routines yet. Example: routine add morning: open youtube; scroll down"
            else all.entries.joinToString("\n") { "${it.key}: ${it.value.replace(";", "; ")}" }
        }
        Regex("^routine add ([a-z0-9_-]+)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(t)?.let { m ->
            val steps = m.groupValues[2].split(";").map { it.trim() }
                .filter { it.isNotEmpty() && !it.lowercase().startsWith("run ") && !it.lowercase().startsWith("routine") }
            if (steps.isEmpty()) return "Add at least one command after the colon"
            all[m.groupValues[1].lowercase()] = steps.joinToString(";")
            save(act, all)
            return "Saved routine ${m.groupValues[1].lowercase()} (${steps.size} steps). Run it with: run ${m.groupValues[1].lowercase()}"
        }
        if (l.startsWith("routine delete ")) {
            val name = l.removePrefix("routine delete ").trim()
            return if (all.remove(name) != null) { save(act, all); "Deleted routine $name" } else "No routine named $name"
        }
        if (l.startsWith("run ")) {
            val name = l.removePrefix("run ").trim()
            val steps = all[name]?.split(";") ?: return null
            val h = Handler(Looper.getMainLooper())
            steps.forEachIndexed { i, cmd -> h.postDelayed({ if (!Agent.cancelled || i == 0) CommandRunner(act, say).run(cmd) }, i * 2500L) }
            return "Running $name (${steps.size} steps)"
        }
        return null
    }
}
