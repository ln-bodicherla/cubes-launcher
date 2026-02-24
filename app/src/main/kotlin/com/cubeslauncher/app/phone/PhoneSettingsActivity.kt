package com.cubeslauncher.app.phone

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityPhoneSettingsBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PhoneSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneSettingsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPhoneSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupCallerId()
        setupCallWaiting()
        setupInternationalAssist()
        setupVoicemailNumber()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionCallerId.setTextColor(accent)
        binding.sectionCalls.setTextColor(accent)
        binding.sectionVoicemail.setTextColor(accent)

        styleWp10Switch(binding.callWaitingSwitch, accent)
        styleWp10Switch(binding.internationalAssistSwitch, accent)
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

    private fun setupCallerId() {
        val options = listOf(
            getString(R.string.phone_settings_caller_id_network),
            getString(R.string.phone_settings_caller_id_hide),
            getString(R.string.phone_settings_caller_id_show)
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.callerIdSpinner.adapter = adapter

        val current = runBlocking { prefs.phoneCallerId.first() }
        binding.callerIdSpinner.setSelection(
            when (current) {
                "hide" -> 1
                "show" -> 2
                else -> 0
            }
        )

        binding.callerIdSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val mode = when (pos) {
                    1 -> "hide"
                    2 -> "show"
                    else -> "network"
                }
                lifecycleScope.launch { prefs.setPhoneCallerId(mode) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCallWaiting() {
        val current = runBlocking { prefs.phoneCallWaiting.first() }
        binding.callWaitingSwitch.isChecked = current

        binding.callWaitingSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setPhoneCallWaiting(isChecked) }
        }
    }

    private fun setupInternationalAssist() {
        val current = runBlocking { prefs.phoneInternationalAssist.first() }
        binding.internationalAssistSwitch.isChecked = current

        binding.internationalAssistSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setPhoneInternationalAssist(isChecked) }
        }
    }

    private fun setupVoicemailNumber() {
        val current = runBlocking { prefs.phoneVoicemailNumber.first() }
        binding.voicemailNumberEdit.setText(current)

        binding.voicemailNumberEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val number = s?.toString()?.trim() ?: ""
                lifecycleScope.launch { prefs.setPhoneVoicemailNumber(number) }
            }
        })
    }
}
