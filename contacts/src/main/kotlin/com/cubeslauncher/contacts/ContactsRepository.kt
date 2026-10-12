package com.cubeslauncher.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.RawContacts
import com.cubeslauncher.contacts.model.Contact
import com.cubeslauncher.contacts.model.ContactGroup
import com.cubeslauncher.contacts.model.EmailEntry
import com.cubeslauncher.contacts.model.PhoneEntry

class ContactsRepository(private val contentResolver: ContentResolver) {

    fun readAllContacts(): List<Contact> {
        val contactsMap = linkedMapOf<Long, ContactBuilder>()

        // Query contacts
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED
                ),
                null, null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC"
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupIdx = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val starredIdx = it.getColumnIndex(ContactsContract.Contacts.STARRED)
                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    contactsMap[id] = ContactBuilder(
                        id = id,
                        lookupKey = it.getString(lookupIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: "",
                        starred = it.getInt(starredIdx) == 1
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }

        // Load phones
        loadPhones(contactsMap)
        // Load emails
        loadEmails(contactsMap)
        // Load account info
        loadAccountInfo(contactsMap)

        return contactsMap.values.map { it.build() }
    }

    fun readStarredContacts(): List<Contact> {
        return readAllContacts().filter { it.starred }
    }

    fun readContact(contactId: Long): Contact? {
        var builder: ContactBuilder? = null

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.STARRED
                ),
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            cursor?.let {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val lookupIdx = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val starredIdx = it.getColumnIndex(ContactsContract.Contacts.STARRED)
                    builder = ContactBuilder(
                        id = it.getLong(idIdx),
                        lookupKey = it.getString(lookupIdx) ?: "",
                        displayName = it.getString(nameIdx) ?: "",
                        starred = it.getInt(starredIdx) == 1
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }

        if (builder == null) return null

        val map = linkedMapOf(contactId to builder!!)
        loadPhones(map)
        loadEmails(map)
        loadAccountInfo(map)
        return builder!!.build()
    }

