package com.cubeslauncher.app.contacts.model

data class ContactGroup(
    val id: Long,
    val title: String,
    val accountName: String?,
    val memberCount: Int
)
