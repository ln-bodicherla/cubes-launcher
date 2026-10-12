package com.cubeslauncher.clock

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.clock.databinding.ActivityClockSettingsBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ClockSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClockSettingsBinding
    private lateinit var prefs: ClockPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityClockSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ClockPreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupSnoozeDuration()
        setupVibrate()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionAlarm.setTextColor(accent)

        styleWp10Switch(binding.vibrateSwitch, accent)
    }

    private fun styleWp10Switch(switch: Switch, accent: Int) {
        val density = resources.displayMetrics.density
        val thumbW = (16 * density).toInt()
        val thumbH = (16 * density).toInt()
        val trackW = (44 * density).toInt()
        val trackH = (22 * density).toInt()
        val strokeW = (2 * density).toInt()

        val tv = TypedValue()
        theme.resolveAttribute(R.attr.wpTextSecondary, tv, true)
        val borderColor = tv.data

        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            setSize(thumbW, thumbH)
        }

        val trackOn = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            setSize(trackW, trackH)
        }

        val trackOff = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(strokeW, borderColor)
            setSize(trackW, trackH)
        }

        val track = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        }

        switch.trackDrawable = track
        switch.thumbDrawable = thumb
        switch.switchMinWidth = trackW
        switch.showText = false
        switch.thumbTextPadding = (2 * density).toInt()
    }

    private fun setupSnoozeDuration() {
        val options = listOf("5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes")
        val values = listOf(5, 10, 15, 20, 30)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.snoozeSpinner.adapter = adapter

        val current = runBlocking { prefs.snoozeDuration.first() }
        val index = values.indexOf(current)
        if (index >= 0) {
            binding.snoozeSpinner.setSelection(index)
        }

        binding.snoozeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                lifecycleScope.launch { prefs.setSnoozeDuration(values[pos]) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupVibrate() {
        val current = runBlocking { prefs.vibrateOnAlarm.first() }
        binding.vibrateSwitch.isChecked = current

        binding.vibrateSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setVibrateOnAlarm(isChecked) }
        }
    }
}
