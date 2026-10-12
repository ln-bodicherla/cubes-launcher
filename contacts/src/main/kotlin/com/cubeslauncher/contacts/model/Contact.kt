package com.cubeslauncher.contacts.model

data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val phones: List<PhoneEntry>,
    val emails: List<EmailEntry>,
    val starred: Boolean,
    val accountName: String?,
    val accountType: String?
)

data class PhoneEntry(val number: String, val type: Int, val label: String?)
data class EmailEntry(val address: String, val type: Int, val label: String?)
