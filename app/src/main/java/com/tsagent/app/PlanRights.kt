package com.tsagent.app

/**
 * The single list of what each plan gives.
 * Buyers, the plan screen and every feature check read from here, so a plan can never promise
 * something the app does not do. To change what a plan includes, change it here only.
 *
 *   free   basic assistant
 *   p1     Plan 1: companion persona
 *   p2     Plan 2: companion + all paid phone features
 *   owner  everything, plus owner-only features (needs owner login)
 *
 * The 17 dashboard plans (PlanCatalog) are separate: each one is an AI specialist mode that
 * is unlocked by its own code, or by Plan 2 / owner. Their rights are listed by describeDashboard().
 */
object PlanRights {
    enum class Feature(val label: String) {
        COMPANION("Companion persona"),
        NATURAL_VOICE("Natural voice replies"),
        FLOATING_ASSISTANT("Floating assistant bubble"),
        PHONE_ACTIONS("Phone actions: calendar, alarm, timer, dial, SMS and email drafts, save file"),
        WEB_SEARCH("Web search in answers"),
        AUTOPLAY("Autoplay"),
        OWNER_PERSONA("Owner persona and favorite song"),
        WATCH_MODE("Watch mode (owner only)")
    }

    private val rights: Map<String, Set<Feature>> = mapOf(
        "free" to emptySet(),
        "p1" to setOf(Feature.COMPANION),
        "p2" to setOf(
            Feature.COMPANION, Feature.NATURAL_VOICE, Feature.FLOATING_ASSISTANT,
            Feature.PHONE_ACTIONS, Feature.WEB_SEARCH, Feature.AUTOPLAY
        ),
        "owner" to Feature.values().toSet()
    )

    /** Features of a plan key: "free", "p1", "p2" or "owner". Unknown keys get nothing. */
    fun features(plan: String): Set<Feature> = rights[plan] ?: emptySet()

    fun has(plan: String, feature: Feature): Boolean = feature in features(plan)

    /** Lines shown to a buyer for Plan 1 / Plan 2 / owner. */
    fun describe(plan: String): List<String> = features(plan).map { it.label }

    /** Lines shown for one dashboard plan (1..17): what its code really unlocks. */
    fun describeDashboard(n: Int): List<String> {
        val p = PlanCatalog.get(n)
        return listOf(
            "AI specialist mode: ${p.name}",
            "Answer length x${p.tokenMul}" + if (p.search) ", web search on" else "",
            "Also included with Plan 2 and owner"
        )
    }

    /**
     * Coins added every month per plan. Placeholder values: the admin panel will set these later.
     * Coins are not spent yet: spending needs the server (see CHANGES-PLAN-RIGHTS.md).
     */
    val monthlyCoins: Map<String, Int> = mapOf(
        "free" to 0, "p1" to 3000, "p2" to 10000, "owner" to Int.MAX_VALUE
    )

    /**
     * Text shown to a buyer. Every line must match what the code really does.
     * Free users chat with their own API key with no limit; they get no paid features.
     */
    val salesText: Map<String, List<String>> = mapOf(
        "free" to listOf(
            "Open the app",
            "Chat with your own API key, no limit",
            "Basic chat only, no special AI features"
        ),
        "p1" to listOf(
            "Companion persona (owner login is still required for it to start)",
            "3,000 coins every month"
        ),
        "p2" to listOf(
            "Everything in Plan 1",
            "Phone actions: calendar, alarm, timer, dial, SMS and email drafts, save file",
            "Floating assistant",
            "Autoplay",
            "Web search in answers (Anthropic provider)",
            "10,000 coins every month"
        )
    )
}
