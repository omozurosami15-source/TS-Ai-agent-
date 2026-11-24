package com.tsagent.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Swipes to the next video every N seconds (TikTok, Shorts, Reels style feeds) for up to an hour. Stop with "stop". Plan 2. */
object Autoplay {
    @Volatile var running = false
    private val main = Handler(Looper.getMainLooper())
    private var task: Runnable? = null
    private var appCtx: Context? = null

    fun start(ctx: Context, seconds: Int): String {
        if (!Entitlements.premium(ctx)) return "Autoplay is part of Plan 2. Redeem a code in SETTINGS."
        if (AgentService.instance == null) return "Enable Accessibility first (SETTINGS)"
        val every = seconds.coerceIn(5, 600)
        task?.let { main.removeCallbacks(it) }
        Agent.cancelled = false
        val wasRunning = running
        running = true
        val end = System.currentTimeMillis() + 60L * 60_000L
        if (!wasRunning) { KeepAliveService.start(ctx); appCtx = ctx.applicationContext }
        val r = object : Runnable {
            override fun run() {
                val svc = AgentService.instance
                if (!running || Agent.cancelled || svc == null || System.currentTimeMillis() > end) { stop(); return }
                svc.scrollDown()
                main.postDelayed(this, every * 1000L)
            }
        }
        task = r
        main.postDelayed(r, every * 1000L)
        return "Autoplay: swiping to the next video every $every seconds, for up to an hour. Say stop to end it."
    }

    fun stop() {
        val was = running
        running = false
        task?.let { main.removeCallbacks(it) }
        task = null
        if (was) appCtx?.let { KeepAliveService.stop(it) }
    }
}
