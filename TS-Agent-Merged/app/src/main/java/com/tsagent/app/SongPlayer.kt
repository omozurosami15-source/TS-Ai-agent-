package com.tsagent.app

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/** Plays the owner's favorite song from a file on the phone. The song is never part of the app or the repo. */
object SongPlayer {
    private var mp: MediaPlayer? = null
    private var appCtx: Context? = null

    fun play(ctx: Context, uri: Uri): String {
        stop()
        return try {
            val m = MediaPlayer()
            m.setDataSource(ctx.applicationContext, uri)
            m.setOnCompletionListener { stop() }
            m.setOnErrorListener { _, _, _ -> stop(); true }
            m.prepare()
            m.start()
            mp = m
            appCtx = ctx.applicationContext
            KeepAliveService.start(ctx)
            "Playing your favorite song"
        } catch (e: Exception) { "Could not play the song file: ${e.message}" }
    }

    fun stop() {
        val had = mp != null
        mp?.let { try { it.stop() } catch (e: Exception) {}; it.release() }
        mp = null
        if (had) appCtx?.let { KeepAliveService.stop(it) }
    }
}
