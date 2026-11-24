package com.tsagent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * cPanel File Manager through the cPanel UAPI, for any number of hosting accounts.
 * Sites are kept on the phone only, in a preference whose name contains "secret" so it is never put in a backup.
 * Each site: name | host | cPanel user | API token | document root (for example public_html).
 */
object CpanelClient {
    data class Site(val name: String, val host: String, val user: String, val token: String, val root: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun sites(ctx: Context): List<Site> {
        val raw = prefs(ctx).getString("cp_secret_sites", "") ?: ""
        if (raw.isBlank()) return emptyList()
        val arr = try { JSONArray(raw) } catch (e: Exception) { return emptyList() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Site(o.optString("name"), o.optString("host"), o.optString("user"), o.optString("token"), o.optString("root", "public_html"))
        }
    }

    /** Reads lines like: name | host | user | token | public_html  and saves them. Returns how many were saved. */
    fun saveFromText(ctx: Context, text: String): Int {
        val arr = JSONArray()
        for (line in text.lines()) {
            val p = line.split("|").map { it.trim() }
            if (p.size < 4 || p[0].isBlank() || p[1].isBlank() || p[2].isBlank() || p[3].isBlank()) continue
            val o = JSONObject()
            o.put("name", p[0]); o.put("host", p[1].removePrefix("https://").removePrefix("http://").trimEnd('/'))
            o.put("user", p[2]); o.put("token", p[3]); o.put("root", p.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "public_html")
            arr.put(o)
        }
        prefs(ctx).edit().putString("cp_secret_sites", arr.toString()).apply()
        return arr.length()
    }

    fun siteNames(ctx: Context): String = sites(ctx).joinToString(", ") { it.name }.ifBlank { "(none)" }

    private fun site(ctx: Context, name: String): Site? = sites(ctx).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** Keeps the path inside the site's document root: no absolute paths, no "..". */
    private fun split(root: String, path: String): Pair<String, String>? {
        val clean = path.trim().trimStart('/')
        if (clean.isBlank() || clean.contains("..") || clean.contains("\\")) return null
        val slash = clean.lastIndexOf('/')
        val dir = if (slash < 0) root else root.trimEnd('/') + "/" + clean.substring(0, slash)
        val file = if (slash < 0) clean else clean.substring(slash + 1)
        return dir to file
    }

    private fun call(s: Site, func: String, params: Map<String, String>, post: Boolean, module: String = "Fileman"): String {
        val query = params.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val url = URL("https://${s.host}:2083/execute/$module/$func" + if (!post && query.isNotEmpty()) "?$query" else "")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("Authorization", "cpanel ${s.user}:${s.token}")
        if (post) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(query.toByteArray()) }
        }
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 401 || code == 403) return "cPanel refused the login (code $code). Check the user and API token."
        if (code !in 200..299) return "cPanel error $code."
        val j = JSONObject(body)
        if (j.optInt("status") != 1) return "cPanel error: " + (j.optJSONArray("errors")?.optString(0) ?: "unknown")
        return j.opt("data")?.toString() ?: ""
    }

    fun list(ctx: Context, siteName: String, dir: String): String {
        val s = site(ctx, siteName) ?: return "No cPanel site named $siteName. Add it in Settings."
        val p = split(s.root, "$dir/x") ?: return "Path not allowed."
        return try { call(s, "list_files", mapOf("dir" to p.first), false).take(3000) } catch (e: Exception) { "Could not reach cPanel: ${e.message}" }
    }

    fun read(ctx: Context, siteName: String, path: String): String {
        val s = site(ctx, siteName) ?: return "No cPanel site named $siteName. Add it in Settings."
        val p = split(s.root, path) ?: return "Path not allowed."
        return try {
            val data = call(s, "get_file_content", mapOf("dir" to p.first, "file" to p.second), false)
            data.take(4000)
        } catch (e: Exception) { "Could not reach cPanel: ${e.message}" }
    }

    /** Writes count per run, so one task cannot change a site without limit. */
    @Volatile var writesThisRun = 0

    /** Creates a database, its user and full rights, all with the account prefix. The password stays on the phone. */
    fun createDb(ctx: Context, siteName: String, short: String): String {
        val s = site(ctx, siteName) ?: return "No cPanel site named $siteName. Add it in Settings."
        if (!Regex("[A-Za-z0-9_]{1,24}").matches(short)) return "Use only letters, numbers and _ for the database name."
        val db = s.user + "_" + short
        val dbUser = s.user + "_" + short.take(10) + "u"
        val pass = (1..20).map { "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")
        return try {
            call(s, "create_database", mapOf("name" to db), true, "Mysql")
            call(s, "create_user", mapOf("name" to dbUser, "password" to pass), true, "Mysql")
            call(s, "set_privileges_on_database", mapOf("user" to dbUser, "database" to db, "privileges" to "ALL PRIVILEGES"), true, "Mysql")
            ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                .putString("cp_secret_db_" + s.name + "_" + short, dbUser + "|" + pass).apply()
            "Database $db and user $dbUser created. Password saved on this phone only."
        } catch (e: Exception) { "Could not reach cPanel: ${e.message}" }
    }

    /** Opens the site address and reports the HTTP answer. */
    fun check(url: String): String = try {
        val c = URL(if (url.startsWith("http")) url else "https://$url").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
        "Site answered HTTP " + c.responseCode + " for " + url
    } catch (e: Exception) { "Site did not answer: " + e.message }

    /** Writes one file. Called by the agent, which decides whether the user already approved it. */
    fun write(ctx: Context, siteName: String, path: String, content: String): String {
        val s = site(ctx, siteName) ?: return "No cPanel site named $siteName. Add it in Settings."
        val p = split(s.root, path) ?: return "Path not allowed."
        if (writesThisRun >= 12) return "Write limit reached for this task (12 files)."
        writesThisRun++
        return try {
            call(s, "save_file_content", mapOf("dir" to p.first, "file" to p.second, "content" to content), true)
            "Saved " + path + " on " + s.name + "."
        } catch (e: Exception) { "Could not reach cPanel: ${e.message}" }
    }
}
