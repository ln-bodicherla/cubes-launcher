package com.cubeslauncher.phone

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telephony.PhoneNumberUtils
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cubeslauncher.phone.R
import com.cubeslauncher.phone.databinding.ActivityIncallBinding
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.launch
import java.util.Locale

class InCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncallBinding
    private val timerHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
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

        enterImmersiveMode()
        applyWindowInsets()
        setupControls()
        observeCallState()

        timerHandler.post(timerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    /**
     * Hide the status bar so the notification shade cannot be pulled down during a call.
     * This is how Google Dialer prevents accidental quick-settings toggles.
     * Navigation bar is kept visible; only the top status bar is hidden.
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val density = resources.displayMetrics.density
            val extraTop = (16 * density).toInt()

            // Use the larger of systemBars or cutout to clear the notch
            val topInset = maxOf(systemBars.top, cutout.top)

            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                topInset + extraTop,
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
            updateMuteUi()
        }

        binding.speakerButton.setOnClickListener { view ->
            showAudioRoutePicker(view)
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

    /**
     * Mute: swap icon only (mic vs mic-off), background stays the same.
     */
    private fun updateMuteUi() {
        val iconRes = if (isMuted) R.drawable.ic_phone_mute_off else R.drawable.ic_phone_mute
        binding.muteIcon.setImageResource(iconRes)
    }

    /**
     * Hold uses the original toggle style (white bg when active) since it
     * represents a clear on/off state the user must notice.
     */
    private fun updateToggleControl(container: View, icon: ImageView, active: Boolean) {
        if (active) {
            container.setBackgroundResource(R.drawable.bg_incall_control_active_rect)
            icon.setColorFilter(0xFF000000.toInt())
        } else {
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CubesInCallService.audioRouteState.collect { audioState ->
                    updateAudioRouteUi(audioState)
                }
            }
        }
    }

    private fun updateAudioRouteUi(audioState: AudioRouteState) {
        val iconRes = when (audioState.currentRoute) {
            CallAudioState.ROUTE_SPEAKER -> R.drawable.ic_phone_speaker
            CallAudioState.ROUTE_BLUETOOTH -> R.drawable.ic_phone_bluetooth
            CallAudioState.ROUTE_WIRED_HEADSET -> R.drawable.ic_phone_headset
            else -> R.drawable.ic_phone_earpiece
        }

        val label = when (audioState.currentRoute) {
            CallAudioState.ROUTE_SPEAKER -> getString(R.string.phone_audio_speaker)
            CallAudioState.ROUTE_BLUETOOTH ->
                audioState.bluetoothDeviceName ?: getString(R.string.phone_audio_bluetooth)
            CallAudioState.ROUTE_WIRED_HEADSET -> getString(R.string.phone_audio_wired)
            else -> getString(R.string.phone_audio_earpiece)
        }

        binding.speakerIcon.setImageResource(iconRes)
        binding.speakerLabel.text = label

        // No background change — icon swap is sufficient to show current route
    }

    private fun showAudioRoutePicker(anchor: View) {
        val audioState = CubesInCallService.audioRouteState.value
        val supported = audioState.supportedRoutes
        val current = audioState.currentRoute

        // Build list of available routes with display labels
        val routes = mutableListOf<Pair<Int, String>>() // route constant to display label
        if (supported and CallAudioState.ROUTE_EARPIECE != 0) {
            routes.add(CallAudioState.ROUTE_EARPIECE to getString(R.string.phone_audio_earpiece))
        }
        if (supported and CallAudioState.ROUTE_BLUETOOTH != 0) {
            val btLabel = audioState.bluetoothDeviceName ?: getString(R.string.phone_audio_bluetooth)
            routes.add(CallAudioState.ROUTE_BLUETOOTH to btLabel)
        }
        if (supported and CallAudioState.ROUTE_WIRED_HEADSET != 0) {
            routes.add(CallAudioState.ROUTE_WIRED_HEADSET to getString(R.string.phone_audio_wired))
        }
        if (supported and CallAudioState.ROUTE_SPEAKER != 0) {
            routes.add(CallAudioState.ROUTE_SPEAKER to getString(R.string.phone_audio_speaker))
        }

        // If only 2 routes available, just toggle between them
        if (routes.size <= 2) {
            val nextRoute = routes.firstOrNull { it.first != current }?.first
                ?: routes.firstOrNull()?.first ?: return
            CubesInCallService.instance?.requestAudioRoute(nextRoute)
            return
        }

        // Show popup menu for 3+ routes
        val popup = PopupMenu(this, anchor)
        routes.forEachIndexed { index, (route, label) ->
            popup.menu.add(0, route, index, label)
        }
        popup.setOnMenuItemClickListener { item ->
            CubesInCallService.instance?.requestAudioRoute(item.itemId)
            true
        }
        popup.show()
    }

    private fun formatPhoneNumber(number: String): String {
        if (number.isEmpty()) return number
        return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
    }

    private fun updateUi(state: CallUiState) {
        val formattedNumber = formatPhoneNumber(state.number)

        if (state.callerName.isNotEmpty()) {
            // Contact name exists: show name (large, up to 2 lines) + number below
            binding.callerName.text = state.callerName
            binding.callerNumber.text = formattedNumber
            binding.callerNumber.visibility = if (state.number.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            // No contact name: show number as the main text, hide secondary
            binding.callerName.text = formattedNumber.ifEmpty { getString(R.string.phone_title) }
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
