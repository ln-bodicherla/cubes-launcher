package com.cubeslauncher.phone

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.phone.R
import com.cubeslauncher.phone.databinding.ItemPhoneHistoryEntryBinding
import com.cubeslauncher.phone.databinding.ItemPhoneHistoryHeaderBinding
import com.cubeslauncher.phone.model.CallLogEntry
import com.cubeslauncher.common.theme.AccentColorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallHistoryAdapter(
    private val items: List<CallHistoryItem>,
    private val onCallClick: (CallLogEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Cache SIM label lookup: phoneAccountId -> display label
    private var simLabelCache: Map<String, String>? = null

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
            val context = binding.root.context

            binding.contactName.text = entry.contactName?.ifEmpty { null } ?: entry.number.ifEmpty { "Unknown" }

            // Time + SIM tag + WhatsApp indicator in subtitle
            val timeText = timeFormat.format(Date(entry.date))
            val simLabel = resolveSimLabel(context, entry.phoneAccountId)
            val parts = mutableListOf(timeText)
            if (simLabel != null) parts.add(simLabel)
            if (entry.isWhatsAppCall) {
                val typeLabel = if (entry.isVideoCall)
                    context.getString(R.string.phone_whatsapp_video)
                else
                    context.getString(R.string.phone_whatsapp_voice)
                parts.add(typeLabel)
            }
            binding.callTime.text = parts.joinToString("  \u00B7  ")

            // Call type icon
            val iconRes = when (entry.callType) {
                CallLog.Calls.INCOMING_TYPE -> R.drawable.ic_call_incoming
                CallLog.Calls.OUTGOING_TYPE -> R.drawable.ic_call_outgoing
                CallLog.Calls.MISSED_TYPE -> R.drawable.ic_call_missed
                else -> R.drawable.ic_call_incoming
            }
            binding.callTypeIcon.setImageResource(iconRes)

            // WhatsApp badge
            binding.whatsappBadge.visibility = if (entry.isWhatsAppCall) View.VISIBLE else View.GONE

            // Tint missed calls with red
            if (entry.callType == CallLog.Calls.MISSED_TYPE) {
                binding.contactName.setTextColor(0xFFE51400.toInt())
            } else {
                val ta = context.obtainStyledAttributes(intArrayOf(R.attr.wpTextPrimary))
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

            // Update action call button for WhatsApp entries
            if (entry.isWhatsAppCall) {
                binding.actionCallIcon.setImageResource(R.drawable.ic_whatsapp)
                val label = if (entry.isVideoCall)
                    context.getString(R.string.phone_whatsapp_video)
                else
                    context.getString(R.string.phone_whatsapp_voice)
                binding.actionCallLabel.text = label
            } else {
                binding.actionCallIcon.setImageResource(R.drawable.ic_phone_call)
                binding.actionCallLabel.text = context.getString(R.string.phone_call)
            }

            // Info button (always visible on right side) -> opens info page
            binding.infoButton.setOnClickListener {
                openCallInfo(it, entry)
            }

            // Action: call (or WhatsApp call)
            binding.actionCall.setOnClickListener {
                onCallClick(entry)
            }

            // Action: message
            binding.actionMessage.setOnClickListener {
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

    private fun resolveSimLabel(context: android.content.Context, phoneAccountId: String?): String? {
        if (phoneAccountId.isNullOrEmpty()) return null

        // Build cache on first call
        if (simLabelCache == null) {
            simLabelCache = buildSimLabelCache(context)
        }

        return simLabelCache?.get(phoneAccountId)
    }

    private fun buildSimLabelCache(context: android.content.Context): Map<String, String> {
        val cache = mutableMapOf<String, String>()
        try {
            val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return cache
            @Suppress("MissingPermission")
            val subscriptions = subManager.activeSubscriptionInfoList ?: return cache
            // Only show SIM labels if there are 2+ SIMs
            if (subscriptions.size < 2) return cache
            for ((index, subInfo) in subscriptions.withIndex()) {
                val label = subInfo.displayName?.toString()?.ifEmpty { null }
                    ?: "SIM ${index + 1}"
                // PHONE_ACCOUNT_ID typically stores the subscription ID as a string
                cache[subInfo.subscriptionId.toString()] = label
                // Also map the ICCID which some devices use
                subInfo.iccId?.let { cache[it] = label }
            }
        } catch (_: SecurityException) {
            // READ_PHONE_STATE not granted
        } catch (_: Exception) {
            // Device may not support multi-SIM
        }
        return cache
    }
}
