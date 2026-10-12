package com.cubeslauncher.clock.alarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.clock.databinding.ItemAlarmBinding
import com.cubeslauncher.clock.model.Alarm
import java.util.Calendar
import java.util.Locale

class AlarmsAdapter(
    private val alarms: List<Alarm>,
    private val onAlarmClick: (Alarm) -> Unit,
    private val onAlarmToggle: (Alarm, Boolean) -> Unit
) : RecyclerView.Adapter<AlarmsAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemAlarmBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            binding.alarmTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)

            val repeatText = if (alarm.repeatDays.isEmpty()) {
                if (alarm.label.isNotEmpty()) alarm.label else "One time"
            } else {
                buildRepeatText(alarm.repeatDays, alarm.label)
            }
            binding.alarmRepeat.text = repeatText

            binding.alarmSwitch.setOnCheckedChangeListener(null)
            binding.alarmSwitch.isChecked = alarm.enabled
            binding.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
                onAlarmToggle(alarm, isChecked)
            }

            binding.root.setOnClickListener { onAlarmClick(alarm) }
        }

        private fun buildRepeatText(days: Set<Int>, label: String): String {
            val dayNames = mapOf(
                Calendar.MONDAY to "Mon",
                Calendar.TUESDAY to "Tue",
                Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu",
                Calendar.FRIDAY to "Fri",
                Calendar.SATURDAY to "Sat",
                Calendar.SUNDAY to "Sun"
            )

            val sortedDays = listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
            ).filter { it in days }

            val weekdays = setOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            )
            val weekend = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            val allDays = weekdays + weekend

            val daysText = when {
                days == allDays -> "Every day"
                days == weekdays -> "Weekdays"
                days == weekend -> "Weekends"
                else -> sortedDays.mapNotNull { dayNames[it] }.joinToString(", ")
            }

            return if (label.isNotEmpty()) "$daysText - $label" else daysText
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlarmBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(alarms[position])
    }

    override fun getItemCount(): Int = alarms.size
}
