package com.tsagent.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/**
 * AI Builder: describe a website or an app, the AI writes the files, and they are saved in the app's
 * own folder on the phone. Uses the plan AI with an optional stronger builder model (set below).
 * Plans: Feature.BUILDER (Plan 2 and up).
 */
class BuilderActivity : Activity() {
    private val accent = 0xFF7DFF9A.toInt()
    private val panel = 0xFF07120D.toInt()
    private lateinit var status: TextView
    private lateinit var files: TextView
    private lateinit var idea: EditText
    private lateinit var projectName: EditText
    private lateinit var modelBox: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Entitlements.can(this, PlanRights.Feature.BUILDER)) { finish(); return }
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        col.addView(label("AI BUILDER", 18f, accent, true))
        col.addView(label("Describe a website or app. The AI writes the files and saves them on your phone.", 12f, 0xFFB8D8CC.toInt()))

        projectName = field("Project name, e.g. my-shop", prefs.getString("builder_last", "") ?: "")
        col.addView(projectName)
        idea = field("What to build, e.g. a restaurant site with menu, contact form and dark theme")
        idea.minLines = 3
        col.addView(idea)

        // a stronger model can be used only for building (leave empty to use the current model)
        modelBox = field("Builder model (optional), e.g. a stronger model name", prefs.getString("builder_model", "") ?: "")
        col.addView(modelBox)
        col.addView(Button(this).apply { text = "SAVE MODEL"; isAllCaps = false; setOnClickListener {
            prefs.edit().putString("builder_model", modelBox.text.toString().trim()).apply()
            status.text = "Builder model saved."
        } }, LinearLayout.LayoutParams(-1, -2))

        col.addView(Button(this).apply { text = "BUILD"; isAllCaps = false; setOnClickListener { build() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(Button(this).apply { text = "ZIP"; isAllCaps = false; setOnClickListener { zipNow() } },
            LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(Button(this).apply { text = "DEPLOY"; isAllCaps = false; setOnClickListener { deployNow() } },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        tools.addView(Button(this).apply { text = "DATABASE"; isAllCaps = false; setOnClickListener { databaseNow() } },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(6) })
        col.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        status = label("", 13f, Color.WHITE)
        col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        files = TextView(this).apply {
            textSize = 12f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(12), dp(10), dp(12), dp(10)); background = card(accent, panel, 12)
        }
        col.addView(files, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF03080A.toInt()); isFillViewport = true; addView(col) })
    }

    private fun build() {
        val what = idea.text.toString().trim()
        if (what.isEmpty()) { status.text = "Describe what to build first."; return }
        val name = safeName(projectName.text.toString().ifBlank { "project" })
        getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("builder_last", name).apply()
        status.text = "Building... this can take a minute."
        files.text = ""
        Thread {
            val result = try { withBuilderModel { Llm.complete(this, SYSTEM, what, 4000, false) } } catch (e: Exception) { "ERROR: " + (e.message ?: "") }
            runOnUiThread {
                if (result.startsWith("ERROR")) { status.text = "Build failed. ${result.removePrefix("ERROR: ")}"; return@runOnUiThread }
                val written = writeFiles(name, result)
                if (written.isEmpty()) status.text = "The AI reply had no files. Try again with more detail."
                else {
                    status.text = "Saved ${written.size} file(s) in: " + projectDir(name).absolutePath
                    files.text = written.joinToString("\n") { "• $it" }
                }
            }
        }.start()
    }

    /** Runs the call with the builder model, then puts the normal model back. */
    private fun <T> withBuilderModel(block: () -> T): T {
        val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val provider = Llm.provider(this)
        val key = "model_$provider"
        val saved = prefs.getString(key, null)
        val builder = prefs.getString("builder_model", "")?.trim().orEmpty()
        if (builder.isNotEmpty()) prefs.edit().putString(key, builder).apply()
        try { return block() } finally {
            if (builder.isNotEmpty()) {
                if (saved == null) prefs.edit().remove(key).apply() else prefs.edit().putString(key, saved).apply()
            }
        }
    }

    /** Reads the files from the AI reply and writes them inside the project folder. Returns the paths written. */
    private fun writeFiles(name: String, reply: String): List<String> {
        val start = reply.indexOf('{'); val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val arr = try { JSONObject(reply.substring(start, end + 1)).optJSONArray("files") } catch (e: Exception) { null } ?: return emptyList()
        val root = projectDir(name)
        val out = mutableListOf<String>()
        for (i in 0 until minOf(arr.length(), 40)) {
            val f = arr.optJSONObject(i) ?: continue
            val path = f.optString("path").trim().trimStart('/')
            val content = f.optString("content")
            if (path.isBlank() || path.contains("..") || content.length > 300_000) continue
            val target = File(root, path)
            if (!target.canonicalPath.startsWith(root.canonicalPath)) continue
            target.parentFile?.mkdirs()
            target.writeText(content)
            out += path
        }
        return out
    }

    private fun projectDir(name: String) = File(File(filesDir, "projects"), name).apply { mkdirs() }

    private fun currentName() = safeName(projectName.text.toString().ifBlank { "project" })

    private fun zipNow() {
        val name = currentName()
        val dir = projectDir(name)
        if (dir.walkTopDown().none { it.isFile }) { status.text = "Build the project first."; return }
        val zip = File(File(filesDir, "projects"), "$name.zip")
        java.util.zip.ZipOutputStream(zip.outputStream().buffered()).use { z ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(dir).path.replace('\\', '/')))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        status.text = "Zipped: " + zip.absolutePath
    }

    /** Uploads the project files to the cPanel site set in Settings, checks the site, and asks the AI to fix up to two times. */
    private fun deployNow() {
        val name = currentName()
        val dir = projectDir(name)
        if (dir.walkTopDown().none { it.isFile }) { status.text = "Build the project first."; return }
        val site = CpanelClient.sites(this).firstOrNull()
        if (site == null) { status.text = "No cPanel site is set up in Settings yet."; return }
        android.app.AlertDialog.Builder(this).setTitle("Upload to cPanel?")
            .setMessage("Upload ${dir.walkTopDown().count { it.isFile }} files to ${site.name} and check the site. Nothing is uploaded until you tap UPLOAD.")
            .setPositiveButton("UPLOAD") { _, _ ->
        status.text = "Uploading to ${site.name}..."
        Thread {
            var round = 0
            var last: String
            try {
                while (true) {
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        CpanelClient.write(this, site.name, f.relativeTo(dir).path.replace('\\', '/'), f.readText())
                    }
                    val url = "https://" + site.host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/') + "/"
                    val res = siteCheck(url)
                    last = res.second
                    if (res.first || round >= 2) break
                    round++
                    runOnUiThread { status.text = "Site check failed. Asking the AI to fix it (round $round)..." }
                    val fix = withBuilderModel { Llm.complete(this, SYSTEM_FIX, "Site check result: $last\n\nCurrent files:\n" + dumpFiles(dir), 4000, false) }
                    writeFiles(name, fix)
                }
                val msg = "Uploaded to ${site.name}. Site check: " + last.take(200)
                runOnUiThread { status.text = msg }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Upload failed: " + (e.message ?: "") }
            }
        }.start()
        }.setNegativeButton("CANCEL", null).show()
    }

    /** Creates a database on the cPanel site, named after the project. */
    private fun databaseNow() {
        val name = currentName()
        val site = CpanelClient.sites(this).firstOrNull()
        if (site == null) { status.text = "No cPanel site is set up in Settings yet."; return }
        android.app.AlertDialog.Builder(this).setTitle("Create a database?")
            .setMessage("Create database ${name.take(8)} on ${site.name}. The password is stored only on this phone.")
            .setPositiveButton("CREATE") { _, _ ->
        status.text = "Creating database..."
        Thread {
            val res = try { CpanelClient.createDb(this, site.name, name.take(8)) } catch (e: Exception) { "Error: " + (e.message ?: "") }
            runOnUiThread { status.text = "Database: " + res.take(300) }
        }.start()
            }.setNegativeButton("CANCEL", null).show()
    }

    /** Opens the site: passes only if the answer is below 400 and the page shows no PHP or database error text. */
    private fun siteCheck(url: String): Pair<Boolean, String> = try {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = true
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val body = stream?.bufferedReader()?.use { it.readText().take(4000) } ?: ""
        val bad = code >= 400 || Regex("(?i)(fatal error|parse error|warning:|notice:|undefined (variable|index|function)|mysql.*error)").containsMatchIn(body)
        Pair(!bad, "HTTP $code" + if (bad) ". Page errors: " + body.take(800) else ". No errors on the page.")
    } catch (e: Exception) {
        Pair(false, "Site did not answer: " + (e.message ?: ""))
    }

    private fun dumpFiles(dir: File): String {
        val sb = StringBuilder()
        dir.walkTopDown().filter { it.isFile }.forEach { f ->
            if (sb.length < 60_000) sb.append("--- ").append(f.relativeTo(dir).path).append('\n').append(f.readText().take(12_000)).append('\n')
        }
        return sb.toString()
    }

    private fun safeName(s: String) = s.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-').ifBlank { "project" }

    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(Color.WHITE); setHintTextColor(0xFF6E8A7A.toInt())
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply { setColor(panel); setStroke(dp(1), accent); cornerRadius = dp(12).toFloat() }
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun card(stroke: Int, fill: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radiusDp).toFloat()
    }

    companion object {
        private const val SYSTEM_FIX = """You fix a website project that failed its check. Find the cause from the check result and the files, and fix it.
Reply with ONLY one JSON object, no markdown: {"files":[{"path":"...","content":"...full corrected file..."}]}. Include only the files you change."""
        private const val SYSTEM = """You are an expert software builder. Create the complete, working project the user describes.
Reply with ONLY one JSON object, no markdown, no code fences, no other text:
{"files":[{"path":"index.html","content":"...full file content..."}]}
Rules: relative paths only (no leading slash, no ..). Put every file the project needs (HTML, CSS, JS, PHP, config, README). Keep each file complete. Use clear, modern, responsive design."""
    }
}
