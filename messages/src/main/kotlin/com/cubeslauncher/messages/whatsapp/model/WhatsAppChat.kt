package com.cubeslauncher.messages.whatsapp.model

data class WhatsAppChat(
    val jid: String,
    val name: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isGroup: Boolean,
    val lastMessageType: String = "text",
    val profilePicPath: String? = null,
    // Chat management fields
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val muteExpiration: Long = 0
)
