package com.tsagent.app

/**
 * One voice for the whole app. The main screen, the background assistant and the watch voice all speak through
 * this gate. Before a speaker starts, it claims the reply: the other speakers are stopped, and the same reply
 * spoken again within a few seconds (by any speaker) is dropped. So only one voice is ever heard.
 */
object SpeechGate {
    private val stoppers = HashMap<String, () -> Unit>()
    private var lastText = ""
    private var lastAt = 0L
    private var owner = ""
    /** Until this time the phone is (probably) still talking, so anything heard that matches the reply is its own voice. */
    private var busyUntil = 0L

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** True if what the mic heard is the phone's own reply coming back (echo), so it must not be sent as a new question. */
    @Synchronized
    fun isEcho(heard: String): Boolean {
        if (System.currentTimeMillis() > busyUntil) return false
        val h = norm(heard)
        val said = norm(lastText)
        if (h.length < 3 || said.isEmpty()) return false
        if (said.contains(h)) return true
        val hw = h.split(" ").filter { it.length >= 2 }
        if (hw.isEmpty()) return false
        val sw = said.split(" ").toSet()
        val hit = hw.count { it in sw }
        return hit * 10 >= hw.size * 6
    }

    /** Returns true if [name] may speak [text] now. Stops every other speaker first. */
    @Synchronized
    fun claim(name: String, text: String, stopMine: () -> Unit): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val now = System.currentTimeMillis()
        if (t == lastText && now - lastAt < 6000L) return false
        lastText = t
        lastAt = now
        busyUntil = now + maxOf(3000L, t.length * 80L) + 4000L
        stoppers[name] = stopMine
        for ((k, stop) in stoppers) if (k != name) try { stop() } catch (_: Exception) {}
        owner = name
        return true
    }
}
