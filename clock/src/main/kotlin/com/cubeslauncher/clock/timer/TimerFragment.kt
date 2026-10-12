package com.cubeslauncher.clock.timer

import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cubeslauncher.clock.R
import com.cubeslauncher.clock.databinding.FragmentTimerBinding
import com.cubeslauncher.clock.service.TimerService
import com.cubeslauncher.common.theme.AccentColorManager
import java.util.Locale

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private enum class State { INPUT, RUNNING, PAUSED, FINISHED }

    private var state = State.INPUT

    // Input digits buffer (max 6 digits: hhmmss)
    private val inputDigits = StringBuilder()

    // Timer state
    private var totalMillis = 0L
    private var remainingMillis = 0L
    private var countdownStartTime = 0L
    private var remainingAtStart = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (state == State.RUNNING) {
                val elapsed = SystemClock.elapsedRealtime() - countdownStartTime
                remainingMillis = (remainingAtStart - elapsed).coerceAtLeast(0)
                updateTimerDisplay()

                if (remainingMillis <= 0) {
                    state = State.FINISHED
                    onTimerFinished()
                } else {
                    handler.postDelayed(this, 50)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNumpad()
        setupButtons()
        updateInputDisplay()
        showInputMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(countdownRunnable)
        _binding = null
    }

    private fun setupNumpad() {
        val keys = mapOf(
            binding.key0 to "0",
            binding.key1 to "1",
            binding.key2 to "2",
            binding.key3 to "3",
            binding.key4 to "4",
            binding.key5 to "5",
            binding.key6 to "6",
            binding.key7 to "7",
            binding.key8 to "8",
            binding.key9 to "9",
            binding.key00 to "00"
        )

        for ((keyView, digit) in keys) {
            keyView.setOnClickListener {
                if (state == State.INPUT) {
                    appendDigit(digit)
                }
            }
        }

        binding.keyBackspace.setOnClickListener {
            if (state == State.INPUT && inputDigits.isNotEmpty()) {
                inputDigits.deleteCharAt(inputDigits.lastIndex)
                updateInputDisplay()
            }
        }

        binding.keyBackspace.setOnLongClickListener {
            if (state == State.INPUT) {
                inputDigits.clear()
                updateInputDisplay()
            }
            true
        }
    }

    private fun appendDigit(digit: String) {
        if (inputDigits.length + digit.length <= 6) {
            inputDigits.append(digit)
            updateInputDisplay()
        }
    }

    private fun updateInputDisplay() {
        // Pad to 6 digits and format as HH:MM:SS
        val padded = inputDigits.toString().padStart(6, '0')
        val h = padded.substring(0, 2)
        val m = padded.substring(2, 4)
        val s = padded.substring(4, 6)
        binding.timeDisplay.text = String.format("%s:%s:%s", h, m, s)

        // Enable start button only if time > 0
        val totalSeconds = h.toInt() * 3600 + m.toInt() * 60 + s.toInt()
        binding.startButton.isEnabled = totalSeconds > 0
        binding.startButton.alpha = if (totalSeconds > 0) 1.0f else 0.3f
    }

    private fun updateTimerDisplay() {
        val totalSec = remainingMillis / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        binding.timeDisplay.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun setupButtons() {
        val accent = AccentColorManager.getAccentColor()
        binding.startButton.setBackgroundColor(accent)
        binding.startButton.setTextColor(Color.WHITE)

        binding.startButton.setOnClickListener {
            when (state) {
                State.INPUT -> startTimer()
                State.PAUSED -> resumeTimer()
                State.FINISHED -> {
                    // Reset to input mode
                    state = State.INPUT
                    inputDigits.clear()
                    updateInputDisplay()
                    showInputMode()
                }
                else -> { /* no-op for RUNNING */ }
            }
        }

        binding.cancelButton.setOnClickListener {
            when (state) {
                State.RUNNING -> {
                    // Pause
                    state = State.PAUSED
                    handler.removeCallbacks(countdownRunnable)
                    stopTimerService()
                    updateRunningButtons()
                }
                State.PAUSED, State.FINISHED -> {
                    // Cancel / reset
                    state = State.INPUT
                    handler.removeCallbacks(countdownRunnable)
                    stopTimerService()
                    inputDigits.clear()
                    updateInputDisplay()
                    showInputMode()
                }
                State.INPUT -> { /* no-op */ }
            }
        }
    }

    private fun startTimer() {
        val padded = inputDigits.toString().padStart(6, '0')
        val h = padded.substring(0, 2).toInt()
        val m = padded.substring(2, 4).toInt()
        val s = padded.substring(4, 6).toInt()
        totalMillis = (h * 3600L + m * 60L + s) * 1000L
        remainingMillis = totalMillis

        if (totalMillis <= 0) return

        state = State.RUNNING
        countdownStartTime = SystemClock.elapsedRealtime()
        remainingAtStart = remainingMillis
        handler.post(countdownRunnable)

        showRunningMode()
        startTimerService()
    }

    private fun resumeTimer() {
        state = State.RUNNING
        countdownStartTime = SystemClock.elapsedRealtime()
        remainingAtStart = remainingMillis
        handler.post(countdownRunnable)

        updateRunningButtons()
        startTimerService()
    }

    private fun showInputMode() {
        binding.numpadContainer.visibility = View.VISIBLE
        binding.startButton.text = getString(R.string.timer_start)
        binding.cancelButton.visibility = View.GONE
    }

    private fun showRunningMode() {
        binding.numpadContainer.visibility = View.GONE
        binding.startButton.visibility = View.GONE
        binding.cancelButton.visibility = View.VISIBLE
        binding.cancelButton.text = getString(R.string.timer_pause)
    }

    private fun updateRunningButtons() {
        when (state) {
            State.RUNNING -> {
                binding.startButton.visibility = View.GONE
                binding.cancelButton.text = getString(R.string.timer_pause)
            }
            State.PAUSED -> {
                binding.startButton.visibility = View.VISIBLE
                binding.startButton.text = getString(R.string.timer_resume)
                binding.cancelButton.text = getString(R.string.timer_cancel)
            }
            State.FINISHED -> {
                binding.startButton.visibility = View.VISIBLE
                binding.startButton.text = getString(R.string.timer_reset)
                binding.cancelButton.visibility = View.GONE
            }
            State.INPUT -> { /* handled by showInputMode */ }
        }
    }

    private fun onTimerFinished() {
        handler.removeCallbacks(countdownRunnable)
        stopTimerService()
        updateRunningButtons()

        // Play notification sound
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()

            // Stop after 5 seconds
            handler.postDelayed({ ringtone?.stop() }, 5000)
        } catch (_: Exception) { }

        // Vibrate
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    private fun startTimerService() {
        val intent = Intent(requireContext(), TimerService::class.java).apply {
            putExtra("remaining_millis", remainingMillis)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopTimerService() {
        try {
            val intent = Intent(requireContext(), TimerService::class.java)
            requireContext().stopService(intent)
        } catch (_: Exception) { }
    }
}
