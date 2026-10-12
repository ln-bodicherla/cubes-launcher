package com.cubeslauncher.messages

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.model.Conversation
import com.cubeslauncher.common.theme.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation, View) -> Unit,
    private val onAvatarLongClick: (Conversation) -> Unit = {},
    private var listTextScale: Float = 1.0f
) : ListAdapter<Conversation, ConversationsAdapter.ViewHolder>(DiffCallback) {

    private val selectedIds = mutableSetOf<Long>()
    var isSelectionMode: Boolean = false
        private set

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatarContainer: FrameLayout = itemView.findViewById(R.id.avatarContainer)
        val contactInitials: TextView = itemView.findViewById(R.id.contactInitials)
        val checkMark: ImageView = itemView.findViewById(R.id.checkMark)
        val contactName: TextView = itemView.findViewById(R.id.contactName)
        val messagePreview: TextView = itemView.findViewById(R.id.messagePreview)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    fun toggleSelection(threadId: Long) {
        if (selectedIds.contains(threadId)) {
            selectedIds.remove(threadId)
        } else {
            selectedIds.add(threadId)
        }
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
    }

    fun selectAll(items: List<Conversation>) {
        selectedIds.clear()
        items.forEach { selectedIds.add(it.threadId) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun enterSelectionMode() {
        isSelectionMode = true
    }

    fun getSelectedIds(): Set<Long> = selectedIds.toSet()

    fun getSelectedCount(): Int = selectedIds.size

    fun updateTextScale(scale: Float) {
        listTextScale = scale
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        val displayName = conversation.contactName ?: conversation.recipientAddresses.firstOrNull() ?: "Unknown"
        val accentColor = AccentColorManager.getAccentColor()
        val isSelected = selectedIds.contains(conversation.threadId)

        // Apply text scale
        holder.contactName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f * listTextScale)
        holder.messagePreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * listTextScale)
        holder.timestamp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * listTextScale)

        // Avatar appearance based on selection
        if (isSelected) {
            // Show check mark, darken background
            holder.contactInitials.text = "\u2713"
            val darkerAccent = darkenColor(accentColor, 0.6f)
            holder.contactInitials.background.setTint(darkerAccent)
            holder.checkMark.visibility = View.GONE
        } else {
            // Normal initials
            val initials = getInitials(displayName)
            holder.contactInitials.text = initials
            holder.contactInitials.background.setTint(accentColor)
            holder.checkMark.visibility = View.GONE
        }

        // Contact name — bold if unread
        holder.contactName.text = displayName
        if (conversation.unreadCount > 0) {
            holder.contactName.setTypeface(
                holder.contactName.context.resources.getFont(R.font.selawik_semibold),
                Typeface.BOLD
            )
        } else {
            holder.contactName.setTypeface(
                holder.contactName.context.resources.getFont(R.font.selawik_regular),
                Typeface.NORMAL
            )
        }

        // Message preview with draft indicator
        if (conversation.isDraft && conversation.snippet.isNotEmpty()) {
            val draftPrefix = holder.itemView.context.getString(R.string.messages_draft)
            val spannable = SpannableStringBuilder()
            spannable.append(draftPrefix)
            spannable.setSpan(
                ForegroundColorSpan(accentColor),
                0, draftPrefix.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.append(conversation.snippet)
            holder.messagePreview.text = spannable
        } else {
            holder.messagePreview.text = conversation.snippet
        }

        // Timestamp
        holder.timestamp.text = formatTimestamp(conversation.date)

        // Click handlers — depend on selection mode
        if (isSelectionMode) {
            holder.itemView.setOnClickListener {
                toggleSelection(conversation.threadId)
            }
            holder.avatarContainer.setOnClickListener {
                toggleSelection(conversation.threadId)
            }
            holder.itemView.setOnLongClickListener { true }
            holder.avatarContainer.setOnLongClickListener { true }
        } else {
            holder.itemView.setOnClickListener { onClick(conversation) }
            holder.itemView.setOnLongClickListener {
                onLongClick(conversation, it)
                true
            }
            holder.avatarContainer.setOnClickListener { onClick(conversation) }
            holder.avatarContainer.setOnLongClickListener {
                onAvatarLongClick(conversation)
                true
            }
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
            parts.isNotEmpty() && parts[0].isNotEmpty() -> parts[0].first().uppercase()
            else -> "?"
        }
    }

    private fun formatTimestamp(dateMillis: Long): String {
        val now = Calendar.getInstance()
        val msgDate = Calendar.getInstance().apply { timeInMillis = dateMillis }

        return when {
            // Today
            now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dateMillis))
            }
            // This week (within 7 days)
            now.timeInMillis - dateMillis < 7 * 24 * 60 * 60 * 1000L -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dateMillis))
            }
            // Older
            else -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dateMillis))
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
            oldItem.threadId == newItem.threadId

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean =
            oldItem == newItem
    }
}
