package com.cubeslauncher.clock.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.clock.ClockPreferencesManager
import com.cubeslauncher.clock.databinding.FragmentAlarmBinding
import com.cubeslauncher.clock.model.Alarm
import com.cubeslauncher.common.theme.AccentColorManager
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmFragment : Fragment() {

    private var _binding: FragmentAlarmBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: ClockPreferencesManager
    private var alarms = mutableListOf<Alarm>()
    private lateinit var adapter: AlarmsAdapter

    private val editAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val id = data.getIntExtra("alarm_id", -1)
            val hour = data.getIntExtra("alarm_hour", 0)
            val minute = data.getIntExtra("alarm_minute", 0)
            val label = data.getStringExtra("alarm_label") ?: ""
            val repeatDays = data.getIntArrayExtra("alarm_repeat_days")?.toSet() ?: emptySet()
            val isNew = data.getBooleanExtra("alarm_is_new", true)

            if (isNew) {
                val newId = if (alarms.isEmpty()) 1 else alarms.maxOf { it.id } + 1
                val alarm = Alarm(
                    id = newId,
                    hour = hour,
                    minute = minute,
                    enabled = true,
                    label = label,
                    repeatDays = repeatDays
                )
                alarms.add(alarm)
            } else {
                val index = alarms.indexOfFirst { it.id == id }
                if (index >= 0) {
                    alarms[index] = alarms[index].copy(
                        hour = hour,
                        minute = minute,
                        label = label,
                        repeatDays = repeatDays
                    )
                }
            }

            saveAndRefresh()
        } else if (result.resultCode == AlarmEditActivity.RESULT_DELETE) {
            val data = result.data ?: return@registerForActivityResult
            val id = data.getIntExtra("alarm_id", -1)
            alarms.removeAll { it.id == id }
            saveAndRefresh()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = ClockPreferencesManager(requireContext())

        adapter = AlarmsAdapter(
            alarms = alarms,
            onAlarmClick = { alarm -> editAlarm(alarm) },
            onAlarmToggle = { alarm, enabled -> toggleAlarm(alarm, enabled) }
        )

        binding.alarmsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.alarmsRecycler.adapter = adapter

        binding.addAlarmButton.setOnClickListener { addAlarm() }

        // Style add button with accent color
        val accent = AccentColorManager.getAccentColor()
        binding.addAlarmButton.setBackgroundColor(accent)

        loadAlarms()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadAlarms() {
        lifecycleScope.launch {
            alarms.clear()
            alarms.addAll(prefs.getAlarms())
            adapter.notifyDataSetChanged()
            updateEmptyState()
        }
    }

    private fun addAlarm() {
        val intent = Intent(requireContext(), AlarmEditActivity::class.java)
        intent.putExtra("alarm_is_new", true)
        editAlarmLauncher.launch(intent)
    }

    private fun editAlarm(alarm: Alarm) {
        val intent = Intent(requireContext(), AlarmEditActivity::class.java).apply {
            putExtra("alarm_is_new", false)
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_hour", alarm.hour)
            putExtra("alarm_minute", alarm.minute)
            putExtra("alarm_label", alarm.label)
            putExtra("alarm_repeat_days", alarm.repeatDays.toIntArray())
        }
        editAlarmLauncher.launch(intent)
    }

    private fun toggleAlarm(alarm: Alarm, enabled: Boolean) {
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarms[index].copy(enabled = enabled)
            saveAndRefresh()
        }
    }

    private fun saveAndRefresh() {
        lifecycleScope.launch {
            prefs.setAlarms(alarms)
            adapter.notifyDataSetChanged()
            updateEmptyState()
            scheduleAllAlarms()
        }
    }

    private fun updateEmptyState() {
        if (alarms.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.alarmsRecycler.visibility = View.GONE
        } else {
            binding.emptyText.visibility = View.GONE
            binding.alarmsRecycler.visibility = View.VISIBLE
        }
    }

    private fun scheduleAllAlarms() {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel all existing alarms, then reschedule enabled ones
        for (alarm in alarms) {
            val pendingIntent = AlarmScheduler.getPendingIntent(requireContext(), alarm)
            alarmManager.cancel(pendingIntent)

            if (alarm.enabled) {
                val triggerTime = AlarmScheduler.getNextTriggerTime(alarm)
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            }
        }
    }
}

object AlarmScheduler {
    fun getNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays.isEmpty()) {
            // One-shot alarm: if time already passed today, schedule for tomorrow
            if (trigger.timeInMillis <= now.timeInMillis) {
                trigger.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // Repeating alarm: find the next matching day
            var found = false
            for (i in 0..7) {
                val candidate = Calendar.getInstance().apply {
                    timeInMillis = trigger.timeInMillis
                    add(Calendar.DAY_OF_YEAR, i)
                }
                val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
                if (dayOfWeek in alarm.repeatDays && candidate.timeInMillis > now.timeInMillis) {
                    trigger.timeInMillis = candidate.timeInMillis
                    found = true
                    break
                }
            }
            if (!found) {
                // Fallback: schedule for next week's first matching day
                trigger.add(Calendar.DAY_OF_YEAR, 7)
            }
        }

        return trigger.timeInMillis
    }

    fun getPendingIntent(context: Context, alarm: Alarm): android.app.PendingIntent {
        val intent = Intent(context, com.cubeslauncher.clock.receiver.AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_hour", alarm.hour)
            putExtra("alarm_minute", alarm.minute)
            putExtra("alarm_label", alarm.label)
            putExtra("alarm_repeat_days", alarm.repeatDays.toIntArray())
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }
}
