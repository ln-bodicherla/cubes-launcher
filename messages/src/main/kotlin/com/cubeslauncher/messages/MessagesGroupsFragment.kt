package com.cubeslauncher.messages

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.databinding.FragmentMessagesGroupsBinding
import com.cubeslauncher.messages.MessagesPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MessagesGroupsFragment : Fragment() {

    private var _binding: FragmentMessagesGroupsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ConversationsAdapter
    private lateinit var repository: MessagesRepository
    private lateinit var preferencesManager: MessagesPreferencesManager
    private var currentConversations: List<com.cubeslauncher.messages.model.Conversation> = emptyList()
    private var swipeRightAction = "archive"
    private var swipeLeftAction = "delete"

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = MessagesRepository(requireContext())
        preferencesManager = MessagesPreferencesManager(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        val listTextScale = runBlocking { preferencesManager.listTextScale.first() }

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
            },
            onAvatarLongClick = { conversation ->
                enterSelectionMode(conversation)
            },
            listTextScale = listTextScale
        )

        binding.conversationsRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MessagesGroupsFragment.adapter
        }

        setupSelectionBar()
        setupSwipeActions()
        loadConversations()
    }

    private fun setupSelectionBar() {
        binding.btnCloseSelection.setOnClickListener { exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener {
            adapter.selectAll(currentConversations)
            updateSelectionCount()
        }
        binding.btnMarkRead.setOnClickListener { bulkMarkRead() }
        binding.btnMarkUnread.setOnClickListener { bulkMarkUnread() }
        binding.btnArchive.setOnClickListener { bulkArchive() }
        binding.btnDelete.setOnClickListener { bulkDelete() }
    }

    private fun setupSwipeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            swipeRightAction = preferencesManager.swipeRightAction.first()
            swipeLeftAction = preferencesManager.swipeLeftAction.first()
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position < 0 || position >= currentConversations.size) return
                val conversation = currentConversations[position]
                val action = if (direction == ItemTouchHelper.RIGHT) swipeRightAction else swipeLeftAction
                performSwipeAction(action, conversation)
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (adapter.isSelectionMode) return 0
                var dirs = 0
                if (swipeRightAction != "none") dirs = dirs or ItemTouchHelper.RIGHT
                if (swipeLeftAction != "none") dirs = dirs or ItemTouchHelper.LEFT
                return dirs
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = Paint()
                    if (dX > 0) {
                        paint.color = getSwipeColor(swipeRightAction)
                        c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(), dX, itemView.bottom.toFloat(), paint)
                    } else if (dX < 0) {
                        paint.color = getSwipeColor(swipeLeftAction)
                        c.drawRect(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat(), paint)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.conversationsRecycler)
    }

    private fun getSwipeColor(action: String): Int {
        return when (action) {
            "archive" -> 0xFF4CAF50.toInt()
            "delete" -> 0xFFF44336.toInt()
            "mark_read" -> 0xFF2196F3.toInt()
            "mark_unread" -> 0xFFFF9800.toInt()
            else -> 0xFF757575.toInt()
        }
    }

    private fun performSwipeAction(action: String, conversation: com.cubeslauncher.messages.model.Conversation) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (action) {
                "archive" -> {
                    preferencesManager.archiveThread(conversation.threadId)
                }
                "delete" -> {
                    preferencesManager.softDeleteThread(conversation.threadId)
                    Toast.makeText(requireContext(), R.string.messages_conversation_deleted, Toast.LENGTH_SHORT).show()
                }
                "mark_read" -> {
                    withContext(Dispatchers.IO) { repository.markThreadRead(conversation.threadId) }
                }
                "mark_unread" -> {
                    withContext(Dispatchers.IO) { repository.markThreadUnread(conversation.threadId) }
                }
            }
            loadConversations()
        }
    }

    private fun enterSelectionMode(conversation: com.cubeslauncher.messages.model.Conversation) {
        adapter.enterSelectionMode()
        adapter.toggleSelection(conversation.threadId)
        binding.selectionBar.visibility = View.VISIBLE
        backCallback.isEnabled = true
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        adapter.clearSelection()
        binding.selectionBar.visibility = View.GONE
        backCallback.isEnabled = false
    }

    private fun updateSelectionCount() {
        binding.selectionCount.text = getString(R.string.messages_selected_count, adapter.getSelectedCount())
    }

    private fun bulkMarkRead() {
        val ids = adapter.getSelectedIds()
        viewLifecycleOwner.lifecycleScope.launch {
            ids.forEach { id -> withContext(Dispatchers.IO) { repository.markThreadRead(id) } }
            exitSelectionMode()
            loadConversations()
        }
    }

    private fun bulkMarkUnread() {
        val ids = adapter.getSelectedIds()
        viewLifecycleOwner.lifecycleScope.launch {
            ids.forEach { id -> withContext(Dispatchers.IO) { repository.markThreadUnread(id) } }
            exitSelectionMode()
            loadConversations()
        }
    }

    private fun bulkArchive() {
        val ids = adapter.getSelectedIds()
        viewLifecycleOwner.lifecycleScope.launch {
            ids.forEach { id -> preferencesManager.archiveThread(id) }
            exitSelectionMode()
            loadConversations()
        }
    }

    private fun bulkDelete() {
        val ids = adapter.getSelectedIds()
        viewLifecycleOwner.lifecycleScope.launch {
            ids.forEach { id -> preferencesManager.softDeleteThread(id) }
            exitSelectionMode()
            loadConversations()
        }
    }

    private fun loadConversations() {
        viewLifecycleOwner.lifecycleScope.launch {
            val archivedThreads = preferencesManager.archivedThreads.first()
            val deletedThreads = preferencesManager.deletedThreads.first()
            val conversations = withContext(Dispatchers.IO) {
                repository.readConversations()
            }.filter { it.isGroup && it.threadId !in archivedThreads && it.threadId !in deletedThreads }

            currentConversations = conversations
            adapter.submitList(conversations)
            binding.emptyState.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE
            binding.conversationsRecycler.visibility = if (conversations.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showConversationMenu(conversation: com.cubeslauncher.messages.model.Conversation, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.messages_delete)
        popup.menu.add(0, 2, 1, R.string.messages_archive)
        popup.menu.add(0, 3, 2, R.string.messages_mark_read)
        popup.menu.add(0, 4, 3, R.string.messages_mark_unread)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferencesManager.softDeleteThread(conversation.threadId)
                        Toast.makeText(requireContext(), R.string.messages_conversation_deleted, Toast.LENGTH_SHORT).show()
                        loadConversations()
                    }
                    true
                }
                2 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        preferencesManager.archiveThread(conversation.threadId)
                        loadConversations()
                    }
                    true
                }
                3 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.markThreadRead(conversation.threadId) }
                        loadConversations()
                    }
                    true
                }
                4 -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.markThreadUnread(conversation.threadId) }
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
        viewLifecycleOwner.lifecycleScope.launch {
            swipeRightAction = preferencesManager.swipeRightAction.first()
            swipeLeftAction = preferencesManager.swipeLeftAction.first()
            val scale = preferencesManager.listTextScale.first()
            adapter.updateTextScale(scale)
        }
        loadConversations()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
