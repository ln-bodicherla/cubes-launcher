package com.cubeslauncher.app.phone

import android.app.KeyguardManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityIncallBinding
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.launch

class InCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncallBinding
    private val timerHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isSpeaker = false
    private var isHolding = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            val state = CubesInCallService.callStateFlow.value
            if (state.state == CallUiState.State.ACTIVE && state.connectTimeMs > 0) {
                val elapsed = System.currentTimeMillis() - state.connectTimeMs
                val seconds = (elapsed / 1000).toInt()
                val mins = seconds / 60
                val secs = seconds % 60
                binding.callTimer.text = String.format("%02d:%02d", mins, secs)
                binding.callTimer.visibility = View.VISIBLE
            }
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on for incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityIncallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupControls()
        observeCallState()

        timerHandler.post(timerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraTop = (16 * density).toInt()

            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                systemBars.top + extraTop,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )

            // Bottom padding on the controls so they sit above the navigation bar
            binding.activeControls.setPadding(0, 0, 0, systemBars.bottom)
            binding.incomingControls.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupControls() {
        binding.answerButton.setOnClickListener {
            CubesInCallService.currentCall.value?.answer(0)
        }

        binding.declineButton.setOnClickListener {
            CubesInCallService.currentCall.value?.reject(false, null)
        }

        binding.endCallButton.setOnClickListener {
            CubesInCallService.currentCall.value?.disconnect()
        }

        binding.muteButton.setOnClickListener {
            isMuted = !isMuted
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.isMicrophoneMute = isMuted
            updateToggleControl(binding.muteButton, binding.muteIcon, isMuted)
        }

        binding.speakerButton.setOnClickListener {
            isSpeaker = !isSpeaker
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = isSpeaker
            updateToggleControl(binding.speakerButton, binding.speakerIcon, isSpeaker)
        }

        binding.holdButton.setOnClickListener {
            val call = CubesInCallService.currentCall.value ?: return@setOnClickListener
            isHolding = !isHolding
            if (isHolding) call.hold() else call.unhold()
            updateToggleControl(binding.holdButton, binding.holdIcon, isHolding)
        }

        binding.incallKeypadButton.setOnClickListener {
            // DTMF keypad during call
        }
    }

    private fun updateToggleControl(container: View, icon: ImageView, active: Boolean) {
        if (active) {
            // WP10 active: white background, dark icon
            container.setBackgroundResource(R.drawable.bg_incall_control_active_rect)
            icon.setColorFilter(0xFF000000.toInt())
        } else {
            // WP10 inactive: dark gray background, white icon
            container.setBackgroundResource(R.drawable.bg_incall_control_rect)
            icon.clearColorFilter()
        }
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CubesInCallService.callStateFlow.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: CallUiState) {
        if (state.callerName.isNotEmpty()) {
            // Contact name exists: show name (large, up to 2 lines) + number below
            binding.callerName.text = state.callerName
            binding.callerNumber.text = state.number
            binding.callerNumber.visibility = if (state.number.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            // No contact name: show number as the main text, hide secondary
            binding.callerName.text = state.number.ifEmpty { getString(R.string.phone_title) }
            binding.callerNumber.text = ""
            binding.callerNumber.visibility = View.GONE
        }

        when (state.state) {
            CallUiState.State.RINGING -> {
                binding.callState.text = getString(R.string.phone_state_ringing)
                binding.incomingControls.visibility = View.VISIBLE
                binding.activeControls.visibility = View.GONE
                binding.callTimer.visibility = View.GONE
            }
            CallUiState.State.DIALING, CallUiState.State.CONNECTING -> {
                binding.callState.text = getString(R.string.phone_state_calling)
                binding.incomingControls.visibility = View.GONE
                binding.activeControls.visibility = View.VISIBLE
                binding.callTimer.visibility = View.GONE
            }
            CallUiState.State.ACTIVE -> {
                binding.callState.text = getString(R.string.phone_state_active)
                binding.incomingControls.visibility = View.GONE
                binding.activeControls.visibility = View.VISIBLE
            }
            CallUiState.State.HOLDING -> {
                binding.callState.text = getString(R.string.phone_state_holding)
                binding.incomingControls.visibility = View.GONE
                binding.activeControls.visibility = View.VISIBLE
            }
            CallUiState.State.DISCONNECTED -> {
                binding.callState.text = getString(R.string.phone_state_disconnected)
                binding.incomingControls.visibility = View.GONE
                binding.activeControls.visibility = View.GONE
                timerHandler.postDelayed({ finish() }, 2000)
            }
            CallUiState.State.IDLE -> {
                finish()
            }
        }
    }
}
