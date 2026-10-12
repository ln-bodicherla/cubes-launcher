package com.cubeslauncher.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.ArrayAdapter
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.keyboard.R
import com.cubeslauncher.keyboard.databinding.ActivityKeyboardSettingsBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.keyboard.KeyboardPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KeyboardSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyboardSettingsBinding
    private lateinit var prefs: KeyboardPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityKeyboardSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = KeyboardPreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupAutoCorrection()
        setupAutoCapitalize()
        setupDoubleSpacePeriod()
        setupShowSuggestions()
        setupGlideTyping()
        setupGlideTrail()
        setupNumberRow()
        setupSound()
        setupVibration()
        setupHeight()
        setupVoiceInput()
        setupClipboard()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionTyping.setTextColor(accent)
        binding.sectionGlide.setTextColor(accent)
        binding.sectionPreferences.setTextColor(accent)
        binding.sectionVoice.setTextColor(accent)
        binding.sectionClipboard.setTextColor(accent)

        styleWp10Switch(binding.autoCorrectionSwitch, accent)
        styleWp10Switch(binding.autoCapitalizeSwitch, accent)
        styleWp10Switch(binding.doubleSpacePeriodSwitch, accent)
        styleWp10Switch(binding.showSuggestionsSwitch, accent)
        styleWp10Switch(binding.glideTypingSwitch, accent)
        styleWp10Switch(binding.glideTrailSwitch, accent)
        styleWp10Switch(binding.numberRowSwitch, accent)
        styleWp10Switch(binding.soundSwitch, accent)
        styleWp10Switch(binding.vibrationSwitch, accent)
        styleWp10Switch(binding.voiceInputSwitch, accent)
        styleWp10Switch(binding.clipboardSwitch, accent)
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

    private fun setupAutoCorrection() {
        val current = runBlocking { prefs.keyboardAutoCorrect.first() }
        binding.autoCorrectionSwitch.isChecked = current

        binding.autoCorrectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardAutoCorrect(isChecked) }
        }
    }

    private fun setupAutoCapitalize() {
        val current = runBlocking { prefs.keyboardAutoCapitalize.first() }
        binding.autoCapitalizeSwitch.isChecked = current

        binding.autoCapitalizeSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardAutoCapitalize(isChecked) }
        }
    }

    private fun setupDoubleSpacePeriod() {
        val current = runBlocking { prefs.keyboardDoubleSpacePeriod.first() }
        binding.doubleSpacePeriodSwitch.isChecked = current

        binding.doubleSpacePeriodSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardDoubleSpacePeriod(isChecked) }
        }
    }

    private fun setupShowSuggestions() {
        val current = runBlocking { prefs.keyboardShowSuggestions.first() }
        binding.showSuggestionsSwitch.isChecked = current

        binding.showSuggestionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardShowSuggestions(isChecked) }
        }
    }

    private fun setupGlideTyping() {
        val current = runBlocking { prefs.keyboardGlideTyping.first() }
        binding.glideTypingSwitch.isChecked = current

        binding.glideTypingSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardGlideTyping(isChecked) }
        }
    }

    private fun setupGlideTrail() {
        val current = runBlocking { prefs.keyboardGlideTrail.first() }
        binding.glideTrailSwitch.isChecked = current

        binding.glideTrailSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardGlideTrail(isChecked) }
        }
    }

    private fun setupNumberRow() {
        val current = runBlocking { prefs.keyboardNumberRow.first() }
        binding.numberRowSwitch.isChecked = current

        binding.numberRowSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardNumberRow(isChecked) }
        }
    }

    private fun setupSound() {
        val current = runBlocking { prefs.keyboardSound.first() }
        binding.soundSwitch.isChecked = current

        binding.soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardSound(isChecked) }
        }
    }

    private fun setupVibration() {
        val current = runBlocking { prefs.keyboardVibration.first() }
        binding.vibrationSwitch.isChecked = current

        binding.vibrationSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardVibration(isChecked) }
        }
    }

    private fun setupHeight() {
        val heights = arrayOf(
            getString(R.string.keyboard_settings_height_short),
            getString(R.string.keyboard_settings_height_normal),
            getString(R.string.keyboard_settings_height_tall)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, heights)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.heightSpinner.adapter = adapter

        val current = runBlocking { prefs.keyboardHeight.first() }
        binding.heightSpinner.setSelection(current)

        binding.heightSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                lifecycleScope.launch { prefs.setKeyboardHeight(position) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupVoiceInput() {
        val current = runBlocking { prefs.keyboardVoiceInput.first() }
        binding.voiceInputSwitch.isChecked = current

        binding.voiceInputSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardVoiceInput(isChecked) }
        }
    }

    private fun setupClipboard() {
        val current = runBlocking { prefs.keyboardClipboard.first() }
        binding.clipboardSwitch.isChecked = current

        binding.clipboardSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setKeyboardClipboard(isChecked) }
        }
    }
}
