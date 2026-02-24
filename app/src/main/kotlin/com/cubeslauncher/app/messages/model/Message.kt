package com.cubeslauncher.app.messages.model

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val isMms: Boolean,
    val mmsImageUri: String?,
    val deliveryStatus: Int,
    val scheduledTime: Long?
)
