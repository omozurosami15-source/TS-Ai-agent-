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

    fun label(ctx: Context): String {
        val pl = plan(ctx)
        val until = prefs(ctx).getLong("plan_until", 0L)
        val name = when (pl) { "p1" -> "PLAN 1"; "p2" -> "PLAN 2"; "owner" -> "OWNER"; else -> "FREE" }
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
        prefs(ctx).edit().putString("plan", c.plan).putString("plan_code_id", c.id)
            .putLong("plan_until", if (c.days == 0) 0L else now() + c.days * 86_400_000L).apply()
        return "Activated: " + label(ctx)
    }

    /** Blocking. Removes the plan if the code it came from was withdrawn in docs/coupons.json. */
    fun syncRevocations(ctx: Context) {
        val id = prefs(ctx).getString("plan_code_id", null) ?: return
        val remote = Remote.coupons()?.let { try { parse(it) } catch (e: Exception) { null } } ?: return
        if (id in remote.revoked) prefs(ctx).edit().remove("plan").remove("plan_until").remove("plan_code_id").apply()
    }
}
