package com.tsagent.app

/** Cleans AI text for display: removes the markdown symbols (**bold**, __bold__, # headings, * lists). */
object Md {
    fun plain(s: String): String = s
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
}
