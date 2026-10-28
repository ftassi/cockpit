package dev.ftassi.cockpit.contacts

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

data class Favorite(val name: String, val number: String)

// One row per phone number of a starred contact.
data class PhoneRow(val contactId: Long, val name: String, val number: String, val isDefault: Boolean)

// Requires READ_CONTACTS.
fun loadFavorites(context: Context): List<Favorite> {
    val rows = mutableListOf<PhoneRow>()
    context.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.IS_SUPER_PRIMARY),
        "${Phone.STARRED} = 1",
        null,
        "${Phone.DISPLAY_NAME_PRIMARY} ASC",
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            rows += PhoneRow(
                contactId = cursor.getLong(0),
                name = cursor.getString(1) ?: continue,
                number = cursor.getString(2) ?: continue,
                isDefault = cursor.getInt(3) != 0,
            )
        }
    }
    return favoritesFrom(rows)
}

// A contact with several numbers gets one tile: the number marked as default
// in the address book, otherwise the first one.
fun favoritesFrom(rows: List<PhoneRow>): List<Favorite> =
    rows.groupBy { it.contactId }.values.map { numbers ->
        val chosen = numbers.firstOrNull { it.isDefault } ?: numbers.first()
        Favorite(chosen.name, chosen.number)
    }
