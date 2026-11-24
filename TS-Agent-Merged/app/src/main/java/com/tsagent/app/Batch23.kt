package com.tsagent.app

import android.content.Context

/** Batch 2 and 3: message summary, price comparison (with web search), monthly expense summary. */
object Batch23 {
    private fun p(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Short summary of the last chat messages saved from notifications. Read-only. */
    fun messageSummary(ctx: Context, onText: (String) -> Unit) {
        val hist = p(ctx).getString("msg_history", "") ?: ""
        if (hist.isBlank()) { onText("No chat messages are saved yet."); return }
        Thread {
            val out = try {
                Llm.complete(ctx, "Summarise these chat messages in 3-5 short lines, same language as the messages. Name who wrote what. No advice.", hist, 300, false).trim()
            } catch (e: Exception) { "Could not summarise right now: " + e.message }
            onText(out)
        }.start()
    }

    /** Price comparison with web search. Shows sources as the model gives them. */
    fun priceCompare(ctx: Context, item: String, onText: (String) -> Unit) {
        if (item.isBlank()) { onText("Say the item name, for example: price compare rice 5kg"); return }
        Thread {
            val out = try {
                Llm.complete(ctx, "You compare prices of a product in Bangladesh and online. List 3 options with price, shop or site name and date if known. Say clearly if you are not sure.", "Compare prices: " + item, 400, true).trim()
            } catch (e: Exception) { "Could not compare prices right now: " + e.message }
            onText(out)
        }.start()
    }

    /** Monthly summary of expenses saved with "expense ...". */
    fun monthlySummary(ctx: Context): String {
        val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.ENGLISH).format(java.util.Date())
        val lines = (p(ctx).getString("expenses", "") ?: "").lines().filter { it.startsWith(month) }
        if (lines.isEmpty()) return "No expenses this month yet."
        val total = lines.sumOf { it.split("|").getOrNull(1)?.trim()?.toIntOrNull() ?: 0 }
        val byItem = lines.groupBy { it.split("|").getOrNull(2)?.trim() ?: "other" }
            .map { (k, v) -> k + ": " + v.sumOf { it.split("|").getOrNull(1)?.trim()?.toIntOrNull() ?: 0 } }
        return "This month (" + month + "): total " + total + ". " + byItem.joinToString(", ")
    }
}
