package com.tsagent.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Opens the right screen or prompt when a permission is missing. Works from an Activity or from a Service. */
object Permissions {
    fun request(ctx: Context, which: String) {
        Handler(Looper.getMainLooper()).post {
            val act = ctx as? Activity
            fun open(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fun appDetails() = open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            when (which.lowercase()) {
                "accessibility" -> open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "overlay" -> open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                "microphone" -> if (act != null) act.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) else appDetails()
                "media" -> if (act != null) act.requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO), 1) else appDetails()
                "camera" -> if (act != null) act.requestPermissions(arrayOf(Manifest.permission.CAMERA), 4) else appDetails()
                "notifications" -> if (act != null) act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) else appDetails()
                else -> appDetails()
            }
        }
    }
}
