package com.tsagent.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

/** Saves a picture to the phone's gallery, under Pictures/Miss Sara. */
object Media {
    fun saveJpeg(ctx: Context, jpg: ByteArray): String {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "miss_sara_" + System.currentTimeMillis() + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Miss Sara")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return "Could not save the photo."
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(jpg) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, done, null, null)
            "Photo saved to Pictures/Miss Sara."
        } catch (e: Exception) {
            "Could not save the photo: " + e.message
        }
    }
}
