package com.tsagent.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

/**
 * Writes code files to Downloads/MissSara so Termux can read them (~/storage/downloads/MissSara).
 * Only .py and .js names are allowed. Running is done separately, after you confirm on the phone.
 */
object Code {
    private val nameRx = Regex("[A-Za-z0-9_\\-]{1,40}\\.(py|js)")
    const val FOLDER = "Download/MissSara"

    fun validName(name: String): Boolean = nameRx.matches(name)

    fun write(ctx: Context, name: String, content: String): String {
        if (!validName(name)) return "Use a name like my_script.py or app.js (letters, digits, _ or -)."
        if (content.length > 200_000) return "The file is too big."
        return try {
            val resolver = ctx.contentResolver
            val files = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            // replace an older file with the same name
            resolver.delete(files, MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?", arrayOf(name, FOLDER + "/"))
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(files, values) ?: return "Could not create the file."
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            "Saved $name in Downloads/MissSara."
        } catch (e: Exception) {
            "Could not save the file: " + e.message
        }
    }
}
