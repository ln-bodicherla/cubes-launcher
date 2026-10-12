package com.cubeslauncher.messages

import android.provider.Telephony
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.model.Message
import com.cubeslauncher.common.theme.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessagesAdapter(
    private val isGroup: Boolean,
    private val onLongClick: (Message, View) -> Unit,
    var messageTextScale: Float = 1.0f
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
        private const val TYPE_TIMESTAMP = 2
    }

    sealed class Item {
        data class MessageItem(val message: Message) : Item()
        data class TimestampItem(val text: String) : Item()
    }

    private val items = mutableListOf<Item>()

    fun submitMessages(messages: List<Message>) {
        items.clear()
        var lastDate: Long = 0

        for (message in messages) {
            // Skip drafts
            if (message.type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue

            // Insert timestamp separator if needed
            if (shouldInsertTimestamp(lastDate, message.date)) {
                items.add(Item.TimestampItem(formatDateSeparator(message.date)))
            }
            lastDate = message.date
            items.add(Item.MessageItem(message))
        }
        notifyDataSetChanged()
    }

    fun addMessage(message: Message) {
        if (items.isNotEmpty()) {
            val lastItem = items.lastOrNull()
            val lastDate = when (lastItem) {
                is Item.MessageItem -> lastItem.message.date
                is Item.TimestampItem -> 0L
                else -> 0L
            }
            if (shouldInsertTimestamp(lastDate, message.date)) {
                items.add(Item.TimestampItem(formatDateSeparator(message.date)))
                notifyItemInserted(items.size - 1)
            }
        }
        items.add(Item.MessageItem(message))
        notifyItemInserted(items.size - 1)
    }

    private fun shouldInsertTimestamp(lastDate: Long, currentDate: Long): Boolean {
        if (lastDate == 0L) return true
        val diff = currentDate - lastDate
        if (diff > 30 * 60 * 1000) return true // >30 min gap

        val lastCal = Calendar.getInstance().apply { timeInMillis = lastDate }
        val currentCal = Calendar.getInstance().apply { timeInMillis = currentDate }
        return lastCal.get(Calendar.DAY_OF_YEAR) != currentCal.get(Calendar.DAY_OF_YEAR) ||
               lastCal.get(Calendar.YEAR) != currentCal.get(Calendar.YEAR)
    }

    private fun formatDateSeparator(dateMillis: Long): String {
        val now = Calendar.getInstance()
        val msgDate = Calendar.getInstance().apply { timeInMillis = dateMillis }

        return when {
            now.get(Calendar.YEAR) == msgDate.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgDate.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dateMillis))
            }
            now.timeInMillis - dateMillis < 7 * 24 * 60 * 60 * 1000L -> {
                SimpleDateFormat("EEEE h:mm a", Locale.getDefault()).format(Date(dateMillis))
            }
            else -> {
                SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(dateMillis))
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.MessageItem -> {
            val msg = (items[position] as Item.MessageItem).message
            if (msg.type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                msg.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                TYPE_SENT
            } else {
                TYPE_RECEIVED
            }
        }
        is Item.TimestampItem -> TYPE_TIMESTAMP
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
            TYPE_RECEIVED -> ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
            TYPE_TIMESTAMP -> TimestampViewHolder(inflater.inflate(R.layout.item_message_timestamp, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.MessageItem -> {
                val message = item.message
                when (holder) {
                    is SentViewHolder -> {
                        holder.messageText.text = message.body
                        holder.messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * messageTextScale)

                        // Apply accent color to sent bubble
                        val accentColor = AccentColorManager.getAccentColor()
                        holder.bubbleContainer.background.setTint(accentColor)

                        // Delivery status
                        val context = holder.itemView.context
                        val statusText = when {
                            message.deliveryStatus == Telephony.Sms.STATUS_COMPLETE ->
                                context.getString(R.string.messages_delivery_delivered)
                            message.deliveryStatus == Telephony.Sms.STATUS_PENDING ->
                                context.getString(R.string.messages_delivery_pending)
                            message.deliveryStatus == Telephony.Sms.STATUS_FAILED ->
                                context.getString(R.string.messages_delivery_failed)
                            else ->
                                context.getString(R.string.messages_delivery_sent)
                        }
                        holder.deliveryStatus.text = statusText
                        holder.deliveryStatus.visibility = View.VISIBLE

                        holder.itemView.setOnLongClickListener {
                            onLongClick(message, it)
                            true
                        }
                    }
                    is ReceivedViewHolder -> {
                        holder.messageText.text = message.body
                        holder.messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * messageTextScale)

                        // Show sender name in group conversations
                        if (isGroup && message.contactName != null) {
                            holder.senderName.text = message.contactName
                            holder.senderName.visibility = View.VISIBLE
                        } else {
                            holder.senderName.visibility = View.GONE
                        }

                        holder.itemView.setOnLongClickListener {
                            onLongClick(message, it)
                            true
                        }
                    }
                }
            }
            is Item.TimestampItem -> {
                (holder as TimestampViewHolder).timestampText.text = item.text
            }
        }
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleContainer: View = itemView.findViewById(R.id.bubbleContainer)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
        val deliveryStatus: TextView = itemView.findViewById(R.id.deliveryStatus)
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val messageText: TextView = itemView.findViewById(R.id.messageText)
    }

    class TimestampViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestampText: TextView = itemView.findViewById(R.id.timestampText)
    }
}
