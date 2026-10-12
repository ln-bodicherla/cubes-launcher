package com.cubeslauncher.clock.alarm

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.cubeslauncher.clock.R
import com.cubeslauncher.clock.databinding.ActivityAlarmEditBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import java.util.Calendar

class AlarmEditActivity : AppCompatActivity() {

    companion object {
        const val RESULT_DELETE = 2
    }

    private lateinit var binding: ActivityAlarmEditBinding
    private var isNew = true
    private var alarmId = -1
    private val selectedDays = mutableSetOf<Int>()

    private val dayButtons = mutableMapOf<Int, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAlarmEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isNew = intent.getBooleanExtra("alarm_is_new", true)
        alarmId = intent.getIntExtra("alarm_id", -1)

        if (!isNew) {
            binding.timePicker.hour = intent.getIntExtra("alarm_hour", 0)
            binding.timePicker.minute = intent.getIntExtra("alarm_minute", 0)
            binding.labelEdit.setText(intent.getStringExtra("alarm_label") ?: "")
            val days = intent.getIntArrayExtra("alarm_repeat_days")
            if (days != null) {
                selectedDays.addAll(days.toSet())
            }
        }

        binding.timePicker.setIs24HourView(true)

        setupDayButtons()
        setupButtons()
    }

    private fun setupDayButtons() {
        val accent = AccentColorManager.getAccentColor()

        dayButtons[Calendar.MONDAY] = binding.dayMon
        dayButtons[Calendar.TUESDAY] = binding.dayTue
        dayButtons[Calendar.WEDNESDAY] = binding.dayWed
        dayButtons[Calendar.THURSDAY] = binding.dayThu
        dayButtons[Calendar.FRIDAY] = binding.dayFri
        dayButtons[Calendar.SATURDAY] = binding.daySat
        dayButtons[Calendar.SUNDAY] = binding.daySun

        for ((day, button) in dayButtons) {
            updateDayButtonStyle(button, day in selectedDays, accent)
            button.setOnClickListener {
                if (day in selectedDays) {
                    selectedDays.remove(day)
                } else {
                    selectedDays.add(day)
                }
                updateDayButtonStyle(button, day in selectedDays, accent)
            }
        }
    }

    private fun updateDayButtonStyle(button: TextView, selected: Boolean, accent: Int) {
        val wpTextPrimary = getThemedColor(R.attr.wpTextPrimary)
        val wpSurface = getThemedColor(R.attr.wpSurface)

        if (selected) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(accent)
            }
            button.background = bg
            button.setTextColor(Color.WHITE)
        } else {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(wpSurface)
            }
            button.background = bg
            button.setTextColor(wpTextPrimary)
        }
    }

    private fun setupButtons() {
        val accent = AccentColorManager.getAccentColor()
        binding.saveButton.setBackgroundColor(accent)
        binding.saveButton.setTextColor(Color.WHITE)

        binding.saveButton.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("alarm_is_new", isNew)
                putExtra("alarm_id", alarmId)
                putExtra("alarm_hour", binding.timePicker.hour)
                putExtra("alarm_minute", binding.timePicker.minute)
                putExtra("alarm_label", binding.labelEdit.text.toString().trim())
                putExtra("alarm_repeat_days", selectedDays.toIntArray())
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        binding.cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        if (!isNew) {
            binding.deleteButton.visibility = android.view.View.VISIBLE
            binding.deleteButton.setOnClickListener {
                val resultIntent = Intent().apply {
                    putExtra("alarm_id", alarmId)
                }
                setResult(RESULT_DELETE, resultIntent)
                finish()
            }
        } else {
            binding.deleteButton.visibility = android.view.View.GONE
        }
    }

    private fun getThemedColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
}
