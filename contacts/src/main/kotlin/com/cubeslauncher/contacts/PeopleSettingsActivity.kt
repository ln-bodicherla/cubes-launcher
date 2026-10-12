package com.cubeslauncher.contacts

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
import com.cubeslauncher.contacts.R
import com.cubeslauncher.contacts.databinding.ActivityPeopleSettingsBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.contacts.ContactsPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PeopleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeopleSettingsBinding
    private lateinit var prefs: ContactsPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPeopleSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ContactsPreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupSortOrder()
        setupDisplayFormat()
        setupHideNoPhone()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionDisplay.setTextColor(accent)
        binding.sectionFilter.setTextColor(accent)

        styleWp10Switch(binding.hideNoPhoneSwitch, accent)
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

    private fun setupSortOrder() {
        val options = listOf(
            getString(R.string.people_settings_sort_first_name),
            getString(R.string.people_settings_sort_last_name)
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.sortOrderSpinner.adapter = adapter

        val current = runBlocking { prefs.contactsSortOrder.first() }
        binding.sortOrderSpinner.setSelection(if (current == "last_name") 1 else 0)

        binding.sortOrderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val order = if (pos == 1) "last_name" else "first_name"
                lifecycleScope.launch { prefs.setContactsSortOrder(order) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDisplayFormat() {
        val options = listOf(
            getString(R.string.people_settings_display_first_last),
            getString(R.string.people_settings_display_last_first)
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.displayFormatSpinner.adapter = adapter

        val current = runBlocking { prefs.contactsDisplayFormat.first() }
        binding.displayFormatSpinner.setSelection(if (current == "last_first") 1 else 0)

        binding.displayFormatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val format = if (pos == 1) "last_first" else "first_last"
                lifecycleScope.launch { prefs.setContactsDisplayFormat(format) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupHideNoPhone() {
        val current = runBlocking { prefs.contactsHideNoPhone.first() }
        binding.hideNoPhoneSwitch.isChecked = current

        binding.hideNoPhoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setContactsHideNoPhone(isChecked) }
        }
    }
}
