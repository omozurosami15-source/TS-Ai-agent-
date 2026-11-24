package com.tsagent.app

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore

/** Sends the newest video or photo straight into another app's share/upload screen (no gallery tapping). */
object Share {
    fun latest(act: Context, appName: String, kind: String, caption: String?): String {
        val video = kind.lowercase().startsWith("video")
        val perm = if (video) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
        if (act.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            Permissions.request(act, "media")
            return "Media permission is needed. Allow it, then repeat the command."
        }
        val pkg = CommandRunner.findPackage(act, appName) ?: return "App not found: $appName"
        val uri = newest(act, video) ?: return "No ${if (video) "video" else "photo"} found in the gallery"
        val i = Intent(Intent.ACTION_SEND).apply {
            type = if (video) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!caption.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
                putExtra(Intent.EXTRA_SUBJECT, caption)
                putExtra(Intent.EXTRA_TITLE, caption)
            }
            setPackage(pkg)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            act.startActivity(i)
            "Opened $appName with your latest ${if (video) "video" else "photo"}. Review it and tap Post."
        } catch (e: Exception) { "$appName cannot receive this file: ${e.message}" }
    }

    private fun newest(ctx: Context, video: Boolean): Uri? {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        ctx.contentResolver.query(base, arrayOf(MediaStore.MediaColumns._ID), null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC")?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(base, c.getLong(0))
        }
        return null
    }
}
