package com.cubeslauncher.messages.whatsapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.messages.databinding.ActivityWhatsappPrivacySettingsBinding
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WhatsAppPrivacySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhatsappPrivacySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWhatsappPrivacySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        setupStatusMessage()
        loadBlocklist()
    }

    private fun setupStatusMessage() {
        binding.saveStatusButton.setOnClickListener {
            val text = binding.statusEditText.text?.toString()?.trim() ?: return@setOnClickListener
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    WhatsAppBridge.setStatusMessage(text)
                }
                Toast.makeText(this@WhatsAppPrivacySettingsActivity, "Status updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadBlocklist() {
        lifecycleScope.launch {
            val blocklistJson = withContext(Dispatchers.IO) {
                WhatsAppBridge.getBlocklist()
            }
            try {
                val arr = JSONArray(blocklistJson)
                val blocked = (0 until arr.length()).map { arr.getString(it) }
                binding.blockedCount.text = "${blocked.size} blocked contact${if (blocked.size != 1) "s" else ""}"

                binding.blockedCount.setOnClickListener {
                    if (blocked.isNotEmpty()) {
                        val items = blocked.toTypedArray()
                        AlertDialog.Builder(this@WhatsAppPrivacySettingsActivity)
                            .setTitle("Blocked Contacts")
                            .setItems(items) { _, which ->
                                AlertDialog.Builder(this@WhatsAppPrivacySettingsActivity)
                                    .setTitle("Unblock")
                                    .setMessage("Unblock ${items[which]}?")
                                    .setPositiveButton("Unblock") { _, _ ->
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            WhatsAppBridge.updateBlocklist(items[which], "unblock")
                                        }
                                        loadBlocklist()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            .show()
                    }
                }
            } catch (_: Exception) {
                binding.blockedCount.text = "0 blocked contacts"
            }
        }
    }
}
