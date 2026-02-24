package com.cubeslauncher.app.messages

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.messages.model.Conversation
import com.cubeslauncher.app.util.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onLongClick: (Conversation, View) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contactInitials: TextView = itemView.findViewById(R.id.contactInitials)
        val contactName: TextView = itemView.findViewById(R.id.contactName)
        val messagePreview: TextView = itemView.findViewById(R.id.messagePreview)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        val displayName = conversation.contactName ?: conversation.recipientAddresses.firstOrNull() ?: "Unknown"

        // Initials
        val initials = getInitials(displayName)
        holder.contactInitials.text = initials

        // Tint the initials circle with accent color
        val accentColor = AccentColorManager.getAccentColor()
        holder.contactInitials.background.setTint(accentColor)

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

        // Click handlers
        holder.itemView.setOnClickListener { onClick(conversation) }
        holder.itemView.setOnLongClickListener {
            onLongClick(conversation, it)
            true
        }
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
