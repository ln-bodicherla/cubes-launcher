package com.cubeslauncher.app.messages

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.FragmentMessagesDeletedBinding
import com.cubeslauncher.app.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeletedFragment : Fragment() {

    private var _binding: FragmentMessagesDeletedBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationsAdapter
    private lateinit var repository: MessagesRepository
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesDeletedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = MessagesRepository(requireContext())
        preferencesManager = PreferencesManager(requireContext())

        adapter = ConversationsAdapter(
            onClick = { conversation ->
                val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_THREAD_ID, conversation.threadId)
                    putExtra(ConversationActivity.EXTRA_ADDRESS, conversation.recipientAddresses.firstOrNull() ?: "")
                }
                startActivity(intent)
            },
            onLongClick = { conversation, anchor ->
                showConversationMenu(conversation, anchor)
            }
        )

        binding.conversationsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DeletedFragment.adapter
        }

        loadConversations()
    }

    private fun loadConversations() {
        viewLifecycleOwner.lifecycleScope.launch {
            val deletedThreads = preferencesManager.deletedThreads.first()
            val conversations = withContext(Dispatchers.IO) {
                repository.readConversations()
            }.filter { it.threadId in deletedThreads }

            adapter.submitList(conversations)
            binding.emptyState.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            binding.conversationsRecycler.visibility = if (conversations.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showConversationMenu(conversation: com.cubeslauncher.app.messages.model.Conversation, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.messages_restore)
        popup.menu.add(0, 2, 1, R.string.messages_delete_permanently)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferencesManager.restoreDeletedThread(conversation.threadId)
                        Toast.makeText(requireContext(), R.string.messages_conversation_restored, Toast.LENGTH_SHORT).show()
                        loadConversations()
                    }
                    true
                }
                2 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repository.deleteConversation(conversation.threadId)
                        }
                        preferencesManager.restoreDeletedThread(conversation.threadId)
                        Toast.makeText(requireContext(), R.string.messages_conversation_deleted, Toast.LENGTH_SHORT).show()
                        loadConversations()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onResume() {
        super.onResume()
        loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
