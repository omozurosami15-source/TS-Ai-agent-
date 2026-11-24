package com.tsagent.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Reads public GitHub data only: latest release and the announcement file. No server of our own. */
object Remote {
    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
    } catch (e: Exception) { null }

    /** Blocking. Returns a message when a newer release exists. */
    fun checkUpdate(ctx: Context): String? {
        if (!Config.githubReady()) return null
        val j = try { JSONObject(get("https://api.github.com/repos/${Config.GITHUB_REPO}/releases/latest") ?: return null) } catch (e: Exception) { return null }
        val tag = j.optString("tag_name").removePrefix("v")
        val cur = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }
        return if (tag.isNotBlank() && newer(tag, cur)) "Update available: v$tag (you have v$cur). Open SETTINGS > UPDATE / RELEASES to download." else null
    }

    /** Blocking. Reads docs/notice.json from the repo. */
    fun notice(): String? {
        if (!Config.githubReady()) return null
        val s = get("https://raw.githubusercontent.com/${Config.GITHUB_REPO}/main/docs/notice.json") ?: return null
        return try { JSONObject(s).optString("notice").ifBlank { null } } catch (e: Exception) { null }
    }

    /** Blocking. The public coupon hash list in the repo (docs/coupons.json), or null when offline. */
    fun coupons(): String? {
        if (!Config.githubReady()) return null
        return get("https://raw.githubusercontent.com/${Config.GITHUB_REPO}/main/docs/coupons.json")
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split(".", "-").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".", "-").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }
}
