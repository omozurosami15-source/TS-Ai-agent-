package com.tsagent.app

import android.content.Context

/**
 * The AI system of each of the 17 plans.
 *
 * What changes per plan (all applied inside Llm, so chat, voice and phone tasks use it automatically):
 *   1. specialist instructions (the plan's role) added to the system prompt
 *   2. the model: TS Cloud uses the plan's tier (1 light, 2 standard, 3 pro); with your own key you can set a
 *      different model for each plan and each provider (pmodel_<plan>_<provider>), empty = the model chosen in SETTINGS
 *   3. the answer length budget
 *   4. web search for the research style plans (6 and 13)
 * The intelligence still comes from the provider and key you use: a plan does not add a new foundation model.
 */
object PlanAi {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Set only while the plan screen tests a plan's AI, so a plan can be tried before it is the default. */
    @Volatile var forced: Int = 0

    /** The plan whose AI is working now: 0 = none (free). */
    fun active(ctx: Context): Int {
        if (forced in 1..17) return forced
        return Entitlements.dashboardPlanNumber(ctx).takeIf { it in 1..17 } ?: 0
    }

    fun profile(ctx: Context): PlanCatalog.Plan? = active(ctx).takeIf { it in 1..17 }?.let { PlanCatalog.get(it) }

    /** Added to the end of the system prompt of every AI request. Empty on the free plan. */
    fun systemBlock(ctx: Context): String {
        val p = profile(ctx) ?: return ""
        return "\n\n[Plan engine: " + p.name + "]\n" + p.role +
            "\nIf the instructions above ask for an exact output format (JSON, one command, only the reply text), follow that format exactly."
    }

    /** Model for this plan and provider, or "" to use the normal model of the provider. */
    fun modelFor(ctx: Context, provider: String): String {
        val p = profile(ctx) ?: return ""
        val own = prefs(ctx).getString("pmodel_${p.n}_$provider", "").orEmpty().trim()
        if (own.isNotEmpty()) return own
        return if (provider == "cloud") p.tier.toString() else ""
    }

    fun setModel(ctx: Context, plan: Int, provider: String, model: String) {
        prefs(ctx).edit().putString("pmodel_${plan}_$provider", model.trim()).apply()
    }

    fun savedModel(ctx: Context, plan: Int, provider: String): String =
        prefs(ctx).getString("pmodel_${plan}_$provider", "").orEmpty().trim()

    fun tokens(ctx: Context, base: Int): Int {
        val p = profile(ctx) ?: return base
        return (base * p.tokenMul).toInt().coerceIn(120, 4096)
    }

    /** Research plans search the web for long answers (short phone commands never trigger a search). */
    fun search(ctx: Context, maxTokens: Int): Boolean = (profile(ctx)?.search == true) && maxTokens >= 1000

    /** One line for the plan screen: which engine this plan uses right now. */
    fun summary(ctx: Context, n: Int): String {
        val p = PlanCatalog.get(n)
        val provider = Llm.provider(ctx)
        val own = savedModel(ctx, n, provider)
        val model = when {
            own.isNotEmpty() -> own
            provider == "cloud" -> "TS Cloud tier ${p.tier}"
            else -> Llm.model(ctx, provider).ifBlank { "(choose a model in SETTINGS)" }
        }
        val level = when (p.tier) { 1 -> "FAST"; 3 -> "DEEP"; else -> "BALANCED" }
        return "ENGINE  " + provider.uppercase() + " / " + model + "\n" +
            "MODE  " + level + "  •  ANSWER LENGTH x" + p.tokenMul + "  •  WEB SEARCH " + (if (p.search) "ON" else "OFF")
    }
}
