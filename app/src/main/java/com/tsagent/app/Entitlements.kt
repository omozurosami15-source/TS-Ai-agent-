package com.tsagent.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plans, unlocked with coupon / purchase codes. No server needed.
 *   free   basic assistant
 *   p1     Plan 1: personal companion persona
 *   p2     Plan 2: all paid features (companion + natural voice, floating assistant, phone abilities, web search, autoplay)
 *   owner  the owner: everything plus owner-only features (owner persona, favorite song)
 * Codes are random 80-bit strings; the app and docs/coupons.json hold only their SHA-256 hashes.
 */
object Entitlements {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun now() = System.currentTimeMillis()

    fun plan(ctx: Context): String {
        val p = prefs(ctx)
        val plan = p.getString("plan", "") ?: ""
        val until = p.getLong("plan_until", 0L)
        return if (plan.isNotEmpty() && (until == 0L || now() < until)) plan else ""
    }

    /** Plan codes redeemed on this device. Actual paid activation still requires a verified coupon/server mapping. */
    fun activePlans(ctx: Context): Set<String> {
        val p = prefs(ctx)
        val until = p.getLong("plan_until", 0L)
        val saved = p.getStringSet("active_plans", emptySet())?.toSet() ?: emptySet()
        return saved.filterTo(mutableSetOf()) { id ->
            if (id.startsWith("module:")) {
                val moduleUntil = p.getLong("module_until_" + id.removePrefix("module:"), 0L)
                moduleUntil == 0L || now() < moduleUntil
            } else {
                until == 0L || now() < until
            }
        }
    }

    private val moduleIds = listOf(
        "neural", "voice", "memory", "soul", "vision", "web", "code", "files",
        "tasks", "notes", "briefing", "translate", "research", "calendar",
        "reminders", "security", "analytics"
    )

    private fun entitlementId(planId: String): String {
        if (planId.startsWith("module:")) return planId
        val n = planId.removePrefix("p").toIntOrNull()
        return if (n != null && n in 1..17) "module:" + moduleIds[n - 1] else planId
    }

    fun isPlanActive(ctx: Context, planId: String): Boolean {
        val active = activePlans(ctx)
        return planId in active || entitlementId(planId) in active
    }

    fun selectPlan(ctx: Context, planId: String): Boolean {
        if (!isPlanActive(ctx, planId)) return false
        prefs(ctx).edit().putString("default_plan", planId).apply()
        return true
    }

    fun isOwner(ctx: Context) = plan(ctx) == "owner" && OwnerAuth.unlocked(ctx)
    fun companion(ctx: Context) = Config.ALL_FREE || plan(ctx) in listOf("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "owner")
    fun premium(ctx: Context) = Config.ALL_FREE || plan(ctx) in listOf("p2", "p3", "p4", "p5", "p6", "p7", "p8", "owner")

    fun label(ctx: Context): String {
        val pl = plan(ctx)
        val until = prefs(ctx).getLong("plan_until", 0L)
        val name = when {
            pl.startsWith("module:") -> pl.removePrefix("module:").uppercase(Locale.US)
            pl == "p1" -> "PLAN 1"
            pl == "p2" -> "PLAN 2"
            pl == "owner" -> "OWNER"
            else -> "FREE"
        }
        return if (pl.isEmpty() || pl == "owner" || until == 0L) name
        else name + " until " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(until))
    }

    private class Coupon(val plan: String, val days: Int, val id: String)
    private class Book(val coupons: Map<String, Coupon>, val revoked: Set<String>)

    private fun parse(json: String): Book {
        val j = JSONObject(json)
        val m = HashMap<String, Coupon>()
        val a = j.optJSONArray("coupons") ?: JSONArray()
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            m[c.getString("h")] = Coupon(c.getString("plan"), c.optInt("days", 0), c.getString("id"))
        }
        val r = HashSet<String>()
        val ra = j.optJSONArray("revoked") ?: JSONArray()
        for (i in 0 until ra.length()) r.add(ra.getString(i))
        return Book(m, r)
    }

    private fun book(ctx: Context): Book {
        var base = try { parse(ctx.assets.open("coupons.json").bufferedReader().readText()) } catch (e: Exception) { Book(emptyMap(), emptySet()) }
        val remote = Remote.coupons()?.let { try { parse(it) } catch (e: Exception) { null } }
        if (remote != null) base = Book(base.coupons + remote.coupons, base.revoked + remote.revoked)
        return base
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Blocking (reads the code list from GitHub when online): call from a background thread. */
    fun redeem(ctx: Context, raw: String): String {
        val code = raw.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        if (code.length < 12) return "That code is not valid."
        val b = book(ctx)
        val c = b.coupons[sha256(code)] ?: return "That code is not valid."
        if (c.id in b.revoked) return "That code has been withdrawn."
        val p = prefs(ctx)
        val active = p.getStringSet("active_plans", emptySet())?.toMutableSet() ?: mutableSetOf()
        active.add(c.plan)
        val editor = p.edit()
            .putStringSet("active_plans", active)
        if (c.plan.startsWith("module:")) {
            val slug = c.plan.removePrefix("module:")
            editor.putLong("module_until_" + slug, if (c.days == 0) 0L else now() + c.days * 86_400_000L)
            editor.putString("module_code_id_" + slug, c.id)
            editor.putString("default_plan", c.plan)
        } else {
            editor.putString("plan", c.plan).putString("default_plan", c.plan)
                .putString("plan_code_id", c.id)
                .putLong("plan_until", if (c.days == 0) 0L else now() + c.days * 86_400_000L)
        }
        editor.apply()
        return "Activated: " + (if (c.plan.startsWith("module:")) c.plan.removePrefix("module:").uppercase(Locale.US) else label(ctx))
    }

    /** Blocking. Removes the plan if the code it came from was withdrawn in docs/coupons.json. */
    fun syncRevocations(ctx: Context) {
        val p = prefs(ctx)
        val remote = Remote.coupons()?.let { try { parse(it) } catch (e: Exception) { null } } ?: return
        val editor = p.edit()
        val active = p.getStringSet("active_plans", emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false
        val legacyId = p.getString("plan_code_id", null)
        if (legacyId != null && legacyId in remote.revoked) {
            editor.remove("plan").remove("plan_until").remove("plan_code_id")
            changed = true
        }
        for (module in active.filter { it.startsWith("module:") }) {
            val slug = module.removePrefix("module:")
            val key = "module_code_id_" + slug
            val id = p.getString(key, null)
            if (id != null && id in remote.revoked) {
                active.remove(module)
                editor.remove(key).remove("module_until_" + slug)
                changed = true
            }
        }
        if (changed) editor.putStringSet("active_plans", active).apply()
    }
}
