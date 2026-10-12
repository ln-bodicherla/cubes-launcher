package com.cubeslauncher.messages.whatsapp

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.cubeslauncher.messages.whatsapp.model.WhatsAppChat
import com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge to the whatsmeow Go library (.aar).
 *
 * When the .aar is not present (not yet built), all Go calls are no-ops and
 * the UI still works against the local JSON message store.
 */
object WhatsAppBridge {

    private const val TAG = "WhatsAppBridge"
    private var initialized = false
    private var store: WhatsAppMessageStore? = null
    private var goAvailable = false
    private var appContext: Context? = null

    // Cached reflection references for the Go bridge class
    private var goBridge: Class<*>? = null

    private var initError: String? = null

    private var onMessageListener: ((WhatsAppMessage) -> Unit)? = null
    private var onQRListener: ((String) -> Unit)? = null
    private var onConnectedListener: (() -> Unit)? = null
    private var onDisconnectedListener: (() -> Unit)? = null
    private var onDebugListener: ((String) -> Unit)? = null
    // Phase 2 listeners
    private var onReceiptListener: ((String) -> Unit)? = null
    private var onReactionListener: ((String) -> Unit)? = null
    private var onMessageRevokeListener: ((String) -> Unit)? = null
    private var onMessageEditListener: ((String) -> Unit)? = null
    // Phase 3 listeners
    private var onGroupInfoUpdateListener: ((String) -> Unit)? = null
    // Phase 4 listeners
    private var onPresenceListener: ((String) -> Unit)? = null
    private var onChatPresenceListener: ((String) -> Unit)? = null
    // Phase 7 listeners
    private var onCallOfferListener: ((String) -> Unit)? = null

