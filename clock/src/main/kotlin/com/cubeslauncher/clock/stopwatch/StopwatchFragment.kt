package com.cubeslauncher.clock.stopwatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.clock.R
import com.cubeslauncher.clock.databinding.FragmentStopwatchBinding
import com.cubeslauncher.clock.service.StopwatchService
import com.cubeslauncher.common.theme.AccentColorManager
import java.util.Locale

class StopwatchFragment : Fragment() {

    private var _binding: FragmentStopwatchBinding? = null
    private val binding get() = _binding!!

    private enum class State { STOPPED, RUNNING, PAUSED }

    private var state = State.STOPPED
    private var startTime = 0L
    private var elapsedBeforePause = 0L
    private var lastLapTime = 0L

    data class Lap(val number: Int, val lapTime: Long, val totalTime: Long)
    private val laps = mutableListOf<Lap>()
    private lateinit var lapsAdapter: LapsAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (state == State.RUNNING) {
                updateDisplay()
                handler.postDelayed(this, 10)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStopwatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lapsAdapter = LapsAdapter(laps)
        binding.lapsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.lapsRecycler.adapter = lapsAdapter

        binding.startStopButton.setOnClickListener { onStartStopClicked() }
        binding.lapResetButton.setOnClickListener { onLapResetClicked() }

        updateButtons()
        updateDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateRunnable)
        _binding = null
    }

    private fun onStartStopClicked() {
        when (state) {
            State.STOPPED -> {
                // Start
                state = State.RUNNING
                startTime = SystemClock.elapsedRealtime()
                elapsedBeforePause = 0L
                lastLapTime = 0L
                laps.clear()
                lapsAdapter.notifyDataSetChanged()
                handler.post(updateRunnable)
                startStopwatchService(StopwatchService.ACTION_START, 0L)
            }
            State.RUNNING -> {
                // Pause
                state = State.PAUSED
                elapsedBeforePause += SystemClock.elapsedRealtime() - startTime
                handler.removeCallbacks(updateRunnable)
                startStopwatchService(StopwatchService.ACTION_PAUSE, 0L)
            }
            State.PAUSED -> {
                // Resume
                state = State.RUNNING
                startTime = SystemClock.elapsedRealtime()
                handler.post(updateRunnable)
                startStopwatchService(StopwatchService.ACTION_RESUME, elapsedBeforePause)
            }
        }
        updateButtons()
    }

    private fun onLapResetClicked() {
        when (state) {
            State.RUNNING -> {
                // Lap
                val totalElapsed = elapsedBeforePause + (SystemClock.elapsedRealtime() - startTime)
                val lapTime = totalElapsed - lastLapTime
                lastLapTime = totalElapsed
                laps.add(0, Lap(laps.size + 1, lapTime, totalElapsed))
                lapsAdapter.notifyDataSetChanged()
            }
            State.PAUSED -> {
                // Reset
                state = State.STOPPED
                elapsedBeforePause = 0L
                lastLapTime = 0L
                laps.clear()
                lapsAdapter.notifyDataSetChanged()
                updateDisplay()
                startStopwatchService(StopwatchService.ACTION_STOP, 0L)
            }
            State.STOPPED -> { /* no-op */ }
        }
        updateButtons()
    }

    private fun updateDisplay() {
        val elapsed = when (state) {
            State.STOPPED -> 0L
            State.PAUSED -> elapsedBeforePause
            State.RUNNING -> elapsedBeforePause + (SystemClock.elapsedRealtime() - startTime)
        }
        binding.timeDisplay.text = formatTime(elapsed)
    }

    private fun updateButtons() {
        when (state) {
            State.STOPPED -> {
                binding.startStopButton.text = getString(R.string.stopwatch_start)
                binding.lapResetButton.text = getString(R.string.stopwatch_lap)
                binding.lapResetButton.isEnabled = false
                binding.lapResetButton.alpha = 0.3f
            }
            State.RUNNING -> {
                binding.startStopButton.text = getString(R.string.stopwatch_stop)
                binding.lapResetButton.text = getString(R.string.stopwatch_lap)
                binding.lapResetButton.isEnabled = true
                binding.lapResetButton.alpha = 1.0f
            }
            State.PAUSED -> {
                binding.startStopButton.text = getString(R.string.stopwatch_start)
                binding.lapResetButton.text = getString(R.string.stopwatch_reset)
                binding.lapResetButton.isEnabled = true
                binding.lapResetButton.alpha = 1.0f
            }
        }

        // Apply accent color to start/stop button when running
        val accent = AccentColorManager.getAccentColor()
        if (state == State.RUNNING) {
            binding.startStopButton.setBackgroundColor(accent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync with service state if it's running in the background
        if (StopwatchService.isRunning) {
            state = State.RUNNING
            startTime = StopwatchService.serviceStartTime
            elapsedBeforePause = StopwatchService.serviceElapsedBeforePause
            handler.post(updateRunnable)
            updateButtons()
        }
    }

    private fun startStopwatchService(action: String, elapsedBeforePause: Long) {
        val ctx = context ?: return
        val intent = Intent(ctx, StopwatchService::class.java).apply {
            this.action = action
            if (action == StopwatchService.ACTION_RESUME) {
                putExtra(StopwatchService.EXTRA_ELAPSED_BEFORE_PAUSE, elapsedBeforePause)
            }
        }
        if (action == StopwatchService.ACTION_PAUSE || action == StopwatchService.ACTION_STOP) {
            ctx.startService(intent)
        } else {
            ContextCompat.startForegroundService(ctx, intent)
        }
    }

    companion object {
        fun formatTime(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val centis = (millis % 1000) / 10
            return String.format(Locale.getDefault(), "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        }
    }
}
