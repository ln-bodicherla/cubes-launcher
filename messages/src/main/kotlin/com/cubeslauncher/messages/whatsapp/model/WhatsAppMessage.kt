package com.cubeslauncher.messages.whatsapp.model

data class WhatsAppMessage(
    val id: String,
    val chatJid: String,
    val senderJid: String,
    val senderName: String?,
    val body: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    // Media fields
    val messageType: String = "text", // text, image, video, audio, document, sticker
    val mediaLocalPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaSize: Long = 0,
    val mediaDuration: Int = 0, // seconds, for audio/video
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val thumbnailPath: String? = null,
    val caption: String? = null,
    // Reply/quote fields
    val quotedMessageId: String? = null,
    val quotedBody: String? = null,
    val quotedSenderName: String? = null,
    // Reaction
    val reactionEmoji: String? = null,
    // Message state
    val isEdited: Boolean = false,
    val isRevoked: Boolean = false,
    val deliveryStatus: String = "sent" // sent, delivered, read
)
