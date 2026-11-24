package com.tsagent.app

import android.content.Context

/** Batch 1 of the phone helpers: reply draft (you approve), expense notes, storage report. */
object Batch1 {
    private fun p(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Writes a short reply to the last chat message. Shows it; nothing is sent. */
    fun draft(ctx: Context, onText: (String) -> Unit) {
        val sender = p(ctx).getString("last_msg_sender", "") ?: ""
        val body = p(ctx).getString("last_msg_body", "") ?: ""
        if (body.isBlank()) { onText("No new message is saved yet."); return }
        Thread {
            val draft = try {
                Llm.complete(ctx, "You write a short, polite reply to a chat message for the phone owner. Same language as the message. Only the reply text, no explanation.",
                    "Message from $sender: $body", 120, false).trim()
            } catch (e: Exception) { "" }
            if (draft.isBlank()) { onText("I could not write a draft right now. Check the AI key."); return@Thread }
            p(ctx).edit().putString("draft_reply", draft).apply()
            onText("Draft for $sender: $draft\nSay 'send draft' to send it, or ignore it.")
        }.start()
    }

    /** Sends the approved draft, only to WhatsApp contacts found by name in your phone contacts. */
    fun sendDraft(ctx: Context): String {
        val draft = p(ctx).getString("draft_reply", "") ?: ""
        val sender = p(ctx).getString("last_msg_sender", "") ?: ""
        val pkg = p(ctx).getString("last_msg_pkg", "") ?: ""
        if (draft.isBlank()) return "There is no draft. Say 'draft reply' first."
        if (pkg != "com.whatsapp") return "For Telegram or imo, I will not send automatically. Please reply in the app yourself."
        val num = ContactsLookup.numberFor(ctx, sender) ?: return "I could not find $sender in your contacts."
        p(ctx).edit().remove("draft_reply").apply()
        return FastSend.whatsapp(ctx, num, draft)
    }

    /** "খরচ 120 চা": adds an expense to today's list. */
    fun addExpense(ctx: Context, raw: String): String {
        val parts = raw.trim().split(Regex("\\s+"), limit = 2)
        val amount = parts.getOrNull(0)?.toIntOrNull() ?: return "Say the amount first, for example: expense 120 tea"
        val item = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "other" }
        val line = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + " | " + amount + " | " + item
        val old = p(ctx).getString("expenses", "") ?: ""
        p(ctx).edit().putString("expenses", (old + line + "\n").takeLast(6000)).apply()
        return "Saved: $amount for $item."
    }

    fun expenseReport(ctx: Context): String {
        val lines = (p(ctx).getString("expenses", "") ?: "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "No expenses saved yet."
        val total = lines.sumOf { it.split("|").getOrNull(1)?.trim()?.toIntOrNull() ?: 0 }
        return "Expenses: " + lines.size + " item(s), total " + total + ". Last: " + lines.takeLast(3).joinToString(" / ")
    }
}
