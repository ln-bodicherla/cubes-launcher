package com.cubeslauncher.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.phone.R
import com.cubeslauncher.phone.databinding.ActivityPhoneCallInfoBinding
import com.cubeslauncher.phone.databinding.ItemPhoneCallInfoEntryBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
    }

    private lateinit var binding: ActivityPhoneCallInfoBinding
    private lateinit var simHelper: SimSelectionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPhoneCallInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        simHelper = SimSelectionHelper(this)
        applyWindowInsets()

        val number = intent.getStringExtra(EXTRA_NUMBER) ?: ""
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)

        val displayName = contactName?.ifEmpty { null } ?: number.ifEmpty { "Unknown" }
        binding.contactName.text = displayName
        binding.contactName.setTextColor(AccentColorManager.getAccentColor())

        if (!contactName.isNullOrEmpty() && number.isNotEmpty()) {
            binding.contactNumber.text = number
            binding.contactNumber.visibility = View.VISIBLE
        } else {
            binding.contactNumber.visibility = View.GONE
        }

        setupActionButtons(number)
        loadCallHistory(number)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraTop = (16 * density).toInt()
            val extraBottom = (24 * density).toInt()

            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                systemBars.top + extraTop,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )

            binding.callListRecyclerView.setPadding(
                binding.callListRecyclerView.paddingLeft,
                binding.callListRecyclerView.paddingTop,
                binding.callListRecyclerView.paddingRight,
                systemBars.bottom + extraBottom
            )
            insets
        }
    }

    private fun setupActionButtons(number: String) {
        binding.actionCall.setOnClickListener {
            if (number.isNotEmpty()) placeCall(number)
        }

        binding.actionCall.setOnLongClickListener {
            if (number.isNotEmpty()) showSimPicker(it, number)
            true
        }

        binding.actionMessage.setOnClickListener {
            if (number.isNotEmpty()) {
                val smsUri = Uri.parse("smsto:$number")
                val intent = Intent(Intent.ACTION_SENDTO, smsUri)
                try {
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }

        binding.actionOpenContact.setOnClickListener {
            openOrCreateContact(number)
        }
    }

    private fun openOrCreateContact(number: String) {
        if (number.isEmpty()) return

        // Try to find existing contact
        val contactUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        try {
            val cursor = contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId = it.getLong(0)
                    val lookupUri = ContactsContract.Contacts.getLookupUri(
                        contentResolver,
                        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
                    )
                    if (lookupUri != null) {
                        startActivity(Intent(Intent.ACTION_VIEW, lookupUri))
                        return
                    }
                }
            }
        } catch (_: Exception) { }

        // No contact — offer to create one
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun showSimPicker(anchor: View, number: String) {
        val sims = simHelper.getAvailableSims()
        if (sims.size < 2) {
            // Single SIM or no SIM info — just place the call normally
            placeCall(number)
            return
        }

        val popup = PopupMenu(this, anchor)
        sims.forEachIndexed { index, sim ->
            popup.menu.add(0, index, index, sim.label)
        }
        popup.setOnMenuItemClickListener { item ->
            val sim = sims.getOrNull(item.itemId)
            placeCall(number, sim?.phoneAccountHandle)
            true
        }
        popup.show()
    }

    private fun placeCall(number: String, phoneAccountHandle: android.telecom.PhoneAccountHandle? = null) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromParts("tel", number, null)
        val telecom = getSystemService(TelecomManager::class.java)
        try {
            val extras = Bundle()
            if (phoneAccountHandle != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }
            telecom?.placeCall(uri, extras)
        } catch (_: SecurityException) {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCallHistory(number: String) {
        if (number.isEmpty() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            binding.emptyState.visibility = View.VISIBLE
            binding.callListRecyclerView.visibility = View.GONE
            return
        }

        val systemEntries = queryCallsForNumber(number)
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        val waEntries = queryWhatsAppCallsForNumber(number, contactName)
        val entries = (systemEntries + waEntries).sortedByDescending { it.date }
        if (entries.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.callListRecyclerView.visibility = View.GONE
            return
        }

        binding.callListRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.callListRecyclerView.adapter = CallInfoAdapter(entries, number)
        binding.callListRecyclerView.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun queryCallsForNumber(number: String): List<CallInfoEntry> {
        val entries = mutableListOf<CallInfoEntry>()
        // Normalize: strip non-digit chars for comparison
        val normalizedTarget = number.replace(Regex("[^\\d+]"), "")

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.FEATURES,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.let {
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val featuresIdx = it.getColumnIndex(CallLog.Calls.FEATURES)
                val componentIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME)
                val accountIdIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (it.moveToNext()) {
                    val entryNumber = it.getString(numberIdx) ?: ""
                    val normalizedEntry = entryNumber.replace(Regex("[^\\d+]"), "")

                    if (normalizedEntry == normalizedTarget ||
                        normalizedEntry.endsWith(normalizedTarget) ||
                        normalizedTarget.endsWith(normalizedEntry)
                    ) {
                        val componentName = if (componentIdx >= 0) it.getString(componentIdx) else null
                        val features = if (featuresIdx >= 0) it.getInt(featuresIdx) else 0
                        val isWhatsApp = componentName?.startsWith("com.whatsapp/") == true ||
                                componentName?.startsWith("com.whatsapp.w4b/") == true
                        val isVideo = (features and CallLog.Calls.FEATURES_VIDEO) != 0

                        entries.add(
                            CallInfoEntry(
                                callType = it.getInt(typeIdx),
                                date = it.getLong(dateIdx),
                                duration = it.getLong(durationIdx),
                                isWhatsApp = isWhatsApp,
                                isVideo = isVideo,
                                phoneAccountId = if (accountIdIdx >= 0) it.getString(accountIdIdx) else null
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } finally {
            cursor?.close()
        }

        return entries
    }

    private fun queryWhatsAppCallsForNumber(number: String, contactName: String?): List<CallInfoEntry> {
        val normalizedTarget = number.replace(Regex("[^\\d+]"), "")
        return WhatsAppCallLogger.getInstance(this).getEntries()
            .filter { entry ->
                val normalizedEntry = entry.number.replace(Regex("[^\\d+]"), "")
                (normalizedEntry.isNotEmpty() && (
                    normalizedEntry == normalizedTarget ||
                    normalizedEntry.endsWith(normalizedTarget) ||
                    normalizedTarget.endsWith(normalizedEntry)
                )) || (!contactName.isNullOrEmpty() && entry.contactName == contactName)
            }
            .map { entry ->
                CallInfoEntry(
                    callType = entry.callType,
                    date = entry.date,
                    duration = entry.duration,
                    isWhatsApp = true,
                    isVideo = entry.isVideoCall
                )
            }
    }

    data class CallInfoEntry(
        val callType: Int,
        val date: Long,
        val duration: Long,
        val isWhatsApp: Boolean = false,
        val isVideo: Boolean = false,
        val phoneAccountId: String? = null
    )

    private inner class CallInfoAdapter(
        private val entries: List<CallInfoEntry>,
        private val number: String
    ) : RecyclerView.Adapter<CallInfoAdapter.ViewHolder>() {

        private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
        private val simLabelCache: Map<String, String> by lazy { buildSimLabelCache() }

        inner class ViewHolder(
            val binding: ItemPhoneCallInfoEntryBinding
        ) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPhoneCallInfoEntryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            val b = holder.binding

            val (typeLabel, iconRes) = when (entry.callType) {
                CallLog.Calls.INCOMING_TYPE ->
                    getString(R.string.phone_info_incoming) to R.drawable.ic_call_incoming
                CallLog.Calls.OUTGOING_TYPE ->
                    getString(R.string.phone_info_outgoing) to R.drawable.ic_call_outgoing
                CallLog.Calls.MISSED_TYPE ->
                    getString(R.string.phone_info_missed) to R.drawable.ic_call_missed
                CallLog.Calls.REJECTED_TYPE ->
                    getString(R.string.phone_info_rejected) to R.drawable.ic_call_missed
                CallLog.Calls.BLOCKED_TYPE ->
                    getString(R.string.phone_info_blocked) to R.drawable.ic_call_missed
                else ->
                    getString(R.string.phone_info_unknown_type) to R.drawable.ic_call_incoming
            }

            b.callTypeIcon.setImageResource(iconRes)

            // Build display label: type + SIM + WhatsApp
            val parts = mutableListOf(typeLabel)
            val simLabel = entry.phoneAccountId?.let { simLabelCache[it] }
            if (simLabel != null) parts.add(simLabel)
            if (entry.isWhatsApp) {
                val waType = if (entry.isVideo)
                    getString(R.string.phone_whatsapp_video)
                else
                    getString(R.string.phone_whatsapp_voice)
                parts.add(waType)
            }
            b.callTypeText.text = parts.joinToString("  \u00B7  ")

            // Tint missed/rejected with accent
            if (entry.callType == CallLog.Calls.MISSED_TYPE || entry.callType == CallLog.Calls.REJECTED_TYPE) {
                b.callTypeText.setTextColor(AccentColorManager.getAccentColor())
            } else {
                val ta = obtainStyledAttributes(intArrayOf(R.attr.wpTextPrimary))
                b.callTypeText.setTextColor(ta.getColor(0, 0xFFFFFFFF.toInt()))
                ta.recycle()
            }

            b.callDuration.text = getString(R.string.phone_info_duration, formatDuration(entry.duration))
            b.callDateTime.text = dateTimeFormat.format(Date(entry.date))

            // Tap to call back using the same method (WhatsApp or regular)
            b.root.setOnClickListener {
                if (entry.isWhatsApp) {
                    WhatsAppCallHelper.makeWhatsAppCall(
                        this@CallInfoActivity,
                        number,
                        entry.isVideo
                    )
                } else {
                    placeCall(number)
                }
            }
        }

        override fun getItemCount(): Int = entries.size

        private fun formatDuration(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return when {
                h > 0 -> String.format("%d:%02d:%02d", h, m, s)
                else -> String.format("%d:%02d", m, s)
            }
        }

        private fun buildSimLabelCache(): Map<String, String> {
            val cache = mutableMapOf<String, String>()
            try {
                val subManager = getSystemService(SubscriptionManager::class.java) ?: return cache
                @Suppress("MissingPermission")
                val subscriptions = subManager.activeSubscriptionInfoList ?: return cache
                if (subscriptions.size < 2) return cache
                for ((index, subInfo) in subscriptions.withIndex()) {
                    val label = subInfo.displayName?.toString()?.ifEmpty { null }
                        ?: "SIM ${index + 1}"
                    cache[subInfo.subscriptionId.toString()] = label
                    subInfo.iccId?.let { cache[it] = label }
                }
            } catch (_: SecurityException) { }
            catch (_: Exception) { }
            return cache
        }
    }
}
