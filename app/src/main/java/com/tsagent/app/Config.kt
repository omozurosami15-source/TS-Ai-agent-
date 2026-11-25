package com.tsagent.app

object Config {
    // Your GitHub repository as OWNER/REPO. Used for update checks, announcements and feature requests.
    const val GITHUB_REPO = "omozurosami15-source/TS-Ai-agent-"
    // Optional backend. Leave empty to run fully on GitHub with no server and no login.
    const val APP_VERSION = "20"
    const val SERVER = "https://tstsvibe.site.je"
    // Speech language for voice input and spoken replies, for example "en-US" or "hi-IN"
    const val VOICE_LOCALE = "en-US"
    // Owner details the app gives out when someone asks who owns it or how to contact the owner
    const val OWNER_NAME = "Tarek"
    const val OWNER_CONTACT = "+8801622058034"
    // Plans are unlocked with codes (see Entitlements.kt). Set ALL_FREE = true only to test every feature without a code.
    const val ALL_FREE = false
    // Where people get a plan or code
    const val BUY_URL = "https://t.me/ts_official_top"
    const val TELEGRAM_URL = "https://t.me/ts_official_top"
    fun enabled() = SERVER.isNotBlank() && !SERVER.contains("YOUR-DOMAIN")
    fun githubReady() = !GITHUB_REPO.startsWith("OWNER")
}
