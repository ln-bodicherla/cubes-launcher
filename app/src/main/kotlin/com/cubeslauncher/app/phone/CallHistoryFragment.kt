package com.cubeslauncher.app.phone

import android.Manifest
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.FragmentPhoneHistoryBinding
import com.cubeslauncher.app.phone.model.CallLogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CallHistoryFragment : Fragment() {

    private var _binding: FragmentPhoneHistoryBinding? = null
    private val binding get() = _binding!!
    private var simHelper: SimSelectionHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        simHelper = SimSelectionHelper(requireContext())
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadCallHistory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        simHelper = null
        _binding = null
    }

    private fun loadCallHistory() {
        val activity = requireActivity()
        if (activity is PhoneActivity && !activity.hasPermission(Manifest.permission.READ_CALL_LOG)) {
            showEmpty()
            return
        }

        val entries = queryCallLog()
        if (entries.isEmpty()) {
            showEmpty()
            return
        }

        val groupedItems = groupByDate(entries)
        binding.historyRecyclerView.adapter = CallHistoryAdapter(groupedItems) { entry ->
            placeCall(entry.number)
        }
        binding.emptyState.visibility = View.GONE
        binding.historyRecyclerView.visibility = View.VISIBLE
    }

    private fun placeCall(number: String) {
        val activity = requireActivity()
        if (activity is PhoneActivity && !activity.hasPermission(Manifest.permission.CALL_PHONE)) {
            Toast.makeText(requireContext(), R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromParts("tel", number, null)
        val telecom = requireContext().getSystemService(TelecomManager::class.java)
        try {
            val extras = Bundle()
            // Use SIM from keypad if available
            val phoneActivity = activity as? PhoneActivity
            val keypadFragment = phoneActivity?.supportFragmentManager?.findFragmentByTag("f2") as? KeypadFragment
            val phoneAccount = keypadFragment?.getSelectedPhoneAccountHandle()
            if (phoneAccount != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
            }
            telecom?.placeCall(uri, extras)
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryCallLog(): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        var cursor: Cursor? = null
        try {
            cursor = requireContext().contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                var count = 0
                while (it.moveToNext() && count < 200) {
                    val number = it.getString(numberIdx) ?: ""
                    val contactName = it.getString(nameIdx)
                    // Skip entries where both number and name are blank
                    if (number.isBlank() && contactName.isNullOrBlank()) continue
                    entries.add(
                        CallLogEntry(
                            id = it.getLong(idIdx),
                            number = number,
                            contactName = contactName,
                            callType = it.getInt(typeIdx),
                            date = it.getLong(dateIdx),
                            duration = it.getLong(durationIdx)
                        )
                    )
                    count++
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } finally {
            cursor?.close()
        }

        return entries
    }

    private fun groupByDate(entries: List<CallLogEntry>): List<CallHistoryItem> {
        val items = mutableListOf<CallHistoryItem>()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        var lastDateKey = ""

        for (entry in entries) {
            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.date }
            val dateKey = when {
                isSameDay(entryCal, today) -> "TODAY"
                isSameDay(entryCal, yesterday) -> "YESTERDAY"
                else -> dateFormat.format(Date(entry.date)).uppercase(Locale.getDefault())
            }

            if (dateKey != lastDateKey) {
                items.add(CallHistoryItem.Header(dateKey))
                lastDateKey = dateKey
            }
            items.add(CallHistoryItem.Entry(entry))
        }

        return items
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.historyRecyclerView.visibility = View.GONE
    }

}

sealed class CallHistoryItem {
    data class Header(val label: String) : CallHistoryItem()
    data class Entry(val entry: CallLogEntry) : CallHistoryItem()
}
