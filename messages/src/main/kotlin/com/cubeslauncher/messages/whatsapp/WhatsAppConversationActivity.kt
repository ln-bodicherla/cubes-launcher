package com.cubeslauncher.messages.whatsapp

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.webkit.MimeTypeMap
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.databinding.ActivityWhatsappConversationBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.messages.MessagesPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WhatsAppConversationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JID = "extra_jid"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_IS_GROUP = "extra_is_group"
    }

    private lateinit var binding: ActivityWhatsappConversationBinding
    private lateinit var messagesAdapter: WhatsAppMessagesAdapter
    private var currentThemeMode: String = "dark"

    private var jid: String = ""
    private var chatName: String = ""
    private var isGroup: Boolean = false

    // Typing indicator debounce
    private var typingJob: Job? = null
    private var isTyping = false

    // Media picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendMediaFromUri(it, "image") }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendMediaFromUri(it, "video") }
    }

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendMediaFromUri(it, "audio") }
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendMediaFromUri(it, "document") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWhatsappConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jid = intent.getStringExtra(EXTRA_JID) ?: ""
        chatName = intent.getStringExtra(EXTRA_NAME) ?: jid
        isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, false)

        setupHeader()
        setupMessages()
        setupComposeBar()
        setupAttachButton()
        setupTypingIndicator()
        applyWindowInsets()
        loadMessages()
        setupRealtimeUpdates()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
        // Subscribe to presence for this contact
        if (!isGroup) {
            lifecycleScope.launch(Dispatchers.IO) {
                WhatsAppBridge.subscribePresence(jid)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WhatsAppBridge.setOnMessageListener(null)
        WhatsAppBridge.setOnPresenceListener(null)
        WhatsAppBridge.setOnChatPresenceListener(null)
        WhatsAppBridge.setOnReceiptListener(null)
        WhatsAppBridge.setOnReactionListener(null)
        WhatsAppBridge.setOnMessageEditListener(null)
        WhatsAppBridge.setOnMessageRevokeListener(null)
    }

    private fun setupHeader() {
        binding.contactName.text = chatName
        binding.backButton.setOnClickListener { finish() }

        // Tap header to open group info when in a group chat
        if (isGroup) {
            binding.contactName.setOnClickListener {
                val intent = android.content.Intent(this, WhatsAppGroupInfoActivity::class.java)
                intent.putExtra(WhatsAppGroupInfoActivity.EXTRA_JID, jid)
                startActivity(intent)
            }
        }
    }

    private fun setupMessages() {
        messagesAdapter = WhatsAppMessagesAdapter(
            isGroup = isGroup,
            onLongClick = { message, view -> showMessageContextMenu(message, view) }
        )

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WhatsAppConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    private fun setupComposeBar() {
        val accentColor = AccentColorManager.getAccentColor()
        binding.sendButton.setColorFilter(accentColor)
        binding.attachButton.setColorFilter(accentColor)

        binding.sendButton.setOnClickListener {
            val body = binding.composeText.text?.toString()?.trim() ?: return@setOnClickListener
            if (body.isEmpty()) return@setOnClickListener
            if (jid.isEmpty()) return@setOnClickListener

            binding.composeText.text?.clear()

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    WhatsAppBridge.sendMessage(jid, body)
                }
                loadMessages()
            }
        }
    }

    private fun setupAttachButton() {
        binding.attachButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Photo")
            popup.menu.add(0, 2, 1, "Video")
            popup.menu.add(0, 3, 2, "Audio")
            popup.menu.add(0, 4, 3, "Document")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> imagePickerLauncher.launch("image/*")
                    2 -> videoPickerLauncher.launch("video/*")
                    3 -> audioPickerLauncher.launch("audio/*")
                    4 -> documentPickerLauncher.launch("*/*")
                }
                true
            }
            popup.show()
        }
    }

    private fun setupTypingIndicator() {
        binding.composeText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isTyping) {
                    isTyping = true
                    lifecycleScope.launch(Dispatchers.IO) {
                        WhatsAppBridge.sendChatPresence(jid, true)
                    }
                }
                typingJob?.cancel()
                typingJob = lifecycleScope.launch {
                    delay(3000)
                    isTyping = false
                    withContext(Dispatchers.IO) {
                        WhatsAppBridge.sendChatPresence(jid, false)
                    }
                }
            }
        })

        // Listen for typing indicators from other users
        WhatsAppBridge.setOnChatPresenceListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("chatJid") == jid) {
                    val state = obj.getString("state")
                    // Could show typing indicator in header subtitle here
                }
            } catch (_: Exception) {}
        }

        // Listen for presence updates
        WhatsAppBridge.setOnPresenceListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("jid") == jid) {
                    // Could show online/offline status in header subtitle here
                }
            } catch (_: Exception) {}
        }
    }

    private fun sendMediaFromUri(uri: Uri, type: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val localFile = copyUriToFile(uri, type) ?: return@withContext
                val mimeType = contentResolver.getType(uri) ?: ""
                val fileName = getFileNameFromUri(uri) ?: localFile.name

                when (type) {
                    "image" -> WhatsAppBridge.sendImage(jid, localFile.absolutePath, "", mimeType)
                    "video" -> WhatsAppBridge.sendVideo(jid, localFile.absolutePath, "", mimeType)
                    "audio" -> WhatsAppBridge.sendAudio(jid, localFile.absolutePath, mimeType)
                    "document" -> WhatsAppBridge.sendDocument(jid, localFile.absolutePath, fileName, mimeType)
                }
            }
            loadMessages()
        }
    }

    private fun copyUriToFile(uri: Uri, type: String): File? {
        return try {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                contentResolver.getType(uri)
            ) ?: "bin"
            val dir = File(filesDir, "whatsapp_media/outgoing")
            dir.mkdirs()
            val outFile = File(dir, "${System.currentTimeMillis()}.$extension")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    return it.getString(0)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun showMessageContextMenu(message: com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage, anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add(0, 1, 0, "Reply")
        popup.menu.add(0, 2, 1, "React")

        if (message.isFromMe && !message.isRevoked) {
            if (message.messageType == "text") {
                popup.menu.add(0, 3, 2, "Edit")
            }
            popup.menu.add(0, 4, 3, "Delete for Everyone")
        }

        popup.menu.add(0, 5, 4, "Copy")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    // Reply: put quoted preview above compose bar
                    // For now just prepend context
                    binding.composeText.requestFocus()
                }
                2 -> showReactionPicker(message)
                3 -> showEditDialog(message)
                4 -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        WhatsAppBridge.revokeMessage(jid, message.senderJid, message.id)
                    }
                    messagesAdapter.updateMessage(message.id) { it.copy(isRevoked = true, body = "") }
                }
                5 -> {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.body))
                }
            }
            true
        }
        popup.show()
    }

    private fun showReactionPicker(message: com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage) {
        val reactions = arrayOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setItems(reactions) { _, which ->
            lifecycleScope.launch(Dispatchers.IO) {
                WhatsAppBridge.sendReaction(jid, message.id, message.senderJid, reactions[which])
            }
        }
        builder.show()
    }

    private fun showEditDialog(message: com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage) {
        val editText = android.widget.EditText(this)
        editText.setText(message.body)
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Edit message")
        builder.setView(editText)
        builder.setPositiveButton("Save") { _, _ ->
            val newText = editText.text.toString().trim()
            if (newText.isNotEmpty() && newText != message.body) {
                lifecycleScope.launch(Dispatchers.IO) {
                    WhatsAppBridge.editMessage(jid, message.id, newText)
                }
                messagesAdapter.updateMessage(message.id) { it.copy(body = newText, isEdited = true) }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset + 16, view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBarInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomInset = maxOf(imeInset, systemBarInset)

            binding.composeBar.setPadding(
                binding.composeBar.paddingLeft,
                binding.composeBar.paddingTop,
                binding.composeBar.paddingRight,
                if (imeInset > 0) 8 else systemBarInset + 8
            )

            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)

            if (imeInset > 0 && messagesAdapter.itemCount > 0) {
                binding.messagesRecyclerView.post {
                    binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
                }
            }

            insets
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                WhatsAppBridge.getMessages(jid)
            }
            messagesAdapter.submitMessages(messages)
            if (messagesAdapter.itemCount > 0) {
                binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        }
    }

    private fun setupRealtimeUpdates() {
        WhatsAppBridge.setOnMessageListener { message ->
            if (message.chatJid == jid) {
                runOnUiThread {
                    messagesAdapter.addMessage(message)
                    binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
                }
            }
        }

        // Listen for receipt updates to refresh delivery status
        WhatsAppBridge.setOnReceiptListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("chatJid") == jid) {
                    runOnUiThread { loadMessages() }
                }
            } catch (_: Exception) {}
        }

        // Listen for reaction updates
        WhatsAppBridge.setOnReactionListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("chatJid") == jid) {
                    runOnUiThread { loadMessages() }
                }
            } catch (_: Exception) {}
        }

        // Listen for edit updates
        WhatsAppBridge.setOnMessageEditListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("chatJid") == jid) {
                    runOnUiThread { loadMessages() }
                }
            } catch (_: Exception) {}
        }

        // Listen for revoke updates
        WhatsAppBridge.setOnMessageRevokeListener { jsonPayload ->
            try {
                val obj = org.json.JSONObject(jsonPayload)
                if (obj.getString("chatJid") == jid) {
                    runOnUiThread { loadMessages() }
                }
            } catch (_: Exception) {}
        }

        // Mark messages as read when viewing this conversation
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = WhatsAppBridge.getMessages(jid)
            val unreadIds = messages.filter { !it.isFromMe }.takeLast(20).map { it.id }
            if (unreadIds.isNotEmpty()) {
                WhatsAppBridge.markRead(jid, unreadIds)
            }
        }
    }
}
