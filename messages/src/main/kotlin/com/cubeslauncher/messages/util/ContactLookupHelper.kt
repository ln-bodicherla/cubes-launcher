package com.cubeslauncher.messages.util

import android.content.ContentResolver
import android.provider.ContactsContract

data class ContactResult(
    val id: Long,
    val displayName: String,
    val phones: List<PhoneResult>
)

data class PhoneResult(
    val number: String,
    val type: Int,
    val label: String?
)

class ContactLookupHelper(private val contentResolver: ContentResolver) {

    fun readAllContacts(): List<ContactResult> {
        val contacts = mutableListOf<ContactResult>()

        try {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                ),
                null, null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: ""
                    val phones = loadPhones(id)
                    contacts.add(ContactResult(id, name, phones))
                }
            }
        } catch (_: SecurityException) {
        }

        return contacts
    }

    private fun loadPhones(contactId: Long): List<PhoneResult> {
        val phones = mutableListOf<PhoneResult>()

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                while (cursor.moveToNext()) {
                    phones.add(
                        PhoneResult(
                            number = cursor.getString(numberIdx) ?: "",
                            type = cursor.getInt(typeIdx),
                            label = cursor.getString(labelIdx)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }

        return phones
    }
}
