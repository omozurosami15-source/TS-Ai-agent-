package com.tsagent.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Foreground service that keeps the process alive while the agent works, so a task continues after you leave the app. */
class KeepAliveService : Service() {
    companion object {
        private const val CH = "agent_running"
        fun start(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, KeepAliveService::class.java)) } catch (e: Exception) {}
        }
        fun stop(ctx: Context) {
            // small delay: stopping right after starting can crash on some Android versions
            Handler(Looper.getMainLooper()).postDelayed({ ctx.stopService(Intent(ctx, KeepAliveService::class.java)) }, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "abort") { Agent.cancelled = true; return START_NOT_STICKY }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, "Agent running", NotificationManager.IMPORTANCE_LOW))
        val abort = PendingIntent.getService(this, 0, Intent(this, KeepAliveService::class.java).setAction("abort"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setContentTitle("TS Agent is working")
            .setContentText("Tap to open. Use ABORT to stop.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "ABORT", abort).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        return START_NOT_STICKY
    }
}
