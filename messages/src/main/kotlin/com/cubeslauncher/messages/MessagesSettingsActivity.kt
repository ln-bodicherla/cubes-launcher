package com.cubeslauncher.messages

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.databinding.ActivityMessagesSettingsBinding
import com.cubeslauncher.messages.whatsapp.WhatsAppBridge
import com.cubeslauncher.messages.whatsapp.WhatsAppPairingActivity
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.messages.MessagesPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MessagesSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesSettingsBinding
    private lateinit var prefs: MessagesPreferencesManager

    private val scaleSteps = floatArrayOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.4f)
    private val swipeActions = arrayOf("archive", "delete", "mark_read", "mark_unread", "none")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMessagesSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = MessagesPreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupDisplaySection()
        setupSwipeSection()
        setupSmsDelivery()
        setupMmsDelivery()
        setupMmsAutoDownload()
        setupGroupMessaging()
        setupWhatsApp()
    }

    override fun onResume() {
        super.onResume()
        updateWhatsAppStatus()
    }

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        binding.sectionDisplay.setTextColor(accent)
        binding.sectionSwipe.setTextColor(accent)
        binding.sectionSms.setTextColor(accent)
        binding.sectionMms.setTextColor(accent)
        binding.sectionWhatsApp.setTextColor(accent)

        styleWp10Switch(binding.smsDeliverySwitch, accent)
        styleWp10Switch(binding.mmsDeliverySwitch, accent)
        styleWp10Switch(binding.mmsAutoDownloadSwitch, accent)
        styleWp10Switch(binding.groupMessagingSwitch, accent)
        styleWp10Switch(binding.whatsappEnableSwitch, accent)
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

    private fun setupDisplaySection() {
        val currentListScale = runBlocking { prefs.listTextScale.first() }
        val currentMessageScale = runBlocking { prefs.messageTextScale.first() }

        val listPos = scaleToPosition(currentListScale)
        val msgPos = scaleToPosition(currentMessageScale)

        binding.listTextScaleSeekBar.progress = listPos
        binding.messageTextScaleSeekBar.progress = msgPos

        updateListPreview(currentListScale)
        updateMessagePreview(currentMessageScale)

        binding.listTextScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val scale = scaleSteps[progress]
                updateListPreview(scale)
                if (fromUser) {
                    lifecycleScope.launch { prefs.setListTextScale(scale) }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        binding.messageTextScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val scale = scaleSteps[progress]
                updateMessagePreview(scale)
                if (fromUser) {
                    lifecycleScope.launch { prefs.setMessageTextScale(scale) }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun scaleToPosition(scale: Float): Int {
        var closest = 0
        var minDiff = Float.MAX_VALUE
        for (i in scaleSteps.indices) {
            val diff = kotlin.math.abs(scaleSteps[i] - scale)
            if (diff < minDiff) {
                minDiff = diff
                closest = i
            }
        }
        return closest
    }

    private fun updateListPreview(scale: Float) {
        binding.listTextScalePreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale)
    }

    private fun updateMessagePreview(scale: Float) {
        binding.messageTextScalePreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * scale)
    }

    private fun setupSwipeSection() {
        val currentRight = runBlocking { prefs.swipeRightAction.first() }
        val currentLeft = runBlocking { prefs.swipeLeftAction.first() }

        binding.swipeRightValue.text = getSwipeActionLabel(currentRight)
        binding.swipeLeftValue.text = getSwipeActionLabel(currentLeft)

        binding.swipeRightRow.setOnClickListener { view ->
            showSwipeActionPicker(view) { action ->
                binding.swipeRightValue.text = getSwipeActionLabel(action)
                lifecycleScope.launch { prefs.setSwipeRightAction(action) }
            }
        }

        binding.swipeLeftRow.setOnClickListener { view ->
            showSwipeActionPicker(view) { action ->
                binding.swipeLeftValue.text = getSwipeActionLabel(action)
                lifecycleScope.launch { prefs.setSwipeLeftAction(action) }
            }
        }
    }

    private fun showSwipeActionPicker(anchor: View, onSelected: (String) -> Unit) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, R.string.messages_swipe_archive)
        popup.menu.add(0, 1, 1, R.string.messages_swipe_delete)
        popup.menu.add(0, 2, 2, R.string.messages_swipe_mark_read)
        popup.menu.add(0, 3, 3, R.string.messages_swipe_mark_unread)
        popup.menu.add(0, 4, 4, R.string.messages_swipe_none)

        popup.setOnMenuItemClickListener { item ->
            val action = swipeActions[item.itemId]
            onSelected(action)
            true
        }
        popup.show()
    }

    private fun getSwipeActionLabel(action: String): String {
        return when (action) {
            "archive" -> getString(R.string.messages_swipe_archive)
            "delete" -> getString(R.string.messages_swipe_delete)
            "mark_read" -> getString(R.string.messages_swipe_mark_read)
            "mark_unread" -> getString(R.string.messages_swipe_mark_unread)
            "none" -> getString(R.string.messages_swipe_none)
            else -> getString(R.string.messages_swipe_archive)
        }
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

    private fun setupWhatsApp() {
        // WhatsApp feature is disabled — force off
        lifecycleScope.launch { prefs.setWhatsappEnabled(false) }
        binding.whatsappEnableSwitch.isChecked = false
        binding.whatsappAccountRow.visibility = View.GONE
    }

    private fun updateWhatsAppStatus() {
        val enabled = runBlocking { prefs.whatsappEnabled.first() }
        if (!enabled) return

        try {
            WhatsAppBridge.init(applicationContext)
            val linked = WhatsAppBridge.isLoggedIn()
            binding.whatsappAccountStatus.setText(
                if (linked) R.string.messages_settings_whatsapp_linked
                else R.string.messages_settings_whatsapp_not_linked
            )
        } catch (_: Exception) {
            binding.whatsappAccountStatus.setText(R.string.messages_settings_whatsapp_not_linked)
        }
    }
}
