package com.tsagent.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CommandRunner(private val act: Context, private val say: (String) -> Unit) {
    private val prefs = act.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    companion object {
        private val KNOWN = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
            "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            "instagram" to listOf("com.instagram.android"),
            "twitter" to listOf("com.twitter.android"),
            "github" to listOf("com.github.android"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "spotify" to listOf("com.spotify.music"),
            "netflix" to listOf("com.netflix.mediaclient")
        )

        /** Bengali and common spoken names mapped to the English name the app list uses. */
        private val ALIASES = mapOf(
            // Bangla
            "ইউটিউব" to "youtube", "ফেসবুক" to "facebook", "টিকটক" to "tiktok", "টিক টক" to "tiktok",
            "টেলিগ্রাম" to "telegram", "ইনস্টাগ্রাম" to "instagram", "ইনস্টা" to "instagram", "টুইটার" to "twitter",
            "হোয়াটসঅ্যাপ" to "whatsapp", "হোয়াটসএপ" to "whatsapp", "স্পটিফাই" to "spotify", "নেটফ্লিক্স" to "netflix",
            "গিটহাব" to "github", "জিমেইল" to "gmail", "জিমেল" to "gmail", "ক্রোম" to "chrome", "ম্যাপ" to "maps",
            "গুগল ম্যাপ" to "maps", "ক্যামেরা" to "camera", "গ্যালারি" to "gallery", "প্লে স্টোর" to "play store",
            "সেটিংস" to "settings", "ক্যালকুলেটর" to "calculator", "মেসেজ" to "messages", "ক্যালেন্ডার" to "calendar",
            "ফোন" to "phone", "ডায়ালার" to "phone",
            // English spellings people say out loud
            "you tube" to "youtube", "utube" to "youtube", "yutube" to "youtube", "face book" to "facebook",
            "tik tok" to "tiktok", "tic tac" to "tiktok", "whats app" to "whatsapp", "watsapp" to "whatsapp", "wats app" to "whatsapp",
            "insta gram" to "instagram", "insta" to "instagram", "g mail" to "gmail", "google chrome" to "chrome",
            "google maps" to "maps", "play store" to "play store", "playstore" to "play store", "setting" to "settings",
            "calculater" to "calculator", "google play" to "play store",
            // Hindi
            "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "फेसबुक" to "facebook", "टिकटॉक" to "tiktok",
            "टेलीग्राम" to "telegram", "टेलिग्राम" to "telegram", "इंस्टाग्राम" to "instagram", "ट्विटर" to "twitter",
            "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "स्पॉटिफ़ाई" to "spotify", "स्पॉटिफाई" to "spotify",
            "नेटफ्लिक्स" to "netflix", "जीमेल" to "gmail", "क्रोम" to "chrome", "कैमरा" to "camera",
            "गैलरी" to "gallery", "सेटिंग्स" to "settings", "सेटिंग" to "settings", "कैलकुलेटर" to "calculator",
            "मैसेज" to "messages", "कैलेंडर" to "calendar", "फ़ोन" to "phone", "फोन" to "phone", "मैप्स" to "maps",
            // Arabic
            "يوتيوب" to "youtube", "فيسبوك" to "facebook", "فيس بوك" to "facebook", "تيك توك" to "tiktok", "تيليغرام" to "telegram",
            "تليجرام" to "telegram", "انستغرام" to "instagram", "انستجرام" to "instagram", "تويتر" to "twitter",
            "واتساب" to "whatsapp", "واتس اب" to "whatsapp", "سبوتيفاي" to "spotify", "نتفليكس" to "netflix",
            "جيميل" to "gmail", "كروم" to "chrome", "خرائط" to "maps", "كاميرا" to "camera", "معرض" to "gallery",
            "متجر بلاي" to "play store", "الإعدادات" to "settings", "الاعدادات" to "settings", "حاسبة" to "calculator",
            "رسائل" to "messages", "التقويم" to "calendar", "الهاتف" to "phone"
        )

        /** Names the admin added in Admin > Settings (spoken=app), any language. */
        @Volatile private var serverAliases: Map<String, String> = emptyMap()

        fun setServerAliases(text: String) {
            serverAliases = text.lines().mapNotNull { line ->
                val p = line.split("=", limit = 2)
                if (p.size == 2 && p[0].trim().isNotEmpty() && p[1].trim().isNotEmpty())
                    p[0].trim().lowercase() to p[1].trim().lowercase()
                else null
            }.toMap()
        }

        /** The spoken name with Bengali words turned into English, so KNOWN and labels can match it. */
        private fun aliasName(name: String): String {
            val n = name.trim()
            val low = n.lowercase()
            serverAliases.entries.firstOrNull { low.contains(it.key) }?.let { return it.value }
            return ALIASES.entries.firstOrNull { n.contains(it.key) }?.value ?: n
        }

        /** Letters and digits only, lowercase: "TS Vibe" and "tsvibe" become the same. */
        private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

        private fun editDist(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]
                dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }

        /** True when an installed app's name is the spoken name, close to it (small spelling slips), or contains it. */
        fun labelMatches(label: String, spoken: String): Boolean {
            val l = norm(label)
            val n = norm(aliasName(spoken))
            if (l.isEmpty() || n.isEmpty()) return false
            if (l.contains(n) || n.contains(l)) return true
            return n.length >= 4 && editDist(l, n) <= 2
        }

        fun findPackage(ctx: Context, rawName: String): String? {
            val name = rawName.trim()
            val pm = ctx.packageManager
            val key = if (name.equals("x", true)) "twitter" else KNOWN.keys.firstOrNull { aliasName(name).contains(it, true) }
            key?.let { KNOWN[it] }?.firstOrNull { pm.getLaunchIntentForPackage(it) != null }?.let { return it }
            return pm.getInstalledApplications(0).firstOrNull {
                pm.getLaunchIntentForPackage(it.packageName) != null &&
                    labelMatches(pm.getApplicationLabel(it).toString(), name)
            }?.packageName
        }

        fun openApp(ctx: Context, rawName: String): String {
            val name = rawName.replace(Regex("(?i)\\b(open|launch)\\b"), "").trim()
            if (name.isEmpty()) return "Which app?"
            val pm = ctx.packageManager
            val key = if (name.equals("x", true)) "twitter" else KNOWN.keys.firstOrNull { aliasName(name).contains(it, true) }
            val known = key?.let { k -> KNOWN[k]?.firstOrNull { pm.getLaunchIntentForPackage(it) != null } }
            if (known != null) {
                val i = pm.getLaunchIntentForPackage(known)!!
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return "Opened $name"
            }
            val app = pm.getInstalledApplications(0).firstOrNull {
                pm.getLaunchIntentForPackage(it.packageName) != null &&
                    labelMatches(pm.getApplicationLabel(it).toString(), name)
            }
            val intent = app?.let { pm.getLaunchIntentForPackage(it.packageName) }
                ?: return "'$name' App not found"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            return "Opened ${pm.getApplicationLabel(app)}"
        }

        /** Blocking call: run on a background thread. */
        fun sendTelegram(ctx: Context, text: String): String {
            val prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
            val token = prefs.getString("token", null)
            val chat = prefs.getString("chat", null)
            if (token == null || chat == null) return "First run tgsetup"
            return try {
                val c = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                val body = "chat_id=${URLEncoder.encode(chat, "UTF-8")}&text=${URLEncoder.encode(text, "UTF-8")}"
                c.outputStream.use { it.write(body.toByteArray()) }
                if (c.responseCode == 200) "Posted to Telegram" else "Telegram error ${c.responseCode}"
            } catch (e: Exception) { "Network error: ${e.message}" }
        }
    }

    private fun withService(block: (AgentService) -> Unit) {
        val s = AgentService.instance
        if (s == null) say("First enable Accessibility") else block(s)
    }

    fun run(raw: String) {
        // A saved shortcut runs its command instead (offline, no AI needed).
        val sc = Shortcuts.lookup(act, raw)
        if (sc != null && sc.lowercase() != raw.trim().lowercase()) { run(sc); return }
        val original = raw.trim()
        val t = Packs.canonical(act, original) ?: original
        val l = t.lowercase()
        if (l in setOf("news", "headlines", "read news", "read headlines", "today's news", "what's the news", "whats the news")) {
            Thread {
                val items = Headlines.fetch(act, 5)
                val msg = if (items.isEmpty()) "Could not load the news right now"
                else items.mapIndexed { i, h -> "${i + 1}. ${h.title}" }.joinToString("\n")
                Handler(Looper.getMainLooper()).post { say(msg); Agent.onSpeak?.invoke(msg.take(400), null) }
            }.start()
            return
        }
        val routine = Routines.handle(act, t, say)
        if (routine != null) { say(routine); Agent.onSpeak?.invoke(routine.take(240), null); return }
        val local = LocalBrain.handle(act, t)
        if (local != null) { say(local); Agent.onSpeak?.invoke(local.take(240), null); return }
        when {
            // A task is running and the user asks something else: answer the question, the task keeps going.
            Agent.running && l in setOf("থামো", "থামাও", "বাদ দাও", "cancel", "বন্ধ করো") -> { Agent.cancelled = true; say("Stopped the task.") }
            Regex("(?i)^(what |how |why |when |where |who |is |are |can |do |does )|^(কী|কি|কেন|কখন|কোথায়|কে|কত|কেমন|তুমি কি|তুমি কী)|\\?$").containsMatchIn(t) &&
                !Regex("(?i)(open|খোল|send|পাঠ|লেখ|post|call|কল |message|মেসেজ|voice|ভয়েস|play|চালাও|photo|ছবি|video|ভিডিও|look|দেখ)").containsMatchIn(t) -> Thread {
                val answer = try {
                    Llm.complete(act, "You are Miss Sara. Answer briefly and clearly in the same language as the question. Do not perform phone actions; just answer.", t, 300, false)
                } catch (e: Exception) { "Could not answer right now." }
                say(answer)
            }.start()
            l == "stop" -> { Agent.cancelled = true; SongPlayer.stop(); Autoplay.stop(); say("Stopping...") }
            l.startsWith("keyset ") -> {
                prefs.edit().putString("apikey", t.substring(7).trim()).apply()
                say("API key saved")
            }
            l.startsWith("tgsetup ") -> {
                val p = t.split(" ")
                if (p.size >= 3) {
                    prefs.edit().putString("token", p[1]).putString("chat", p[2]).apply()
                    say("Telegram saved")
                } else say("Usage: tgsetup BOT_TOKEN @channel")
            }
            l.startsWith("tg ") -> Thread {
                val m = sendTelegram(act, t.substring(3))
                Handler(Looper.getMainLooper()).post { say(m) }
            }.start()
            l.startsWith("click ") -> withService {
                say(if (it.clickByText(t.substring(6))) "Clicked" else "Button not found")
            }
            l.startsWith("type ") -> withService {
                say(if (it.typeText(t.substring(5))) "Typed" else "No input field is focused")
            }
            l == "back" -> withService { it.back(); say("Back") }
            l == "home" -> withService { it.home(); say("Home") }
            l == "scroll" -> withService { it.scrollDown(); say("Scroll") }
            // Writing only (no phone control): a post or caption is written well and copied to the clipboard.
            !Agent.running && Llm.configured(act) && Regex("(?i)^(write (a )?(post|caption)|post |caption |পোস্ট|ক্যাপশন)").containsMatchIn(t) -> Thread {
                val text = try {
                    Llm.complete(act, "You are an excellent social media writer. Write the post or caption the user asks for: clear, natural and well structured, in the language the user asks for (Bengali or English). Use short paragraphs and a few fitting emojis. Return only the text to post, with no explanation.", t, 1200, false)
                } catch (e: Exception) { "Could not write it right now." }
                try {
                    val cm = act.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("post", text))
                } catch (e: Exception) { }
                say(text + "\n\n(Copied. Paste it in the app you want.)")
            }.start()
            // Evidence: saved only on this phone. Nothing is sent anywhere.
            l.startsWith("প্রমাণ সেভ ") || l.startsWith("evidence save ") -> {
                val note = if (l.startsWith("evidence save ")) t.substring(14) else t.substring("প্রমাণ সেভ ".length)
                say(Evidence.save(act, note))
            }
            l == "প্রমাণ দেখাও" || l == "evidence list" -> say(Evidence.list(act))
            l == "setup" || l == "সেটআপ" -> (act as? MainActivity)?.showSetup()
            l == "guide" || l == "গাইড" -> (act as? MainActivity)?.showGuide()
            l == "voice switch" || l == "ভয়েস বদলাও" -> say((act as? MainActivity)?.switchVoice() ?: "Voice change is not available here.")
            // Look at the screen and explain what is shown, including errors and how to fix them. Spoken and shown.
            l in setOf("look", "স্ক্রিন দেখো", "স্ক্রিনে কী", "এরর কী", "কী এরর", "স্ক্রিনে কী আছে", "error?", "what is on screen") -> Thread {
                val jpg = AgentService.instance?.screenshotJpeg()
                val answer = if (jpg == null) "I cannot see the screen. Turn on Accessibility and Android 11 or newer."
                else try {
                    Llm.completeVision(act, "You look at a phone or computer screen for the user. Say clearly, in the user's language (Bengali if they speak Bengali): what the screen shows, whether there is an error, the exact error line or message, what it means, and the exact next step to fix it. Keep it short and clear.", t, listOf("Picture: the screen." to jpg), 500)
                } catch (e: Exception) { "Could not read the screen: " + e.message }
                say(answer)
            }.start()
            // Your Telegram channel (t.me/ts_official_top): "telegram post <text>" / "টেলিগ্রাম পোস্ট <text>", or "telegram open".
            l == "telegram open" || l == "টেলিগ্রাম খোলো" -> say(TelegramChannel.open(act))
            l.startsWith("telegram post ") || l.startsWith("টেলিগ্রাম পোস্ট ") -> {
                val body = if (l.startsWith("telegram post ")) t.substring(14) else t.substring("টেলিগ্রাম পোস্ট ".length)
                say(TelegramChannel.post(act, body.trim()))
            }
            // Inbox: "inbox" (current app) or "tiktok inbox" / "টিকটক ইনবক্স" (opens the app first). No AI call.
            l.contains("inbox") || l.contains("ইনবক্স") -> Thread {
                val appPart = l.replace("inbox", "").replace("ইনবক্স", "").trim()
                say(Inbox.open(act, appPart))
            }.start()
            // Fast WhatsApp: "wa 8801XXXXXXXXX : your message" opens and sends with no AI call.
            l.startsWith("wa ") && t.contains(":") -> {
                val body = t.substring(3)
                val num = body.substringBefore(":").trim()
                val msg = body.substringAfter(":").trim()
                say(FastSend.whatsapp(act, num, msg))
            }
            // Daily job: 08:00 and 19:00 with the phone's own agent.
            l in setOf("daily on", "দৈনিক চালু", "রোজ চালু") -> say(DailyJobs.on(act))
            l in setOf("daily off", "দৈনিক বন্ধ", "রোজ বন্ধ") -> say(DailyJobs.off(act))
            // Ramadan: days until it starts, prayer alarms, and turning off pet words in Ramadan.
            l in setOf("ramadan", "রোজা", "রোজা কতদিন", "রোজা কবে") -> Thread { say(Ramadan.daysUntil()) }.start()
            l in setOf("রোজা মোড চালু", "ramadan mode on") -> { act.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).edit().putBoolean("ramadan_mode", true).apply(); say("Ramadan mode on. I will not use pet words.") }
            l in setOf("রোজা মোড বন্ধ", "ramadan mode off") -> { act.getSharedPreferences("cfg", android.content.Context.MODE_PRIVATE).edit().putBoolean("ramadan_mode", false).apply(); say("Ramadan mode off.") }
            l.startsWith("prayer alarms") || l.startsWith("নামাজের অ্যালার্ম") -> Thread {
                val city = if (l.startsWith("prayer alarms")) t.substring(13).trim() else t.substring("নামাজের অ্যালার্ম".length).trim()
                say(AppAlarms.scheduleToday(act, city))
            }.start()
            // Bangladesh: dollar rate and prayer times, from free sources without a key.
            l in setOf("dollar rate", "dollar", "ডলার রেট", "ডলারের দাম") -> Thread { say(Bangladesh.dollarRate()) }.start()
            l.startsWith("prayer ") || l.startsWith("নামাজের সময় ") -> Thread {
                val city = if (l.startsWith("prayer ")) t.substring(7).trim() else t.substring("নামাজের সময় ".length).trim()
                say(Bangladesh.prayerTimes(city))
            }.start()
            // Real network: current weather for a city (no key needed).
            l.startsWith("weather ") || l.startsWith("আবহাওয়া ") -> Thread {
                val city = t.substringAfter(" ").trim()
                say(Weather.report(city))
            }.start()
            l.startsWith("ai ") -> Agent(act, say).start(t.substring(3))
            Llm.configured(act) -> Agent(act, say).start(original)
            else -> say("Unknown command. Try: open youtube, scroll down, share latest video to youtube, set timer 5 minutes, run <routine>. For free-form tasks add an AI key in SETTINGS.")
        }
    }
}
