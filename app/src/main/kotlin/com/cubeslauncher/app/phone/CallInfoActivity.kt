package com.cubeslauncher.app.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityPhoneCallInfoBinding
import com.cubeslauncher.app.databinding.ItemPhoneCallInfoEntryBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.ThemeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
    }

    private lateinit var binding: ActivityPhoneCallInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPhoneCallInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromParts("tel", number, null)
        val telecom = getSystemService(TelecomManager::class.java)
        try {
            telecom?.placeCall(uri, Bundle())
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

        val entries = queryCallsForNumber(number)
        if (entries.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.callListRecyclerView.visibility = View.GONE
            return
        }

        binding.callListRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.callListRecyclerView.adapter = CallInfoAdapter(entries)
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
            CallLog.Calls.DURATION
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

                while (it.moveToNext()) {
                    val entryNumber = it.getString(numberIdx) ?: ""
                    val normalizedEntry = entryNumber.replace(Regex("[^\\d+]"), "")

                    if (normalizedEntry == normalizedTarget ||
                        normalizedEntry.endsWith(normalizedTarget) ||
                        normalizedTarget.endsWith(normalizedEntry)
                    ) {
                        entries.add(
                            CallInfoEntry(
                                callType = it.getInt(typeIdx),
                                date = it.getLong(dateIdx),
                                duration = it.getLong(durationIdx)
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

    data class CallInfoEntry(
        val callType: Int,
        val date: Long,
        val duration: Long
    )

    private inner class CallInfoAdapter(
        private val entries: List<CallInfoEntry>
    ) : RecyclerView.Adapter<CallInfoAdapter.ViewHolder>() {

        private val dateTimeFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())

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
            b.callTypeText.text = typeLabel

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
    }
}
