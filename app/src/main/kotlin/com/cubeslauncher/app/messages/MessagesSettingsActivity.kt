package com.cubeslauncher.app.messages

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityMessagesSettingsBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MessagesSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesSettingsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMessagesSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupSmsDelivery()
        setupMmsDelivery()
        setupMmsAutoDownload()
        setupGroupMessaging()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionSms.setTextColor(accent)
        binding.sectionMms.setTextColor(accent)

        styleWp10Switch(binding.smsDeliverySwitch, accent)
        styleWp10Switch(binding.mmsDeliverySwitch, accent)
        styleWp10Switch(binding.mmsAutoDownloadSwitch, accent)
        styleWp10Switch(binding.groupMessagingSwitch, accent)
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

    private fun setupSmsDelivery() {
        val current = runBlocking { prefs.messagesSmsDelivery.first() }
        binding.smsDeliverySwitch.isChecked = current

        binding.smsDeliverySwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setMessagesSmsDelivery(isChecked) }
        }
    }

    private fun setupMmsDelivery() {
        val current = runBlocking { prefs.messagesMmsDelivery.first() }
        binding.mmsDeliverySwitch.isChecked = current

        binding.mmsDeliverySwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setMessagesMmsDelivery(isChecked) }
        }
    }

    private fun setupMmsAutoDownload() {
        val current = runBlocking { prefs.messagesMmsAutoDownload.first() }
        binding.mmsAutoDownloadSwitch.isChecked = current

        binding.mmsAutoDownloadSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setMessagesMmsAutoDownload(isChecked) }
        }
    }

    private fun setupGroupMessaging() {
        val current = runBlocking { prefs.messagesGroupMessaging.first() }
        binding.groupMessagingSwitch.isChecked = current

        binding.groupMessagingSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setMessagesGroupMessaging(isChecked) }
        }
    }
}
