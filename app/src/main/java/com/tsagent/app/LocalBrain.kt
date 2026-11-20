package com.tsagent.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import java.net.URLEncoder

/** Offline command engine: common commands run with no AI key, no server, no cost. Returns a reply, or null when the text is not a known command. */
object LocalBrain {
    private val MULTI = Regex("(?i)(\\band\\b|\\bthen\\b|,)")
    private val I = RegexOption.IGNORE_CASE
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Links the user saved in MEMORY, one per line, for example "Website - My Shop: https://example.com". */
    private fun memLinks(act: Context): List<Pair<String, String>> {
        val mem = act.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("memory", "") ?: ""
        return mem.lines().mapNotNull { line ->
            val m = Regex("^(.*?)[\\s:\\-]*(https?://\\S+)\\s*$").find(line.trim()) ?: return@mapNotNull null
            val name = m.groupValues[1].trim().trimEnd(':', '-', ' ')
            if (name.isEmpty()) null else name to m.groupValues[2]
        }
    }

    private fun findLink(act: Context, query: String): Pair<String, String>? {
        val q = query.trim().lowercase()
        if (q.length < 2) return null
        return memLinks(act).firstOrNull { it.first.lowercase().contains(q) }
    }

    fun handle(act: Context, raw: String): String? =
        try { run(act, raw) } catch (e: Exception) { "Could not do that: ${e.message}" }

    /** Owner only: plays the saved favorite song file, or opens the saved link. */
    fun playFavorite(ctx: Context): String {
        if (!Entitlements.isOwner(ctx)) return "No favorite song is saved for you."
        val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val uri = p.getString("fav_song_uri", null)
        val link = p.getString("fav_song_link", "") ?: ""
        return when {
            !uri.isNullOrBlank() -> SongPlayer.play(ctx, Uri.parse(uri))
            link.startsWith("https://") -> {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Playing your favorite song"
            }
            else -> "No favorite song saved yet. Open SETTINGS and pick a file or paste a link."
        }
    }

    /** Short greetings and thanks: answered on the phone, so they never use up an AI call. Only exact short phrases match. */
    private val smallTalk: Map<String, String> = mapOf(
        "hi" to "Hi! How can I help you?",
        "hello" to "Hello! What do you need?",
        "hey" to "Hey! What do you need?",
        "good morning" to "Good morning! Ready when you are.",
        "good night" to "Good night! Sleep well.",
        "thanks" to "You are welcome!",
        "thank you" to "You are welcome!",
        "হাই" to "হাই! কী করতে পারি বলো।",
        "হ্যালো" to "হ্যালো! বলো, কী লাগবে?",
        "হ্যালো সারা" to "হ্যালো! বলো, আমি শুনছি।",
        "হাই সারা" to "হাই! বলো, কী করতে পারি?",
        "সুপ্রভাত" to "সুপ্রভাত! বলো, আজ কী করবে?",
        "শুভ রাত্রি" to "শুভ রাত্রি! ভালো ঘুমাও।",
        "ধন্যবাদ" to "ধন্যবাদ! আর কিছু লাগলে বলো।",
        "কেমন আছো" to "আমি ভালো আছি। তুমি কেমন আছো?",
        "কেমন আছ" to "আমি ভালো আছি। তুমি কেমন আছ?"
    )

