package com.cubeslauncher.app.messages.model

data class Conversation(
    val threadId: Long,
    val recipientAddresses: List<String>,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val isGroup: Boolean,
    val isDraft: Boolean
)