    private fun loadPhones(contactsMap: Map<Long, ContactBuilder>) {
        if (contactsMap.isEmpty()) return
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Phone.CONTACT_ID,
                    CommonDataKinds.Phone.NUMBER,
                    CommonDataKinds.Phone.TYPE,
                    CommonDataKinds.Phone.LABEL
                ),
                null, null, null
            )
            cursor?.let {
                val contactIdIdx = it.getColumnIndex(CommonDataKinds.Phone.CONTACT_ID)
                val numberIdx = it.getColumnIndex(CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(CommonDataKinds.Phone.TYPE)
                val labelIdx = it.getColumnIndex(CommonDataKinds.Phone.LABEL)
                while (it.moveToNext()) {
                    val contactId = it.getLong(contactIdIdx)
                    contactsMap[contactId]?.phones?.add(
                        PhoneEntry(
                            number = it.getString(numberIdx) ?: "",
                            type = it.getInt(typeIdx),
                            label = it.getString(labelIdx)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
    }

    private fun loadEmails(contactsMap: Map<Long, ContactBuilder>) {
        if (contactsMap.isEmpty()) return
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    CommonDataKinds.Email.CONTACT_ID,
                    CommonDataKinds.Email.ADDRESS,
                    CommonDataKinds.Email.TYPE,
                    CommonDataKinds.Email.LABEL
                ),
                null, null, null
            )
            cursor?.let {
                val contactIdIdx = it.getColumnIndex(CommonDataKinds.Email.CONTACT_ID)
                val addressIdx = it.getColumnIndex(CommonDataKinds.Email.ADDRESS)
                val typeIdx = it.getColumnIndex(CommonDataKinds.Email.TYPE)
                val labelIdx = it.getColumnIndex(CommonDataKinds.Email.LABEL)
                while (it.moveToNext()) {
                    val contactId = it.getLong(contactIdIdx)
                    contactsMap[contactId]?.emails?.add(
                        EmailEntry(
                            address = it.getString(addressIdx) ?: "",
                            type = it.getInt(typeIdx),
                            label = it.getString(labelIdx)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
    }

    private fun loadAccountInfo(contactsMap: Map<Long, ContactBuilder>) {
        if (contactsMap.isEmpty()) return
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(
                    RawContacts.CONTACT_ID,
                    RawContacts.ACCOUNT_NAME,
                    RawContacts.ACCOUNT_TYPE
                ),
                null, null, null
            )
            cursor?.let {
                val contactIdIdx = it.getColumnIndex(RawContacts.CONTACT_ID)
                val accountNameIdx = it.getColumnIndex(RawContacts.ACCOUNT_NAME)
                val accountTypeIdx = it.getColumnIndex(RawContacts.ACCOUNT_TYPE)
                while (it.moveToNext()) {
                    val contactId = it.getLong(contactIdIdx)
                    val builder = contactsMap[contactId]
                    if (builder != null && builder.accountName == null) {
                        builder.accountName = it.getString(accountNameIdx)
                        builder.accountType = it.getString(accountTypeIdx)
                    }
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
    }

    fun readGroups(): List<ContactGroup> {
        val groups = mutableListOf<ContactGroup>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Groups.CONTENT_SUMMARY_URI,
                arrayOf(
                    ContactsContract.Groups._ID,
                    ContactsContract.Groups.TITLE,
                    ContactsContract.Groups.ACCOUNT_NAME,
                    ContactsContract.Groups.SUMMARY_COUNT
                ),
                "${ContactsContract.Groups.DELETED} = 0",
                null,
                ContactsContract.Groups.TITLE
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.Groups._ID)
                val titleIdx = it.getColumnIndex(ContactsContract.Groups.TITLE)
                val accountIdx = it.getColumnIndex(ContactsContract.Groups.ACCOUNT_NAME)
                val countIdx = it.getColumnIndex(ContactsContract.Groups.SUMMARY_COUNT)
                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: continue
                    groups.add(
                        ContactGroup(
                            id = it.getLong(idIdx),
                            title = title,
                            accountName = it.getString(accountIdx),
                            memberCount = if (countIdx >= 0) it.getInt(countIdx) else 0
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return groups
    }

    fun readGroupMembers(groupId: Long): List<Contact> {
        val contactIds = mutableSetOf<Long>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(CommonDataKinds.GroupMembership.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                null
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(CommonDataKinds.GroupMembership.CONTACT_ID)
                while (it.moveToNext()) {
                    contactIds.add(it.getLong(idIdx))
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }

        if (contactIds.isEmpty()) return emptyList()
        return readAllContacts().filter { it.id in contactIds }
    }

    fun createContact(
        firstName: String,
        lastName: String,
        phones: List<PhoneEntry>,
        emails: List<EmailEntry>,
        accountName: String?,
        accountType: String?
    ): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        // RawContact
        ops.add(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .build()
        )

        // StructuredName
        val displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                .build()
        )

        // Phones
        for (phone in phones) {
            if (phone.number.isBlank()) continue
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(CommonDataKinds.Phone.TYPE, phone.type)
                    .build()
            )
        }

        // Emails
        for (email in emails) {
            if (email.address.isBlank()) continue
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.ADDRESS, email.address)
                    .withValue(CommonDataKinds.Email.TYPE, email.type)
                    .build()
            )
        }

        return try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun updateContact(
        contactId: Long,
        firstName: String,
        lastName: String,
        phones: List<PhoneEntry>,
        emails: List<EmailEntry>
    ): Boolean {
        // Find raw contact ID
        val rawContactId = getRawContactId(contactId) ?: return false
        val ops = ArrayList<ContentProviderOperation>()

        // Update name
        ops.add(
            ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                .withValue(
                    CommonDataKinds.StructuredName.DISPLAY_NAME,
                    listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
                )
                .build()
        )

        // Delete existing phones and emails, then re-insert
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                )
                .build()
        )
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                )
                .build()
        )

        for (phone in phones) {
            if (phone.number.isBlank()) continue
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(CommonDataKinds.Phone.TYPE, phone.type)
                    .build()
            )
        }

        for (email in emails) {
            if (email.address.isBlank()) continue
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.ADDRESS, email.address)
                    .withValue(CommonDataKinds.Email.TYPE, email.type)
                    .build()
            )
        }

        return try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteContact(contactId: Long): Boolean {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return try {
            contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun toggleStar(contactId: Long, starred: Boolean): Boolean {
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return try {
            contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun getRawContactId(contactId: Long): Long? {
        var rawId: Long? = null
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )
            if (cursor?.moveToFirst() == true) {
                rawId = cursor.getLong(0)
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return rawId
    }

    fun getAccounts(): List<Pair<String?, String?>> {
        val accounts = mutableListOf<Pair<String?, String?>>()
        accounts.add(null to null) // Device account
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                null, null, null
            )
            val seen = mutableSetOf<String>()
            cursor?.let {
                val nameIdx = it.getColumnIndex(RawContacts.ACCOUNT_NAME)
                val typeIdx = it.getColumnIndex(RawContacts.ACCOUNT_TYPE)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx)
                    val type = it.getString(typeIdx)
                    if (name != null && seen.add(name)) {
                        accounts.add(name to type)
                    }
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return accounts
    }

    private class ContactBuilder(
        val id: Long,
        val lookupKey: String,
        val displayName: String,
        val starred: Boolean,
        var accountName: String? = null,
        var accountType: String? = null,
        val phones: MutableList<PhoneEntry> = mutableListOf(),
        val emails: MutableList<EmailEntry> = mutableListOf()
    ) {
        fun build() = Contact(
            id = id,
            lookupKey = lookupKey,
            displayName = displayName,
            phones = phones.toList(),
            emails = emails.toList(),
            starred = starred,
            accountName = accountName,
            accountType = accountType
        )
    }
}
