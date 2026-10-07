package com.jake.duolauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

internal data class ContactResult(val id: Long, val lookupKey: String, val name: String) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

/** Looks contacts up by name for All apps, on demand and only while the user has turned the feature on
 * and granted Android's contacts permission. Only names are read (never numbers or addresses), nothing
 * is cached, and tapping a result opens it in the system Contacts app.
 */
internal object ContactsSearch {
    fun search(context: Context, query: String): List<ContactResult> {
        if (query.trim().length < ContactMatch.MIN_QUERY) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        // The provider narrows by the first word; ContactMatch then applies the stricter word-prefix rule.
        val first = query.trim().substringBefore(' ')
        val found = mutableListOf<ContactResult>()
        runCatching {
            context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%$first%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { cursor ->
                while (cursor.moveToNext() && found.size < MAX_SCANNED) {
                    val name = cursor.getString(2) ?: continue
                    if (ContactMatch.matches(name, query)) found += ContactResult(cursor.getLong(0), cursor.getString(1) ?: continue, name)
                }
            }
        }
        return found.take(ContactMatch.MAX_RESULTS)
    }

    fun open(context: Context, contact: ContactResult) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, contact.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private const val MAX_SCANNED = 200
}
