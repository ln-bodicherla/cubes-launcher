package com.cubeslauncher.messages.whatsapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.messages.databinding.FragmentWhatsappChatsBinding
import com.cubeslauncher.messages.whatsapp.model.WhatsAppChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhatsAppChatsFragment : Fragment() {

    private var _binding: FragmentWhatsappChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: WhatsAppChatsAdapter
    private var allChats: List<WhatsAppChat> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWhatsappChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WhatsAppChatsAdapter { chat ->
            val intent = Intent(requireContext(), WhatsAppConversationActivity::class.java).apply {
                putExtra(WhatsAppConversationActivity.EXTRA_JID, chat.jid)
                putExtra(WhatsAppConversationActivity.EXTRA_NAME, chat.name)
                putExtra(WhatsAppConversationActivity.EXTRA_IS_GROUP, chat.isGroup)
            }
            startActivity(intent)
        }

        binding.whatsappChatsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WhatsAppChatsFragment.adapter
        }

        setupSearch()
        setupLongPress()
        loadChats()
    }

    private fun setupSearch() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterChats(s?.toString() ?: "")
            }
        })
    }

    private fun filterChats(query: String) {
        if (query.isEmpty()) {
            adapter.submitList(sortChats(allChats))
        } else {
            val filtered = allChats.filter { chat ->
                chat.name.contains(query, ignoreCase = true) ||
                chat.lastMessage.contains(query, ignoreCase = true)
            }
            adapter.submitList(sortChats(filtered))
        }
        binding.emptyState.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        binding.whatsappChatsRecycler.visibility = if (adapter.currentList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun sortChats(chats: List<WhatsAppChat>): List<WhatsAppChat> {
        // Pinned chats first, then by last message time
        return chats.sortedWith(compareByDescending<WhatsAppChat> { it.isPinned }
            .thenByDescending { it.lastMessageTime })
    }

    private fun setupLongPress() {
        adapter.setOnLongClickListener { chat, view ->
            showChatContextMenu(chat, view)
        }
    }

    private fun showChatContextMenu(chat: WhatsAppChat, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, if (chat.isPinned) "Unpin" else "Pin")
        popup.menu.add(0, 2, 1, if (chat.isArchived) "Unarchive" else "Archive")
        popup.menu.add(0, 3, 2, if (chat.isMuted) "Unmute" else "Mute")
        popup.menu.add(0, 4, 3, "Mark as read")
        popup.menu.add(0, 5, 4, "Delete")

        popup.setOnMenuItemClickListener { item ->
            val store = WhatsAppMessageStore.getInstance(requireContext())
            when (item.itemId) {
                1 -> {
                    val updated = chat.copy(isPinned = !chat.isPinned)
                    store.upsertChat(updated)
                    loadChats()
                }
                2 -> {
                    val updated = chat.copy(isArchived = !chat.isArchived)
                    store.upsertChat(updated)
                    loadChats()
                }
                3 -> {
                    val updated = chat.copy(isMuted = !chat.isMuted)
                    store.upsertChat(updated)
                    loadChats()
                }
                4 -> {
                    val updated = chat.copy(unreadCount = 0)
                    store.upsertChat(updated)
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val messages = WhatsAppBridge.getMessages(chat.jid)
                        val unreadIds = messages.filter { !it.isFromMe }.takeLast(20).map { it.id }
                        if (unreadIds.isNotEmpty()) {
                            WhatsAppBridge.markRead(chat.jid, unreadIds)
                        }
                    }
                    loadChats()
                }
                5 -> {
                    store.saveMessages(chat.jid, emptyList())
                    val chats = store.loadChats().toMutableList()
                    chats.removeAll { it.jid == chat.jid }
                    store.saveChats(chats)
                    loadChats()
                }
            }
            true
        }
        popup.show()
    }

    private fun loadChats() {
        viewLifecycleOwner.lifecycleScope.launch {
            val chats = withContext(Dispatchers.IO) {
                WhatsAppBridge.init(requireContext().applicationContext)
                WhatsAppBridge.refreshContactNames()
                WhatsAppBridge.getChats().filter { it.lastMessage.isNotEmpty() && !it.isArchived }
            }
            allChats = chats
            val sorted = sortChats(chats)
            adapter.submitList(sorted)
            binding.emptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            binding.whatsappChatsRecycler.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
