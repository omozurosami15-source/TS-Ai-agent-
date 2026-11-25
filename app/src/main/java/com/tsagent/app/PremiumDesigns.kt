package com.tsagent.app

/**
 * The 17 premium designs. Each plan has its own look (style, accent colour, hero name and tagline).
 * Designs are drawn in code (PremiumBgView), so they animate and need no image files.
 */
object PremiumDesigns {
    enum class Style { WAVES, NODES, GRID, ORB, BARS, RINGS, SCAN }

    class Spec(val style: Style, val accent: Int, val hero: String, val tagline: String)

    private val specs = listOf(
        Spec(Style.NODES, 0xFF38E8FF.toInt(), "NEURAL CORE", "Deep reasoning, step by step"),
        Spec(Style.WAVES, 0xFFFF5AD9.toInt(), "VOICE STUDIO", "Natural speech, short and clear"),
        Spec(Style.GRID, 0xFF4DB8FF.toInt(), "MEMORY VAULT", "Recall what you saved"),
        Spec(Style.ORB, 0xFF4DF0A8.toInt(), "SOUL STUDIO", "Calm, warm and supportive"),
        Spec(Style.SCAN, 0xFF66F2BD.toInt(), "VISION LAB", "Camera and screen insight"),
        Spec(Style.NODES, 0xFF38C2FF.toInt(), "WEB INTELLIGENCE", "Sources and fact checks"),
        Spec(Style.GRID, 0xFF9E6BFF.toInt(), "CODING STUDIO", "Working code, debugged"),
        Spec(Style.BARS, 0xFF52D1F2.toInt(), "FILE READER", "Documents and key facts"),
        Spec(Style.RINGS, 0xFF59F2B3.toInt(), "TASK AUTOMATION", "Goals turned into steps"),
        Spec(Style.BARS, 0xFFFFB84D.toInt(), "SMART NOTES", "Tidy notes, fast"),
        Spec(Style.RINGS, 0xFF40E0E6.toInt(), "DAILY BRIEFING", "What matters today"),
        Spec(Style.WAVES, 0xFFF261B3.toInt(), "TRANSLATOR", "Bengali and English"),
        Spec(Style.NODES, 0xFF4DCCFF.toInt(), "RESEARCH DESK", "Findings, sources, open questions"),
        Spec(Style.BARS, 0xFF4DE0B3.toInt(), "CALENDAR HUB", "Dates and schedules"),
        Spec(Style.RINGS, 0xFFFFAD40.toInt(), "REMINDER CENTER", "Never miss a time"),
        Spec(Style.SCAN, 0xFF47F2C7.toInt(), "SECURITY CENTER", "Protect accounts and privacy"),
        Spec(Style.BARS, 0xFF73BFFF.toInt(), "SYSTEM ANALYTICS", "Numbers and trends")
    )

    fun get(n: Int): Spec = specs[(n - 1).coerceIn(0, specs.size - 1)]
}

/** Picture designs for the top plans (PREMIUM_RICH). Plans without a picture use the animated code design. */
object PremiumPictures {
    private val files = mapOf(
        1 to "plan_designs/rich_01.png", 2 to "plan_designs/rich_02.png",
        3 to "plan_designs/plan_03_scene.png", 4 to "plan_designs/plan_04_scene.png",
        5 to "plan_designs/rich_05.png", 6 to "plan_designs/rich_06.png",
        7 to "plan_designs/rich_07.png", 8 to "plan_designs/rich_08.png",
        9 to "plan_designs/rich_09.png", 13 to "plan_designs/rich_13.png"
    )
    fun file(n: Int): String? = files[n]
}