    private fun torch(ctx: Context, on: Boolean): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "This phone has no torch."
            cm.setTorchMode(id, on)
            if (on) "Torch on" else "Torch off"
        } catch (e: Exception) {
            "Could not change the torch: " + e.message
        }
    }

    private fun volume(ctx: Context, dir: Int): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, android.media.AudioManager.FLAG_SHOW_UI)
        return if (dir == android.media.AudioManager.ADJUST_RAISE) "Volume up" else "Volume down"
    }

    private fun run(act: Context, raw: String): String? {
        val t = raw.trim().trimEnd('.', '!', '?')
        val l = t.lowercase()
        if (l.isEmpty()) return null
        smallTalk[l]?.let { return it }
        val svc = AgentService.instance
        fun launch(i: Intent) = act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun view(url: String, msg: String): String { launch(Intent(Intent.ACTION_VIEW, Uri.parse(url))); return msg }
        fun go(action: String, msg: String): String { launch(Intent(action)); return msg }
        fun screen(msg: String, f: (AgentService) -> Unit): String {
            if (svc == null) return "Enable Accessibility first (SETTINGS)"
            f(svc)
            return msg
        }

        Regex("^(?:share|upload|post|send)(?: my| the)?(?: (?:latest|last|newest|recent))? (video|photo|image|picture|pic)(?: file)? (?:to|on|in|at) ([a-z][a-z ]*?)(?: (?:with )?caption (.+))?$", I)
            .matchEntire(t)?.let { m -> return Share.latest(act, m.groupValues[2].trim(), m.groupValues[1], m.groupValues[3].ifBlank { null }) }

        Regex("^(?:who (?:is|are) (?:the |your )?(?:owner|developer|creator|maker|admin)(?: of (?:this|the) app)?|who (?:owns|made|created|built|developed) (?:this|it|you)(?: app)?|(?:owner|developer|creator))$", I)
            .matchEntire(t)?.let { return "This app is owned and developed by ${Config.OWNER_NAME}." }
        Regex("^.*\\b(?:owner|developer|creator|admin)\\b.*\\b(?:number|phone|contact|whatsapp)\\b.*$|^.*\\b(?:number|phone|contact|whatsapp)\\b.*\\b(?:owner|developer|creator|admin)\\b.*$|^contact(?: us)?$", I)
            .matchEntire(t)?.let { return "${Config.OWNER_NAME}: ${Config.OWNER_CONTACT}" }

        Regex("^(?:play )?(?:my |the )?favou?rite (?:song|music)(?: please)?$", I).matchEntire(t)?.let { return playFavorite(act) }
        Regex("^(?:stop|pause) (?:the )?(?:music|song)$", I).matchEntire(t)?.let { SongPlayer.stop(); return "Music stopped" }
        Regex("^stop autoplay$", I).matchEntire(t)?.let { Autoplay.stop(); return "Autoplay stopped" }
        Regex("^autoplay(?: every)?(?: (\\d{1,3}))?(?: seconds?)?$", I).matchEntire(t)?.let { m ->
            return Autoplay.start(act, m.groupValues[1].toIntOrNull() ?: 30)
        }

        // Phone facts and simple controls: answered on the phone, no AI call.
        when {
            l in setOf("time", "what time is it", "what's the time", "current time", "কয়টা বাজে", "সময় কত", "এখন কয়টা বাজে") ->
                return java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date())
            l in setOf("date", "today's date", "what is the date", "what's the date", "আজ কত তারিখ", "আজ কি তারিখ", "আজকের তারিখ") ->
                return java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date())
            l in setOf("battery", "battery level", "battery status", "ব্যাটারি কত", "ব্যাটারি কতো") -> {
                val bm = act.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                return "Battery: " + bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%"
            }
            l in setOf("torch on", "turn on torch", "flashlight on", "টর্চ জ্বালাও", "টর্চ চালু করো") -> return torch(act, true)
            l in setOf("torch off", "turn off torch", "flashlight off", "টর্চ বন্ধ করো", "টর্চ বন্ধ") -> return torch(act, false)
            l in setOf("volume up", "increase volume", "ভলিউম বাড়াও") -> return volume(act, android.media.AudioManager.ADJUST_RAISE)
            l in setOf("volume down", "decrease volume", "ভলিউম কমাও") -> return volume(act, android.media.AudioManager.ADJUST_LOWER)
        }

        when (l) {
            "back", "go back" -> return screen("Back") { it.back() }
            "home", "go home" -> return screen("Home") { it.home() }
            "recents", "recent apps" -> return screen("Recent apps") { it.global(AccessibilityService.GLOBAL_ACTION_RECENTS) }
            "notifications", "open notifications" -> return screen("Notifications") { it.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
            "quick settings" -> return screen("Quick settings") { it.global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
            "lock", "lock screen", "lock phone" -> return screen("Locking") { if (Build.VERSION.SDK_INT >= 28) it.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            "screenshot", "take screenshot" -> return screen("Screenshot") { if (Build.VERSION.SDK_INT >= 28) it.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }
            "scroll", "scroll down" -> return screen("Scrolling down") { it.scrollDown() }
            "scroll up" -> return screen("Scrolling up") { it.scrollUp() }
            "wifi", "wifi settings" -> return go(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")
            "bluetooth", "bluetooth settings" -> return go(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            "settings", "open settings" -> return go(Settings.ACTION_SETTINGS, "Settings")
            "my memory", "what do you know about me", "who am i", "what do you remember" -> {
                if (!OwnerAuth.unlocked(act)) return "Owner login required. Open SETTINGS > Owner login."
                val m = act.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("memory", "") ?: ""
                return if (m.isBlank()) "Memory is empty. Add facts in MEMORY or say: remember <fact>" else m
            }
            "my websites", "my sites", "my links", "websites", "links" -> {
                val all = memLinks(act)
                return if (all.isEmpty()) "No links saved. Add lines like: Website - My Shop: https://example.com in MEMORY"
                else all.joinToString("\n") { it.first.removePrefix("Website - ").removePrefix("website - ") + ": " + it.second }
            }
            "forget memory", "clear memory" -> {
                if (!OwnerAuth.unlocked(act)) return "Owner login required. Open SETTINGS > Owner login."
                act.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().remove("memory").apply()
                return "Memory cleared"
            }
        }

        Regex("^(?:give me |show |send |what is |what's )?(?:the )?(?:link|url|website|site)(?: of| for| to)? (.+)$", I).matchEntire(t)?.let { m ->
            val hit = findLink(act, m.groupValues[1]) ?: return null
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", hit.second))
            return hit.first.removePrefix("Website - ").removePrefix("website - ") + ": " + hit.second + " (copied)"
        }
        Regex("^(.+?) (?:link|url|website|site)$", I).matchEntire(t)?.let { m ->
            val hit = findLink(act, m.groupValues[1]) ?: return null
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", hit.second))
            return hit.first.removePrefix("Website - ").removePrefix("website - ") + ": " + hit.second + " (copied)"
        }
        Regex("^(?:remember|note)(?: that)? (.+)$", I).matchEntire(t)?.let { m ->
            if (!OwnerAuth.unlocked(act)) return "Owner login required. Open SETTINGS > Owner login."
            val p = act.getSharedPreferences("cfg", Context.MODE_PRIVATE)
            p.edit().putString("memory", ((p.getString("memory", "") ?: "") + "\n" + m.groupValues[1]).trim()).apply()
            return "Remembered"
        }
        Regex("^enter (.+)$", I).matchEntire(t)?.let { m ->
            if (svc == null) return "Enable Accessibility first (SETTINGS)"
            return if (svc.typeText(m.groupValues[1])) { svc.pressEnter(); "Entered" } else "No input field is focused"
        }
        Regex("^set (?:a )?timer (?:for )?(\\d+) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?)$", I).matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            val u = m.groupValues[2].lowercase()
            val secs = when { u.startsWith("h") -> n * 3600; u.startsWith("m") -> n * 60; else -> n }
            launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Timer set for $n ${m.groupValues[2]}"
        }
        Regex("^(?:search )?youtube (?:for )?(.+)$", I).matchEntire(t)?.let { return view("https://www.youtube.com/results?search_query=" + enc(it.groupValues[1]), "Searching YouTube") }
        Regex("^play (.+) on youtube$", I).matchEntire(t)?.let { return view("https://www.youtube.com/results?search_query=" + enc(it.groupValues[1]), "Searching YouTube") }
        Regex("^(?:google|search(?: google)?(?: for)?) (.+)$", I).matchEntire(t)?.let { m ->
            launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, m.groupValues[1]))
            return "Searching"
        }
        Regex("^(?:map|navigate to|directions to) (.+)$", I).matchEntire(t)?.let { return view("geo:0,0?q=" + enc(it.groupValues[1]), "Opening map") }
        Regex("^(?:call|dial) ([+0-9 ()-]{3,})$", I).matchEntire(t)?.let { return view("tel:" + Uri.encode(it.groupValues[1].trim()), "Dialing") }
        Regex("^open (?:url |link |website )?(https?://\\S+)$", I).matchEntire(t)?.let { return view(it.groupValues[1], "Opening link") }
        Regex("^(?:open|launch|start) ([a-z0-9][a-z0-9 ._-]*)$", I).matchEntire(t)?.let { m ->
            if (MULTI.containsMatchIn(m.groupValues[1])) return null
            if (CommandRunner.findPackage(act, m.groupValues[1]) == null) {
                findLink(act, m.groupValues[1])?.let { return view(it.second, "Opening " + it.first.removePrefix("Website - ").removePrefix("website - ")) }
            }
            return CommandRunner.openApp(act, m.groupValues[1])
        }
        return null
    }
}
