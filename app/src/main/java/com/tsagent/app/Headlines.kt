package com.tsagent.app

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

/** Reads public RSS feeds straight from the phone. No server, no API key. */
object Headlines {
    data class Item(val title: String, val link: String)

    private const val DEFAULT_FEED = "https://feeds.bbci.co.uk/news/rss.xml"

    private fun feeds(ctx: Context): List<String> {
        val saved = (ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("feeds", "") ?: "")
            .lines().map { it.trim() }.filter { it.startsWith("https://") }
        return saved.ifEmpty { listOf(DEFAULT_FEED) }
    }

    /** Blocking: call from a background thread. Tries each feed until one works. */
    fun fetch(ctx: Context, max: Int): List<Item> {
        for (url in feeds(ctx)) {
            val items = try { parse(url, max) } catch (e: Exception) { emptyList() }
            if (items.isNotEmpty()) return items
        }
        return emptyList()
    }

    private fun parse(url: String, max: Int): List<Item> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "TS-Agent")
        if (c.responseCode != 200) return emptyList()
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(c.inputStream, null)
        val out = ArrayList<Item>()
        var inItem = false
        var title = ""
        var link = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && out.size < max) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item", "entry" -> { inItem = true; title = ""; link = "" }
                    "title" -> if (inItem) title = p.nextText().trim()
                    "link" -> if (inItem) link = (p.getAttributeValue(null, "href") ?: p.nextText()).trim()
                }
            } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
                if (title.isNotEmpty() && link.startsWith("http")) out.add(Item(title, link))
                inItem = false
            }
            ev = p.next()
        }
        return out
    }
}
