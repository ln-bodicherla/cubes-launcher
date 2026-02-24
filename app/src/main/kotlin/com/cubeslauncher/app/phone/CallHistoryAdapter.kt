package com.cubeslauncher.app.phone

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ItemPhoneHistoryEntryBinding
import com.cubeslauncher.app.databinding.ItemPhoneHistoryHeaderBinding
import com.cubeslauncher.app.phone.model.CallLogEntry
import com.cubeslauncher.app.util.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHistoryAdapter(
    private val items: List<CallHistoryItem>,
    private val onCallClick: (CallLogEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    private var expandedPosition: Int = -1

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is CallHistoryItem.Header -> TYPE_HEADER
        is CallHistoryItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemPhoneHistoryHeaderBinding.inflate(inflater, parent, false)
            )
            else -> EntryViewHolder(
                ItemPhoneHistoryEntryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CallHistoryItem.Header -> (holder as HeaderViewHolder).bind(item)
            is CallHistoryItem.Entry -> (holder as EntryViewHolder).bind(item.entry, position)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(
        private val binding: ItemPhoneHistoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: CallHistoryItem.Header) {
            binding.headerText.text = header.label
            binding.headerText.setTextColor(AccentColorManager.getAccentColor())
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemPhoneHistoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(entry: CallLogEntry, position: Int) {
            binding.contactName.text = entry.contactName?.ifEmpty { null } ?: entry.number.ifEmpty { "Unknown" }

            binding.callTime.text = timeFormat.format(Date(entry.date))

            val iconRes = when (entry.callType) {
                CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming
                CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing
                CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed
                else -> R.drawable.ic_call_incoming
            }
            binding.callTypeIcon.setImageResource(iconRes)

            // Tint missed calls with accent color
            if (entry.callType == CallLog.Calls.MISSED_TYPE) {
                binding.contactName.setTextColor(AccentColorManager.getAccentColor())
            } else {
                val ta = binding.root.context.obtainStyledAttributes(intArrayOf(R.attr.wpTextPrimary))
                binding.contactName.setTextColor(ta.getColor(0, 0xFFFFFFFF.toInt()))
                ta.recycle()
            }

            // Expand/collapse
            val isExpanded = position == expandedPosition
            binding.actionRow.visibility = if (isExpanded) View.VISIBLE else View.GONE

            binding.entryTile.setOnClickListener {
                val adapterPos = bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) return@setOnClickListener

                val previousExpanded = expandedPosition
                expandedPosition = if (previousExpanded == adapterPos) -1 else adapterPos

                if (previousExpanded != -1) notifyItemChanged(previousExpanded)
                if (expandedPosition != -1) notifyItemChanged(expandedPosition)
            }

            // Info button (always visible on right side) → opens info page
            binding.infoButton.setOnClickListener {
                openCallInfo(it, entry)
            }

            // Action: call
            binding.actionCall.setOnClickListener {
                onCallClick(entry)
            }

            // Action: message
            binding.actionMessage.setOnClickListener {
                val context = it.context
                val smsUri = Uri.parse("smsto:${entry.number}")
                val intent = Intent(Intent.ACTION_SENDTO, smsUri)
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun openCallInfo(view: View, entry: CallLogEntry) {
            val context = view.context
            val intent = Intent(context, CallInfoActivity::class.java).apply {
                putExtra(CallInfoActivity.EXTRA_NUMBER, entry.number)
                putExtra(CallInfoActivity.EXTRA_CONTACT_NAME, entry.contactName)
            }
            context.startActivity(intent)
        }
    }
}
