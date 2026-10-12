package com.cubeslauncher.messages.whatsapp.model

data class WhatsAppGroupInfo(
    val jid: String,
    val name: String,
    val topic: String,
    val participants: List<GroupParticipant>,
    val createdAt: Long,
    val isLocked: Boolean,
    val isAnnounce: Boolean
)

data class GroupParticipant(
    val jid: String,
    val name: String = "",
    val isAdmin: Boolean = false,
    val isSuperAdmin: Boolean = false
)
