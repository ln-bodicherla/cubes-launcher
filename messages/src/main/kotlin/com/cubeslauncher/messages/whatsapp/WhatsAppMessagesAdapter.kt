package com.cubeslauncher.messages.whatsapp

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.whatsapp.model.WhatsAppMessage
import com.cubeslauncher.common.theme.AccentColorManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WhatsAppMessagesAdapter(
    private val isGroup: Boolean,
    private val onLongClick: ((WhatsAppMessage, View) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
        private const val TYPE_TIMESTAMP = 2
        private const val TYPE_IMAGE_SENT = 3
        private const val TYPE_IMAGE_RECEIVED = 4
        private const val TYPE_VIDEO_SENT = 5
        private const val TYPE_VIDEO_RECEIVED = 6
        private const val TYPE_AUDIO_SENT = 7
        private const val TYPE_AUDIO_RECEIVED = 8
        private const val TYPE_DOCUMENT_SENT = 9
        private const val TYPE_DOCUMENT_RECEIVED = 10
        private const val TYPE_STICKER = 11
    }

    sealed class Item {
        data class MessageItem(val message: WhatsAppMessage) : Item()
        data class TimestampItem(val text: String) : Item()
    }

    private val items = mutableListOf<Item>()

    fun submitMessages(messages: List<WhatsAppMessage>) {
        items.clear()
        var lastDate: Long = 0

        for (message in messages) {
            val timeMillis = message.timestamp * 1000
            if (shouldInsertTimestamp(lastDate, timeMillis)) {
                items.add(Item.TimestampItem(formatDateSeparator(timeMillis)))
            }
            lastDate = timeMillis
            items.add(Item.MessageItem(message))
        }
        notifyDataSetChanged()
    }

    fun addMessage(message: WhatsAppMessage) {
        val timeMillis = message.timestamp * 1000
        if (items.isNotEmpty()) {
            val lastItem = items.lastOrNull()
            val lastDate = when (lastItem) {
                is Item.MessageItem -> lastItem.message.timestamp * 1000
                is Item.TimestampItem -> 0L
                else -> 0L
            }
            if (shouldInsertTimestamp(lastDate, timeMillis)) {
                items.add(Item.TimestampItem(formatDateSeparator(timeMillis)))
                notifyItemInserted(items.size - 1)
            }
        }
        items.add(Item.MessageItem(message))
        notifyItemInserted(items.size - 1)
    }

    fun updateMessage(messageId: String, updater: (WhatsAppMessage) -> WhatsAppMessage) {
        for (i in items.indices) {
            val item = items[i]
            if (item is Item.MessageItem && item.message.id == messageId) {
                items[i] = Item.MessageItem(updater(item.message))
                notifyItemChanged(i)
                break
            }
        }
    }

    private fun shouldInsertTimestamp(lastDate: Long, currentDate: Long): Boolean {
        if (lastDate == 0L) return true
        val diff = currentDate - lastDate
        if (diff > 30 * 60 * 1000) return true

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

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is Item.MessageItem -> {
            val msg = item.message
            if (msg.isRevoked) {
                if (msg.isFromMe) TYPE_SENT else TYPE_RECEIVED
            } else when (msg.messageType) {
                "image" -> if (msg.isFromMe) TYPE_IMAGE_SENT else TYPE_IMAGE_RECEIVED
                "video" -> if (msg.isFromMe) TYPE_VIDEO_SENT else TYPE_VIDEO_RECEIVED
                "audio", "ptt" -> if (msg.isFromMe) TYPE_AUDIO_SENT else TYPE_AUDIO_RECEIVED
                "document" -> if (msg.isFromMe) TYPE_DOCUMENT_SENT else TYPE_DOCUMENT_RECEIVED
                "sticker" -> TYPE_STICKER
                else -> if (msg.isFromMe) TYPE_SENT else TYPE_RECEIVED
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
            TYPE_IMAGE_SENT -> ImageSentViewHolder(inflater.inflate(R.layout.item_message_image_sent, parent, false))
            TYPE_IMAGE_RECEIVED -> ImageReceivedViewHolder(inflater.inflate(R.layout.item_message_image_received, parent, false))
            TYPE_VIDEO_SENT -> VideoSentViewHolder(inflater.inflate(R.layout.item_message_video_sent, parent, false))
            TYPE_VIDEO_RECEIVED -> VideoReceivedViewHolder(inflater.inflate(R.layout.item_message_video_received, parent, false))
            TYPE_AUDIO_SENT -> AudioSentViewHolder(inflater.inflate(R.layout.item_message_audio_sent, parent, false))
            TYPE_AUDIO_RECEIVED -> AudioReceivedViewHolder(inflater.inflate(R.layout.item_message_audio_received, parent, false))
            TYPE_DOCUMENT_SENT -> DocumentSentViewHolder(inflater.inflate(R.layout.item_message_document_sent, parent, false))
            TYPE_DOCUMENT_RECEIVED -> DocumentReceivedViewHolder(inflater.inflate(R.layout.item_message_document_received, parent, false))
            TYPE_STICKER -> StickerViewHolder(inflater.inflate(R.layout.item_message_sticker, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.MessageItem -> {
                val message = item.message

                // Set up long click for all message types
                holder.itemView.setOnLongClickListener {
                    onLongClick?.invoke(message, it)
                    true
                }

                when (holder) {
                    is SentViewHolder -> bindSentText(holder, message)
                    is ReceivedViewHolder -> bindReceivedText(holder, message)
                    is ImageSentViewHolder -> bindImageSent(holder, message)
                    is ImageReceivedViewHolder -> bindImageReceived(holder, message)
                    is VideoSentViewHolder -> bindVideoSent(holder, message)
                    is VideoReceivedViewHolder -> bindVideoReceived(holder, message)
                    is AudioSentViewHolder -> bindAudioSent(holder, message)
                    is AudioReceivedViewHolder -> bindAudioReceived(holder, message)
                    is DocumentSentViewHolder -> bindDocumentSent(holder, message)
                    is DocumentReceivedViewHolder -> bindDocumentReceived(holder, message)
                    is StickerViewHolder -> bindSticker(holder, message)
                }
            }
            is Item.TimestampItem -> {
                (holder as TimestampViewHolder).timestampText.text = item.text
            }
        }
    }

    // --- Bind Text ---

    private fun bindSentText(holder: SentViewHolder, message: WhatsAppMessage) {
        val accentColor = AccentColorManager.getAccentColor()
        holder.bubbleContainer.background.setTint(accentColor)

        if (message.isRevoked) {
            holder.messageText.text = "This message was deleted"
            holder.messageText.setTypeface(null, Typeface.ITALIC)
        } else {
            holder.messageText.text = buildDisplayText(message)
            holder.messageText.setTypeface(null, Typeface.NORMAL)
        }

        // Show quoted message if present
        bindQuote(holder.itemView, message)

        // Delivery status
        holder.deliveryStatus.visibility = when (message.deliveryStatus) {
            "read" -> {
                holder.deliveryStatus.text = "\u2713\u2713"
                holder.deliveryStatus.setTextColor(accentColor)
                View.VISIBLE
            }
            "delivered" -> {
                holder.deliveryStatus.text = "\u2713\u2713"
                View.VISIBLE
            }
            "sent" -> {
                holder.deliveryStatus.text = "\u2713"
                View.VISIBLE
            }
            else -> View.GONE
        }

        // Edited label
        val editedView = holder.itemView.findViewById<TextView>(R.id.editedLabel)
        editedView?.visibility = if (message.isEdited) View.VISIBLE else View.GONE
    }

    private fun bindReceivedText(holder: ReceivedViewHolder, message: WhatsAppMessage) {
        if (message.isRevoked) {
            holder.messageText.text = "This message was deleted"
            holder.messageText.setTypeface(null, Typeface.ITALIC)
        } else {
            holder.messageText.text = buildDisplayText(message)
            holder.messageText.setTypeface(null, Typeface.NORMAL)
        }

        if (isGroup && message.senderName != null) {
            holder.senderName.text = message.senderName
            holder.senderName.visibility = View.VISIBLE
        } else {
            holder.senderName.visibility = View.GONE
        }

        bindQuote(holder.itemView, message)
    }

    private fun buildDisplayText(message: WhatsAppMessage): String {
        var text = message.body
        if (message.isEdited) {
            text += " (edited)"
        }
        return text
    }

    private fun bindQuote(itemView: View, message: WhatsAppMessage) {
        val quoteBar = itemView.findViewById<View>(R.id.quoteBar)
        val quoteSender = itemView.findViewById<TextView>(R.id.quoteSenderName)
        val quoteBody = itemView.findViewById<TextView>(R.id.quoteBody)

        if (quoteBar != null && message.quotedMessageId != null) {
            quoteBar.visibility = View.VISIBLE
            quoteSender?.text = message.quotedSenderName ?: ""
            quoteSender?.visibility = if (message.quotedSenderName != null) View.VISIBLE else View.GONE
            quoteBody?.text = message.quotedBody ?: ""
            quoteBody?.visibility = View.VISIBLE
        } else {
            quoteBar?.visibility = View.GONE
        }
    }

    // --- Bind Image ---

    private fun bindImageSent(holder: ImageSentViewHolder, message: WhatsAppMessage) {
        val accentColor = AccentColorManager.getAccentColor()
        holder.bubbleContainer.background.setTint(accentColor)

        loadMediaImage(holder.mediaImage, message)

        if (!message.caption.isNullOrEmpty()) {
            holder.captionText.text = message.caption
            holder.captionText.visibility = View.VISIBLE
        } else {
            holder.captionText.visibility = View.GONE
        }

        holder.deliveryStatus.visibility = View.GONE
    }

    private fun bindImageReceived(holder: ImageReceivedViewHolder, message: WhatsAppMessage) {
        if (isGroup && message.senderName != null) {
            holder.senderName.text = message.senderName
            holder.senderName.visibility = View.VISIBLE
        } else {
            holder.senderName.visibility = View.GONE
        }

        loadMediaImage(holder.mediaImage, message)

        if (!message.caption.isNullOrEmpty()) {
            holder.captionText.text = message.caption
            holder.captionText.visibility = View.VISIBLE
        } else {
            holder.captionText.visibility = View.GONE
        }
    }

    // --- Bind Video ---

    private fun bindVideoSent(holder: VideoSentViewHolder, message: WhatsAppMessage) {
        val accentColor = AccentColorManager.getAccentColor()
        holder.bubbleContainer.background.setTint(accentColor)

        loadMediaImage(holder.mediaImage, message)

        if (!message.caption.isNullOrEmpty()) {
            holder.captionText.text = message.caption
            holder.captionText.visibility = View.VISIBLE
        } else {
            holder.captionText.visibility = View.GONE
        }

        if (message.mediaDuration > 0) {
            holder.videoDuration.text = formatDuration(message.mediaDuration)
            holder.videoDuration.visibility = View.VISIBLE
        } else {
            holder.videoDuration.visibility = View.GONE
        }

        holder.deliveryStatus.visibility = View.GONE
    }

    private fun bindVideoReceived(holder: VideoReceivedViewHolder, message: WhatsAppMessage) {
        if (isGroup && message.senderName != null) {
            holder.senderName.text = message.senderName
            holder.senderName.visibility = View.VISIBLE
        } else {
            holder.senderName.visibility = View.GONE
        }

        loadMediaImage(holder.mediaImage, message)

        if (!message.caption.isNullOrEmpty()) {
            holder.captionText.text = message.caption
            holder.captionText.visibility = View.VISIBLE
        } else {
            holder.captionText.visibility = View.GONE
        }

        if (message.mediaDuration > 0) {
            holder.videoDuration.text = formatDuration(message.mediaDuration)
            holder.videoDuration.visibility = View.VISIBLE
        } else {
            holder.videoDuration.visibility = View.GONE
        }
    }

    // --- Bind Audio ---

    private fun bindAudioSent(holder: AudioSentViewHolder, message: WhatsAppMessage) {
        val accentColor = AccentColorManager.getAccentColor()
        holder.bubbleContainer.background.setTint(accentColor)

        holder.audioDuration.text = if (message.mediaDuration > 0)
            formatDuration(message.mediaDuration) else "0:00"

        holder.audioSeekBar.progress = 0
        holder.deliveryStatus.visibility = View.GONE
    }

    private fun bindAudioReceived(holder: AudioReceivedViewHolder, message: WhatsAppMessage) {
        if (isGroup && message.senderName != null) {
            holder.senderName.text = message.senderName
            holder.senderName.visibility = View.VISIBLE
        } else {
            holder.senderName.visibility = View.GONE
        }

        holder.audioDuration.text = if (message.mediaDuration > 0)
            formatDuration(message.mediaDuration) else "0:00"

        holder.audioSeekBar.progress = 0
    }

    // --- Bind Document ---

    private fun bindDocumentSent(holder: DocumentSentViewHolder, message: WhatsAppMessage) {
        val accentColor = AccentColorManager.getAccentColor()
        holder.bubbleContainer.background.setTint(accentColor)

        holder.fileName.text = message.mediaFileName ?: message.body.ifEmpty { "Document" }
        holder.fileSize.text = if (message.mediaSize > 0) formatFileSize(message.mediaSize) else ""
        holder.deliveryStatus.visibility = View.GONE
    }

    private fun bindDocumentReceived(holder: DocumentReceivedViewHolder, message: WhatsAppMessage) {
        if (isGroup && message.senderName != null) {
            holder.senderName.text = message.senderName
            holder.senderName.visibility = View.VISIBLE
        } else {
            holder.senderName.visibility = View.GONE
        }

        holder.fileName.text = message.mediaFileName ?: message.body.ifEmpty { "Document" }
        holder.fileSize.text = if (message.mediaSize > 0) formatFileSize(message.mediaSize) else ""
    }

    // --- Bind Sticker ---

    private fun bindSticker(holder: StickerViewHolder, message: WhatsAppMessage) {
        loadMediaImage(holder.stickerImage, message)
    }

    // --- Helper Methods ---

    private fun loadMediaImage(imageView: ImageView, message: WhatsAppMessage) {
        val path = message.mediaLocalPath ?: message.thumbnailPath
        if (path != null) {
            Glide.with(imageView.context)
                .load(File(path))
                .centerCrop()
                .into(imageView)
        } else {
            imageView.setImageDrawable(null)
        }
    }

    private fun formatDuration(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    // --- ViewHolder classes ---

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

    class ImageSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleContainer: View = itemView.findViewById(R.id.bubbleContainer)
        val mediaImage: ImageView = itemView.findViewById(R.id.mediaImage)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val deliveryStatus: TextView = itemView.findViewById(R.id.deliveryStatus)
    }

    class ImageReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val mediaImage: ImageView = itemView.findViewById(R.id.mediaImage)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
    }

    class VideoSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleContainer: View = itemView.findViewById(R.id.bubbleContainer)
        val mediaImage: ImageView = itemView.findViewById(R.id.mediaImage)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val videoDuration: TextView = itemView.findViewById(R.id.videoDuration)
        val deliveryStatus: TextView = itemView.findViewById(R.id.deliveryStatus)
    }

    class VideoReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val mediaImage: ImageView = itemView.findViewById(R.id.mediaImage)
        val captionText: TextView = itemView.findViewById(R.id.captionText)
        val videoDuration: TextView = itemView.findViewById(R.id.videoDuration)
    }

    class AudioSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleContainer: View = itemView.findViewById(R.id.bubbleContainer)
        val playPauseButton: ImageButton = itemView.findViewById(R.id.playPauseButton)
        val audioSeekBar: SeekBar = itemView.findViewById(R.id.audioSeekBar)
        val audioDuration: TextView = itemView.findViewById(R.id.audioDuration)
        val deliveryStatus: TextView = itemView.findViewById(R.id.deliveryStatus)
    }

    class AudioReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val playPauseButton: ImageButton = itemView.findViewById(R.id.playPauseButton)
        val audioSeekBar: SeekBar = itemView.findViewById(R.id.audioSeekBar)
        val audioDuration: TextView = itemView.findViewById(R.id.audioDuration)
    }

    class DocumentSentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleContainer: View = itemView.findViewById(R.id.bubbleContainer)
        val fileName: TextView = itemView.findViewById(R.id.fileName)
        val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        val deliveryStatus: TextView = itemView.findViewById(R.id.deliveryStatus)
    }

    class DocumentReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderName: TextView = itemView.findViewById(R.id.senderName)
        val fileName: TextView = itemView.findViewById(R.id.fileName)
        val fileSize: TextView = itemView.findViewById(R.id.fileSize)
    }

    class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stickerImage: ImageView = itemView.findViewById(R.id.stickerImage)
    }
}
