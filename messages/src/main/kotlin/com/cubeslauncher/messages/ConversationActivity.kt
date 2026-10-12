package com.cubeslauncher.messages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.databinding.ActivityConversationBinding
import com.cubeslauncher.messages.model.Message
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.messages.MessagesPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ConversationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
    }

    private lateinit var binding: ActivityConversationBinding
    private lateinit var repository: MessagesRepository
    private lateinit var messagesAdapter: MessagesAdapter
    private var currentThemeMode: String = "dark"

    private var threadId: Long = -1L
    private var address: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MessagesRepository(this)
        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""

        setupHeader()
        setupMessages()
        setupComposeBar()
        applyWindowInsets()
        loadMessages()
        loadDraft()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    override fun onPause() {
        super.onPause()
        saveDraft()
    }

    private fun setupHeader() {
        // Resolve contact name
        val contactName = repository.resolveContactName(address) ?: address
        binding.contactName.text = contactName

        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupMessages() {
        val isGroup = address.contains(",") || address.contains(";")
        val textScale = runBlocking { MessagesPreferencesManager(applicationContext).messageTextScale.first() }
        messagesAdapter = MessagesAdapter(isGroup, { message, anchor ->
            showMessageMenu(message, anchor)
        }, textScale)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messagesAdapter
        }
    }

    private fun setupComposeBar() {
        // Tint send button with accent color
        val accentColor = AccentColorManager.getAccentColor()
        binding.sendButton.setColorFilter(accentColor)

        binding.sendButton.setOnClickListener {
            val body = binding.composeText.text?.toString()?.trim() ?: return@setOnClickListener
            if (body.isEmpty()) return@setOnClickListener
            if (address.isEmpty()) return@setOnClickListener

            binding.composeText.text?.clear()

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    repository.sendSms(address, body)
                    if (threadId >= 0) {
                        repository.deleteDraft(threadId)
                    }
                }

                // Add sent message to adapter
                val sentMessage = Message(
                    id = -1,
                    threadId = threadId,
                    address = address,
                    contactName = null,
                    body = body,
                    date = System.currentTimeMillis(),
                    type = android.provider.Telephony.Sms.MESSAGE_TYPE_SENT,
                    read = true,
                    isMms = false,
                    mmsImageUri = null,
                    deliveryStatus = android.provider.Telephony.Sms.STATUS_PENDING,
                    scheduledTime = null
                )
                messagesAdapter.addMessage(sentMessage)
                binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset + 16, view.paddingRight, view.paddingBottom)
            insets
        }

        // Handle both system bars and IME (keyboard) insets on the root layout
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

            // Set bottom padding on root to push compose bar above keyboard
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)

            // Scroll to bottom when keyboard opens
            if (imeInset > 0 && messagesAdapter.itemCount > 0) {
                binding.messagesRecyclerView.post {
                    binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
                }
            }

            insets
        }
    }

    private fun loadMessages() {
        if (threadId < 0) return
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                repository.readMessages(threadId)
            }
            messagesAdapter.submitMessages(messages)
            if (messagesAdapter.itemCount > 0) {
                binding.messagesRecyclerView.scrollToPosition(messagesAdapter.itemCount - 1)
            }

            // Mark thread as read
            withContext(Dispatchers.IO) {
                repository.markThreadRead(threadId)
            }
        }
    }

    private fun loadDraft() {
        if (threadId < 0) return
        lifecycleScope.launch {
            val draft = withContext(Dispatchers.IO) {
                repository.getDraft(threadId)
            }
            if (!draft.isNullOrEmpty()) {
                binding.composeText.setText(draft)
                binding.composeText.setSelection(draft.length)
            }
        }
    }

    private fun saveDraft() {
        val text = binding.composeText.text?.toString()?.trim() ?: return
        if (threadId < 0) return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (text.isNotEmpty()) {
                    repository.saveDraft(threadId, address, text)
                } else {
                    repository.deleteDraft(threadId)
                }
            }
        }
    }

    private fun showMessageMenu(message: Message, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.messages_copy)
        popup.menu.add(0, 2, 1, R.string.messages_delete)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", message.body))
                    Toast.makeText(this, R.string.messages_copied, Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repository.deleteMessage(message.id, message.isMms)
                        }
                        Toast.makeText(this@ConversationActivity, R.string.messages_deleted, Toast.LENGTH_SHORT).show()
                        loadMessages()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
