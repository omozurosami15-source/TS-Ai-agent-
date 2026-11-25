package com.tsagent.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** Finds a phone number by a contact's name, from the phone's own contacts. No AI and no API key. */
object ContactsLookup {
    fun numberFor(ctx: Context, name: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val q = name.trim()
        if (q.isEmpty()) return null
        val cur = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$q%"), null
        ) ?: return null
        cur.use {
            if (it.moveToFirst()) return it.getString(0)?.filter { c -> c.isDigit() || c == '+' }
        }
        return null
    }
}
