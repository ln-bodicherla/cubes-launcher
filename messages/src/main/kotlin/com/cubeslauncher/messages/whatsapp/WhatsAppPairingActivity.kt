package com.cubeslauncher.messages.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.databinding.ActivityWhatsappPairingBinding
import com.cubeslauncher.messages.MessagesPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WhatsAppPairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWhatsappPairingBinding
    private val debugLog = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWhatsappPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        binding.linkWithPhoneLabel.setOnClickListener {
            binding.linkWithPhoneLabel.visibility = View.GONE
            binding.phoneInputRow.visibility = View.VISIBLE
            // Hide QR stuff
            binding.qrCodeImage.visibility = View.GONE
            binding.loadingSpinner.visibility = View.GONE
        }

        binding.linkButton.setOnClickListener {
            val phone = binding.phoneInput.text.toString().trim()
            if (phone.isNotEmpty()) {
                startPhonePairing(phone)
            }
        }

        applyWindowInsets()
        startPairing()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset + 16, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun startPairing() {
        binding.loadingSpinner.visibility = View.VISIBLE
        binding.qrCodeImage.visibility = View.GONE
        binding.statusText.visibility = View.GONE

        try {
            WhatsAppBridge.init(applicationContext)
        } catch (e: Exception) {
            binding.loadingSpinner.visibility = View.GONE
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = "Bridge init failed: ${e.message}"
            return
        }

        if (!WhatsAppBridge.isGoAvailable()) {
            binding.loadingSpinner.visibility = View.GONE
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = "Go bridge not available: ${WhatsAppBridge.getInitError() ?: "unknown"}"
            return
        }

        WhatsAppBridge.startQRPairing(
            onQR = { qrString ->
                runOnUiThread {
                    showQRCode(qrString)
                }
            },
            onSuccess = {
                runOnUiThread {
                    onPairingSuccess()
                }
            }
        )
    }

    private fun showQRCode(qrString: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                generateQRBitmap(qrString, 560)
            }
            if (bitmap != null) {
                binding.loadingSpinner.visibility = View.GONE
                binding.qrCodeImage.setImageBitmap(bitmap)
                binding.qrCodeImage.visibility = View.VISIBLE
            }
        }
    }

    private fun onPairingSuccess() {
        lifecycleScope.launch {
            MessagesPreferencesManager(applicationContext).setWhatsappEnabled(true)
        }
        Toast.makeText(this, R.string.whatsapp_pairing_success, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun addDebug(msg: String) {
        debugLog.add(msg)
        if (debugLog.size > 8) debugLog.removeAt(0)
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = debugLog.joinToString("\n")
    }

    private fun startPhonePairing(phone: String) {
        binding.phoneInputRow.visibility = View.GONE
        binding.loadingSpinner.visibility = View.VISIBLE
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = "Requesting pairing code..."

        WhatsAppBridge.setOnDebugListener { msg ->
            runOnUiThread { addDebug(msg) }
        }

        lifecycleScope.launch {
            val code = withContext(Dispatchers.IO) {
                WhatsAppBridge.init(applicationContext)
                WhatsAppBridge.pairPhone(phone) {
                    runOnUiThread { onPairingSuccess() }
                }
            }

            if (code.startsWith("ERROR:")) {
                binding.loadingSpinner.visibility = View.GONE
                binding.statusText.text = code.removePrefix("ERROR:")
            } else {
                binding.loadingSpinner.visibility = View.GONE
                binding.statusText.visibility = View.GONE
                binding.pairingCodeText.visibility = View.VISIBLE
                binding.pairingCodeText.text = code.chunked(4).joinToString(" - ")
                binding.pairingCodeHint.visibility = View.VISIBLE
                binding.pairingCodeHint.text = "Tap code to copy"

                val copyAction = View.OnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Pairing Code", code)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@WhatsAppPairingActivity, "Code copied", Toast.LENGTH_SHORT).show()
                }
                binding.pairingCodeText.setOnClickListener(copyAction)
                binding.pairingCodeHint.setOnClickListener(copyAction)
            }
        }
    }

    private fun generateQRBitmap(content: String, size: Int): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