    fun init(context: Context) {
        if (initialized) return

        appContext = context.applicationContext
        store = WhatsAppMessageStore.getInstance(context)

        // Try to load the Go bridge via reflection
        try {
            goBridge = Class.forName("whatsmeowbridge.Whatsmeowbridge")
            goAvailable = true

            val dbPath = context.filesDir.resolve("whatsmeow.db").absolutePath
            goBridge!!.getMethod("init", String::class.java).invoke(null, dbPath)

            // Set media path for downloaded files
            val mediaDir = context.filesDir.resolve("whatsapp_media").absolutePath
            try {
                goBridge!!.getMethod("setMediaPath", String::class.java).invoke(null, mediaDir)
            } catch (_: NoSuchMethodException) {
                Log.w(TAG, "setMediaPath not available in Go bridge")
            }

            // Create an EventHandler proxy to receive callbacks
            val handlerClass = Class.forName("whatsmeowbridge.EventHandler")
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                handlerClass.classLoader,
                arrayOf(handlerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onMessage" -> handleOnMessage(args?.get(0) as? String ?: "")
                    "onQRCode" -> onQRListener?.invoke(args?.get(0) as? String ?: "")
                    "onConnected" -> {
                        // Fetch contact names in background after connecting
                        Thread { refreshContactNames() }.start()
                        onConnectedListener?.invoke()
                    }
                    "onDisconnected" -> onDisconnectedListener?.invoke()
                    "onHistorySync" -> {
                        handleOnHistorySync(args?.get(0) as? String ?: "")
                        // Re-apply contact names after each history sync
                        Thread { refreshContactNames() }.start()
                    }
                    "onDebug" -> onDebugListener?.invoke(args?.get(0) as? String ?: "")
                    // Phase 2
                    "onReceipt" -> handleOnReceipt(args?.get(0) as? String ?: "")
                    "onReaction" -> handleOnReaction(args?.get(0) as? String ?: "")
                    "onMessageRevoke" -> handleOnMessageRevoke(args?.get(0) as? String ?: "")
                    "onMessageEdit" -> handleOnMessageEdit(args?.get(0) as? String ?: "")
                    // Phase 3
                    "onGroupInfoUpdate" -> onGroupInfoUpdateListener?.invoke(args?.get(0) as? String ?: "")
                    // Phase 4
                    "onPresence" -> onPresenceListener?.invoke(args?.get(0) as? String ?: "")
                    "onChatPresence" -> onChatPresenceListener?.invoke(args?.get(0) as? String ?: "")
                    // Phase 7
                    "onCallOffer" -> onCallOfferListener?.invoke(args?.get(0) as? String ?: "")
                }
                null
            }
            goBridge!!.getMethod("setEventHandler", handlerClass).invoke(null, proxy)

            Log.d(TAG, "Go bridge loaded successfully")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "whatsmeow .aar not found — running in offline/stub mode")
            initError = "ClassNotFound: ${e.message}"
            goAvailable = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Go bridge", e)
            initError = "${e.javaClass.simpleName}: ${e.message}\n${e.cause?.message ?: ""}"
            goAvailable = false
        }

        initialized = true
    }

    fun isGoAvailable(): Boolean = goAvailable

    fun getInitError(): String? = initError

    fun connect() {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("connect").invoke(null)
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed", e)
        }
    }

    fun disconnect() {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("disconnect").invoke(null)
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect failed", e)
        }
    }

    fun isConnected(): Boolean {
        if (!goAvailable) return false
        return try {
            goBridge!!.getMethod("isConnected").invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun isLoggedIn(): Boolean {
        if (!goAvailable) return false
        return try {
            goBridge!!.getMethod("isLoggedIn").invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun startQRPairing(onQR: (String) -> Unit, onSuccess: () -> Unit) {
        onQRListener = onQR
        onConnectedListener = onSuccess

        if (!goAvailable) {
            Log.w(TAG, "Go bridge not available for QR pairing")
            return
        }
        try {
            goBridge!!.getMethod("getQRChannel").invoke(null)
        } catch (e: Exception) {
            Log.e(TAG, "QR pairing failed", e)
        }
    }

    /**
     * Starts phone-number based pairing. Returns an 8-digit linking code,
     * or a string starting with "ERROR:" on failure.
     */
    fun pairPhone(phone: String, onSuccess: () -> Unit): String {
        onConnectedListener = onSuccess
        if (!goAvailable) return "ERROR:Go bridge not available"
        return try {
            val result = goBridge!!.getMethod("pairPhone", String::class.java).invoke(null, phone)
            result as? String ?: "ERROR:null result"
        } catch (e: Exception) {
            "ERROR:${e.cause?.message ?: e.message}"
        }
    }

    /**
     * Looks up a phone number in the device's contacts.
     */
    private fun lookupContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates chat names using the device's phone contacts.
     */
    fun refreshContactNames() {
        val context = appContext ?: return

        val chats = store?.loadChats() ?: return
        var updated = false
        val updatedChats = chats.map { chat ->
            val needsName = chat.name == chat.jid || chat.name.isEmpty()
                    || chat.name.contains("@") || chat.name.startsWith("+")
            if (needsName && !chat.isGroup) {
                val number = "+" + chat.jid.substringBefore("@")
                val contactName = lookupContactName(context, number)
                if (contactName != null) {
                    updated = true
                    chat.copy(name = contactName)
                } else {
                    // At least show as phone number
                    if (chat.name.contains("@")) {
                        updated = true
                        chat.copy(name = number)
                    } else {
                        chat
                    }
                }
            } else {
                chat
            }
        }
        if (updated) {
            store?.saveChats(updatedChats)
        }
    }

    fun sendMessage(jid: String, text: String) {
        if (goAvailable) {
            try {
                goBridge!!.getMethod("sendMessage", String::class.java, String::class.java)
                    .invoke(null, jid, text)
            } catch (e: Exception) {
                Log.e(TAG, "Send message failed", e)
            }
        }

        // Store sent message locally regardless
        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = text,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "text"
        )
        store?.appendMessage(msg)

        // Update chat list
        val existingChats = store?.loadChats() ?: emptyList()
        val existing = existingChats.find { it.jid == jid }
        val updatedChat = WhatsAppChat(
            jid = jid,
            name = existing?.name ?: jid,
            lastMessage = text,
            lastMessageTime = System.currentTimeMillis() / 1000,
            unreadCount = 0,
            isGroup = existing?.isGroup ?: false,
            lastMessageType = "text"
        )
        store?.upsertChat(updatedChat)

        onMessageListener?.invoke(msg)
    }

    // --- Phase 1: Media Send ---

    fun sendImage(jid: String, filePath: String, caption: String = "", mimeType: String = "") {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "uploadAndSendImage",
                    String::class.java, String::class.java, String::class.java, String::class.java
                ).invoke(null, jid, filePath, caption, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Send image failed", e)
            }
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = caption,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "image",
            mediaLocalPath = filePath,
            mediaMimeType = mimeType.ifEmpty { "image/jpeg" },
            caption = caption.ifEmpty { null }
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(jid, caption.ifEmpty { "Photo" }, "image")
        onMessageListener?.invoke(msg)
    }

    fun sendVideo(jid: String, filePath: String, caption: String = "", mimeType: String = "") {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "uploadAndSendVideo",
                    String::class.java, String::class.java, String::class.java, String::class.java
                ).invoke(null, jid, filePath, caption, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Send video failed", e)
            }
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = caption,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "video",
            mediaLocalPath = filePath,
            mediaMimeType = mimeType.ifEmpty { "video/mp4" },
            caption = caption.ifEmpty { null }
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(jid, caption.ifEmpty { "Video" }, "video")
        onMessageListener?.invoke(msg)
    }

    fun sendAudio(jid: String, filePath: String, mimeType: String = "") {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "uploadAndSendAudio",
                    String::class.java, String::class.java, String::class.java
                ).invoke(null, jid, filePath, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Send audio failed", e)
            }
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = "",
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "audio",
            mediaLocalPath = filePath,
            mediaMimeType = mimeType.ifEmpty { "audio/ogg; codecs=opus" }
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(jid, "Audio", "audio")
        onMessageListener?.invoke(msg)
    }

    fun sendDocument(jid: String, filePath: String, fileName: String = "", mimeType: String = "") {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "uploadAndSendDocument",
                    String::class.java, String::class.java, String::class.java, String::class.java
                ).invoke(null, jid, filePath, fileName, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Send document failed", e)
            }
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = fileName,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "document",
            mediaLocalPath = filePath,
            mediaMimeType = mimeType.ifEmpty { "application/octet-stream" },
            mediaFileName = fileName
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(jid, fileName.ifEmpty { "Document" }, "document")
        onMessageListener?.invoke(msg)
    }

    private fun updateChatForSentMessage(jid: String, preview: String, type: String) {
        val existingChats = store?.loadChats() ?: emptyList()
        val existing = existingChats.find { it.jid == jid }
        val updatedChat = WhatsAppChat(
            jid = jid,
            name = existing?.name ?: jid,
            lastMessage = preview,
            lastMessageTime = System.currentTimeMillis() / 1000,
            unreadCount = 0,
            isGroup = existing?.isGroup ?: false,
            lastMessageType = type
        )
        store?.upsertChat(updatedChat)
    }

    // --- Phase 2: Message Interactions ---

    fun sendReaction(chatJid: String, targetMessageId: String, senderJid: String, emoji: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod(
                "sendReaction",
                String::class.java, String::class.java, String::class.java, String::class.java
            ).invoke(null, chatJid, targetMessageId, senderJid, emoji)
        } catch (e: Exception) {
            Log.e(TAG, "Send reaction failed", e)
        }
    }

    fun editMessage(chatJid: String, targetMessageId: String, newText: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod(
                "editMessage",
                String::class.java, String::class.java, String::class.java
            ).invoke(null, chatJid, targetMessageId, newText)
        } catch (e: Exception) {
            Log.e(TAG, "Edit message failed", e)
        }

        // Update local store
        store?.updateMessage(chatJid, targetMessageId) { msg ->
            msg.copy(body = newText, isEdited = true)
        }
    }

    fun revokeMessage(chatJid: String, senderJid: String, targetMessageId: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod(
                "revokeMessage",
                String::class.java, String::class.java, String::class.java
            ).invoke(null, chatJid, senderJid, targetMessageId)
        } catch (e: Exception) {
            Log.e(TAG, "Revoke message failed", e)
        }

        store?.updateMessage(chatJid, targetMessageId) { msg ->
            msg.copy(isRevoked = true, body = "")
        }
    }

    fun markRead(chatJid: String, messageIds: List<String>) {
        if (!goAvailable) return
        try {
            val idsJson = JSONArray(messageIds).toString()
            goBridge!!.getMethod(
                "markRead",
                String::class.java, String::class.java
            ).invoke(null, chatJid, idsJson)
        } catch (e: Exception) {
            Log.e(TAG, "Mark read failed", e)
        }
    }

    fun sendReplyMessage(chatJid: String, quotedId: String, quotedSenderJid: String, text: String) {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "sendReplyMessage",
                    String::class.java, String::class.java, String::class.java, String::class.java
                ).invoke(null, chatJid, quotedId, quotedSenderJid, text)
            } catch (e: Exception) {
                Log.e(TAG, "Send reply failed", e)
            }
        }

        // Store locally
        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = chatJid,
            senderJid = "me",
            senderName = null,
            body = text,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "text",
            quotedMessageId = quotedId,
            quotedSenderName = quotedSenderJid
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(chatJid, text, "text")
        onMessageListener?.invoke(msg)
    }

    // --- Phase 3: Group Features ---

    fun getGroupInfo(jid: String): String {
        if (!goAvailable) return "{}"
        return try {
            goBridge!!.getMethod("getGroupInfo", String::class.java).invoke(null, jid) as? String ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "Get group info failed", e)
            "{}"
        }
    }

    fun createGroup(name: String, participantJids: List<String>): String {
        if (!goAvailable) return ""
        return try {
            val jidsJson = JSONArray(participantJids).toString()
            goBridge!!.getMethod("createGroup", String::class.java, String::class.java)
                .invoke(null, name, jidsJson) as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Create group failed", e)
            ""
        }
    }

    fun setGroupName(jid: String, name: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("setGroupName", String::class.java, String::class.java)
                .invoke(null, jid, name)
        } catch (e: Exception) {
            Log.e(TAG, "Set group name failed", e)
        }
    }

    fun setGroupTopic(jid: String, topic: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("setGroupTopic", String::class.java, String::class.java)
                .invoke(null, jid, topic)
        } catch (e: Exception) {
            Log.e(TAG, "Set group topic failed", e)
        }
    }

    fun leaveGroup(jid: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("leaveGroup", String::class.java).invoke(null, jid)
        } catch (e: Exception) {
            Log.e(TAG, "Leave group failed", e)
        }
    }

    fun updateGroupParticipants(jid: String, participantJids: List<String>, action: String) {
        if (!goAvailable) return
        try {
            val jidsJson = JSONArray(participantJids).toString()
            goBridge!!.getMethod(
                "updateGroupParticipants",
                String::class.java, String::class.java, String::class.java
            ).invoke(null, jid, jidsJson, action)
        } catch (e: Exception) {
            Log.e(TAG, "Update group participants failed", e)
        }
    }

    fun getGroupInviteLink(jid: String, reset: Boolean = false): String {
        if (!goAvailable) return ""
        return try {
            goBridge!!.getMethod("getGroupInviteLink", String::class.java, Boolean::class.java)
                .invoke(null, jid, reset) as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Get group invite link failed", e)
            ""
        }
    }

    fun joinGroupWithLink(link: String): String {
        if (!goAvailable) return ""
        return try {
            goBridge!!.getMethod("joinGroupWithLink", String::class.java)
                .invoke(null, link) as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Join group failed", e)
            ""
        }
    }

    // --- Phase 4: Presence & Status ---

    fun subscribePresence(jid: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("subscribePresence", String::class.java).invoke(null, jid)
        } catch (e: Exception) {
            Log.e(TAG, "Subscribe presence failed", e)
        }
    }

    fun sendPresence(type: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("sendPresence", String::class.java).invoke(null, type)
        } catch (e: Exception) {
            Log.e(TAG, "Send presence failed", e)
        }
    }

    fun sendChatPresence(chatJid: String, composing: Boolean) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("sendChatPresence", String::class.java, Boolean::class.java)
                .invoke(null, chatJid, composing)
        } catch (e: Exception) {
            Log.e(TAG, "Send chat presence failed", e)
        }
    }

    fun getProfilePicture(jid: String): String {
        if (!goAvailable) return ""
        return try {
            goBridge!!.getMethod("getProfilePicture", String::class.java)
                .invoke(null, jid) as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Get profile picture failed", e)
            ""
        }
    }

    fun isOnWhatsApp(phone: String): String {
        if (!goAvailable) return "{}"
        return try {
            goBridge!!.getMethod("isOnWhatsApp", String::class.java)
                .invoke(null, phone) as? String ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "IsOnWhatsApp failed", e)
            "{}"
        }
    }

    // --- Phase 6: Rich Content ---

    fun sendLocation(chatJid: String, lat: Double, lng: Double, name: String = "", address: String = "") {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod(
                "sendLocation",
                String::class.java, Double::class.java, Double::class.java,
                String::class.java, String::class.java
            ).invoke(null, chatJid, lat, lng, name, address)
        } catch (e: Exception) {
            Log.e(TAG, "Send location failed", e)
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = chatJid,
            senderJid = "me",
            senderName = null,
            body = "$lat,$lng",
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "location",
            caption = listOfNotNull(name.ifEmpty { null }, address.ifEmpty { null }).joinToString("\n")
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(chatJid, "Location", "location")
        onMessageListener?.invoke(msg)
    }

    fun sendContact(chatJid: String, displayName: String, vcard: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod(
                "sendContact",
                String::class.java, String::class.java, String::class.java
            ).invoke(null, chatJid, displayName, vcard)
        } catch (e: Exception) {
            Log.e(TAG, "Send contact failed", e)
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = chatJid,
            senderJid = "me",
            senderName = null,
            body = displayName,
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "contact",
            caption = vcard
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(chatJid, displayName, "contact")
        onMessageListener?.invoke(msg)
    }

    fun sendVoiceNote(jid: String, filePath: String, mimeType: String = "") {
        if (goAvailable) {
            try {
                goBridge!!.getMethod(
                    "uploadAndSendAudioPTT",
                    String::class.java, String::class.java, String::class.java
                ).invoke(null, jid, filePath, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "Send voice note failed", e)
            }
        }

        val msg = WhatsAppMessage(
            id = "sent_${System.currentTimeMillis()}",
            chatJid = jid,
            senderJid = "me",
            senderName = null,
            body = "",
            timestamp = System.currentTimeMillis() / 1000,
            isFromMe = true,
            messageType = "ptt",
            mediaLocalPath = filePath,
            mediaMimeType = mimeType.ifEmpty { "audio/ogg; codecs=opus" }
        )
        store?.appendMessage(msg)
        updateChatForSentMessage(jid, "Voice message", "ptt")
        onMessageListener?.invoke(msg)
    }

    // --- Phase 7: Privacy & Blocking ---

    fun getBlocklist(): String {
        if (!goAvailable) return "[]"
        return try {
            goBridge!!.getMethod("getBlocklist").invoke(null) as? String ?: "[]"
        } catch (e: Exception) {
            Log.e(TAG, "Get blocklist failed", e)
            "[]"
        }
    }

    fun updateBlocklist(jid: String, action: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("updateBlocklist", String::class.java, String::class.java)
                .invoke(null, jid, action)
        } catch (e: Exception) {
            Log.e(TAG, "Update blocklist failed", e)
        }
    }

    fun getPrivacySettings(): String {
        if (!goAvailable) return "{}"
        return try {
            goBridge!!.getMethod("getPrivacySettings").invoke(null) as? String ?: "{}"
        } catch (e: Exception) {
            Log.e(TAG, "Get privacy settings failed", e)
            "{}"
        }
    }

    fun setStatusMessage(text: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("setStatusMessage", String::class.java).invoke(null, text)
        } catch (e: Exception) {
            Log.e(TAG, "Set status message failed", e)
        }
    }

    fun rejectCall(callId: String, callerJid: String) {
        if (!goAvailable) return
        try {
            goBridge!!.getMethod("rejectCall", String::class.java, String::class.java)
                .invoke(null, callId, callerJid)
        } catch (e: Exception) {
            Log.e(TAG, "Reject call failed", e)
        }
    }

    fun getSubscribedNewsletters(): String {
        if (!goAvailable) return "[]"
        return try {
            goBridge!!.getMethod("getSubscribedNewsletters").invoke(null) as? String ?: "[]"
        } catch (e: Exception) {
            Log.e(TAG, "Get newsletters failed", e)
            "[]"
        }
    }

    // --- Core ---

    fun getChats(): List<WhatsAppChat> {
        return store?.loadChats() ?: emptyList()
    }

    fun getMessages(jid: String): List<WhatsAppMessage> {
        return store?.loadMessages(jid) ?: emptyList()
    }

    fun logout() {
        if (goAvailable) {
            try {
                goBridge!!.getMethod("logout").invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed", e)
            }
        }
        store?.clearAll()
    }

    // --- Listener Setters ---

    fun setOnMessageListener(listener: ((WhatsAppMessage) -> Unit)?) {
        onMessageListener = listener
    }

    fun setOnConnectedListener(listener: (() -> Unit)?) {
        onConnectedListener = listener
    }

    fun setOnDisconnectedListener(listener: (() -> Unit)?) {
        onDisconnectedListener = listener
    }

    fun setOnDebugListener(listener: ((String) -> Unit)?) {
        onDebugListener = listener
    }

    fun setOnReceiptListener(listener: ((String) -> Unit)?) {
        onReceiptListener = listener
    }

    fun setOnReactionListener(listener: ((String) -> Unit)?) {
        onReactionListener = listener
    }

    fun setOnMessageRevokeListener(listener: ((String) -> Unit)?) {
        onMessageRevokeListener = listener
    }

    fun setOnMessageEditListener(listener: ((String) -> Unit)?) {
        onMessageEditListener = listener
    }

    fun setOnGroupInfoUpdateListener(listener: ((String) -> Unit)?) {
        onGroupInfoUpdateListener = listener
    }

    fun setOnPresenceListener(listener: ((String) -> Unit)?) {
        onPresenceListener = listener
    }

    fun setOnChatPresenceListener(listener: ((String) -> Unit)?) {
        onChatPresenceListener = listener
    }

    fun setOnCallOfferListener(listener: ((String) -> Unit)?) {
        onCallOfferListener = listener
    }

    // --- Callback handlers (invoked via reflection proxy from Go) ---

    private fun handleOnMessage(jsonPayload: String) {
        try {
            val obj = JSONObject(jsonPayload)
            val msg = WhatsAppMessage(
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
                // Quote fields
                quotedMessageId = obj.optString("quotedMessageId", "").ifEmpty { null },
                quotedBody = obj.optString("quotedBody", "").ifEmpty { null },
                quotedSenderName = obj.optString("quotedSenderName", "").ifEmpty { null }
            )

            store?.appendMessage(msg)

            val existingChats = store?.loadChats() ?: emptyList()
            val existing = existingChats.find { it.jid == msg.chatJid }
            val displayPreview = when (msg.messageType) {
                "image" -> msg.caption ?: "Photo"
                "video" -> msg.caption ?: "Video"
                "audio", "ptt" -> "Audio"
                "document" -> msg.mediaFileName ?: "Document"
                "sticker" -> "Sticker"
                "location" -> "Location"
                "contact" -> msg.body
                else -> msg.body
            }
            val updatedChat = WhatsAppChat(
                jid = msg.chatJid,
                name = existing?.name ?: msg.senderName ?: msg.chatJid,
                lastMessage = displayPreview,
                lastMessageTime = msg.timestamp,
                unreadCount = (existing?.unreadCount ?: 0) + if (!msg.isFromMe) 1 else 0,
                isGroup = existing?.isGroup ?: msg.chatJid.contains("@g.us"),
                lastMessageType = msg.messageType
            )
            store?.upsertChat(updatedChat)

            onMessageListener?.invoke(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    private fun handleOnHistorySync(jsonPayload: String) {
        try {
            val arr = JSONArray(jsonPayload)
            var totalChats = 0
            var totalMessages = 0
            for (i in 0 until arr.length()) {
                val chatObj = arr.getJSONObject(i)
                val jid = chatObj.getString("jid")
                totalChats++

                val chat = WhatsAppChat(
                    jid = jid,
                    name = chatObj.optString("name", jid),
                    lastMessage = chatObj.optString("lastMessage", ""),
                    lastMessageTime = chatObj.optLong("lastMsgTime", 0),
                    unreadCount = chatObj.optInt("unreadCount", 0),
                    isGroup = chatObj.optBoolean("isGroup", false),
                    lastMessageType = chatObj.optString("lastMessageType", "text")
                )
                store?.upsertChat(chat)

                val messagesArr = chatObj.optJSONArray("messages") ?: continue
                for (j in 0 until messagesArr.length()) {
                    val msgObj = messagesArr.getJSONObject(j)
                    val msg = WhatsAppMessage(
                        id = msgObj.getString("id"),
                        chatJid = jid,
                        senderJid = msgObj.optString("senderJid", ""),
                        senderName = msgObj.optString("senderName", "").ifEmpty { null },
                        body = msgObj.optString("body", ""),
                        timestamp = msgObj.optLong("timestamp", 0),
                        isFromMe = msgObj.optBoolean("isFromMe", false),
                        messageType = msgObj.optString("messageType", "text"),
                        mediaMimeType = msgObj.optString("mediaMimeType", "").ifEmpty { null },
                        mediaFileName = msgObj.optString("mediaFileName", "").ifEmpty { null },
                        mediaSize = msgObj.optLong("mediaSize", 0),
                        mediaDuration = msgObj.optInt("mediaDuration", 0),
                        caption = msgObj.optString("caption", "").ifEmpty { null },
                        thumbnailPath = msgObj.optString("thumbnailPath", "").ifEmpty { null }
                    )
                    store?.appendMessage(msg)
                    totalMessages++
                }
            }
            onDebugListener?.invoke("HistorySync: $totalChats chats, $totalMessages msgs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse history sync", e)
            onDebugListener?.invoke("HistorySync error: ${e.message}")
        }
    }

    private fun handleOnReceipt(jsonPayload: String) {
        try {
            val obj = JSONObject(jsonPayload)
            val chatJid = obj.getString("chatJid")
            val status = obj.getString("status")
            val messageIds = obj.getJSONArray("messageIds")

            for (i in 0 until messageIds.length()) {
                val msgId = messageIds.getString(i)
                store?.updateMessage(chatJid, msgId) { msg ->
                    msg.copy(deliveryStatus = status)
                }
            }

            onReceiptListener?.invoke(jsonPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse receipt", e)
        }
    }

    private fun handleOnReaction(jsonPayload: String) {
        try {
            val obj = JSONObject(jsonPayload)
            val chatJid = obj.getString("chatJid")
            val targetId = obj.optString("reactionTargetId", "")
            val emoji = obj.optString("reactionEmoji", "")

            if (targetId.isNotEmpty()) {
                store?.updateMessage(chatJid, targetId) { msg ->
                    msg.copy(reactionEmoji = emoji.ifEmpty { null })
                }
            }

            onReactionListener?.invoke(jsonPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reaction", e)
        }
    }

    private fun handleOnMessageRevoke(jsonPayload: String) {
        try {
            val obj = JSONObject(jsonPayload)
            val chatJid = obj.getString("chatJid")
            val targetId = obj.getString("targetId")

            store?.updateMessage(chatJid, targetId) { msg ->
                msg.copy(isRevoked = true, body = "")
            }

            onMessageRevokeListener?.invoke(jsonPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message revoke", e)
        }
    }

    private fun handleOnMessageEdit(jsonPayload: String) {
        try {
            val obj = JSONObject(jsonPayload)
            val chatJid = obj.getString("chatJid")
            val targetId = obj.getString("targetId")
            val newBody = obj.getString("newBody")

            store?.updateMessage(chatJid, targetId) { msg ->
                msg.copy(body = newBody, isEdited = true)
            }

            onMessageEditListener?.invoke(jsonPayload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message edit", e)
        }
    }
}
