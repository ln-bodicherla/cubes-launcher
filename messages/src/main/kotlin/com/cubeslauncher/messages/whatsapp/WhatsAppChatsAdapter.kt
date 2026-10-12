package com.cubeslauncher.messages.whatsapp

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.whatsapp.model.WhatsAppChat
import com.cubeslauncher.common.theme.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WhatsAppChatsAdapter(
    private val onClick: (WhatsAppChat) -> Unit
) : ListAdapter<WhatsAppChat, WhatsAppChatsAdapter.ViewHolder>(DiffCallback) {

    private var onLongClickListener: ((WhatsAppChat, View) -> Unit)? = null

    fun setOnLongClickListener(listener: (WhatsAppChat, View) -> Unit) {
        onLongClickListener = listener
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contactInitials: TextView = itemView.findViewById(R.id.contactInitials)
        val contactName: TextView = itemView.findViewById(R.id.contactName)
        val messagePreview: TextView = itemView.findViewById(R.id.messagePreview)
        val timestamp: TextView = itemView.findViewById(R.id.timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_whatsapp_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = getItem(position)

        // Initials
        val initials = getInitials(chat.name)
        holder.contactInitials.text = initials

        // Tint the initials circle with accent color
        val accentColor = AccentColorManager.getAccentColor()
        holder.contactInitials.background.setTint(accentColor)

        // Contact name — bold if unread
        if (chat.unreadCount > 0) {
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

        // Message preview with media type prefix
        val previewPrefix = when (chat.lastMessageType) {
            "image" -> "\uD83D\uDCF7 "
            "video" -> "\uD83C\uDFA5 "
            "audio", "ptt" -> "\uD83C\uDFA4 "
            "document" -> "\uD83D\uDCC4 "
            "sticker" -> "\uD83C\uDF1F "
            "location" -> "\uD83D\uDCCD "
            "contact" -> "\uD83D\uDC64 "
            else -> ""
        }
        holder.messagePreview.text = "$previewPrefix${chat.lastMessage}"

        // Timestamp (seconds → millis)
        val timeMillis = chat.lastMessageTime * 1000
        holder.timestamp.text = if (timeMillis > 0) formatTimestamp(timeMillis) else ""

        // Pin indicator in name
        val pinSuffix = if (chat.isPinned) " \uD83D\uDCCC" else ""
        holder.contactName.text = "${chat.name}$pinSuffix"

        // Click handler
        holder.itemView.setOnClickListener { onClick(chat) }

        // Long click handler
        holder.itemView.setOnLongClickListener { view ->
            onLongClickListener?.invoke(chat, view)
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
            now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dateMillis))
            }
            now.timeInMillis - dateMillis < 7 * 24 * 60 * 60 * 1000L -> {
                SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dateMillis))
            }
            else -> {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dateMillis))
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<WhatsAppChat>() {
        override fun areItemsTheSame(oldItem: WhatsAppChat, newItem: WhatsAppChat): Boolean =
            oldItem.jid == newItem.jid

        override fun areContentsTheSame(oldItem: WhatsAppChat, newItem: WhatsAppChat): Boolean =
            oldItem == newItem
    }
}
