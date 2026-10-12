package com.cubeslauncher.messages.whatsapp

import android.content.Context
import com.cubeslauncher.messages.whatsapp.model.WhatsAppChat
import com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WhatsAppMessageStore private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: WhatsAppMessageStore? = null

        fun getInstance(context: Context): WhatsAppMessageStore {
            return instance ?: synchronized(this) {
                instance ?: WhatsAppMessageStore(context.applicationContext).also { instance = it }
            }
        }

        private const val CHATS_FILE = "whatsapp_chats.json"
        private const val MESSAGES_DIR = "whatsapp_messages"
    }

    private fun chatsFile(): File = File(context.filesDir, CHATS_FILE)

    private fun messagesDir(): File {
        val dir = File(context.filesDir, MESSAGES_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun messagesFile(jid: String): File {
        val safeJid = jid.replace("@", "_at_").replace(".", "_")
        return File(messagesDir(), "$safeJid.json")
    }

    // --- Chats ---

    @Synchronized
    fun saveChats(chats: List<WhatsAppChat>) {
        val arr = JSONArray()
        for (chat in chats) {
            arr.put(JSONObject().apply {
                put("jid", chat.jid)
                put("name", chat.name)
                put("lastMessage", chat.lastMessage)
                put("lastMessageTime", chat.lastMessageTime)
                put("unreadCount", chat.unreadCount)
                put("isGroup", chat.isGroup)
                put("lastMessageType", chat.lastMessageType)
                if (chat.profilePicPath != null) put("profilePicPath", chat.profilePicPath)
                put("isPinned", chat.isPinned)
                put("isArchived", chat.isArchived)
                put("isMuted", chat.isMuted)
                if (chat.muteExpiration > 0) put("muteExpiration", chat.muteExpiration)
            })
        }
        chatsFile().writeText(arr.toString())
    }

    @Synchronized
    fun loadChats(): List<WhatsAppChat> {
        val file = chatsFile()
        if (!file.exists()) return emptyList()

        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                WhatsAppChat(
                    jid = obj.getString("jid"),
                    name = obj.optString("name", ""),
                    lastMessage = obj.optString("lastMessage", ""),
                    lastMessageTime = obj.optLong("lastMessageTime", 0),
                    unreadCount = obj.optInt("unreadCount", 0),
                    isGroup = obj.optBoolean("isGroup", false),
                    lastMessageType = obj.optString("lastMessageType", "text"),
                    profilePicPath = obj.optString("profilePicPath", "").ifEmpty { null },
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isMuted = obj.optBoolean("isMuted", false),
                    muteExpiration = obj.optLong("muteExpiration", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun upsertChat(chat: WhatsAppChat) {
        val chats = loadChats().toMutableList()
        val index = chats.indexOfFirst { it.jid == chat.jid }
        if (index >= 0) {
            chats[index] = chat
        } else {
            chats.add(chat)
        }
        saveChats(chats.sortedByDescending { it.lastMessageTime })
    }

    // --- Messages ---

    @Synchronized
    fun saveMessages(jid: String, messages: List<WhatsAppMessage>) {
        val arr = JSONArray()
        for (msg in messages) {
            arr.put(messageToJson(msg))
        }
        messagesFile(jid).writeText(arr.toString())
    }

    @Synchronized
    fun loadMessages(jid: String): List<WhatsAppMessage> {
        val file = messagesFile(jid)
        if (!file.exists()) return emptyList()

        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                jsonToMessage(arr.getJSONObject(i))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun appendMessage(message: WhatsAppMessage) {
        val file = messagesFile(message.chatJid)
        val arr = try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }

        // Check for duplicate
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("id") == message.id) return
        }

        arr.put(messageToJson(message))

        // Keep only last 1000 messages per chat
        if (arr.length() > 1000) {
            val trimmed = JSONArray()
            for (i in (arr.length() - 1000) until arr.length()) {
                trimmed.put(arr.getJSONObject(i))
            }
            file.writeText(trimmed.toString())
        } else {
            file.writeText(arr.toString())
        }
    }

    fun clearAll() {
        chatsFile().delete()
        messagesDir().deleteRecursively()
    }

    @Synchronized
    fun updateMessage(chatJid: String, messageId: String, updater: (WhatsAppMessage) -> WhatsAppMessage) {
        val messages = loadMessages(chatJid).toMutableList()
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            messages[index] = updater(messages[index])
            saveMessages(chatJid, messages)
        }
    }

    private fun messageToJson(msg: WhatsAppMessage): JSONObject = JSONObject().apply {
        put("id", msg.id)
        put("chatJid", msg.chatJid)
        put("senderJid", msg.senderJid)
        put("senderName", msg.senderName ?: "")
        put("body", msg.body)
        put("timestamp", msg.timestamp)
        put("isFromMe", msg.isFromMe)
        // Media fields
        put("messageType", msg.messageType)
        if (msg.mediaLocalPath != null) put("mediaLocalPath", msg.mediaLocalPath)
        if (msg.mediaMimeType != null) put("mediaMimeType", msg.mediaMimeType)
        if (msg.mediaFileName != null) put("mediaFileName", msg.mediaFileName)
        if (msg.mediaSize > 0) put("mediaSize", msg.mediaSize)
        if (msg.mediaDuration > 0) put("mediaDuration", msg.mediaDuration)
        if (msg.mediaWidth > 0) put("mediaWidth", msg.mediaWidth)
        if (msg.mediaHeight > 0) put("mediaHeight", msg.mediaHeight)
        if (msg.thumbnailPath != null) put("thumbnailPath", msg.thumbnailPath)
        if (msg.caption != null) put("caption", msg.caption)
        // Reply/quote fields
        if (msg.quotedMessageId != null) put("quotedMessageId", msg.quotedMessageId)
        if (msg.quotedBody != null) put("quotedBody", msg.quotedBody)
        if (msg.quotedSenderName != null) put("quotedSenderName", msg.quotedSenderName)
        // Reaction
        if (msg.reactionEmoji != null) put("reactionEmoji", msg.reactionEmoji)
        // Message state
        put("isEdited", msg.isEdited)
        put("isRevoked", msg.isRevoked)
        put("deliveryStatus", msg.deliveryStatus)
    }

    private fun jsonToMessage(obj: JSONObject): WhatsAppMessage = WhatsAppMessage(
        id = obj.getString("id"),
        chatJid = obj.getString("chatJid"),
        senderJid = obj.optString("senderJid", ""),
        senderName = obj.optString("senderName", "").ifEmpty { null },
        body = obj.optString("body", ""),
        timestamp = obj.optLong("timestamp", 0),
        isFromMe = obj.optBoolean("isFromMe", false),
        // Media fields
        messageType = obj.optString("messageType", "text"),
        mediaLocalPath = obj.optString("mediaLocalPath", "").ifEmpty { null },
        mediaMimeType = obj.optString("mediaMimeType", "").ifEmpty { null },
        mediaFileName = obj.optString("mediaFileName", "").ifEmpty { null },
        mediaSize = obj.optLong("mediaSize", 0),
        mediaDuration = obj.optInt("mediaDuration", 0),
        mediaWidth = obj.optInt("mediaWidth", 0),
        mediaHeight = obj.optInt("mediaHeight", 0),
        thumbnailPath = obj.optString("thumbnailPath", "").ifEmpty { null },
        caption = obj.optString("caption", "").ifEmpty { null },
        // Reply/quote fields
        quotedMessageId = obj.optString("quotedMessageId", "").ifEmpty { null },
        quotedBody = obj.optString("quotedBody", "").ifEmpty { null },
        quotedSenderName = obj.optString("quotedSenderName", "").ifEmpty { null },
        // Reaction
        reactionEmoji = obj.optString("reactionEmoji", "").ifEmpty { null },
        // Message state
        isEdited = obj.optBoolean("isEdited", false),
        isRevoked = obj.optBoolean("isRevoked", false),
        deliveryStatus = obj.optString("deliveryStatus", "sent")
    )
}
