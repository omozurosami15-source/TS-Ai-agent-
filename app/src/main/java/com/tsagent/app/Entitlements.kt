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

    fun isOwner(ctx: Context) = plan(ctx) == "owner" && OwnerAuth.unlocked(ctx)
    fun companion(ctx: Context) = Config.ALL_FREE || plan(ctx) in listOf("p1", "p2", "owner")
    fun premium(ctx: Context) = Config.ALL_FREE || plan(ctx) in listOf("p2", "owner")

<<<<<<< HEAD
    fun label(ctx: Context): String {
        val pl = plan(ctx)
        val until = prefs(ctx).getLong("plan_until", 0L)
        val name = when (pl) { "p1" -> "PLAN 1"; "p2" -> "PLAN 2"; "owner" -> "OWNER"; else -> "FREE" }
=======
    /**
     * Checks whether a dashboard plan is currently entitled.
     * Dashboard plan IDs are p1..p17; owner access includes every plan.
     */
    private val modulePlanIds = mapOf(
        "neural" to 1, "voice" to 2, "memory" to 3, "soul" to 4,
        "vision" to 5, "web" to 6, "code" to 7, "files" to 8,
        "tasks" to 9, "notes" to 10, "briefing" to 11, "translate" to 12,
        "research" to 13, "calendar" to 14, "reminders" to 15,
        "security" to 16, "analytics" to 17
    )

    // ---- plans owned by this phone: p1..p17 (a code for a module counts as its plan). Buying several is allowed. ----
    private fun ownedJson(ctx: Context): JSONObject =
        try { JSONObject(prefs(ctx).getString("owned", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }

    /** "p7" for plan 7 and for the module code of plan 7; null for anything that is not a dashboard plan. */
    private fun planKeyOf(plan: String): String? {
        if (plan.matches(Regex("p(?:[1-9]|1[0-7])"))) return plan
        val n = modulePlanIds[plan.removePrefix("module:")] ?: return null
        return "p$n"
    }

    /** Every dashboard plan that is currently usable on this phone, in ascending order. */
    fun ownedPlanNumbers(ctx: Context): List<Int> {
        val out = HashSet<Int>()
        val o = ownedJson(ctx)
        val ks = o.keys()
        while (ks.hasNext()) {
            val key = ks.next()
            val e = o.optJSONObject(key) ?: continue
            val until = e.optLong("u", 0L)
            val n = key.removePrefix("p").toIntOrNull() ?: continue
            if (n in 1..17 && (until == 0L || now() < until)) out.add(n)
        }
        planKeyOf(plan(ctx))?.removePrefix("p")?.toIntOrNull()?.let { if (it in 1..17) out.add(it) }
        return out.sorted()
    }

    /**
     * The plan that is shown on the Home screen and whose AI is used: 0 = the original free Home.
     * The owner picks any plan; everybody else gets the default they chose among the plans they own
     * (the plan they bought last until they choose another default).
     */
    fun dashboardPlanNumber(ctx: Context): Int {
        val chosen = prefs(ctx).getInt("dashboard_plan", 0)
        if (Config.ALL_FREE) return chosen.coerceIn(0, 17)
        if (plan(ctx) == "owner") return (if (chosen in 1..17) chosen else 1)
        val owned = ownedPlanNumbers(ctx)
        if (owned.isEmpty()) return 0
        if (chosen in owned) return chosen
        val last = prefs(ctx).getInt("owned_last", 0)
        return if (last in owned) last else owned.last()
    }

    /** A dashboard plan is unlocked by its code (or module code), or by the owner login. */
    fun isPlanActive(ctx: Context, planId: String): Boolean {
        if (!planId.matches(Regex("p(?:[1-9]|1[0-7])"))) return false
        if (Config.ALL_FREE) return true
        val number = planId.removePrefix("p").toIntOrNull() ?: return false
        if (plan(ctx) == "owner") return true
        return number in ownedPlanNumbers(ctx)
    }

    /** Makes a plan the default shown on Home, only when it is really unlocked. Selecting never grants access. */
    fun selectPlan(ctx: Context, planId: String): Boolean {
        if (!isPlanActive(ctx, planId)) return false
        val n = planId.removePrefix("p").toIntOrNull() ?: return false
        prefs(ctx).edit().putInt("dashboard_plan", n).apply()
        return true
    }

    fun label(ctx: Context): String {
        val pl = plan(ctx)
        val until = prefs(ctx).getLong("plan_until", 0L)
        val moduleName = modulePlanIds.entries.firstOrNull { it.value == dashboardPlanNumber(ctx) }?.key
        val name = when {
            pl == "p1" -> "PLAN 1"
            pl == "p2" -> "PLAN 2"
            pl == "owner" -> "OWNER"
            pl.startsWith("module:") -> moduleName?.uppercase(Locale.US) ?: "MODULE"
            else -> "FREE"
        }
>>>>>>> 26712bef9165d438e019f5218df1752f7d2450eb
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
<<<<<<< HEAD
        prefs(ctx).edit().putString("plan", c.plan).putString("plan_code_id", c.id)
            .putLong("plan_until", if (c.days == 0) 0L else now() + c.days * 86_400_000L).apply()
=======
        val until = if (c.days == 0) 0L else now() + c.days * 86_400_000L
        prefs(ctx).edit().putString("plan", c.plan).putString("plan_code_id", c.id)
            .putLong("plan_until", until).apply()
        planKeyOf(c.plan)?.let { key ->
            val o = ownedJson(ctx)
            o.put(key, JSONObject().put("u", until).put("id", c.id))
            val n = key.removePrefix("p").toIntOrNull() ?: 0
            // the plan that was just bought is shown on Home straight away; it can be changed later in the plan screen
            prefs(ctx).edit().putString("owned", o.toString()).putInt("owned_last", n).putInt("dashboard_plan", n).apply()
        }
>>>>>>> 26712bef9165d438e019f5218df1752f7d2450eb
        return "Activated: " + label(ctx)
    }

    /** Blocking. Removes the plan if the code it came from was withdrawn in docs/coupons.json. */
    fun syncRevocations(ctx: Context) {
<<<<<<< HEAD
        val id = prefs(ctx).getString("plan_code_id", null) ?: return
        val remote = Remote.coupons()?.let { try { parse(it) } catch (e: Exception) { null } } ?: return
        if (id in remote.revoked) prefs(ctx).edit().remove("plan").remove("plan_until").remove("plan_code_id").apply()
=======
        val remote = Remote.coupons()?.let { try { parse(it) } catch (e: Exception) { null } } ?: return
        val id = prefs(ctx).getString("plan_code_id", null)
        if (id != null && id in remote.revoked) prefs(ctx).edit().remove("plan").remove("plan_until").remove("plan_code_id").apply()
        val o = ownedJson(ctx)
        var changed = false
        val keys = ArrayList<String>()
        val ks = o.keys()
        while (ks.hasNext()) keys.add(ks.next())
        for (k in keys) {
            val cid = o.optJSONObject(k)?.optString("id").orEmpty()
            if (cid.isNotEmpty() && cid in remote.revoked) { o.remove(k); changed = true }
        }
        if (changed) prefs(ctx).edit().putString("owned", o.toString()).apply()
>>>>>>> 26712bef9165d438e019f5218df1752f7d2450eb
    }
}
